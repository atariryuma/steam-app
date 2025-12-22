/**
 * uinput_jni.c
 *
 * JNI bindings for uinput_bridge.c
 *
 * Maps Java native methods to C functions:
 * - nativeInit() → uinput_init()
 * - nativeCreateVirtualController() → uinput_create_xbox360_controller()
 * - nativeSendButtonEvent() → uinput_send_button_event()
 * - nativeSendAxisEvent() → uinput_send_axis_event()
 * - nativeDestroy() → uinput_destroy()
 *
 * Data marshalling:
 * - jstring → const char* (UTF-8)
 * - jfloat (-1.0 ~ 1.0) → int (-32768 ~ 32767)
 * - jint → int (direct)
 * - jboolean → int (1 or 0)
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>

#define TAG "uinput_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// External functions from uinput_bridge.c
extern int uinput_init();
extern int uinput_create_xbox360_controller(const char* name, int vendor_id, int product_id);
extern int uinput_send_button_event(int button_code, int pressed);
extern int uinput_send_axis_event(int axis_code, int value);
extern void uinput_destroy();

/**
 * JNI: Initialize uinput
 *
 * Java signature:
 * private external fun nativeInit(): Boolean
 *
 * @return JNI_TRUE on success, JNI_FALSE on failure
 */
JNIEXPORT jboolean JNICALL
Java_com_steamdeck_mobile_core_input_NativeUInputBridge_nativeInit(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("nativeInit called");

    int result = uinput_init();
    if (result < 0) {
        LOGE("uinput_init failed (result=%d)", result);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * JNI: Create virtual Xbox 360 controller
 *
 * Java signature:
 * private external fun nativeCreateVirtualController(
 *     name: String,
 *     vendorId: Int,
 *     productId: Int
 * ): Int
 *
 * @param name Controller name (e.g., "Steam Deck Mobile Controller")
 * @param vendor_id Vendor ID (0x045e for Xbox)
 * @param product_id Product ID (0x028e for Xbox 360)
 * @return Controller ID (0) on success, -1 on failure
 */
JNIEXPORT jint JNICALL
Java_com_steamdeck_mobile_core_input_NativeUInputBridge_nativeCreateVirtualController(
    JNIEnv* env,
    jobject thiz,
    jstring name,
    jint vendor_id,
    jint product_id
) {
    const char* name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        LOGE("Failed to convert jstring to const char*");
        return -1;
    }

    LOGI("nativeCreateVirtualController called: name=%s, vendor=0x%04x, product=0x%04x",
         name_str, vendor_id, product_id);

    int result = uinput_create_xbox360_controller(name_str, vendor_id, product_id);

    (*env)->ReleaseStringUTFChars(env, name, name_str);

    if (result < 0) {
        LOGE("uinput_create_xbox360_controller failed (result=%d)", result);
        return -1;
    }

    // Return 0 as controller ID (single controller for now)
    return 0;
}

/**
 * JNI: Send button event
 *
 * Java signature:
 * private external fun nativeSendButtonEvent(button: Int, pressed: Boolean): Boolean
 *
 * @param button Xbox button code (e.g., BTN_A=0x130)
 * @param pressed JNI_TRUE for press, JNI_FALSE for release
 * @return JNI_TRUE on success, JNI_FALSE on failure
 */
JNIEXPORT jboolean JNICALL
Java_com_steamdeck_mobile_core_input_NativeUInputBridge_nativeSendButtonEvent(
    JNIEnv* env,
    jobject thiz,
    jint button,
    jboolean pressed
) {
    int pressed_int = pressed ? 1 : 0;
    int result = uinput_send_button_event(button, pressed_int);

    if (result < 0) {
        LOGE("uinput_send_button_event failed (button=%d, pressed=%d)", button, pressed_int);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * JNI: Send axis event
 *
 * Java signature:
 * private external fun nativeSendAxisEvent(axis: Int, value: Float): Boolean
 *
 * Data conversion:
 * - Android value: -1.0 to 1.0 (float)
 * - evdev value: -32768 to 32767 (int)
 *
 * Formula: evdev_value = (android_value + 1.0) * 32767.5 - 32768
 *
 * @param axis evdev axis code (e.g., ABS_X=0x00)
 * @param value Android axis value (-1.0 to 1.0)
 * @return JNI_TRUE on success, JNI_FALSE on failure
 */
JNIEXPORT jboolean JNICALL
Java_com_steamdeck_mobile_core_input_NativeUInputBridge_nativeSendAxisEvent(
    JNIEnv* env,
    jobject thiz,
    jint axis,
    jfloat value
) {
    // Convert Android float (-1.0 ~ 1.0) to evdev int (-32768 ~ 32767)
    int evdev_value = (int)((value + 1.0f) * 32767.5f - 32768.0f);

    // Clamp to evdev range
    if (evdev_value < -32768) evdev_value = -32768;
    if (evdev_value > 32767) evdev_value = 32767;

    int result = uinput_send_axis_event(axis, evdev_value);

    if (result < 0) {
        LOGE("uinput_send_axis_event failed (axis=%d, value=%f, evdev=%d)", axis, value, evdev_value);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * JNI: Destroy virtual controller
 *
 * Java signature:
 * private external fun nativeDestroy()
 */
JNIEXPORT void JNICALL
Java_com_steamdeck_mobile_core_input_NativeUInputBridge_nativeDestroy(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("nativeDestroy called");
    uinput_destroy();
}
