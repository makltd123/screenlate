cmake_minimum_required(VERSION 3.22.1)
project(screenlate_native LANGUAGES C CXX)

# Built for Android by the Gradle build and for the host by CI, so JVM unit tests can load it too.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)

include(FetchContent)
# The release source archive keeps the C++ project in its sentencepiece/ subfolder.
FetchContent_Declare(
    sentencepiece
    URL https://github.com/google/sentencepiece/releases/download/v0.2.2/sentencepiece-0.2.2.tar.gz
    URL_HASH SHA256=3d2b5e824b5622038dc7b490897efe05ebbbb9e7350fc142f3ecc8789ef9bdf6
    SOURCE_SUBDIR sentencepiece
)
FetchContent_MakeAvailable(sentencepiece)

set(SPM_ROOT ${sentencepiece_SOURCE_DIR}/sentencepiece)

add_library(spm_jni SHARED spm_jni.cpp)
# The public header includes "third_party/absl/..." relative to the SentencePiece root.
target_include_directories(spm_jni PRIVATE ${SPM_ROOT}/src ${SPM_ROOT} ${SPM_ROOT}/third_party/abseil-cpp)
target_link_libraries(spm_jni PRIVATE sentencepiece-static)

if(ANDROID)
    target_link_libraries(spm_jni PRIVATE log)
else()
    find_package(JNI REQUIRED)
    target_include_directories(spm_jni PRIVATE ${JNI_INCLUDE_DIRS})
endif()
