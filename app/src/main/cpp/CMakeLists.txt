cmake_minimum_required(VERSION 3.22.1)

project(SteamDeckMobile)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

# Minimal XServer components for Steam display
# Include drawable.c, xconnector_epoll.c, and gpu_image.c for XServer functionality
add_library(winlator SHARED
            winlator/drawable.c
            winlator/xconnector_epoll.c
            winlator/gpu_image.c)

target_link_libraries(winlator
                      log
                      android
                      jnigraphics
                      EGL
                      GLESv2)

# uinput controller bridge - Creates virtual Xbox 360 controller via /dev/uinput
# Native implementation for low-latency controller input to Wine games
add_library(uinput_bridge SHARED
            uinput/uinput_bridge.c
            uinput/uinput_jni.c)

target_link_libraries(uinput_bridge log)

# PRoot - Linux chroot environment for Wine/Box64
# Built as executable (libproot.so) not library, following Winlator approach
add_subdirectory(proot)