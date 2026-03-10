## 自动验证

- `./gradlew :user:testDebugUnitTest :playback-contract:testDebugUnitTest :playback-service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

## 手动检查

- 未登录启动应用后，确认会优先进入 `LoginActivity`，且页面上存在明确的“跳过”入口。
- 在 `LoginActivity` 点击“跳过”后返回主界面，确认主界面不再展示当前“未登录”占位卡片。
- 跳过登录后直接选择本地文件或已有本地列表项，确认本地播放不受登录影响。
- 在 `LoginActivity` 确认欢迎标题、副标题与右上角“跳过”入口可见，且登录操作区整体位于页面中部。
- 在 `LoginActivity` 确认右上角“跳过”为较大的描边按钮，单手点击区域明显大于普通文字按钮。
- 在 `LoginActivity` 分别切换到手机号和邮箱登录，确认两种模式都会展示正确输入项并能提交。
- 在 `LoginActivity` 输入手机号和密码完成登录，返回主界面后确认账号入口仍可继续打开登录页/账户页。
- 在 `LoginActivity` 切换到邮箱登录并输入邮箱和密码完成登录，确认也能建立会话并返回主界面。
- 登录成功后返回主界面，确认右上角账户入口显示在线头像；若头像为空或加载失败，确认安全回退为默认人像图标。
- 登录后在 `LoginActivity` 点击“退出登录”，确认回到未登录状态；重新启动应用时仍会先进入登录页，但可以再次跳过。
- 关闭并重启应用后，确认已登录状态可恢复；若服务端会话失效，确认状态安全降级为未登录。
