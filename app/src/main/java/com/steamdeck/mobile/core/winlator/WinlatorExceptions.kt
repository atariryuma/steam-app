package com.steamdeck.mobile.core.winlator

/**
 * Base exception for emulator-related errors
 */
open class EmulatorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when Wine prefix initialization fails.
 */
class WinePrefixException(
    message: String,
    cause: Throwable? = null
) : EmulatorException(message, cause)

/**
 * Exception thrown when process launch fails.
 */
class ProcessLaunchException(
    message: String,
    cause: Throwable? = null
) : EmulatorException(message, cause)

/**
 * Exception thrown when Wine binary is not found or invalid.
 */
class WineBinaryNotFoundException(
    message: String = "Wine binary not found. Please run initialize() first.",
    cause: Throwable? = null
) : EmulatorException(message, cause)

/**
 * Exception thrown when Box64 binary is not found or invalid.
 */
class Box64BinaryNotFoundException(
    message: String = "Box64 binary not found. Please run initialize() first.",
    cause: Throwable? = null
) : EmulatorException(message, cause)

/**
 * Exception thrown when container is not found.
 */
class ContainerNotFoundException(
    containerId: String,
    cause: Throwable? = null
) : EmulatorException("Container not found: $containerId", cause)

/**
 * Exception thrown when process is not found.
 */
class ProcessNotFoundException(
    processId: String,
    cause: Throwable? = null
) : EmulatorException("Process not found: $processId", cause)
