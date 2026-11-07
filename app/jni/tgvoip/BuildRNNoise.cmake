# rnnoise

set(RNNOISE_DIR "${TGCALLS_DEPS_DIR}/rnnoise")

add_library(rnnoise STATIC
  "${RNNOISE_DIR}/src/celt_lpc.c"
  "${RNNOISE_DIR}/src/denoise.c"
  "${RNNOISE_DIR}/src/kiss_fft.c"
  "${RNNOISE_DIR}/src/pitch.c"
  "${RNNOISE_DIR}/src/rnn_data.c"
  "${RNNOISE_DIR}/src/rnn_reader.c"
  "${RNNOISE_DIR}/src/rnn_reader.c"
  "${RNNOISE_DIR}/src/rnn.c"
)
target_include_directories(rnnoise PUBLIC
  "${RNNOISE_DIR}/include"
)

# RNNoise vendors an Opus FFT implementation. Group audio pulls both it and
# libopus into tgcallsjni; keep RNNoise's FFT definitions and callers isolated.
target_compile_definitions(rnnoise PRIVATE
  opus_fft_c=rnnoise_opus_fft_c
  opus_fft_impl=rnnoise_opus_fft_impl
  opus_ifft_c=rnnoise_opus_ifft_c
)
