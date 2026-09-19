cmake_minimum_required(VERSION 3.22.1)

project("abhilekh-native")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Native library
add_library(
    abhilekh-native
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_link_libraries(
    abhilekh-native
    ${log-lib}
    ${jnigraphics-lib}
)
