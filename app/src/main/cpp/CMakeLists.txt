cmake_minimum_required(VERSION 3.22.1)
project(blocks_native LANGUAGES CXX)

add_library(blocks_native SHARED
    native_solver.cpp
    solver/board.cpp
    solver/pattern.cpp
    solver/symmetric_pattern.cpp
    solver/search.cpp)
target_include_directories(blocks_native PRIVATE solver)
target_compile_features(blocks_native PRIVATE cxx_std_20)
set_target_properties(blocks_native PROPERTIES CXX_EXTENSIONS OFF)
target_compile_options(blocks_native PRIVATE -Wall -Wextra)
