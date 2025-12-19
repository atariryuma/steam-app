package com.steamdeck.mobile.core.logging

import android.util.Log

/**
 * Centralized logging utility for the application.
 *
 * Benefits:
 * - Consistent log format across the app
 * - Easy to add crash reporting integration (e.g., Firebase Crashlytics)
 * - Can be disabled in production builds
 * - Type-safe tag management
 *
 * Usage:
 * ```kotlin
 * AppLogger.d("MyTag", "Debug message")
 * AppLogger.e("MyTag", "Error occurred", exception)
 * ```
 */
object AppLogger {
    private const val APP_TAG = "SteamDeckMobile"
    private const val MAX_TAG_LENGTH = 23 // Android Log tag limit

    /**
     * Log level enumeration.
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    /**
     * Formats a tag to ensure it doesn't exceed Android's limit.
     *
     * @param tag The tag to format
     * @return Formatted tag with app prefix
     */
    private fun formatTag(tag: String): String {
        val fullTag = "$APP_TAG:$tag"
        return if (fullTag.length > MAX_TAG_LENGTH) {
            fullTag.substring(0, MAX_TAG_LENGTH)
        } else {
            fullTag
        }
    }

    /**
     * Log verbose message.
     *
     * @param tag Log tag (will be prefixed with app name)
     * @param message Message to log
     * @param throwable Optional throwable to log
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.v(formattedTag, message, throwable)
        } else {
            Log.v(formattedTag, message)
        }
    }

    /**
     * Log debug message.
     *
     * @param tag Log tag (will be prefixed with app name)
     * @param message Message to log
     * @param throwable Optional throwable to log
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.d(formattedTag, message, throwable)
        } else {
            Log.d(formattedTag, message)
        }
    }

    /**
     * Log info message.
     *
     * @param tag Log tag (will be prefixed with app name)
     * @param message Message to log
     * @param throwable Optional throwable to log
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.i(formattedTag, message, throwable)
        } else {
            Log.i(formattedTag, message)
        }
    }

    /**
     * Log warning message.
     *
     * @param tag Log tag (will be prefixed with app name)
     * @param message Message to log
     * @param throwable Optional throwable to log
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.w(formattedTag, message, throwable)
        } else {
            Log.w(formattedTag, message)
        }
    }

    /**
     * Log error message.
     *
     * @param tag Log tag (will be prefixed with app name)
     * @param message Message to log
     * @param throwable Optional throwable to log
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val formattedTag = formatTag(tag)
        if (throwable != null) {
            Log.e(formattedTag, message, throwable)
        } else {
            Log.e(formattedTag, message)
        }

        // TODO: Send to crash reporting service (e.g., Firebase Crashlytics)
        // if (BuildConfig.DEBUG.not()) {
        //     FirebaseCrashlytics.getInstance().recordException(throwable ?: Exception(message))
        // }
    }
}
