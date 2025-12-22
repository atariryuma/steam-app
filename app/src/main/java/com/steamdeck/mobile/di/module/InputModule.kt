package com.steamdeck.mobile.di.module

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Input (controller) related DI module
 *
 * All input-related classes use @Inject constructor, so no manual bindings needed:
 * - GameControllerManager @Inject constructor
 * - ControllerEventBus @Inject constructor
 * - ControllerInputRouter @Inject constructor
 * - NativeUInputBridge @Inject constructor
 */
@Module
@InstallIn(SingletonComponent::class)
object InputModule {
 // No manual providers needed - all classes use @Inject constructor
}
