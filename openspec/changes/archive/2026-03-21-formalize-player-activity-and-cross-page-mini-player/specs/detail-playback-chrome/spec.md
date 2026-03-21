## ADDED Requirements

### Requirement: 播放感知详情页在底部提供共享 mini player chrome
系统 SHALL 为专辑、歌手和歌单详情页提供共享的底部 mini player chrome；当当前播放上下文存在有效选中项时，该 chrome MUST 显示在详情页底部作为独立 overlay，而不是继续由各页面分别拼接或完全缺失。

#### Scenario: 当前存在播放项时展示共享 detail mini player
- **WHEN** 用户位于专辑、歌手或歌单详情页
- **AND** 当前播放上下文存在有效选中项
- **THEN** 系统在页面底部展示共享 detail mini player chrome
- **AND** 该 chrome 使用统一的播放信息与控制语义

#### Scenario: 当前没有播放项时不展示 detail mini player
- **WHEN** 用户位于专辑、歌手或歌单详情页
- **AND** 当前播放上下文不存在有效选中项
- **THEN** 系统不展示 detail mini player chrome
- **AND** 页面底部不保留无意义的空白播放器占位

### Requirement: detail mini player 作为底部 overlay 不遮挡正文内容
系统 SHALL 让 detail mini player 作为独立底部 overlay 悬浮在详情页内容之上，并为正文内容预留稳定的底部可见空间，避免最后一项内容、吸顶 tab 或其他正文元素被播放器遮挡。

#### Scenario: 展示 detail mini player 时正文预留底部空间
- **WHEN** 当前详情页展示底部 detail mini player
- **THEN** 系统为正文滚动内容预留与 overlay 占位相匹配的底部空间
- **AND** 最后一项可滚动内容不会被 detail mini player 遮挡

#### Scenario: detail mini player 不改变正文顶部吸顶基准
- **WHEN** 当前详情页展示底部 detail mini player 且用户继续上滑正文内容
- **THEN** 系统保持正文顶部吸顶逻辑仍以顶部 chrome 为基准
- **AND** 不会因为底部 mini player 的存在改变顶部吸顶计算结果

### Requirement: detail mini player 复用统一播放器入口与局部控制
系统 SHALL 让 detail mini player 的主体点击、播放控制和播放列表入口遵循统一行为：主体打开独立播放器页，播放按钮仅切换播放状态，播放列表按钮在当前详情页直接展开共享播放列表 sheet。

#### Scenario: 点击 detail mini player 主体打开独立播放器页
- **WHEN** 用户点击 detail mini player 的主体区域
- **THEN** 系统打开独立 `PlayerActivity`
- **AND** 不会把当前详情页本地切成完整播放器页

#### Scenario: 点击 detail mini player 播放按钮仅切换播放状态
- **WHEN** 用户点击 detail mini player 的播放或暂停按钮
- **THEN** 系统仅切换当前播放状态
- **AND** 不会因此自动离开当前详情页

#### Scenario: 点击 detail mini player 列表按钮在当前页展开播放列表
- **WHEN** 用户点击 detail mini player 的播放列表入口
- **THEN** 系统在当前详情页底部展开共享播放列表 sheet
- **AND** 当前详情页不会因此自动跳转到独立 `PlayerActivity`

#### Scenario: 在 detail mini player 歌曲展示区横滑切歌
- **WHEN** 用户在 detail mini player 的歌曲展示区域进行满足阈值的横向滑动
- **AND** 当前播放队列存在可切换的上一首或下一首
- **THEN** 系统切换到对应的上一首或下一首
- **AND** 不会因此直接打开独立播放器页
