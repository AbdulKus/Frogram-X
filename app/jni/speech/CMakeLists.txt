include(FetchContent)
# The app disables C++ exceptions globally; ggml/whisper require them.
# Directory scope keeps the rest of the app's native flags unchanged.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_Declare(frogram_whisper_source
  URL https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/4979e04f5dcaccb36057e059bbaed8a2f5288315
  URL_HASH SHA256=ec8e33f4c838ed459ad0a23da255c0262f150a6f54839a17e504ddf1495305d5
)
FetchContent_MakeAvailable(frogram_whisper_source)
include(reuse_language_encoder.cmake)
add_library(frogram_whisper SHARED whisper_jni.cpp)
target_compile_features(frogram_whisper PRIVATE cxx_std_17)
target_link_libraries(frogram_whisper PRIVATE whisper android log)
target_link_options(frogram_whisper PRIVATE "-Wl,-z,max-page-size=16384")
