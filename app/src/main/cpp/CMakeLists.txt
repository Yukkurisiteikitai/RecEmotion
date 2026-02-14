cmake_minimum_required(VERSION 3.22.1)
project(recemotion_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found. Clone it into app/src/main/cpp/llama.cpp")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE ${LLAMA_DIR})

target_link_libraries(llama_jni
    PRIVATE
        llama
        log
)
