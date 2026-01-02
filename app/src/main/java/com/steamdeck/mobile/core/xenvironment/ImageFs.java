package com.steamdeck.mobile.core.xenvironment;

import android.content.Context;

import androidx.annotation.NonNull;

import com.steamdeck.mobile.core.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class ImageFs {
    public static final String USER = "xuser";
    public static final String HOME_PATH = "/home/"+USER;
    public static final String CACHE_PATH = "/home/"+USER+"/.cache";
    public static final String CONFIG_PATH = "/home/"+USER+"/.config";
    public static final String WINEPREFIX = "/home/"+USER+"/.wine";
    private final File rootDir;
    private String winePath = null;  // CRITICAL FIX (2025-12-27): Dynamically detect Proton vs Wine

    private ImageFs(File rootDir) {
        this.rootDir = rootDir;
    }

    public static ImageFs find(Context context) {
        // CRITICAL: Use winlator/rootfs directory (not imagefs)
        // This matches WinlatorEmulator.rootfsDir configuration
        // Path: /data/data/com.steamdeck.mobile/files/winlator/rootfs
        return new ImageFs(new File(context.getFilesDir(), "winlator/rootfs"));
    }

    public File getRootDir() {
        return rootDir;
    }

    public boolean isValid() {
        return rootDir.isDirectory() && getImgVersionFile().exists();
    }

    public int getVersion() {
        File imgVersionFile = getImgVersionFile();
        return imgVersionFile.exists() ? Integer.parseInt(FileUtils.readLines(imgVersionFile).get(0)) : 0;
    }

    public String getFormattedVersion() {
        return String.format(Locale.ENGLISH, "%.1f", (float)getVersion());
    }

    public void createImgVersionFile(int version) {
        getConfigDir().mkdirs();
        File file = getImgVersionFile();
        try {
            file.createNewFile();
            FileUtils.writeString(file, String.valueOf(version));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getWinePath() {
        // CRITICAL FIX (2025-12-27): Dynamically detect Proton 10 vs Wine 10.10
        // Proton 10: Wine binaries at /lib/wine/aarch64-windows/
        // Wine 10.10: Wine binaries at /opt/wine/lib/wine/x86_64-windows/
        if (winePath == null) {
            File protonBinDir = new File(rootDir, "/bin/wine");
            File wineBinDir = new File(rootDir, "/opt/wine/bin/wine");

            if (protonBinDir.exists()) {
                // Proton 10 ARM64EC: binaries at root level (/bin, /lib)
                winePath = "";
            } else if (wineBinDir.exists()) {
                // Wine 10.10: binaries in /opt/wine subdirectory
                winePath = "/opt/wine";
            } else {
                // Fallback to Wine structure (most common)
                winePath = "/opt/wine";
            }
        }
        return winePath;
    }

    public void setWinePath(String winePath) {
        this.winePath = FileUtils.toRelativePath(rootDir.getPath(), winePath);
    }

    public File getConfigDir() {
        return new File(rootDir, ".winlator");
    }

    public File getImgVersionFile() {
        return new File(getConfigDir(), ".img_version");
    }

    public File getInstalledWineDir() {
        return new File(rootDir, "/opt/installed-wine");
    }

    public File getTmpDir() {
        return new File(rootDir, "/tmp");
    }

    public File getLib32Dir() {
        return new File(rootDir, "/usr/lib/arm-linux-gnueabihf");
    }

    public File getLib64Dir() {
        return new File(rootDir, "/usr/lib/aarch64-linux-gnu");
    }

    @NonNull
    @Override
    public String toString() {
        return rootDir.getPath();
    }
}
