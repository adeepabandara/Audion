# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("audion")

# Define the RNNoise library name
set(RNNOISE_LIB_NAME rnnoise)

# List all RNNoise source files
set(RNNOISE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/denoise.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/rnn.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/pitch.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/kiss_fft.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/celt_lpc.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/nnet.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/nnet_default.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/parse_lpcnet_weights.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/rnnoise_data.c
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src/rnnoise_tables.c
)

# Create the RNNoise library
add_library(${RNNOISE_LIB_NAME}
    SHARED
    ${RNNOISE_SOURCES}
)

# Set include directories for RNNoise
target_include_directories(${RNNOISE_LIB_NAME}
    PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/include
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/rnnoise/src
)

# Set compiler definitions
target_compile_definitions(${RNNOISE_LIB_NAME}
    PRIVATE
    RNNOISE_BUILD
    DISABLE_DEBUG_FLOAT
)

# Set compiler flags
target_compile_options(${RNNOISE_LIB_NAME}
    PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
    -O3  # Optimization level
)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME}
    SHARED
    native-lib.cpp
)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${RNNOISE_LIB_NAME}
    android
    log
)