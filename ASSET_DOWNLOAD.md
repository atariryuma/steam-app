# Asset Download Guide

## Overview

This project requires large asset files (>100MB) that cannot be stored in Git due to GitHub's file size limits. These files are automatically downloaded during the build process.

## Required Assets

| File | Size | Description | Source |
|------|------|-------------|--------|
| `rootfs.tar.xz` | 53MB | Wine 10.10 rootfs | Winlator 11.0 Beta |
| `box64-0.3.6.tar.xz` | 3MB | Box64 0.3.6 ARM64 emulator | Winlator 11.0 Beta |
| `proton-10-arm64ec.tar.xz` | 196MB | Proton 10 ARM64EC (optional) | Winlator Cmod v13.1.1 |

**Total download size:** ~252MB

## Automatic Download (Recommended)

Assets are automatically downloaded when you build the project:

```bash
./gradlew assembleDebug
```

The build script will:
1. Check if assets already exist
2. Download missing files from official sources
3. Verify file integrity (SHA256)
4. Continue with normal build process

## Manual Download (Alternative)

If automatic download fails, download manually:

### 1. Wine 10.10 Rootfs
```bash
# Download from Winlator releases
wget https://github.com/Winlator/winlator/releases/download/v11.0-beta/rootfs.tar.xz
mv rootfs.tar.xz app/src/main/assets/
```

### 2. Box64 0.3.6
```bash
# Download from Winlator releases
wget https://github.com/Winlator/winlator/releases/download/v11.0-beta/box64-0.3.6.tar.xz
mkdir -p app/src/main/assets/box64/
mv box64-0.3.6.tar.xz app/src/main/assets/box64/
```

### 3. Proton 10 ARM64EC (Optional)
```bash
# Download from Winlator Cmod releases
wget https://github.com/K11MCH1/Winlator/releases/download/v13.1.1/proton-10-arm64ec.tar.xz
mkdir -p app/src/main/assets/proton/
mv proton-10-arm64ec.tar.xz app/src/main/assets/proton/
```

## Troubleshooting

### Download fails during build

**Symptom:** Gradle build fails with "Download failed" error

**Solution:**
1. Check your internet connection
2. Try manual download (see above)
3. Ensure you have write permissions in the assets directory

### Slow download speed

**Symptom:** Build takes very long on first run

**Explanation:** First build downloads ~252MB of assets. Subsequent builds skip download if files exist.

**Solution:**
- Use a fast internet connection for first build
- Download manually from a mirror if needed

### SHA256 verification fails

**Symptom:** "SHA256 mismatch" error during build

**Solution:**
1. Delete corrupted file from `app/src/main/assets/`
2. Re-run build to download fresh copy
3. If error persists, verify download URL is correct

## Build Script Details

Asset downloads are managed by `download-assets.gradle.kts`:

- **Automatic execution:** Runs before `preBuild` task
- **Idempotent:** Skips download if file already exists
- **Verified:** Checks SHA256 hash (when available)
- **Resilient:** Provides fallback instructions on failure

## Storage Requirements

Ensure you have sufficient disk space:

- **Assets (compressed):** ~252MB
- **Assets (extracted at runtime):** ~1.5GB
- **APK size:** ~290MB (debug), ~80MB (release with R8)

## Contributing

If you update asset files:

1. Update URLs in `download-assets.gradle.kts`
2. Update SHA256 hashes for verification
3. Update this documentation with new file sizes
4. Test download script with `./gradlew downloadAssets`

## License

Asset files are sourced from:
- **Winlator**: Apache 2.0 License (https://github.com/Winlator/winlator)
- **Wine**: LGPL 2.1+ (https://www.winehq.org/)
- **Box64**: MIT License (https://github.com/ptitSeb/box64)
- **Proton**: BSD-3-Clause (https://github.com/ValveSoftware/Proton)
