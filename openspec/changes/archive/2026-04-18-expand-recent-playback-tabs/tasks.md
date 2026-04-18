## 1. Repository 与模型
- [x] 1.1 扩展用户中心远端数据源，接入歌曲、视频、声音、歌单、专辑和播客 6 个最近播放接口
- [x] 1.2 接入本机最近听歌缓存数据源，记录真实歌曲播放行为且不依赖服务端
- [x] 1.3 为本机最近听歌缓存建立最多 100 条的保留与裁剪规则
- [x] 1.4 新增最近播放多类型 UI model，并保留本地 / 歌曲类型的独立模型
- [x] 1.5 为视频、声音、歌单、专辑和播客补齐最小可用解析字段

## 2. ViewModel 与缓存
- [x] 2.1 引入 `RecentPlaybackTab` 和每 tab 独立 content state
- [x] 2.2 调整 tab 顺序为本地、歌曲、视频、声音、歌单、专辑、播客
- [x] 2.3 默认加载本地 tab，其它 tab 按需加载
- [x] 2.4 保留已加载 tab 的缓存，切换回来不丢数据
- [x] 2.5 本地 tab 与远端 tab 切换时复用各自缓存
- [x] 2.6 刷新只重拉当前 tab，不影响其它 tab 已缓存结果

## 3. 页面渲染
- [x] 3.1 将最近播放页改造成 7 个 tab 的最近播放总页
- [x] 3.2 页面列表骨架参考搜索结果页
- [x] 3.3 本地 tab 放在最前并作为默认优先展示 tab
- [x] 3.4 本地 / 歌曲 tab 保留现有能力，其它 tab 首版只渲染不做功能
- [x] 3.5 为每个 tab 提供独立 loading / empty / error 表达

## 4. 测试与验证
- [x] 4.1 更新 repository 定向测试，覆盖本机最近听歌缓存数据源和新增 recent endpoint 请求
- [x] 4.2 更新 ViewModel 测试，覆盖默认本地 tab、按需加载、100 条上限与缓存
- [x] 4.3 更新 UI 测试，覆盖本地 tab 排序、默认选中、多 tab 渲染与非歌曲 tab 的只读行为
- [x] 4.4 跑 `:app:testDebugUnitTest`
- [x] 4.5 跑 `:playback-service:testDebugUnitTest`
- [x] 4.6 跑 `:app:assembleDebug`
