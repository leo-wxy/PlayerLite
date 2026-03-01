# Native 缓存库框架设计（方案 2 定稿）

- 日期：2026-03-01
- 状态：已确认（可进入实现计划）
- 目标：实现独立 Native 缓存库框架，支持 HTTP 直链音频的 Source 字节流缓存

## 1. 背景与目标

本设计用于播放器网络 Source 的字节流缓存能力建设，重点目标：

1. 首播速度优先
2. 支持重启后复用（磁盘缓存）
3. 支持会话级别按需切换（仅内存 / 内存+磁盘）
4. 缓存能力与 `:player` 模块解耦，便于替换不同缓存实现
5. 缓存清理可由业务强控制，并提供完善回调

## 2. 模块边界与依赖

### 2.1 模块划分

- 新增独立模块：`:cache-core`
- `:cache-core` 包含 Kotlin + C++ + JNI，输出独立 native 库（例如 `libcachecore.so`）
- `:player` 不引入 cache 依赖，不改现有 `IPlaysource`/`IPlaySource` 协议

### 2.2 依赖方向

1. `:player` -> 无 cache 依赖
2. `:cache-core` -> 无 `:player` 依赖
3. `app`/`playback-service` -> 同时依赖 `:player` 与 `:cache-core`
4. 业务层组装具体 Source（例如 `CachedNetworkSource`），并将其传给播放器

### 2.3 网络能力边界

- `:cache-core` 只定义 SPI（`RangeDataProvider`）
- 网络拉取由业务实现并注入
- `:cache-core` 内部在 cache miss 时调用 SPI，不内置 OkHttp/HTTP 实现

## 3. 请求级缓存模型

### 3.1 会话创建

业务层通过 `resourceKey + config` 打开会话：

```kotlin
CacheCore.openSession(
    OpenSessionParams(
        resourceKey = "...",
        provider = rangeDataProvider,
        config = SessionCacheConfig(...),
        metadata = mapOf("scene" to "...")
    )
)
```

### 3.2 请求级参数

`OpenSessionParams`：

1. `resourceKey: String`（必填）
2. `provider: RangeDataProvider`（必填）
3. `config: SessionCacheConfig?`（可选，覆盖全局）
4. `metadata: Map<String, String>?`（可选）
5. `resourceInfoHint`（可选，如 contentLength/etag）

`SessionCacheConfig`：

1. `cacheMode: MEMORY_ONLY | MEMORY_AND_DISK`
2. `blockSizeBytes: Int?`（会话级自定义块大小）
3. `closeSessionPolicy: IMMEDIATE | FLUSH_WITH_TIMEOUT | FLUSH_ALL`
4. `prefetchConfig`（窗口/阈值/自适应开关）

默认关闭策略：`IMMEDIATE`。

## 4. 数据流与并发模型

### 4.1 读路径

1. `read`/`readDirect` -> `cacheSession.readSequential(size)`
2. session 将顺序读映射为 `readAt(offset, size)`
3. 命中顺序：内存 -> 磁盘 -> 网络 provider（miss）
4. miss 拉回后先给前台读返回可消费数据，再由策略管理持久化流程

### 4.2 seek 语义

1. `seek` 仅更新游标，不主动触发下载
2. 下次 `read` 按新游标读取
3. `SEEK_SIZE` 可通过已知元信息或 provider 查询

### 4.3 分片模型（支持自定义块大小）

1. 块大小优先级：`session.blockSizeBytes` > `global.defaultBlockSizeBytes`
2. 每个 `resourceKey` 固化 `blockSizeBytes` 到索引
3. 同 key 块大小不一致时：返回 `CONFIG_MISMATCH` 或按配置重建
4. 建议约束：`64KB~2MB`、4KB 对齐

### 4.4 并发控制

1. 同一 session 前台读串行化
2. 同一 `resourceKey + blockIndex` in-flight 去重
3. 预取任务与前台读分队列，前台优先

### 4.5 混合预取策略

1. 默认边播边取
2. 高频 seek / 高 miss / 网络抖动时扩大预取窗口
3. 稳定播放后降级回基础窗口

## 5. 全局初始化与配置

### 5.1 全局初始化

```kotlin
CacheCore.init(config)
```

按进程初始化（`app` 与 `:playback` 进程独立）。

### 5.2 全局配置

`CacheCoreConfig` 关键字段：

1. `cacheRootDir`
2. `defaultBlockSizeBytes`
3. `defaultCloseSessionPolicy`（默认 `IMMEDIATE`）
4. `cleanupPolicy`（可选，业务注入）
5. `cleanupCandidateSelector`（可选，业务阻塞式决策）
6. `eventListener`
7. `memoryBudgetBytes`
8. `maxDiskBytes`（默认建议 300MB）
9. `highWatermarkPercent`（默认 90）
10. `lowWatermarkPercent`（默认 70）

### 5.3 异常配置容错

- 配置异常不阻断初始化
- 回退默认值并打印 warning
- 典型异常：阈值越界、`low >= high`、块大小非法等

## 6. 清理机制与业务控制

### 6.1 水位清理

1. 当 `usedBytes >= maxDiskBytes * highWatermarkPercent` 触发清理
2. 清理至 `usedBytes <= maxDiskBytes * lowWatermarkPercent` 停止

### 6.2 默认与自定义策略

1. 未传清理策略：内置 `LRU`
2. 传入自定义策略：按业务策略执行

### 6.3 阻塞式候选决策（业务回调）

- cache-core 先生成候选池（默认按 LRU 排序）
- 阻塞调用业务 selector，传入候选 `RemovedCacheItem` 列表
- 业务返回允许删除的 `resourceKeys`
- 底层据此执行真实删除

### 6.4 清理回调（聚合，不逐项）

1. `onCleanupTriggered`
2. `onCleanupBatchCompleted`（包含 `removedItems: List<RemovedCacheItem>`）
3. `onCleanupCompleted`
4. `onCleanupFailed`

## 7. 清理不影响当前播放的保障

1. `active pin`：活跃会话的 `resourceKey` 引用计数 > 0 时禁止物理删除
2. 清理前过滤活跃 key
3. 活跃项标记 `DEFERRED`，会话结束后再删
4. 两阶段删除：`DELETING` 标记 -> 物理删除 -> 索引提交
5. 回调中区分 `deletedItems / skippedActiveItems / deferredItems`

默认行为：`EXCLUDE_ACTIVE + DEFERRED`。

## 8. 事件模型

缓存回调至少覆盖：

1. `onChunkAdded`（支持标注 MEMORY/DISK）
2. `onResourceCompleted`（仅全部 `DISK_READY`）
3. `onResourceDeleted`
4. `onCacheError`
5. 清理相关批量事件（见第 6.4）

回调原则：异步派发，不阻塞 read 主路径；同 key 顺序可控。

## 9. 错误处理与降级

### 9.1 错误码分层

1. `INIT_*`
2. `SESSION_*`
3. `CONFIG_*`
4. `PROVIDER_*`
5. `CACHE_IO_*`
6. `CLEANUP_*`

### 9.2 降级策略

1. cache-core 初始化失败：业务回退 `NoCacheSource`
2. provider 连续失败：可降级直读网络源
3. `MEMORY_AND_DISK` 异常：可按会话降级到 `MEMORY_ONLY`

## 10. 测试与验收

### 10.1 单元测试

1. 分片映射与 offset 计算
2. 配置异常回退默认值
3. 阻塞式候选回调与超时兜底
4. `closeSessionPolicy` 三模式

### 10.2 集成测试

1. `MEMORY_ONLY` / `MEMORY_AND_DISK` 切换
2. 重启后 `DISK_READY` 命中
3. selector 返回 key 后删除正确性
4. `clearAll` 批量回调 payload 正确

### 10.3 验收标准

1. 首播速度优先目标达成（与无缓存基线对比）
2. 二次播放命中率与耗时改善可量化
3. 清理可观测且可控
4. `:player` 保持零缓存依赖

## 11. 非目标（本期不做）

1. 修改 `:player` 模块内部实现
2. 固定绑定某个网络库
3. 自动强制淘汰（业务不允许时）

## 12. 后续步骤

1. 基于本设计输出实现计划（任务拆分、里程碑、风险）
2. 按模块顺序落地：`cache-core` 骨架 -> 会话读写 -> 清理与回调 -> 业务接入
