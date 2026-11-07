# The pinned upstream implementation encodes frame zero for language detection,
# then encodes it again at the same context length. Keep its cross-attention
# cache for the first decode when the two encoder inputs are identical.
set(whisper_cpp "${frogram_whisper_source_SOURCE_DIR}/src/whisper.cpp")
file(READ "${whisper_cpp}" whisper_source)
set(detect_old [=[    // auto-detect language if not specified
    if (params.language]=])
set(detect_new [=[    const int language_audio_ctx = state->exp_n_audio_ctx;
    bool reuse_language_encoder = false;
    // auto-detect language if not specified
    if (params.language]=])
set(language_old [=[        params.language = whisper_lang_str(lang_id);

        WHISPER_LOG_INFO]=])
set(language_new [=[        params.language = whisper_lang_str(lang_id);
        reuse_language_encoder = params.offset_ms / 10 == 0 &&
            language_audio_ctx == params.audio_ctx && params.encoder_begin_callback == nullptr;

        WHISPER_LOG_INFO]=])
set(encode_old [=[        if (!whisper_encode_internal(*ctx, *state, seek, params.n_threads, params.abort_callback, params.abort_callback_user_data)) {
            WHISPER_LOG_ERROR("%s: failed to encode\n", __func__);
            return -6;
        }]=])
set(encode_new [=[        if (reuse_language_encoder) {
            reuse_language_encoder = false;
            if (params.abort_callback && params.abort_callback(params.abort_callback_user_data)) {
                return -6;
            }
        } else if (!whisper_encode_internal(*ctx, *state, seek, params.n_threads, params.abort_callback, params.abort_callback_user_data)) {
            WHISPER_LOG_ERROR("%s: failed to encode\n", __func__);
            return -6;
        }]=])
if (NOT whisper_source MATCHES "bool reuse_language_encoder = false;")
  foreach (part detect language encode)
    string(FIND "${whisper_source}" "${${part}_old}" patch_offset)
    if (patch_offset EQUAL -1)
      message(FATAL_ERROR "Unexpected whisper.cpp source for ${part} encoder patch")
    endif()
    string(REPLACE "${${part}_old}" "${${part}_new}" whisper_source "${whisper_source}")
  endforeach()
  file(WRITE "${whisper_cpp}" "${whisper_source}")
endif()
