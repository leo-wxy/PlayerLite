cmake_minimum_required(VERSION 3.22.1)

project(cachecore)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    cachecore
    SHARED
    jni/cache_core_jni_shared.cpp
    jni/cache_core_jni_lifecycle.cpp
    jni/cache_core_jni_session.cpp
    jni/cache_core_jni_query.cpp
    jni/cache_core_jni_clear.cpp
    core/cache_runtime_lifecycle.cpp
    core/cache_runtime_session.cpp
    core/cache_storage_files.cpp
    core/cache_metadata_codec.cpp
    core/cache_read_pipeline.cpp
    core/cache_memory_budget.cpp
    core/cache_lookup.cpp
    core/cache_clear.cpp
    core/cache_provider_bridge.cpp
)

find_library(log-lib log)

target_include_directories(
    cachecore
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    cachecore
    ${log-lib}
)
