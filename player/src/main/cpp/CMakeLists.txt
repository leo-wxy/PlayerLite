cmake_minimum_required(VERSION 3.22.1)

project(player_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(player SHARED
    ffmpeg_decoder.cpp
    ffmpeg_player.cpp
    jni_play_source.cpp
    player.cpp
)

# Keep Android default naming: libplayer.so
set_target_properties(player PROPERTIES
    OUTPUT_NAME "player"
)

target_link_libraries(player PRIVATE log)

set(FFMPEG_SUBMODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/FFmpeg-n6.1.4")
set(FFMPEG_INCLUDE_DIR "${FFMPEG_SUBMODULE_ROOT}/out/${ANDROID_ABI}/include")
set(FFMPEG_LIB_FILE "${FFMPEG_SUBMODULE_ROOT}/out-jniLibs/${ANDROID_ABI}/libffmpeg.so")

set(FFMPEG_READY TRUE)

if(NOT EXISTS "${FFMPEG_INCLUDE_DIR}/libavformat/avformat.h")
    set(FFMPEG_READY FALSE)
endif()

if(NOT EXISTS "${FFMPEG_LIB_FILE}")
    set(FFMPEG_READY FALSE)
endif()

if(FFMPEG_READY)
    target_compile_definitions(player PRIVATE HAVE_FFMPEG=1)
    target_include_directories(player PRIVATE "${FFMPEG_INCLUDE_DIR}")

    add_library(ffmpeg SHARED IMPORTED)
    set_target_properties(ffmpeg PROPERTIES
        IMPORTED_LOCATION "${FFMPEG_LIB_FILE}"
    )
    target_link_libraries(player PRIVATE ffmpeg)
else()
    message(WARNING "FFmpeg headers/libs not found. player.so will be built with a stub decoder.\n"
        "Expected headers: ${FFMPEG_INCLUDE_DIR}\n"
        "Expected lib: ${FFMPEG_LIB_FILE}")
endif()
