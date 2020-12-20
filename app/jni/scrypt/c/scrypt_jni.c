// Copyright (C) 2011 - Will Glozer.  All rights reserved.

#include <errno.h>
#include <stdlib.h>
#include <inttypes.h>

#include <jni.h>
#include "crypto_scrypt.h"

#ifdef ANDROID

#include <android/log.h>
#include <stdint.h>

#define ANDROID_LOG_TAG "ScryptLog"
#define ALOG(msg, ...) __android_log_print(ANDROID_LOG_VERBOSE, ANDROID_LOG_TAG, msg, ##__VA_ARGS__)

#define STR1(x)  #x
#define STR(x)  STR1(x)

void log_basic_info();

#endif

jbyteArray JNICALL scryptN(JNIEnv *env, jclass cls, jbyteArray passwd, jbyteArray salt,
    jint N, jint r, jint p, jint dkLen)
{

#ifdef ANDROID
  log_basic_info();
#endif

    jint Plen = (*env)->GetArrayLength(env, passwd);
    jint Slen = (*env)->GetArrayLength(env, salt);
    jbyte *P = (*env)->GetByteArrayElements(env, passwd, NULL);
    jbyte *S = (*env)->GetByteArrayElements(env, salt,   NULL);
    uint8_t *buf = malloc(sizeof(uint8_t) * dkLen);
    jbyteArray DK = NULL;

    if (P == NULL || S == NULL || buf == NULL) goto cleanup;

    if (crypto_scrypt((uint8_t *) P, Plen, (uint8_t *) S, Slen, N, r, p, buf, dkLen)) {
        jclass e = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        char *msg;
        switch (errno) {
            case EINVAL:
                msg = "N must be a power of 2 greater than 1";
                break;
            case EFBIG:
            case ENOMEM:
                msg = "Insufficient memory available";
                break;
            default:
                msg = "Memory allocation failed";
        }
        (*env)->ThrowNew(env, e, msg);
        goto cleanup;
    }

    DK = (*env)->NewByteArray(env, dkLen);
    if (DK == NULL) goto cleanup;

    (*env)->SetByteArrayRegion(env, DK, 0, dkLen, (jbyte *) buf);

  cleanup:

    if (P) (*env)->ReleaseByteArrayElements(env, passwd, P, JNI_ABORT);
    if (S) (*env)->ReleaseByteArrayElements(env, salt,   S, JNI_ABORT);
    if (buf) free(buf);

    return DK;
}

#ifdef ANDROID

char *get_byte_array_summary(JNIEnv *env, jbyteArray jarray) {
  int len = (*env)->GetArrayLength(env, jarray);
  jbyte *bytes = (*env)->GetByteArrayElements(env, jarray, NULL);

  static char buff[10240];
  int i;
  for (i = 0; i < len; ++i) {
    buff[i] = bytes[i] % 32 + 'a';
  }
  buff[i] = '\0';

  if (bytes) (*env)->ReleaseByteArrayElements(env, jarray, bytes, JNI_ABORT);

  return buff;
}

void log_basic_info() {
  ALOG("Basic info for native scrypt run:");
  ALOG("Native library targeting arch: %s", STR(ANDROID_TARGET_ARCH));
}

#endif

static const JNINativeMethod methods[] = {
    { "scryptN", "([B[BIIII)[B", (void *) scryptN }
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass cls = (*env)->FindClass(env, "com/lambdaworks/crypto/SCrypt");
    int r = (*env)->RegisterNatives(env, cls, methods, 1);

    return (r == JNI_OK) ? JNI_VERSION_1_6 : -1;
}
