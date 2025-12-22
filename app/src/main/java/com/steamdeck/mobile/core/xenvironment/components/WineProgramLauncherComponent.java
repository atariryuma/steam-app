package com.steamdeck.mobile.core.xenvironment.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import android.preference.PreferenceManager;

import com.steamdeck.mobile.box86_64.Box86_64Preset;
import com.steamdeck.mobile.box86_64.Box86_64PresetManager;
import com.steamdeck.mobile.core.util.Callback;
import com.steamdeck.mobile.core.util.DefaultVersion;
import com.steamdeck.mobile.core.util.ElfPatcher;
import com.steamdeck.mobile.core.util.EnvVars;
import com.steamdeck.mobile.core.util.ProcessHelper;
import com.steamdeck.mobile.core.util.TarCompressorUtils;
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig;
import com.steamdeck.mobile.core.xenvironment.EnvironmentComponent;
import com.steamdeck.mobile.core.xenvironment.ImageFs;

import java.io.File;

public class WineProgramLauncherComponent extends EnvironmentComponent {
    private String guestExecutable;
    private static int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private String box86Preset = Box86_64Preset.COMPATIBILITY;
    private String box64Preset = Box86_64Preset.COMPATIBILITY;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private boolean wow64Mode = true;

    @Override
    public void start() {
        android.util.Log.e("WineProgramLauncher", "start() called");
        synchronized (lock) {
            stop();
            android.util.Log.e("WineProgramLauncher", "Extracting Box86/64 files");
            extractBox86_64Files();
            android.util.Log.e("WineProgramLauncher", "Executing guest program: " + guestExecutable);
            pid = execGuestProgram();
            android.util.Log.e("WineProgramLauncher", "Guest program PID: " + pid);
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public boolean isWoW64Mode() {
        return wow64Mode;
    }

    public void setWoW64Mode(boolean wow64Mode) {
        this.wow64Mode = wow64Mode;
    }

    public String[] getBindingPaths() {
        return bindingPaths;
    }

    public void setBindingPaths(String[] bindingPaths) {
        this.bindingPaths = bindingPaths;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public String getBox86Preset() {
        return box86Preset;
    }

    public void setBox86Preset(String box86Preset) {
        this.box86Preset = box86Preset;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    private int execGuestProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();
        File tmpDir = environment.getTmpDir();
        // CRITICAL: Android nativeLibraryDir points to lib/arm64 but libraries are at lib/arm64-v8a
        // We need to try multiple paths to find libproot.so
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;

        // Possible library paths (Android inconsistency between platforms)
        String[] possiblePaths = {
            nativeLibraryDir + "/libproot.so",                    // lib/arm64/
            nativeLibraryDir + "-v8a/libproot.so",               // lib/arm64-v8a/
            nativeLibraryDir.replace("/arm64", "/arm64-v8a") + "/libproot.so"  // Explicit replace
        };

        File prootBinary = null;
        String prootPath = null;

        for (String path : possiblePaths) {
            File candidate = new File(path);
            android.util.Log.e("WineProgramLauncher", "Trying path: " + path);
            if (candidate.exists()) {
                prootBinary = candidate;
                prootPath = path;
                android.util.Log.e("WineProgramLauncher", "Found proot at: " + prootPath);
                break;
            }
        }

        if (prootBinary == null || !prootBinary.exists()) {
            android.util.Log.e("WineProgramLauncher", "PRoot library not found in any of the expected paths");
            android.util.Log.e("WineProgramLauncher", "Searched paths:");
            for (String path : possiblePaths) {
                android.util.Log.e("WineProgramLauncher", "  - " + path);
            }
            return -1;
        }

        // CRITICAL: Bind mount box64 binary into rootfs
        File box64Binary = new File(context.getFilesDir(), "winlator/box64/box64");
        if (!box64Binary.exists()) {
            android.util.Log.e("WineProgramLauncher", "Box64 binary not found: " + box64Binary.getAbsolutePath());
            return -1;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enableBox86_64Logs = preferences.getBoolean("enable_box86_64_logs", false);

        EnvVars envVars = new EnvVars();
        if (!wow64Mode) addBox86EnvVars(envVars, enableBox86_64Logs);
        addBox64EnvVars(envVars, enableBox86_64Logs);
        envVars.put("HOME", ImageFs.HOME_PATH);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", "/tmp");
        envVars.put("LC_ALL", "en_US.utf8");
        envVars.put("DISPLAY", ":0");
        envVars.put("PATH", imageFs.getWinePath()+"/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        envVars.put("LD_LIBRARY_PATH", "/usr/lib/aarch64-linux-gnu:/usr/lib/arm-linux-gnueabihf");
        envVars.put("ANDROID_SYSVSHM_SERVER", UnixSocketConfig.SYSVSHM_SERVER_PATH);

        if ((new File(imageFs.getLib64Dir(), "libandroid-sysvshm.so")).exists() ||
            (new File(imageFs.getLib32Dir(), "libandroid-sysvshm.so")).exists()) envVars.put("LD_PRELOAD", "libandroid-sysvshm.so");
        if (this.envVars != null) envVars.putAll(this.envVars);

        boolean bindSHM = envVars.get("WINEESYNC").equals("1");

        // Use libproot.so from native library directory (Winlator approach)
        String command = prootBinary.getAbsolutePath();
        // CRITICAL: Winlator's proot doesn't support -k flag
        // Seccomp bypass is handled internally by libproot.so build configuration
        command += " --kill-on-exit";

        // CRITICAL: DO NOT use --rootfs option!
        // --rootfs puts PRoot in chroot mode, which blocks Android filesystem paths
        // Instead, use bind mounts only (matches WinlatorEmulator.kt line 797)
        // This allows box64 to run from Android path while accessing rootfs files via binds
        command += " -b " + rootDir.getAbsolutePath() + ":/data/data/com.winlator/files/rootfs";

        // Bind /tmp to rootfs/tmp for XServer socket access
        command += " -b " + new File(rootDir, "tmp").getAbsolutePath() + ":/tmp";

        // CRITICAL: Bind Wine directory so box64 can find wine binary
        // Wine path from ImageFs (e.g., /opt/wine) → Android real path
        File wineDir = new File(rootDir, imageFs.getWinePath().substring(1)); // Remove leading /
        if (wineDir.exists()) {
            command += " -b " + wineDir.getAbsolutePath() + ":" + imageFs.getWinePath();
        }

        command += " --bind=/dev";

        if (bindSHM) {
            File shmDir = new File(rootDir, "/tmp/shm");
            shmDir.mkdirs();
            command += " --bind="+shmDir.getAbsolutePath()+":/dev/shm";
        }

        command += " --bind=/proc";
        command += " --bind=/sys";

        // CRITICAL: Bind-mount Android's /system directory for box64's ELF interpreter
        // box64 needs /system/bin/linker64 and /system/lib64/*.so
        // Bind the entire /system directory to ensure all dependencies are accessible
        command += " --bind=/system";

        if (bindingPaths != null) {
            for (String path : bindingPaths) command += " --bind="+(new File(path)).getAbsolutePath();
        }

        // CRITICAL FIX: Copy box64 into rootfs instead of symlinking
        // Symlinks from inside PRoot chroot cannot reference files outside the chroot
        // We need to physically copy the box64 binary into the rootfs
        File box64InRootfs = new File(rootDir, "usr/local/bin/box64");
        File box64ParentDir = box64InRootfs.getParentFile();
        if (box64ParentDir != null && !box64ParentDir.exists()) {
            box64ParentDir.mkdirs();
        }

        // CRITICAL: Delete existing symlink/file first to force fresh copy
        // Previous iterations may have created symlinks that don't work in PRoot
        if (box64InRootfs.exists()) {
            android.util.Log.e("WineProgramLauncher", "Deleting existing box64 (symlink or outdated file): " + box64InRootfs.getAbsolutePath());
            box64InRootfs.delete();
        }

        // Copy box64 using Java file I/O (more reliable than shell commands)
        try {
            android.util.Log.e("WineProgramLauncher", "Copying box64: " + box64Binary.getAbsolutePath() + " -> " + box64InRootfs.getAbsolutePath());

            // Use Java streams for reliable file copying
            java.io.FileInputStream fis = new java.io.FileInputStream(box64Binary);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(box64InRootfs);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fis.close();
            fos.close();

            // Make executable using chmod
            Runtime.getRuntime().exec(new String[]{"chmod", "755", box64InRootfs.getAbsolutePath()}).waitFor();

            android.util.Log.e("WineProgramLauncher", "Box64 copy successful, size: " + box64InRootfs.length() + " bytes");

            // CRITICAL: Patch box64's ELF interpreter path to use Android linker
            // box64 binary references /lib/ld-linux-aarch64.so.1 (standard Linux path)
            // but Android uses /system/bin/linker64
            // Since we bind-mount /system, we can directly use /system/bin/linker64
            File androidLinker = new File("/system/bin/linker64");
            if (androidLinker.exists()) {
                // Patch box64's interpreter path to use Android's linker directly
                // /system is bind-mounted in PRoot, so /system/bin/linker64 is accessible
                String interpreterPath = "/system/bin/linker64";
                boolean patchSuccess = ElfPatcher.patchInterpreterPathJava(box64InRootfs, interpreterPath);
                if (patchSuccess) {
                    android.util.Log.e("WineProgramLauncher", "Successfully patched box64 interpreter to: " + interpreterPath);
                } else {
                    android.util.Log.e("WineProgramLauncher", "Failed to patch box64 interpreter (check ElfPatcher logs for details)");
                }
            } else {
                android.util.Log.e("WineProgramLauncher", "Android linker not found: " + androidLinker.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.e("WineProgramLauncher", "Failed to copy box64", e);
        }

        // CRITICAL FIX: Execute box64 using original Android filesystem path (NOT rootfs copy)
        // Using rootfs copy causes ELF version mismatch with rootfs libc.so.6
        // Using Android original (box64Binary) avoids version conflicts
        // This matches WinlatorEmulator.kt's successful approach (line 806: box64ToUse.absolutePath)
        // Environment variables are passed via ProcessHelper.exec's envp parameter
        // PRoot will inherit these variables and make them available to box64/wine
        command += " " + box64Binary.getAbsolutePath() + " " + guestExecutable;

        // Prepend PROOT_TMP_DIR to environment variables
        // These will be passed to Runtime.exec() as the environment for the proot process
        envVars.put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());

        // CRITICAL: PROOT_LOADER may be needed for Seccomp bypass on Android 10+
        // libproot-loader.so provides alternative dynamic linker that avoids Seccomp restrictions
        String nativeLibraryDir2 = environment.getContext().getApplicationInfo().nativeLibraryDir;
        envVars.put("PROOT_LOADER", nativeLibraryDir2+"/libproot-loader.so");
        if (!wow64Mode) envVars.put("PROOT_LOADER_32", nativeLibraryDir2+"/libproot-loader32.so");

        return ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    private void extractBox86_64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String box86Version = preferences.getString("box86_version", DefaultVersion.BOX86);
        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        String currentBox86Version = preferences.getString("current_box86_version", "");
        String currentBox64Version = preferences.getString("current_box64_version", "");
        File rootDir = imageFs.getRootDir();

        if (wow64Mode) {
            File box86File = new File(rootDir, "/usr/local/bin/box86");
            if (box86File.isFile()) {
                box86File.delete();
                preferences.edit().putString("current_box86_version", "").apply();
            }
        }
        else if (!box86Version.equals(currentBox86Version)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box86-"+box86Version+".tzst", rootDir);
            preferences.edit().putString("current_box86_version", box86Version).apply();
        }

        if (!box64Version.equals(currentBox64Version)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box64-"+box64Version+".tzst", rootDir);
            preferences.edit().putString("current_box64_version", box64Version).apply();
        }
    }

    private void addBox86EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX86_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX86_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX86_LOG", "1");
            envVars.put("BOX86_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box86", environment.getContext(), box86Preset));
        envVars.put("BOX86_X11GLX", "1");
        envVars.put("BOX86_NORCFILES", "1");
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");
        if (wow64Mode) envVars.put("BOX64_MMAP32", "1");
        envVars.put("BOX64_AVX", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        envVars.put("BOX64_X11GLX", "1");
        envVars.put("BOX64_NORCFILES", "1");
    }

    public void suspendProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.suspendProcess(pid);
        }
    }

    public void resumeProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.resumeProcess(pid);
        }
    }
}
