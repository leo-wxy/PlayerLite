#!/usr/bin/env bash
set -euo pipefail

# Based on /Users/wxy/Downloads/FFmpeg-n6.1.4/build.sh

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="${FFMPEG_DIR:-$PROJECT_ROOT/third_party/FFmpeg-n6.1.4}"

ABI="${ABI:-all}"
API="${API:-21}"

if [ ! -d "$SRC_DIR" ]; then
  printf "ERROR: FFmpeg source not found at %s\n" "$SRC_DIR" >&2
  printf "If git is enabled, initialize submodule:\n" >&2
  printf "  git submodule update --init --recursive\n" >&2
  exit 1
fi

if [ "$ABI" = "all" ]; then
  for target_abi in arm64-v8a armeabi-v7a; do
    printf "\n===== Building %s =====\n" "$target_abi"
    ABI="$target_abi" API="$API" ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}" FFMPEG_DIR="$SRC_DIR" bash "$0"
  done

  printf "\nBuilt libraries:\n"
  printf "  %s\n" "$SRC_DIR/out-jniLibs/arm64-v8a/libffmpeg.so"
  printf "  %s\n" "$SRC_DIR/out-jniLibs/armeabi-v7a/libffmpeg.so"
  exit 0
fi

case "$ABI" in
  arm64-v8a)
    ARCH=aarch64
    TRIPLE=aarch64-linux-android
    MIN_API=21
    ABI_CONFIG_FLAGS=""
    ABI_CFLAGS=""
    ;;
  armeabi-v7a)
    ARCH=arm
    TRIPLE=armv7a-linux-androideabi
    MIN_API=21
    ABI_CONFIG_FLAGS="--cpu=armv7-a --enable-neon"
    ABI_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=neon"
    ;;
  *)
    printf "ERROR: unsupported ABI: %s\n" "$ABI" >&2
    printf "Supported ABIs: arm64-v8a, armeabi-v7a, all\n" >&2
    exit 1
    ;;
esac

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  printf "ERROR: ANDROID_NDK_HOME is not set\n" >&2
  exit 1
fi

if [ "$API" -lt "$MIN_API" ]; then
  printf "ERROR: API level must be >= %s for %s\n" "$MIN_API" "$ABI" >&2
  exit 1
fi

if [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-arm64" ]; then
  HOST_TAG=darwin-arm64
elif [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64" ]; then
  HOST_TAG=darwin-x86_64
else
  printf "ERROR: unsupported NDK host tag under %s/toolchains/llvm/prebuilt\n" "$ANDROID_NDK_HOME" >&2
  exit 1
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
SYSROOT="$TOOLCHAIN/sysroot"
PREFIX="$SRC_DIR/out/$ABI"
BUILD_DIR="$SRC_DIR/.build-$ABI"
CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
CXX="$TOOLCHAIN/bin/${TRIPLE}${API}-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
STRIP="$TOOLCHAIN/bin/llvm-strip"

rm -rf "$PREFIX" "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

EXTRA_CFLAGS="-O3 -fPIC -ffunction-sections -fdata-sections"
if [ -n "$ABI_CFLAGS" ]; then
  EXTRA_CFLAGS="$EXTRA_CFLAGS $ABI_CFLAGS"
fi

cd "$BUILD_DIR"
"$SRC_DIR/configure" \
  --prefix="$PREFIX" \
  --arch="$ARCH" \
  --target-os=android \
  --enable-cross-compile \
  --sysroot="$SYSROOT" \
  --cc="$CC" \
  --cxx="$CXX" \
  --ar="$AR" \
  --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
  --nm="$TOOLCHAIN/bin/llvm-nm" \
  --strip="$STRIP" \
  --disable-shared --enable-static --enable-pic \
  --disable-programs --disable-doc \
  --disable-avdevice --enable-avfilter --disable-postproc --disable-swscale \
  --disable-network \
  --disable-asm \
  --disable-vulkan \
  $ABI_CONFIG_FLAGS \
  --disable-symver \
  --enable-small \
  --disable-everything \
  --enable-avutil --enable-avcodec --enable-avformat --enable-avfilter --enable-swresample \
  --enable-protocol=file --enable-protocol=pipe \
  --enable-demuxer=mov --enable-demuxer=matroska --enable-demuxer=ogg \
  --enable-demuxer=mp3 --enable-demuxer=wav --enable-demuxer=flac --enable-demuxer=aac \
  --enable-parser=aac --enable-parser=mpegaudio --enable-parser=opus --enable-parser=vorbis --enable-parser=flac \
  --enable-decoder=aac --enable-decoder=mp3float --enable-decoder=opus --enable-decoder=vorbis --enable-decoder=flac --enable-decoder=alac \
  --enable-decoder=pcm_s16le --enable-decoder=pcm_s16be --enable-decoder=pcm_u8 \
  --enable-decoder=pcm_s24le --enable-decoder=pcm_s32le --enable-decoder=pcm_f32le \
  --enable-filter=abuffer --enable-filter=abuffersink --enable-filter=atempo \
  --extra-cflags="$EXTRA_CFLAGS" \
  --extra-ldflags="-Wl,--gc-sections" \
  --extra-libs="-lm -lz -llog"

make -j"$(getconf _NPROCESSORS_ONLN)"
make install
cd "$SRC_DIR"

MERGE_AVUTIL="$PREFIX/lib/libavutil.merge.a"
cp "$PREFIX/lib/libavutil.a" "$MERGE_AVUTIL"
"$AR" d "$MERGE_AVUTIL" vulkan.o tx_float_neon.o 2>/dev/null || true

"$CC" -shared -o "$PREFIX/lib/libffmpeg.so" \
  -Wl,-soname,libffmpeg.so \
  -Wl,--gc-sections \
  -Wl,--whole-archive \
  "$PREFIX/lib/libavformat.a" \
  "$PREFIX/lib/libavcodec.a" \
  "$PREFIX/lib/libavfilter.a" \
  "$PREFIX/lib/libswresample.a" \
  "$MERGE_AVUTIL" \
  -Wl,--no-whole-archive \
  -lm -lz -llog

"$STRIP" "$PREFIX/lib/libffmpeg.so"
rm -f "$MERGE_AVUTIL"

JNI_OUT_DIR="$SRC_DIR/out-jniLibs/$ABI"
mkdir -p "$JNI_OUT_DIR"
cp "$PREFIX/lib/libffmpeg.so" "$JNI_OUT_DIR/libffmpeg.so"

printf "Built: %s\n" "$PREFIX/lib/libffmpeg.so"
printf "Synced: %s\n" "$JNI_OUT_DIR/libffmpeg.so"
