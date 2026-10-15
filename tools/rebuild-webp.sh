#!/usr/bin/env bash
# Reproduce libwebp 1.4.0 for both supported Android ABIs.
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to an SDK with NDK 28.2.13676358 and CMake 3.22.1}"
: "${WEBP_OUTPUT_DIR:?Set WEBP_OUTPUT_DIR to the generated native directory}"
webp_work="${WEBP_WORK_DIR:-$(mktemp -d)}"
webp_commit=845d5476a866141ba35ac133f856fa62f0b7445f
mkdir -p "$webp_work"
curl --fail --location --retry 3 "https://github.com/webmproject/libwebp/archive/$webp_commit.tar.gz" -o "$webp_work/source.tar.gz"
echo '746aa87589ca248d7dd9755fd3aad5368a8e26531e5e85636da8399f13885e7a  source.tar.gz' | (cd "$webp_work" && sha256sum --check)
tar -xzf "$webp_work/source.tar.gz" -C "$webp_work"
webp_source="$webp_work/libwebp-$webp_commit"
cmake_bin="$ANDROID_HOME/cmake/3.22.1/bin/cmake"
for webp_abi in arm64-v8a x86_64; do
  webp_build="$webp_work/build-$webp_abi"
  "$cmake_bin" -S "$webp_source" -B "$webp_build" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_HOME/ndk/28.2.13676358/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$webp_abi" -DANDROID_PLATFORM=android-29 \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384' \
    -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF \
    -DWEBP_BUILD_GIF2WEBP=OFF -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF \
    -DWEBP_BUILD_WEBPINFO=OFF -DWEBP_BUILD_WEBPMUX=OFF -DWEBP_BUILD_EXTRAS=OFF
  "$cmake_bin" --build "$webp_build" --parallel 2
  mkdir -p "$WEBP_OUTPUT_DIR/overrides/$webp_abi"
  for webp_name in sharpyuv webp webpdecoder webpdemux webpmux; do
    webp_target="$WEBP_OUTPUT_DIR/overrides/$webp_abi/lib$webp_name.so"
    cp "$webp_build/lib$webp_name.so" "$webp_target"
    "$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$webp_target"
    chmod 644 "$webp_target"
  done
done
mkdir -p "$WEBP_OUTPUT_DIR/assets"
cp "$webp_source/COPYING" "$WEBP_OUTPUT_DIR/assets/COPYING.libwebp"
python3 - "$WEBP_OUTPUT_DIR/overrides" <<'PY'
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1])
checksums={str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(root.glob('*/*.so'))}
(root/'checksums.json').write_text(json.dumps(checksums,indent=2)+'\n')
print('Built', len(checksums), 'native overrides')
PY
