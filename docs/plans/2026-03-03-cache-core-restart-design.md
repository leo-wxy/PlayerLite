# Cache Core 重建设计（V1）

- 日期：2026-03-03
- 状态：已确认（可进入实现）
- 分支：`codex/cache-core-restart`

## 1. 目标与硬约束

本次为缓存功能重建，目标是先把可用能力做出来，并避免上轮职责混乱与不可控改动。

已确认硬约束：

1. V1 必须支持 `MEMORY_AND_DISK`。
2. V1 必须支持重启后缓存复用。
3. `resourceKey` 由业务传入（`mediaId`），直接作为磁盘文件名，不做转义/哈希。
4. 缓存关键运行时错误默认中断播放，不自动降级无缓存。
5. 数据源范围仅 `http/https`。
6. 核心架构采用 Native-first（跨平台 C++ 核心），V1 先在 Android 完整落地。
7. 接口边界采用 `CacheCore` + `CacheSession`，禁止全局会话读写绕过。

## 2. 架构与边界

### 2.1 模块划分

1. `cache-core-native`（C++）：
- 负责缓存核心逻辑：会话管理、块映射、内存/磁盘缓存、索引恢复、清理。
- 不依赖 Android 平台 API。

2. `cache-core-android`（JNI + Kotlin 薄封装）：
- 负责 JNI 桥接、参数校验、错误码映射、线程边界。
- 对上层暴露 Kotlin API。

3. `playback-service` 接入层：
- 提供 `CachedNetworkSource`，适配 `IPlaysource`。
- 注入 `RangeDataProvider` 实现网络读取。

### 2.2 职责边界

- `CacheCore`：全局生命周期与会话工厂（`init/shutdown/openSession`）。
- `CacheSession`：单资源读写（`read/seek/cancelPendingRead/close`）。
- 不提供 `CacheCore.read(sessionId, ...)` 这类全局越权接口。

## 3. 数据流与行为语义

### 3.1 读取路径

`CacheSession.read` 命中顺序固定：

1. 内存块命中
2. 磁盘块命中
3. 网络缺块读取（Range）

设计要求：

- 单次 read 允许部分返回，不要求读满请求大小。
- 避免单次同步 read 长阻塞导致卡顿。

### 3.2 seek 与取消

- `seek` 只更新游标，不主动预读。
- seek 触发前必须取消 in-flight 网络读取。
- `RangeDataProvider.cancelInFlightRead()` 必须可中断阻塞中的读。
- 取消语义为“已取消/可重试”，不可误判为 EOF。

### 3.3 并发语义

- 同 session 内游标更新与读流程串行化。
- 同 `resourceKey + blockIndex` in-flight miss 去重。
- 禁止多请求重复下载同一块。

## 4. 磁盘布局（按业务约束）

以 `resourceKey` 直接命名：

1. `cacheRoot/<resourceKey>.data`
- 缓存字节数据文件。

2. `cacheRoot/<resourceKey>_config.json`
- 缓存控制信息（至少包含）：
  - `version`
  - `resourceKey`
  - `contentLength`
  - `durationMs`（可选）
  - `blockSizeBytes`
  - `blocks`
  - `completedRanges`
  - `lastAccessEpochMs`

3. `cacheRoot/<resourceKey>_extra.json`
- 业务扩展字段与调试信息。

写盘一致性：

- `.data` 和 `*_config.json` 使用临时文件 + 原子替换策略。
- 进程重启时按 `*_config.json` + `.data` 恢复。
- 索引损坏或数据不一致按错误返回，终止播放。

## 5. 对外 API（V1）

### 5.1 CacheCore

- `init(config): Result<Unit>`
- `shutdown(): Unit`
- `openSession(params): Result<CacheSession>`
- `getResourceCache(resourceKey): Result<ResourceCacheEntry>`

### 5.2 CacheSession

- `read(size: Int): Result<ByteArray>`
- `readAt(offset: Long, size: Int): Result<ByteArray>`
- `seek(offset: Long, whence: Int): Result<Long>`
- `cancelPendingRead(): Unit`
- `close(): Unit`

### 5.3 RangeDataProvider

- `readAt(offset: Long, size: Int): ByteArray`
- `cancelInFlightRead(): Unit`
- `queryContentLength(): Long?`
- `close(): Unit`

## 6. 错误处理

错误码按域分层：

- `E_INIT_*`
- `E_SESSION_*`
- `E_PROVIDER_*`
- `E_IO_*`
- `E_CONFIG_*`
- `E_CORRUPT_*`

策略：

- 缓存关键路径错误返回失败，由 `playback-service` 终止播放。
- 不做自动 no-cache 降级。

## 7. 测试与验收标准

### 7.1 单元测试必须覆盖

1. `CacheSession` 多实例隔离。
2. seek 触发取消后可快速切到新 offset 读取。
3. partial-read 语义（非读满返回）。
4. `*_config.json` 恢复路径与损坏路径。
5. 错误码映射准确性。

### 7.2 集成测试必须覆盖

1. `playback-service` 接入后可播放 http/https 资源。
2. 缓存写入后重启进程可命中缓存。
3. seek 时不会被旧网络请求长期阻塞。

## 8. 非目标（V1 不做）

1. iOS 端落地（仅保证 C++ 核心跨平台可迁移）。
2. 自动降级策略。
3. 高级预取自适应策略。
4. 多协议支持（除 http/https 外）。
