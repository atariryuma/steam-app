package com.steamdeck.mobile.core.vr;

/**
 * XR (VR/AR) Activity stub
 *
 * Winlator XServer includes VR support via XrActivity.
 * Since Steam Deck Mobile doesn't require VR, this is a stub implementation
 * that always returns false/null to disable VR features.
 *
 * Original: com.winlator.XrActivity (Winlator 10.1.0)
 */
public class XrActivity {
    /**
     * Check if XR/VR is supported
     * @return false (VR not supported in Steam Deck Mobile)
     */
    public static boolean isSupported() {
        return false;
    }

    /**
     * Get XR activity instance
     * @return null (VR not available)
     */
    public static XrActivity getInstance() {
        return null;
    }

    /**
     * Get immersive mode setting
     * @return false (no immersive VR)
     */
    public static boolean getImmersive() {
        return false;
    }

    /**
     * Get side-by-side rendering mode
     * @return false (no SBS rendering)
     */
    public static boolean getSBS() {
        return false;
    }

    /**
     * Begin VR frame rendering (stub)
     */
    public Object beginFrame(boolean immersive, boolean sbs) {
        return null;
    }

    /**
     * Bind VR framebuffer (stub)
     */
    public void bindFramebuffer() {
        // No-op
    }
}
