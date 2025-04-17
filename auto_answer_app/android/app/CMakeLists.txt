cmake_minimum_required(VERSION 3.10.2)

# Set the project name
project(auto_answer_app)

# Set the C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable visibility of all native symbols by default
set(CMAKE_C_VISIBILITY_PRESET default)
set(CMAKE_CXX_VISIBILITY_PRESET default)

# Define NDEBUG in release mode
if(NOT CMAKE_BUILD_TYPE MATCHES Debug)
  add_definitions(-DNDEBUG)
endif()

# Set ABI-specific optimization flags
if(ANDROID_ABI STREQUAL "armeabi-v7a")
  # Optimization flags for 32-bit ARM
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfloat-abi=softfp -mfpu=neon")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp -mfpu=neon")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
  # Optimization flags for 64-bit ARM
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
endif()

# This is a placeholder CMake configuration
# It helps ensure proper handling of native libraries across different architectures
message(STATUS "Configured for ABI: ${ANDROID_ABI}") 