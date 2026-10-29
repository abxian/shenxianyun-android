# 安卓续费/额度恢复和文件迁移同步（257）

2026-10-29，用户明确本轮同步安卓；承接桌面256，原生Apple继续冻结。

首页在已绑定码过期/流量限制且VPN未运行时显示续费按钮。前台或VPN运行时每24–36秒轻量检查；后台返回将检查时间清零。网络错误退避至5分钟。始终按服务端状态区分expired/traffic_limit/disabled/网络失败，HTTP错误不再吞为null当成功。VPN启动前即使本地日期过期也查服务器；未恢复则不启动，不自动替用户打开已关闭VPN。

受管订阅保持原deviceToken/subscriptionUrl，续费/后台加时/补满额度后下载验证并写入原managed UUID，恢复旧占位到正常订阅；只有成功落地才存opaque revision。revision在下载前取快照，避免把下载过程中产生的新版本提前标已更新。后台迁移到旧时间戳文件/同秒变化通过revision发现；旧服务没有revision则兼容update_version。查询/下载优先NOT_VPN网络，避免受限节点拦住恢复；8秒连接/读取、状态总预算30秒。设备撤销不再自动换票绕过，需要用户明确重新导入。

修改：MainActivity.kt/ManagedProfile.kt/AccessRecoveryPolicy.kt、MainDesign.kt和design_main.xml；scripts/test-access-recovery.py为集成静态守卫，可加--kotlin用Java/kotlinc在临时目录执行真实纯策略8项。无用户配置/网络IO；脚本不等于设备或完整Gradle验证。完整Release APK与lint仍使用GitHub Actions build-apk-simple.yaml，构建来源/签名/下载哈希单独记录，安装生效需新APK。累计流量序列已入账/未入账两类路径保持，未修改计费语义。

回退源码0ae5772和239测试APK保留；无数据迁移/清数据需求，不复制生产码或凭据入测试。Web配套7c4b45b提供结构化失败原因/no-store/revision；未部署的新字段不代表已生效。其他站52nm不改。
