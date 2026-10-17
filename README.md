# VolumeBooster Clean

纯净版系统音量增强器：仅保留 **LoudnessEnhancer 全局音频增益** 核心功能，
无广告、无统计、无推送、无网络请求。

## 功能

- 滑块 0~100% → 0~+7.5 dB 全局媒体音量增益（`LoudnessEnhancer`，session 0）
- 前台服务常驻，App 退后台增益持续生效
- 通知栏实时显示当前增益
- minSdk 19 / targetSdk 33，零第三方依赖，APK 约 17 KB

## 本地构建

```bash
export ANDROID_HOME=/path/to/android-sdk
./build.sh
# 产物：VolumeBooster_Clean.apk
```

## 自动发布

推送 `v*` tag 即触发 GitHub Actions 自动编译并发布 Release：

```bash
git tag v1.0 && git push origin v1.0
```

工作流见 `.github/workflows/release.yml`（ubuntu-latest + JDK 17 + Android SDK 33，
aapt2/javac/d8/apksigner 手动构建，softprops/action-gh-release 发布附件）。

## 目录

```
├── AndroidManifest.xml
├── build.sh                  # 可移植构建脚本
├── res/                      # 布局 / 主题 / 图标
├── src/com/clean/volumebooster/
│   ├── MainActivity.java     # 滑块 UI + 偏好存储
│   └── BoosterService.java   # 前台服务 + 增益控制
└── .github/workflows/release.yml
```
