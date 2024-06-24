# Makefile for native JNI libraries. To be built with ndk-build.
#
# To view the commands that will be run when building the application, run
#
#     ndk-build -B --dry-run
#
# NOTE: Do not use `$(wildcard ...)` in this script! It makes the linking order
#       non-deterministic.

LOCAL_PATH       := $(call my-dir)

TARGET_PLATFORM  := android-33

# libnacl

include $(CLEAR_VARS)

LOCAL_MODULE     := nacl-jni

LOCAL_SRC_FILES  := $(LOCAL_PATH)/nacl/salsa20-jni.c
LOCAL_SRC_FILES  += $(LOCAL_PATH)/nacl/poly1305-jni.c
LOCAL_SRC_FILES  += $(LOCAL_PATH)/nacl/curve25519-jni.c
LOCAL_SRC_FILES  += $(LOCAL_PATH)/nacl/smult_donna.c
LOCAL_SRC_FILES  += $(LOCAL_PATH)/nacl/smult_donna-c64.c

LOCAL_LDFLAGS    += -Wl,--build-id=none  # Reproducible builds

include $(BUILD_SHARED_LIBRARY)
