# VaultZip

[English README](README.md)

VaultZip 是一个面向 Android 的压缩包工具，当前重点覆盖解压、文件预览、分卷处理，以及最小可用的 ZIP 压缩流程。

## 功能

### 解压
- 打开并解压常见压缩格式
- 当前原生归档链路已覆盖：
  - RAR
  - ZIP / ZIPX
  - 7Z
  - TAR
  - TGZ / GZ
- 支持分卷识别与处理
- 支持加密压缩包密码输入流程
- 支持整包解压与单文件解出

### 预览
- 支持文本文件预览
- 支持大图预览
- 支持 PDF 前 10 页翻页预览

### 压缩
- 主界面已拆分为“解压 / 压缩”两个标签页
- 通过 SAF 选择多个输入文件
- 创建标准 `.zip` 压缩包
- 通过 `ACTION_CREATE_DOCUMENT` 选择输出位置
- 压缩时显示进度与状态

## 技术栈

- Kotlin
- Android Views + Fragments
- MVVM
- Hilt
- WorkManager
- JNI / NDK / CMake
- 7-Zip 源码
- UnRAR 源码
- SAF（`content://` URI）

## 项目结构

- `app/src/main/java/com/vaultzip/ui/main` — 主界面与顶层导航
- `app/src/main/java/com/vaultzip/ui/extract` — 解压页面
- `app/src/main/java/com/vaultzip/ui/compress` — ZIP 压缩页面
- `app/src/main/java/com/vaultzip/ui/preview` — 预览页与预览组件
- `app/src/main/java/com/vaultzip/archive` — 解压领域、仓库、bridge、模型
- `app/src/main/java/com/vaultzip/compress` — 压缩仓库与模型
- `app/src/main/cpp` — JNI 与原生归档路由
- `third_party/7zip` — vendored 7-Zip 源码
- `third_party/unrar` — vendored UnRAR 源码

## 环境要求

- Android Studio / Android SDK
- JDK 17
- Android NDK `28.0.13004108`
- `compileSdk 35`
- `minSdk 26`

## 构建

如果你的环境里已经有可用的 Gradle：

```bash
gradle -p . :app:assembleDebug
```

如果你使用本地 Gradle 分发包，也可以直接指定对应的 Gradle 可执行文件。

当前仓库还没有提交 Gradle Wrapper，所以构建时需要：
- 本机已安装 Gradle，或
- 使用本地已有的 Gradle 分发路径

## 运行

生成的 debug APK 默认在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 当前范围

已实现：
- 压缩包解压
- 分卷处理
- 密码输入流程
- 文件预览
- ZIP 压缩 MVP

暂未实现：
- 7z 压缩创建
- 加密 ZIP 创建
- 分卷压缩创建
- 高级压缩参数

## 说明

- 解压流程当前主要走 native archive backend。
- 当前压缩 MVP 由 Kotlin 侧 `ZipOutputStream` 实现。
- 文件访问使用 SAF，因此可直接处理系统文件选择器返回的 `content://` 资源。
