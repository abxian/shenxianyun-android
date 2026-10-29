# 安卓续费/额度恢复和文件迁移同步（257）

2026-10-29，用户明确本轮同步安卓；承接桌面256，原生Apple继续冻结。

首页在已绑定码过期/流量限制且VPN未运行时显示续费按钮。前台或VPN运行时每24–36秒轻量检查；后台返回将检查时间清零。网络错误退避至5分钟。始终按服务端状态区分expired/traffic_limit/disabled/网络失败，HTTP错误不再吞为null当成功。VPN启动前即使本地日期过期也查服务器；未恢复则不启动，不自动替用户打开已关闭VPN。

受管订阅保持原deviceToken/subscriptionUrl，续费/后台加时/补满额度后下载验证并写入原managed UUID，恢复旧占位到正常订阅；只有成功落地才存opaque revision。revision在下载前取快照，避免把下载过程中产生的新版本提前标已更新。后台迁移到旧时间戳文件/同秒变化通过revision发现；旧服务没有revision则兼容update_version。查询/下载优先NOT_VPN网络，避免受限节点拦住恢复；8秒连接/读取、状态总预算30秒。设备撤销不再自动换票绕过，需要用户明确重新导入。

修改：MainActivity.kt/ManagedProfile.kt/AccessRecoveryPolicy.kt、MainDesign.kt和design_main.xml；scripts/test-access-recovery.py为集成静态守卫，可加--kotlin用Java/kotlinc在临时目录执行真实纯策略8项。无用户配置/网络IO；脚本不等于设备或完整Gradle验证。完整Release APK与lintVital使用GitHub Actions build-apk-simple.yaml，构建来源/签名/下载哈希单独记录，安装生效需新APK。累计流量序列已入账/未入账两类路径保持，未修改计费语义。

回退源码0ae5772和239测试APK保留；无数据迁移/清数据需求，不复制生产码或凭据入测试。Web配套7c4b45b提供结构化失败原因/no-store/revision；配套字段本轮已上线并与新包共同生效。其他站52nm不改。

检查通过accessStateMutex串行，429/503 Retry-After形成冷却，返回前台也不绕过服务端等待；MainActivity可空异常消息的首轮编译错误已修正。


## 2026-10-29测试交付与验证

准确产品源：PC `731cce62453e5d68b7d4bd3a57ad8070483c137e`；Android `991c617895a2be36c8d2a0936e8973c708cd55fb`；Web `f4eba974e38f77ca003d6cab68872cc2b7e8ceea`（业务7c4b45b）。此后文档提交不改变已构建二进制。

PC Frontend40803513399、三平台Clippy40803516602、Mac ARM64/Windows x64/ARM64 Development40803519924全部成功；Android40804837373 assembleMetaRelease与lintVital成功。Android首次570dee5编译错误为Throwable.message可空，357修复后991增加检查互斥与429/503 Retry-After冷却；仅交最终991包。

已部署sxnn subfile5010，宿主/容器app.py SHA256 `218dfbeb4b27e4c89b4baee0853483b8ae7aab661893efdc2e04fbd03b6a08df`一致；Docker healthy/restart0，公网严格TLS状态404/invalid_code/no-store及endpoints200，数据库只读integrityok。部署前禁网无生产挂载298项全过；私有备份/vol2/docker-projects-backups/vpn-web-recovery256-20261029-100219的源码/compose/SQLite在线备份已完整性/SHA校验，23M；vol1约18GiB/vol2约182GiB空闲，无裁剪恢复点。52nm/订单/额度数据/模式schema不改。回退仅恢复旧app.py并标准compose重建，不用旧数据库覆盖现网新订单。复用工具ops/task256/deploy.sh+README准确Git源已下载回读核验。

验证：客户端实际Home回调+调度器17项、原有17项、本地TS/完整lint/Biome/web构建；Android实际纯Kotlin策略8项及静态集成守卫；后台本地与NAS298项/迁移13项；实际Web端点与实际Home回调隔离联调（到期→后台加时、真实额度用尽→补满、A→B低时间戳迁移）通过，原token/device计数均1且imports0，未自动启动VPN。未使用生产码做数据改动，未验证真实付款或真实VPN。

公开独立Pre-release：

- abxian/shenxianyun: https://github.com/abxian/shenxianyun/releases/tag/access-recovery-test-20261029（427413824）
- abxian/shenxianyun-android: https://github.com/abxian/shenxianyun-android/releases/tag/access-recovery-test-20261029（427414224）

非v标签access-recovery-test-20261029；准确产品提交、prerelease/latest=false，正式PC v2.5.46/383155547与Android v2.11.50/380915365保持，不合PC/Android main，不改updater/Dufs。原生Apple仍8fd29aa冻结，正式Mac命名兼容仍241。

| 包 | 字节 | SHA256 |
|---|---:|---|
| Shenxianyun-test-20261029-Windows-ARM64-recovery256.exe | 48251291 | 4470d227e558f6476402eaccc2434858e5132749b5c2911396d41dd24f3aab06 |
| Shenxianyun-test-20261029-macOS-AppleSilicon-recovery256.dmg | 70107673 | 8e2b08af09421eedf13b295eaac51af1615ff5f07c8cbcd5c4fe31fe6ed41344 |
| Shenxianyun-test-20261029-Windows-x64-recovery256.exe | 54228232 | 23fac06c15d714db3f10df49dfb5819a639f499c40f5ebcf203ea3c28d62a37b |
| Shenxianyun-test-20261029-Android-ARM64-recovery257.apk | 24457029 | e922c5c0a0ae5311778a20b309fe3245ab20f24e76703532252009c107184da9 |
| Shenxianyun-test-20261029-Android-universal-recovery257.apk | 72430554 | 2629514c041b782497212e6540f874af15b1f3b79db700c4ca931046926aceb6 |

Mac包神仙云.app / de.sxnn.shenxianyun.desktop / arm64 / 2.5.46+deploytest.1029.731cce6，通过DMG完整性、深度严格签名；ad-hoc无公证。Windows安装器PE结构与CI目标核对；尚无Windows执行证据。Android com.jc116.shenxianyun/2.11.50/211050，ARM64和通用ABI核对，v1/v2/v3验签及正式证书f30240ae494e087087490729f21b804f1b46b4474d1ec2f642ea6e1eb58ca410一致；同版本覆盖安装请核SHA，勿卸载清数据。

下载核验：13个公开初始资产已匿名完整HTTP200/字节/SHA/Release digest一致，结果见公开public-download-receipt.json；回执也上传两页并分别匿名回下载一致。白名单recovery256-257-evidence.zip包含修改源码快照、公开校验/联调/部署工具、前提/参数/范围/回退/manifest哈希，无用户配置/原始凭据日志/私有备份库。工具固定输入变化会使检查失效，修改后需重验。

本机桌面 /Users/cgnay/Desktop/神仙云测试版-20261029/续费恢复与订阅同步-256-257 已复制9文件并SHA回读。当前安装/运行Mac仍fix255，本轮未替换或中断用户App；用户需覆盖安装新包生效。旧fix255/239与源码保留，约142GiB空闲，不裁剪里程碑。实机验收还需网站续费/后台加时补满/切文件/VPN下更新/断网重试/手动断开保持测试；未把构建与模拟标为设备验收。

回执SHA256 `77ecaa1bb198b22ea85dd20c06c6bcfab93348d4d64eae4d36abd4464641eeeb`；证据ZIP SHA256 `78b3ac6a512bc99d43897ce96d3103565492887af814fbace39827d4c315c25a`。
