# 260 安卓到期时间常驻

2026-10-29 / Codex，用户明确要求首页保持流量隐藏但显示到期时间。原design_main.xml以clashRunning控制到期日期，停VPN首页错误隐藏；现改为绑定activationCode就显示，与VPN开关独立。未返回期限时显示“到期时间：待同步”，不把未知时间冒充长期有效。仍沿既有setActivationExpiresAt和续费/加时同步，不改计费/请求频率/凭据/签名。

入口design/src/main/res/layout/design_main.xml、values/strings.xml、values-zh/strings.xml；scripts/test-access-recovery.py新增静态集成守卫。无安卓setting入口，本轮本机高级模式仅PC；无iOS/后端/生产数据改动。安卓没有新增套餐已用/剩余统计入口，不把本机转发字节当套餐用量；后续额度明细按后台API可用/展示授权单独实现。

XML解析/现有恢复静态守卫通过；完整APK与lintVital使用build-apk-simple.yaml准确本开发分支，源码/结果/签名工件另记，不把源码已改当已安装。基线6024a0f/旧recovery257包保留，revert本次提交并覆盖旧APK回退，不清数据不裁剪恢复点。最终CI/交付状态待补。
