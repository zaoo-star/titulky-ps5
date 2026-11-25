# Pro find_package(libevent) v chiaki-ng: libevent se sestavuje spolu s ní (ios/chiaki/CMakeLists.txt, FetchContent).
set(LIBEVENT_INCLUDE_DIRS "${PL_LIBEVENT_INCLUDE_DIRS}")
set(LIBEVENT_LIBRARIES event_core_static)
set(libevent_FOUND TRUE)
