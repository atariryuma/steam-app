package com.steamdeck.mobile.core.xenvironment;

import android.content.Context;

import com.steamdeck.mobile.core.util.TarCompressorUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rootfs installer for Linux environment
 *
 * Extracts rootfs.txz and rootfs_patches.tzst from assets to create
 * a complete Ubuntu-based Linux environment for Wine/Box64 execution.
 *
 * Based on Winlator's ImageFsInstaller implementation.
 */
public class RootfsInstaller {
    private static final String TAG = "RootfsInstaller";
    private static final int LATEST_VERSION = 1; // Version 1: Initial rootfs from Winlator 10.1
    private static final AtomicBoolean isInstalling = new AtomicBoolean(false);

    /**
     * Install rootfs if needed (blocking operation)
     *
     * @param context Android context
     * @return true if rootfs is ready (either already installed or newly installed)
     */
    public static boolean installIfNeeded(Context context) {
        ImageFs imageFs = ImageFs.find(context);

        // Check if rootfs is already valid
        if (imageFs.isValid() && imageFs.getVersion() >= LATEST_VERSION) {
            android.util.Log.i(TAG, "Rootfs already installed, version: " + imageFs.getVersion());
            return true;
        }

        // Prevent concurrent installations
        if (!isInstalling.compareAndSet(false, true)) {
            android.util.Log.w(TAG, "Installation already in progress");
            return false;
        }

        try {
            return installFromAssets(context);
        } finally {
            isInstalling.set(false);
        }
    }

    /**
     * Extract rootfs from assets (blocking operation)
     *
     * Steps:
     * 1. Clear existing rootfs directory
     * 2. Extract rootfs.txz (main Ubuntu filesystem)
     * 3. Extract rootfs_patches.tzst (patches and updates)
     * 4. Create version file to mark installation complete
     *
     * @param context Android context
     * @return true if installation succeeded
     */
    private static boolean installFromAssets(Context context) {
        android.util.Log.i(TAG, "Installing rootfs from assets...");

        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();

        // Clear existing rootfs (but preserve /home and /opt/installed-wine)
        clearRootDir(rootDir);

        // Extract main rootfs (rootfs.txz, ~53MB compressed)
        android.util.Log.i(TAG, "Extracting rootfs.txz...");
        boolean success = TarCompressorUtils.extract(
            TarCompressorUtils.Type.XZ,
            context,
            "rootfs.txz",
            rootDir
        );

        if (!success) {
            android.util.Log.e(TAG, "Failed to extract rootfs.txz");
            return false;
        }

        // FIXME: Extract patches (rootfs_patches.tzst, ~3.6MB compressed)
        // ZSTD extraction disabled: zstd-jni has no ARM64 native libs
        // Skipping patches for now - they contain optimizations but are not essential
        // TODO: Convert rootfs_patches.tzst to XZ format for compatibility
        /*
        android.util.Log.i(TAG, "Extracting rootfs_patches.tzst...");
        success = TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context,
            "rootfs_patches.tzst",
            rootDir
        );

        if (!success) {
            android.util.Log.e(TAG, "Failed to extract rootfs_patches.tzst");
            return false;
        }
        */
        android.util.Log.w(TAG, "Skipping rootfs_patches.tzst (ZSTD not supported on ARM64)");

        // Create version file to mark installation complete
        imageFs.createImgVersionFile(LATEST_VERSION);
        android.util.Log.i(TAG, "Rootfs installation complete, version: " + LATEST_VERSION);

        return true;
    }

    /**
     * Clear rootfs directory while preserving user data
     *
     * Preserves:
     * - /home directory (user data)
     * - /opt/installed-wine directory (Wine installations)
     *
     * Based on Winlator's clearRootDir implementation
     */
    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String name = file.getName();
                        // Preserve user data directories
                        if (name.equals("home") || name.equals("opt")) {
                            if (name.equals("opt")) {
                                clearOptDir(file);
                            }
                            continue;
                        }
                    }
                    deleteRecursive(file);
                }
            }
        } else {
            rootDir.mkdirs();
        }
    }

    /**
     * Clear /opt directory while preserving installed Wine
     */
    private static void clearOptDir(File optDir) {
        File[] files = optDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // Preserve installed Wine directory
                if (file.getName().equals("installed-wine")) {
                    continue;
                }
                deleteRecursive(file);
            }
        }
    }

    /**
     * Delete file or directory recursively
     */
    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
