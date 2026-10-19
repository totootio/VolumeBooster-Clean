# VolumeBooster Clean

纯净版系统音量增强器：仅保留 **LoudnessEnhancer 全局音频增益** 核心功能，
无广告、无统计、无推送、无网络请求。

## 功能

- 底部单手控制条：− / 滑条 / + 与预设面包块（-40% / -20% / 0 / +10% / +20% / +30%）
- 增益范围 **-40% ~ +30%**（-3.0 dB ~ +2.25 dB，负值为衰减），`LoudnessEnhancer` session 0 全局生效
- 三级引擎回退：LoudnessEnhancer → Equalizer → DynamicsProcessing（含 limiter 防过载）
- 前台服务常驻，App 退后台增益持续生效；启动默认开启，总开关一键启停（3 秒防抖）
- 白天 / 黑夜双皮肤，`values-night` 跟随系统深浅色
- minSdk 19 / targetSdk 33，零第三方依赖

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

签名密钥通过 secrets 在 CI 中还原，与本地共用同一把正式发布密钥，保证各版本可覆盖升级：

| Secret | 内容 |
|---|---|
| `VB_KEYSTORE_BASE64` | base64 编码的 `vb.keystore`（PKCS12，CN=VolumeBooster, OU=Release, O=totootio, C=CN） |
| `VB_KEYSTORE_PASS` | 密钥库/密钥密码（本地构建时通过同名环境变量传入） |

密钥缺失时 CI 会回退为临时自签名密钥并给出 warning。本地构建：

```bash
export VB_KEYSTORE_PASS=<密钥密码>
./build.sh
```

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
