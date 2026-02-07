# 蓝牙键盘应用编译指南

## 方法1: 使用 GitHub Actions 自动编译（推荐）

1. **创建 GitHub 仓库**
   - 在 GitHub 上创建一个新仓库
   - 上传本项目所有文件

2. **触发自动编译**
   - GitHub Actions 会自动开始编译
   - 或者手动进入 Actions 标签页，点击 "Run workflow"

3. **下载 APK**
   - 编译完成后，进入 Actions 页面
   - 点击最新的工作流运行
   - 在 Artifacts 部分下载 `debug-apk` 或 `release-apk`

## 方法2: 本地编译

### 环境要求
- JDK 17+
- Android SDK API 33+
- Gradle 8.2+

### 编译步骤

1. **安装依赖**
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# 下载 Android SDK
mkdir -p ~/Android/Sdk
cd ~/Android/Sdk
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
```

2. **设置环境变量**
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

3. **安装 SDK 组件**
```bash
sdkmanager "platforms;android-33" "build-tools;33.0.0"
```

4. **编译项目**
```bash
./gradlew assembleDebug
```

5. **获取 APK**
- 调试版: `app/build/outputs/apk/debug/app-debug.apk`
- 发布版: `app/build/outputs/apk/release/app-release-unsigned.apk`

## 方法3: 使用 Android Studio

1. 下载并安装 [Android Studio](https://developer.android.com/studio)
2. 打开项目文件夹
3. 等待 Gradle 同步完成
4. 点击菜单 "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"
5. APK 文件将生成在 `app/build/outputs/apk/debug/` 目录

## 安装说明

1. 将 APK 文件传输到 Android 手机
2. 在手机上启用 "允许安装未知来源应用"
3. 安装 APK
4. 授予蓝牙和位置权限

## 使用方法

1. 打开应用
2. 点击 "连接" 按钮
3. 在电脑蓝牙设置中搜索 "Android BT Keyboard"
4. 完成配对后即可使用
