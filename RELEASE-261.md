# 261 正式发布 · 2026-10-29 / Codex

用户明确要求“发布新版本”，承接260测试版的首页/到期显示、本机setting、续费额度恢复和订阅迁移、Mac卡住/圆形按钮、两列节点与自动项置顶。本轮PC和Android独立发布，不含Apple商店或52nm。

Android2.11.51 / versionCode211051，准确产品f06b0c634acdd3c292c5e8ef500cfae0d4aa5f79，合main并创建v2.11.51；本轮相对已验260产品只递增build.gradle.kts版本和docs/RELEASE_2.11.51.md，不把相同2.11.50测试包改名冒称新版。

[正式Release](https://github.com/abxian/shenxianyun-android/releases/tag/v2.11.51)，ID427791384，准确正式CI40866073173成功、GitHub latest为2.11.51。main触发的重复APK40866069527/预览40866069533取消，不作为交付。8初始资产匿名全文size/SHA/GitHub digest一致；7APK均正式证书f30240ae494e087087490729f21b804f1b46b4474d1ec2f642ea6e1eb58ca410及v1/v2/v3验签通过、包名com.jc116.shenxianyun/211051，分发ARM64和通用ABI准确。XML和恢复集成守卫通过；assembleMetaRelease/lintVital来自正式CI，不冒称完整独立lint或真实设备VPN已过。

Dufs神仙云.apk和shenxianyunall.apk用同PC sync.lock，NAS原件下载核SHA后两包旧文件备份并原子替换，PCupdate.json核前后未变。恢复点/vol2/docker-projects-backups/shenxianyun-android-release261-20261029-194652-124823，manifest afddaf58...，两个公共URL当时已匿名全文HTTP200/大小/SHA对准确原件一致。后续发现sxy旧DNS坏线路，261单域名候选修复和最终默认域名读回另记，不修改本APK或凭据。

普通首页绑定提取码后断开/运行均显示到期时间，未知显示待同步；继续不展示套餐已用/剩余量，不把本机转发字节当套餐统计。本轮没有安卓setting入口（本机模式仅PC），恢复检查/原凭据/迁移逻辑沿此前257/260，不修改后台期限/额度。

桌面神仙云正式版-20261029的arm64-v8a/universal新211051包SHA已核；不自动安装用户设备。旧正式/255/256/257/260里程碑和Git基线保留无裁剪。工具/源改动快照和校验在公开白名单证据，rc/ops/release261-20261029；实际设备覆盖安装、VPN和真实续费恢复单列未验。

版本API/项目记忆与完整发布收尾见本报告末尾及ops/release261-20261029最终JSON回执。

## 最终公开入口与升级提示验收

PC下载站已原子切到2.5.47，默认DNS及TLS下14个PC安装器/更新文件URL验证，完整update与sig一致、大包Content-Range总长度/首尾对原件，NAS全文件SHA与准确GitHub匿名全文原件一致。按评估358不再通过受限生产NPS重复搬运约700MB；此前重复Dufs全传输已取消，不算通过。Android两包此前完整公共HTTP200/SHA通过，GitHub四updater最新原件签名/版本/架构一致。默认域名与NAS均可返回新版metadata，Cloudflare/GoogleDoH回读健康221，旧112如自愈仍按原健康阈值管理；没有永久禁用其它站点。

升级提示已同步：PC2.5.47 / 安卓2.11.51(211051)。site_config只更新desktop七项/app四项，写前在线SQLite备份完整性/SHA，BEGIN IMMEDIATE内再次核旧值再单事务写11键；无schema/业务源码/期限额度/设备/订单变更，不重启subfile。公开/api/desktop-version和/api/app-version、NAS只读11键/DB完整性和容器healthy、app.py源SHA一致均通过。备份/vol2/docker-projects-backups/vpn-web-release261-20261029-212430-632330，DB SHA256 eead86b28ddd43c69647736137e2bdaf4476bc502d5622492649182cbdb1570f；回滚只按私有manifest恢复11键且拒覆盖后续管理员更改，禁止整库覆盖新业务数据。原PC元数据实际为2.5.41，升级到2.5.47；不是假定从2.5.46变。

准确源产品标签保持63b56278/f06b0c6；后续仅报告和公开工具文档提交。发布工具与范围/前置/参数/风险/回滚见证据ZIP，工具下载/归档只在回读SHA后写成功。Trilium项目/实际AI记忆和基础设施记录以本次最终marker及回执为准，内部只读审核与用户设备验收独立。
