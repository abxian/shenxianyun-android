# 神仙云 Android 客户端

这是神仙云安卓客户端，基于 Clash Meta for Android 改造。目标是保留稳定的原版 VPN/TUN 开关和 Mihomo 内核能力，同时把使用流程简化为提取码订阅、一键启动、节点选择。

## 当前功能

- 提取码订阅
  - 用户首次打开后输入后台提取码。
  - 客户端自动请求 `https://sub.jc116.com/api/verify/<code>`。
  - 验证成功后自动创建 URL 订阅配置，保存提取码、配置 UUID、过期时间、订阅更新版本。
  - 同一设备首次导入同一提取码时，后台记录一次提取次数。
  - 自动更新订阅不重复记录提取次数。
- 代理开关
  - 使用原版 Clash Meta for Android 的 VPN 启停能力。
  - 提取码未过期时允许开启代理。
  - 提取码过期后禁止开启代理。
  - 已保存过期时间后，断网时也按本地过期时间判断。
- 订阅更新
  - 客户端定时请求 `/api/update-state/<code>`。
  - 后台推送更新后，客户端静默更新订阅。
  - 客户端启动时检查 APK 更新。
- 在线统计
  - VPN 启动后向 `/api/client/heartbeat/<code>` 上报在线。
  - VPN 停止后向 `/api/client/offline/<code>` 上报离线。
- 一键导入
  - 支持网页打开 `shenxianyun://install-config?url=<订阅地址>&name=<名称>`。
  - 安卓端会自动解析订阅地址中的提取码，保存并导入，不再进入原配置编辑页。
- UI 简化
  - 首页突出启动按钮、模式切换、提取码、节点选择。
  - 续费/新购入口合并，已有提取码时进入续费，没有提取码时进入新购。

## 目录结构

```text
clashmeta-android/
  build.gradle.kts                         # 全局版本号、构建配置
  local.properties                         # 本地 Android SDK 路径，不提交
  app/
    src/main/AndroidManifest.xml           # 应用、协议、Activity 注册
    src/main/java/com/github/kr328/clash/
      MainActivity.kt                      # 神仙云主流程、提取码、更新、心跳
      ExternalControlActivity.kt           # shenxianyun:// 一键导入
  design/
    src/main/res/layout/design_main.xml    # 首页布局
    src/main/res/values/strings.xml        # 文案
  core/                                    # Mihomo/Clash 内核桥接
  service/                                 # VPN 服务、配置处理
```

## 环境要求

推荐环境：

- JDK 21
- Android Studio 或 Android SDK Command-line Tools
- Android SDK Platform 35
- Android Build Tools
- Android NDK 29.0.14206865
- CMake
- Go，项目内核构建会用到
- Git

本机 `local.properties` 示例：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

## 检查环境

```powershell
java -version
.\gradlew.bat --version
```

如果 Gradle 能正常输出版本，说明 JDK 和 Wrapper 基本可用。

## 修改版本号

版本号在根目录 `build.gradle.kts`：

```kotlin
versionName = "2.11.31"
versionCode = 211031
```

给用户推送 APK 更新时，后台 `latest_version_code` 必须大于用户当前安装版本的 `versionCode`。

## 本地编译测试包

```powershell
cd C:\Users\fucku\Desktop\vpn\clashmeta-android
.\gradlew.bat :app:assembleMetaDebug
```

产物路径：

```text
app/build/outputs/apk/meta/debug/
```

推荐上传通用包：

```text
cmfa-2.11.31-meta-universal-debug.apk
```

也可以按 CPU 架构分发：

```text
arm64-v8a      大部分新安卓手机
armeabi-v7a    老安卓手机
x86/x86_64     模拟器或少数设备
```

## 编译 Release 包

需要签名文件。创建 `signing.properties`：

```properties
keystore.path=C\:\\path\\to\\shenxianyun.jks
keystore.password=your-store-password
key.alias=your-key-alias
key.password=your-key-password
```

然后执行：

```powershell
.\gradlew.bat :app:assembleMetaRelease
```

如果只是本地测试或后台 APK 更新测试，可以先用 Debug 包。正式分发建议使用固定签名的 Release 包，否则用户可能无法覆盖安装。

## GitHub Actions 编译

安卓仓库可以用 GitHub Actions 编译 APK。基本步骤：

1. checkout
2. setup JDK 21
3. setup Android SDK、NDK、CMake
4. 写入 `local.properties`
5. 如果构建 Release，写入签名文件和 `signing.properties`
6. 执行 `./gradlew :app:assembleMetaDebug` 或 `assembleMetaRelease`
7. 上传 `app/build/outputs/apk/meta/**.apk`

私有仓库 Actions 可能受 GitHub 计费限制影响。本地编译不受影响。

## 后台接口

默认后台地址在 `MainActivity.kt` 和 `ExternalControlActivity.kt`：

```kotlin
const val SUBSCRIPTION_BASE_URL = "https://sub.jc116.com"
```

客户端使用：

```text
GET /api/verify/<code>
GET /api/update-state/<code>
GET /api/app-version
GET /api/client/heartbeat/<code>
GET /api/client/offline/<code>
GET /sub/<code>
```

首次输入提取码和网页一键导入都会请求：

```text
/api/verify/<code>?import=1&client_id=<设备ID>
```

后台使用 `client_id` 去重，所以同一设备同一提取码只计一次。

## 一键导入协议

AndroidManifest 注册：

```text
shenxianyun://install-config
```

网页按钮格式：

```text
shenxianyun://install-config?url=<encoded subscription url>&name=<encoded name>
```

`ExternalControlActivity.kt` 会：

1. 解析 `url`。
2. 从 `/sub/<code>` 或 `?code=<code>` 中提取提取码。
3. 验证提取码并记录首次导入。
4. 创建或更新当前订阅。
5. 打开主界面。

## 常见修改点

- 改后台域名：`MainActivity.kt`、`ExternalControlActivity.kt`。
- 改版本号：根目录 `build.gradle.kts`。
- 改首页 UI：`design/src/main/res/layout/design_main.xml`。
- 改首页逻辑：`app/src/main/java/com/github/kr328/clash/MainActivity.kt`。
- 改一键导入：`ExternalControlActivity.kt`。
- 改应用名称和图标：`design/src/main/res/values/strings.xml`、`app/src/main/res/mipmap-*`。
- 改 VPN 服务行为：`service/` 目录。

## 发布前检查

1. 安装 APK。
2. 首次输入提取码，确认后台提取次数加 1。
3. 再次打开或自动更新订阅，确认次数不重复增加。
4. 点击启动，系统弹出 VPN 授权并能联网。
5. Chrome 打开网页确认 VPN 不自动退出。
6. 后台在线客户端页面能看到安卓设备。
7. 停止代理后后台状态离线。
8. 网页 `一键导入神仙云` 能打开 APP 并自动导入。
9. 过期提取码不能启动代理。

## 当前版本

当前本地版本：

```text
versionCode: 211031
versionName: 2.11.31.Meta.debug
```

后台 APK 更新配置里的 `latest_version_code` 要使用 `211031` 或更高。
