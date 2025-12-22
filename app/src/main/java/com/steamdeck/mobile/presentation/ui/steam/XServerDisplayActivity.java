package com.steamdeck.mobile.presentation.ui.steam;

import android.app.Activity;
import com.steamdeck.mobile.core.xserver.XServer;
import com.steamdeck.mobile.presentation.widget.XServerView;
import com.steamdeck.mobile.presentation.widget.InputControlsView;

/**
 * XServer Display Activity stub
 *
 * NOTE: This is a minimal stub to satisfy WinHandler dependencies.
 * The actual XServer rendering is done via SteamDisplayScreen.kt (Compose UI).
 *
 * TODO: Future integration will connect WinHandler with Compose-based SteamDisplayScreen
 */
public class XServerDisplayActivity extends Activity {
    // Stub implementation - Compose version is in SteamDisplayScreen.kt

    /**
     * Get XServer instance (stub)
     * @return null (not implemented in Compose version)
     */
    public XServer getXServer() {
        return null;
    }

    /**
     * Get XServerView instance (stub)
     * @return null (not implemented in Compose version)
     */
    public XServerView getXServerView() {
        return null;
    }

    /**
     * Get InputControlsView instance (stub)
     * @return null (not implemented in Compose version)
     */
    public InputControlsView getInputControlsView() {
        return null;
    }
}
