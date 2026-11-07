#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <algorithm>
#include <memory>
#include <string>
#include <thread>
#include <vector>

// Java's single worker owns inference. Cancellation may arrive from any thread.
static std::atomic<bool> cancelled{false};
static std::unique_ptr<whisper_context, decltype(&whisper_free)> modelContext(nullptr, whisper_free);
static std::string loadedModelPath;

extern "C" JNIEXPORT void JNICALL
Java_org_thunderdog_challegram_util_OfflineSpeech_resetNative(JNIEnv *, jclass) {
    cancelled.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_org_thunderdog_challegram_util_OfflineSpeech_cancelNative(JNIEnv *, jclass) {
    cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_org_thunderdog_challegram_util_OfflineSpeech_releaseModelNative(JNIEnv *, jclass) {
  modelContext.reset();
  loadedModelPath.clear();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_thunderdog_challegram_util_OfflineSpeech_transcribeNative(
        JNIEnv *env, jclass, jstring path, jfloatArray audio) {
  try {
    if (cancelled.load()) return nullptr;
    const char *model = env->GetStringUTFChars(path, nullptr);
    if (!model) return nullptr;
    std::string modelPath(model);
    env->ReleaseStringUTFChars(path, model);
    const auto count = env->GetArrayLength(audio);
    if (count <= 0 || count > 16000 * 600) return nullptr;
    if (!modelContext || modelPath != loadedModelPath) {
      modelContext.reset();
      auto config = whisper_context_default_params();
      config.use_gpu = false;
      config.flash_attn = true;
      modelContext.reset(whisper_init_from_file_with_params_no_state(modelPath.c_str(), config));
      loadedModelPath = std::move(modelPath);
    }
    if (!modelContext || cancelled.load()) return nullptr;
    std::unique_ptr<whisper_state, decltype(&whisper_free_state)> state(
        whisper_init_state(modelContext.get()), whisper_free_state);
    if (!state) return nullptr;
    std::vector<float> samples(count);
    env->GetFloatArrayRegion(audio, 0, count, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    auto params = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    params.beam_search.beam_size = 3;
    params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    params.language = "auto";
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.abort_callback = [](void *) { return cancelled.load(); };
    if (whisper_full_with_state(modelContext.get(), state.get(), params, samples.data(), count) != 0 || cancelled.load()) return nullptr;
    std::string text;
    for (int i = 0; i < whisper_full_n_segments_from_state(state.get()); i++) {
        text += whisper_full_get_segment_text_from_state(state.get(), i);
    }
    auto result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
  } catch (...) {
    return nullptr;
  }
}
