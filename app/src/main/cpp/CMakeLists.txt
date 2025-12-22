cmake_minimum_required(VERSION 3.22.1)

project(SteamDeckMobile)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

# Minimal XServer components for Steam display
# Include drawable.c and xconnector_epoll.c for XServer functionality
add_library(winlator SHARED
            winlator/drawable.c
            winlator/xconnector_epoll.c)

target_link_libraries(winlator
                      log
                      android
                      jnigraphics)

# PRoot - Linux chroot environment for Wine/Box64
# Built as executable (libproot.so) not library, following Winlator approach
add_subdirectory(proot)