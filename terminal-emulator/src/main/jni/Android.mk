LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CFLAGS += -std=c23 -Wall -Wextra -Werror -Os -fno-stack-protector -Wl,--gc-sections
LOCAL_MODULE := libtermux
LOCAL_SRC_FILES := termux.c
include $(BUILD_SHARED_LIBRARY)
