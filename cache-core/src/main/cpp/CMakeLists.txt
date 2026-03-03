cmake_minimum_required(VERSION 3.22.1)

project(cachecore)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    cachecore
    SHARED
    cache_core_jni.cpp
    core/cache_runtime_lifecycle.cpp
    core/cache_runtime_session.cpp
)

find_library(log-lib log)

target_link_libraries(
    cachecore
    ${log-lib}
)
