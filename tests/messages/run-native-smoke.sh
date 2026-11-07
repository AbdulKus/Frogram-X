#!/bin/sh
# Linux/Termux host test of the actual JNI bridge, model and cancellation.
set -eu
repo=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
speech_test_dir=${1:?Usage: run-native-smoke.sh /absolute/path/to/test-cache}
mkdir -p "$speech_test_dir"
speech_test_dir=$(CDPATH= cd -- "$speech_test_dir" && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK installation}"
revision=4979e04f5dcaccb36057e059bbaed8a2f5288315
archive="$speech_test_dir/whisper.tar.gz"
if [ ! -f "$archive" ]; then
  curl --fail --location --retry 2 "https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/$revision" -o "$archive"
fi
printf '%s  %s\n' ec8e33f4c838ed459ad0a23da255c0262f150a6f54839a17e504ddf1495305d5 "$archive" | sha256sum --check
if [ ! -d "$speech_test_dir/whisper.cpp-$revision" ]; then
  tar -xzf "$archive" -C "$speech_test_dir"
fi
source="$speech_test_dir/whisper.cpp-$revision"
cmake -S "$source" -B "$speech_test_dir/whisper-build" \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
  -DGGML_CPU_KLEIDIAI=OFF -DWHISPER_BUILD_TESTS=OFF \
  -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF
cmake --build "$speech_test_dir/whisper-build" --target whisper -j 2
c++ -shared -fPIC -fexceptions -std=c++17 \
  -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
  -I "$source/include" -I "$source/ggml/include" \
  "$repo/app/jni/speech/whisper_jni.cpp" \
  "$speech_test_dir/whisper-build/src/libwhisper.a" \
  "$speech_test_dir/whisper-build/ggml/src/libggml.a" \
  "$speech_test_dir/whisper-build/ggml/src/libggml-cpu.a" \
  "$speech_test_dir/whisper-build/ggml/src/libggml-base.a" \
  -pthread -o "$speech_test_dir/libfrogram_whisper.so"
model="$speech_test_dir/ggml-small-q5_1.bin"
if [ ! -f "$model" ]; then
  curl --fail --location --retry 2 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin -o "$model"
fi
printf '%s  %s\n' ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb "$model" | sha256sum --check
mkdir -p "$speech_test_dir/native-tests"
javac -d "$speech_test_dir/native-tests" \
  "$repo/tests/messages/native/OfflineSpeech.java" "$repo/tests/messages/NativeSpeechSmokeTest.java"
java -Xmx512m -cp "$speech_test_dir/native-tests" NativeSpeechSmokeTest \
  "$speech_test_dir/libfrogram_whisper.so" "$model" "$source/samples/jfk.wav"
