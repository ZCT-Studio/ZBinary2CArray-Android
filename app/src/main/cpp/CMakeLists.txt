cmake_minimum_required(VERSION 3.22.1)
project("zbinary2carray_android")

# Enable C++20 for std::filesystem and modern features
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Collect all source files
file(GLOB_RECURSE ZBTCA_SOURCES
    "ZBinary2CArray/*.hpp"
)
file(GLOB_RECURSE CORE_SOURCES
    "core/*.hpp"
    "core/*.cpp"
)

# Main shared library — JNI bridge + core
add_library(zbinary2carray SHARED
    jni_bridge.cpp
    ${CORE_SOURCES}
)

# Include directories
target_include_directories(zbinary2carray PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/ZBinary2CArray
    ${CMAKE_CURRENT_SOURCE_DIR}/core
)

# Link against Android system libraries
target_link_libraries(zbinary2carray
    android
    log
)