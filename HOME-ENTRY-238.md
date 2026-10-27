# 首页与订阅更新重构 #238

2026-10-27，基线4ef5039。未连接：无提取码时直接输入并启动，有提取码时只显示切换提取码与启动。实际ClashStart后展示节点、模式、设置；ClashStop后收起并刷新现有订阅。系统VPN授权和错误提示保留。

首页：design/src/main/res/layout/design_main.xml、design/.../MainDesign.kt；流程：app/.../MainActivity.kt。启动刷新成功才记完成，失败在再次进入Activity时重试；自动刷新排队后核对当前提取码，避免恢复旧账号；网络失败向轮询传播以退避。下载：ManagedProfile.kt优先用ConnectivityManager底层非VPN网络绑定HTTP连接，再尝试默认路径，仅传输失败换路；保留TLS验证，拒绝携带token重定向。仅401允许重验凭据，普通网络失败不轮换令牌。

验证按本分支Build Android APK临时构件记录，不自动发布；VPN运行下底层网络能力与系统权限需真机补验，不承诺断网也能下载。更新成功才commit配置；后台不切走用户手工选中的其他配置，既有行为保留。

回退：正常revert本任务提交，基线4ef5039及现有正式APK保留；不删除用户订阅/数据库，不改版本或签名配置。无NAS部署、无52nm改动。CI结果与剩余验证同步Trilium HyOJQ4yf7QkD、AI账本w4UwkxfgQUFr。
