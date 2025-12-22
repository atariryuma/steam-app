/**
 * uinput_bridge.c
 *
 * Native uinput bridge for creating virtual Xbox 360 controller on Android
 *
 * Architecture:
 * - Creates virtual input device via Linux /dev/uinput
 * - Emulates Xbox 360 controller (VID: 0x045e, PID: 0x028e)
 * - Sends button events (EV_KEY) and axis events (EV_ABS)
 * - No root required (works with Android 8+ targetSdk 28)
 *
 * Performance:
 * - <1ms per event (direct ioctl to kernel)
 * - Event synchronization via EV_SYN
 *
 * Error handling:
 * - Returns -1 on failure (graceful degradation to InputBridge app)
 * - Logs errors via __android_log_print
 */

#include <linux/uinput.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>

#define TAG "uinput_bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global file descriptor for /dev/uinput
static int uinput_fd = -1;

/**
 * Initialize uinput device
 * Opens /dev/uinput with O_WRONLY | O_NONBLOCK
 *
 * @return 0 on success, -1 on failure
 */
int uinput_init() {
    if (uinput_fd >= 0) {
        LOGI("uinput already initialized (fd=%d)", uinput_fd);
        return 0;
    }

    uinput_fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (uinput_fd < 0) {
        LOGE("Failed to open /dev/uinput: %s (errno=%d)", strerror(errno), errno);
        LOGE("Possible causes:");
        LOGE("  1. SELinux policy denial (requires targetSdk <= 28)");
        LOGE("  2. /dev/uinput does not exist");
        LOGE("  3. Permission denied (check ls -l /dev/uinput)");
        return -1;
    }

    LOGI("uinput initialized successfully (fd=%d)", uinput_fd);
    return 0;
}

/**
 * Create virtual Xbox 360 controller
 *
 * Xbox 360 controller capabilities:
 * - Buttons: A, B, X, Y, LB, RB, Back, Start, Xbox, LS, RS (11 buttons)
 * - Axes: Left stick (X, Y), Right stick (RX, RY), Triggers (Z, RZ), D-pad (HAT0X, HAT0Y)
 * - Vendor ID: 0x045e (Microsoft)
 * - Product ID: 0x028e (Xbox 360 Controller)
 *
 * @param name Device name (e.g., "Steam Deck Mobile Controller")
 * @param vendor_id Vendor ID (0x045e for Xbox)
 * @param product_id Product ID (0x028e for Xbox 360)
 * @return 0 on success, -1 on failure
 */
int uinput_create_xbox360_controller(const char* name, int vendor_id, int product_id) {
    if (uinput_fd < 0) {
        LOGE("uinput not initialized (call uinput_init first)");
        return -1;
    }

    // Enable EV_KEY (buttons) event type
    if (ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY) < 0) {
        LOGE("Failed to enable EV_KEY: %s", strerror(errno));
        return -1;
    }

    // Enable Xbox 360 button codes
    // BTN_A=0x130 (304), BTN_B=0x131 (305), BTN_X=0x133 (307), BTN_Y=0x134 (308)
    // BTN_TL=0x136 (310, LB), BTN_TR=0x137 (311, RB)
    // BTN_SELECT=0x13a (314, Back), BTN_START=0x13b (315, Start)
    // BTN_MODE=0x13c (316, Xbox button)
    // BTN_THUMBL=0x13d (317, LS), BTN_THUMBR=0x13e (318, RS)
    int button_codes[] = {
        BTN_A, BTN_B, BTN_X, BTN_Y,
        BTN_TL, BTN_TR,
        BTN_SELECT, BTN_START, BTN_MODE,
        BTN_THUMBL, BTN_THUMBR
    };

    for (int i = 0; i < sizeof(button_codes) / sizeof(button_codes[0]); i++) {
        if (ioctl(uinput_fd, UI_SET_KEYBIT, button_codes[i]) < 0) {
            LOGE("Failed to enable button %d: %s", button_codes[i], strerror(errno));
            return -1;
        }
    }

    // Enable EV_ABS (absolute axes) event type
    if (ioctl(uinput_fd, UI_SET_EVBIT, EV_ABS) < 0) {
        LOGE("Failed to enable EV_ABS: %s", strerror(errno));
        return -1;
    }

    // Configure absolute axes
    // ABS_X/Y: Left stick (-32768 to 32767)
    // ABS_RX/RY: Right stick (-32768 to 32767)
    // ABS_Z/RZ: Triggers (0 to 255)
    // ABS_HAT0X/HAT0Y: D-pad (-1, 0, 1)
    struct uinput_abs_setup abs_setup;

    // Left stick X
    abs_setup.code = ABS_X;
    abs_setup.absinfo.minimum = -32768;
    abs_setup.absinfo.maximum = 32767;
    abs_setup.absinfo.fuzz = 16;
    abs_setup.absinfo.flat = 128;
    abs_setup.absinfo.value = 0;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_X: %s", strerror(errno));
        return -1;
    }

    // Left stick Y
    abs_setup.code = ABS_Y;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_Y: %s", strerror(errno));
        return -1;
    }

    // Right stick X
    abs_setup.code = ABS_RX;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_RX: %s", strerror(errno));
        return -1;
    }

    // Right stick Y
    abs_setup.code = ABS_RY;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_RY: %s", strerror(errno));
        return -1;
    }

    // Left trigger (Z)
    abs_setup.code = ABS_Z;
    abs_setup.absinfo.minimum = 0;
    abs_setup.absinfo.maximum = 255;
    abs_setup.absinfo.fuzz = 0;
    abs_setup.absinfo.flat = 0;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_Z: %s", strerror(errno));
        return -1;
    }

    // Right trigger (RZ)
    abs_setup.code = ABS_RZ;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_RZ: %s", strerror(errno));
        return -1;
    }

    // D-pad X (HAT0X)
    abs_setup.code = ABS_HAT0X;
    abs_setup.absinfo.minimum = -1;
    abs_setup.absinfo.maximum = 1;
    abs_setup.absinfo.fuzz = 0;
    abs_setup.absinfo.flat = 0;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_HAT0X: %s", strerror(errno));
        return -1;
    }

    // D-pad Y (HAT0Y)
    abs_setup.code = ABS_HAT0Y;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &abs_setup) < 0) {
        LOGE("Failed to setup ABS_HAT0Y: %s", strerror(errno));
        return -1;
    }

    // Setup device metadata
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));

    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = vendor_id;
    usetup.id.product = product_id;
    usetup.id.version = 1;

    strncpy(usetup.name, name, UINPUT_MAX_NAME_SIZE - 1);
    usetup.name[UINPUT_MAX_NAME_SIZE - 1] = '\0';

    if (ioctl(uinput_fd, UI_DEV_SETUP, &usetup) < 0) {
        LOGE("Failed to setup device: %s", strerror(errno));
        return -1;
    }

    // Create the device
    if (ioctl(uinput_fd, UI_DEV_CREATE) < 0) {
        LOGE("Failed to create device: %s", strerror(errno));
        return -1;
    }

    LOGI("Xbox 360 controller created: %s (VID=0x%04x, PID=0x%04x)", name, vendor_id, product_id);
    return 0;
}

/**
 * Send button event
 *
 * @param button_code Xbox button code (e.g., BTN_A=0x130)
 * @param pressed 1 for press, 0 for release
 * @return 0 on success, -1 on failure
 */
int uinput_send_button_event(int button_code, int pressed) {
    if (uinput_fd < 0) {
        LOGE("uinput not initialized");
        return -1;
    }

    struct input_event ev[2];
    memset(ev, 0, sizeof(ev));

    // Button event
    ev[0].type = EV_KEY;
    ev[0].code = button_code;
    ev[0].value = pressed ? 1 : 0;

    // Synchronization event
    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;

    if (write(uinput_fd, ev, sizeof(ev)) < 0) {
        LOGE("Failed to send button event (code=%d, pressed=%d): %s", button_code, pressed, strerror(errno));
        return -1;
    }

    return 0;
}

/**
 * Send axis event
 *
 * @param axis_code evdev axis code (e.g., ABS_X=0x00)
 * @param value Axis value (-32768 to 32767 for sticks, 0-255 for triggers)
 * @return 0 on success, -1 on failure
 */
int uinput_send_axis_event(int axis_code, int value) {
    if (uinput_fd < 0) {
        LOGE("uinput not initialized");
        return -1;
    }

    struct input_event ev[2];
    memset(ev, 0, sizeof(ev));

    // Axis event
    ev[0].type = EV_ABS;
    ev[0].code = axis_code;
    ev[0].value = value;

    // Synchronization event
    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;

    if (write(uinput_fd, ev, sizeof(ev)) < 0) {
        LOGE("Failed to send axis event (code=%d, value=%d): %s", axis_code, value, strerror(errno));
        return -1;
    }

    return 0;
}

/**
 * Destroy virtual controller and cleanup
 */
void uinput_destroy() {
    if (uinput_fd < 0) {
        LOGI("uinput not initialized (nothing to destroy)");
        return;
    }

    if (ioctl(uinput_fd, UI_DEV_DESTROY) < 0) {
        LOGE("Failed to destroy device: %s", strerror(errno));
    }

    close(uinput_fd);
    uinput_fd = -1;

    LOGI("uinput destroyed and closed");
}
