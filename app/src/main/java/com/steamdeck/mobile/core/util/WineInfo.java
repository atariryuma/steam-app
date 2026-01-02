package com.steamdeck.mobile.core.util;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.steamdeck.mobile.core.xenvironment.ImageFs;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WineInfo implements Parcelable {
    public static final WineInfo MAIN_WINE_VERSION = new WineInfo("9.2", "x86_64");
    private static final Pattern pattern = Pattern.compile("^wine\\-([0-9\\.]+)\\-?([0-9\\.]+)?\\-(x86|x86_64)$");
    public final String version;
    public final String subversion;
    public final String path;
    private String arch;

    public WineInfo(String version, String arch) {
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
    }

    public WineInfo(String version, String subversion, String arch, String path) {
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
    }

    private WineInfo(Parcel in) {
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return arch.equals("x86_64");
    }

    public String getExecutable(Context context, boolean wow64Mode) {
        if (this == MAIN_WINE_VERSION) {
            // CRITICAL FIX (2025-12-27): Dynamic Proton/Wine path detection
            ImageFs imageFs = ImageFs.find(context);
            String wineBasePath = imageFs.getWinePath();  // Returns "" for Proton, "/opt/wine" for Wine
            File wineBinDir = new File(imageFs.getRootDir(), wineBasePath + "/bin");
            File wineBinFile = new File(wineBinDir, "wine");
            File winePreloaderBinFile = new File(wineBinDir, "wine-preloader");

            // HIGH FIX (2025-12-27): Proton may use different binary names - fallback if variants don't exist
            File sourceWine = new File(wineBinDir, wow64Mode ? "wine-wow64" : "wine32");
            File sourcePreloader = new File(wineBinDir, wow64Mode ? "wine-preloader-wow64" : "wine32-preloader");

            // Fallback to generic names if specific variants don't exist (Proton compatibility)
            if (!sourceWine.exists()) {
                sourceWine = new File(wineBinDir, "wine");
            }
            if (!sourcePreloader.exists()) {
                sourcePreloader = new File(wineBinDir, "wine-preloader");
            }

            FileUtils.copy(sourceWine, wineBinFile);
            FileUtils.copy(sourcePreloader, winePreloaderBinFile);
            FileUtils.chmod(wineBinFile, 0771);
            FileUtils.chmod(winePreloaderBinFile, 0771);
            return wow64Mode ? "wine" : "wine64";
        }
        else {
            // MEDIUM FIX (2025-12-27): Check for wine64 first, then wine (Proton uses "wine", Wine uses "wine64")
            File wine64Binary = new File(path, "/bin/wine64");
            File wineBinary = new File(path, "/bin/wine");
            if (wine64Binary.isFile()) {
                return "wine64";
            } else if (wineBinary.isFile()) {
                return "wine";
            } else {
                // Fallback to "wine" if neither exists
                return "wine";
            }
        }
    }

    public String identifier() {
        return "wine-"+fullVersion()+"-"+(this == MAIN_WINE_VERSION ? "custom" : arch);
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        return "Wine "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, String identifier) {
        if (identifier.equals(MAIN_WINE_VERSION.identifier())) return MAIN_WINE_VERSION;
        Matcher matcher = pattern.matcher(identifier);
        if (matcher.find()) {
            File installedWineDir = ImageFs.find(context).getInstalledWineDir();
            String path = (new File(installedWineDir, identifier)).getPath();
            return new WineInfo(matcher.group(1), matcher.group(2), matcher.group(3), path);
        }
        else return MAIN_WINE_VERSION;
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null ||wineVersion.equals(MAIN_WINE_VERSION.identifier());
    }
}
