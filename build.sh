#!/bin/bash
# 纯净版音量增强器 —— 可移植构建脚本（本地 / GitHub Actions 通用）
# 依赖：JDK + Android SDK（platforms;android-33, build-tools;33.0.2）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$SDK/build-tools/33.0.2"
AJ="$SDK/platforms/android-33/android.jar"

OUT="$ROOT/build"
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/apk"

# 1. 编译资源
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

# 2. 链接（生成 R.java + base APK）
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  --java "$OUT/gen" \
  --min-sdk-version 19 \
  --target-sdk-version 33 \
  --version-code 3 \
  --version-name 1.2 \
  "$OUT/res.zip"

# 3. 编译 Java（--release 8 保证 API 签名兼容；lambda 由 d8 脱糖）
javac -nowarn -encoding UTF-8 \
  --release 8 \
  -cp "$AJ" \
  -d "$OUT/obj" \
  $(find src -name '*.java') $(find "$OUT/gen" -name '*.java')

# 4. dex 化
"$BT/d8" --lib "$AJ" --min-api 19 --release \
  --output "$OUT/apk" \
  $(find "$OUT/obj" -name '*.class')

# 5. 打包 dex 进 APK
cp "$OUT/base.apk" "$OUT/app-unsigned.apk"
(cd "$OUT/apk" && zip -q -X "$OUT/app-unsigned.apk" classes.dex)

# 6. 对齐
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

# 7. 签名（无既有密钥时自动生成自签名密钥）
KS="$ROOT/vb.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias vb -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=VolumeBooster, O=Clean, C=CN"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/VolumeBooster_Clean.apk" "$OUT/app-aligned.apk"

"$BT/apksigner" verify "$ROOT/VolumeBooster_Clean.apk"
echo "BUILD OK: $ROOT/VolumeBooster_Clean.apk"
