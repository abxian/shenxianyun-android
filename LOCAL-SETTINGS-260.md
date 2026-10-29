# 260 安卓到期时间常驻

2026-10-29 / Codex，用户明确要求首页保持流量隐藏但显示到期时间。原design_main.xml以clashRunning控制到期日期，停VPN首页错误隐藏；现改为绑定activationCode就显示，与VPN开关独立。未返回期限时显示“到期时间：待同步”，不把未知时间冒充长期有效。仍沿既有setActivationExpiresAt和续费/加时同步，不改计费/请求频率/凭据/签名。

入口design/src/main/res/layout/design_main.xml、values/strings.xml、values-zh/strings.xml；scripts/test-access-recovery.py新增静态集成守卫。无安卓setting入口，本轮本机高级模式仅PC；无iOS/后端/生产数据改动。安卓没有新增套餐已用/剩余统计入口，不把本机转发字节当套餐用量；后续额度明细按后台API可用/展示授权单独实现。

XML解析/现有恢复静态守卫通过；完整APK与lintVital使用build-apk-simple.yaml准确本开发分支，源码/结果/签名工件另记，不把源码已改当已安装。基线6024a0f/旧recovery257包保留，revert本次提交并覆盖旧APK回退，不清数据不裁剪恢复点。最终准确源CI及工件验证已完成，交付如下。

## 2026-10-29 最终测试交付

准确产品源87c84c9430ae02bd777f4cf2c49ecf954b114a01，build-apk-simple运行40851417233 success，assembleMetaRelease/lintVital成功；不是完整独立lint全部规则验收。XML解析和scripts/test-access-recovery.py静态集成守卫通过，本轮无Kotlin业务逻辑变化。

ARM64和通用两包从准确CI同一工件解出，GitHub digest/字节/SHA先验；分别核arm64-v8a和arm64-v8a/armeabi-v7a/x86/x86_64，签名v1/v2/v3及正式证书SHA256 f30240ae494e087087490729f21b804f1b46b4474d1ec2f642ea6e1eb58ca410通过，包名com.jc116.shenxianyun。仍2.11.50/versionCode211050，用户须根据expiry260文件名及SHA识别，不把相同版本号当旧包。

[本轮安卓测试页](https://github.com/abxian/shenxianyun-android/releases/tag/local-settings-test-20261029)附件有两APK/TESTING/verification/SHA256SUMS/白名单工具及准确修改文件快照。与PC三包一起已复制桌面神仙云测试版-20261029/本机设置与到期时间-260逐文件核SHA，没有自动安装、不清设备数据。旧recovery257可覆盖回退，不裁剪里程碑；正式latest/updater/Dufs未改。

普通首页不新增套餐已用/剩余量；安卓尚无本轮新增的套餐明细UI，不把本机转发计数说成套餐余量；PC高级账户show_usage_status的展示条件也不等于安卓已实现。真机停止/连接与续费加时后到期显示仍需安装设备验收，构建和静态守卫不冒称实机完成；本轮无服务器/iOS/52nm变化。

公开下载补充：两仓非v标签local-settings-test-20261029，PC Release427723255/Android427724174。匿名完整HTTP200下载13个初始附件，全部大小/SHA256/GitHub digest一致；另将public-download-receipt.json上传两处并匿名全文回读一致，共15次下载核验。正式latest仍PC v2.5.46/id383155547、Android v2.11.50/id380915365。两仓标签准确指向各自产品源，不移动main或旧包。
