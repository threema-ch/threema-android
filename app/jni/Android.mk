LOCAL_PATH       := $(call my-dir)

TARGET_PLATFORM  := android-26

include $(CLEAR_VARS)
LOCAL_MODULE     := scrypt

LOCAL_SRC_FILES  := $(wildcard $(LOCAL_PATH)/scrypt/c/*.c)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/scrypt/include

LOCAL_CFLAGS     += -DANDROID -DHAVE_CONFIG_H -DANDROID_TARGET_ARCH="$(TARGET_ARCH)"
LOCAL_CFLAGS     += -D_FORTIFY_SOURCE=2
LOCAL_LDFLAGS    += -lc -llog

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE     := nacl-jni
LOCAL_SRC_FILES  := salsa20-jni.c poly1305-jni.c curve25519-jni.c

include $(BUILD_SHARED_LIBRARY)
