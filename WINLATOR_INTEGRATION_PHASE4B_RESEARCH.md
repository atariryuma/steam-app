# Winlator Integration - Phase 4B Research & Architecture
## Wine Distribution Strategy & Implementation Plan

**日付**: 2025-12-17
**ステータス**: 📋 設計完了 (実装準備中)
**目的**: Wineバイナリの最適な配布・抽出戦略の決定

---

## 🔍 調査結果サマリー

### 重要な発見

#### 1. Winlator 6.0+ の配布戦略

**従来 (〜v5.x)**:
- APK (アプリ本体) + OBB (データファイル、~200MB)
- 初回起動時にOBBから抽出

**現在 (v6.0+)**:
- **All-in-one APK**: Wineバイナリ含めて全てAPKにバンドル
- **OBB不要**: ダウンロードなしで即使用可能
- **初回起動高速化**: ネットワーク不要

**参考**:
- [Winlator GitHub Releases](https://github.com/brunodev85/winlator/releases)
- [How to Download, Install & Setup Winlator](https://winlator.com/download-install-winlator/)
- [Winlator APK Download](https://winlator.com/download-winlator/)

**発見**: Winlator v10.1 APKサイズ = **254MB** (Wine + Box64 + DXVK全て含む)

#### 2. WorkManager 2025 ベストプラクティス

**Android 14+ 推奨アプローチ**:
- WorkManagerがデータ同期カテゴリのForeground Service推奨
- DownloadManagerは非推奨 (セキュリティ脆弱性あり)

**進捗トラッキング**:
```kotlin
// Worker内
setProgress(workDataOf("progress" to 50, "downloaded" to 50MB, "total" to 100MB))

// UI観測 (Jetpack Compose)
val workInfo by workManager.getWorkInfoByIdFlow(requestId).collectAsState()
val progress = workInfo?.progress?.getInt("progress", 0) ?: 0
```

**Long-running Worker**:
```kotlin
class LargeFileDownloadWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo()) // 通知表示

        downloadWithProgress { bytesDownloaded, totalBytes ->
            val progress = (bytesDownloaded * 100 / totalBytes).toInt()
            setProgress(workDataOf("progress" to progress))
        }

        return Result.success()
    }
}
```

**参考**:
- [Observe intermediate worker progress - Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe)
- [Support for long-running workers - Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Downloading Files using Work Manager](https://aayush.io/posts/downloading-via-work-manager/)
- [Step by Step Guide to Download Files With WorkManager](https://www.droidcon.com/2022/03/10/step-by-step-guide-to-download-files-with-workmanager/)

#### 3. OkHttp進捗トラッキング

**Interceptorパターン**:
```kotlin
class ProgressInterceptor(
    private val progressListener: (bytesRead: Long, contentLength: Long) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        return originalResponse.newBuilder()
            .body(ProgressResponseBody(originalResponse.body!!, progressListener))
            .build()
    }
}

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: (Long, Long) -> Unit
) : ResponseBody() {
    private val bufferedSource: BufferedSource by lazy {
        source(responseBody.source()).buffer()
    }

    override fun source(): Source {
        return object : ForwardingSource(responseBody.source()) {
            var totalBytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0L
                progressListener(totalBytesRead, contentLength())
                return bytesRead
            }
        }
    }
}
```

**参考**:
- [OkHttp Recipes](https://square.github.io/okhttp/recipes/)
- [Comprehensive Guide to OkHttp](https://scrapfly.io/blog/posts/guide-to-okhttp-java-kotlin)
- [okhttp3-downloadprogress-interceptor](https://github.com/jobinlawrance/okhttp3-downloadprogress-interceptor)

---

## 🎯 実装戦略の決定

### Option A: All-in-one APK (Winlator方式) ⭐ **推奨**

**メリット**:
- ✅ ネットワーク不要 (オフライン完全動作)
- ✅ 初回起動高速
- ✅ 確実なバージョン管理
- ✅ ユーザー体験最高

**デメリット**:
- ❌ APKサイズ増大 (推定 +20MB Wine binaries)
- ❌ Play Storeの100MB制限に注意必要

**実装方法**:
```
app/src/main/assets/winlator/
├── box64-0.3.6.tzst (3.9MB) ✅ 実装済み
├── wine-9.0-arm64.tzst (~20MB) ← 追加予定
├── default.box64rc ✅ 実装済み
└── env_vars.json ✅ 実装済み

初期化フロー:
1. assets/ から .tzst を抽出
2. ZstdDecompressor で解凍 + tar展開
3. 実行権限設定
4. 完了
```

**APKサイズ試算**:
| コンポーネント | サイズ | 累計 |
|--------------|-------|------|
| 現在 (Phase 4A) | 23MB | 23MB |
| Wine 9.0 ARM64 | ~20MB | **43MB** |
| DXVK/VKD3D (Phase 5) | ~5MB | 48MB |
| **合計** | - | **<50MB** ✅ |

### Option B: 初回起動時ダウンロード

**メリット**:
- ✅ 初期APKサイズ小 (23MB維持)
- ✅ 柔軟なアップデート

**デメリット**:
- ❌ 初回起動遅い (~100MB DL)
- ❌ ネットワーク必須
- ❌ ダウンロード失敗リスク
- ❌ WorkManager実装複雑度

**実装方法**:
```kotlin
@HiltWorker
class WineDownloadWorker @AssistedInject constructor(...) : CoroutineWorker() {
    override suspend fun doWork(): Result {
        val url = "https://dl.winehq.org/wine-builds/android/wine-9.0-arm64.tar.xz"
        // OkHttpでダウンロード + 進捗更新
        // WorkManager constraints (WiFi only, storage not low)
    }
}
```

### 🏆 最終決定: **Option A (All-in-one APK)**

**理由**:
1. ✅ APKサイズ目標内 (43MB < 50MB)
2. ✅ ユーザー体験優先 (オフライン動作、高速起動)
3. ✅ Winlator実績あり (v10.1 = 254MB APK成功)
4. ✅ 実装シンプル (既存Box64パターン再利用)

---

## 🏗️ アーキテクチャ設計

### ディレクトリ構造

```
context.filesDir/winlator/
├── box64/
│   ├── box64 (binary) ✅ Phase 4A完了
│   ├── default.box64rc ✅
│   └── env_vars.json ✅
├── wine/ ← Phase 4B追加予定
│   ├── bin/
│   │   ├── wine64 (ARM64 binary)
│   │   ├── wineserver
│   │   └── ...
│   ├── lib/
│   │   ├── wine/ (PE-DLLs)
│   │   └── ...
│   └── share/
│       ├── wine/ (fonts, etc.)
│       └── ...
└── containers/
    └── {container-id}/
        ├── drive_c/
        └── ...
```

### 初期化シーケンス (Phase 4B完了後)

```kotlin
suspend fun initialize(progressCallback: ((Float, String) -> Unit)?): Result<Unit> {
    // 0.0-0.2: Create directories
    dataDir.mkdirs()
    box64Dir.mkdirs()
    wineDir.mkdirs() // 新規
    containersDir.mkdirs()

    // 0.2-0.3: Extract assets
    extractAsset("winlator/box64-0.3.6.tzst", ...)
    extractAsset("winlator/wine-9.0-arm64.tzst", ...) // 新規

    // 0.3-0.6: Box64 extraction
    zstdDecompressor.decompressAndExtract(box64Tzst, box64Dir) { progress, status ->
        progressCallback?.invoke(0.3f + progress * 0.3f, status)
    }

    // 0.6-0.9: Wine extraction (新規)
    zstdDecompressor.decompressAndExtract(wineTzst, wineDir) { progress, status ->
        progressCallback?.invoke(0.6f + progress * 0.3f, status)
    }

    // 0.9-1.0: Verification
    verifyBinaries() // box64 + wine64確認

    progressCallback?.invoke(1.0f, "Initialization complete")
}
```

### WinlatorEmulator 更新計画

```kotlin
class WinlatorEmulator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zstdDecompressor: ZstdDecompressor
) : WindowsEmulator {

    private val wineDir = File(dataDir, "wine")

    companion object {
        // Wine assets
        private const val WINE_ASSET = "winlator/wine-9.0-arm64.tzst"
    }

    override suspend fun initialize(...): Result<Unit> {
        // ... Box64 extraction (既存)

        progressCallback?.invoke(0.6f, "Extracting Wine binaries...")

        // Wine extraction
        val wineTzstFile = File(wineDir, "wine-9.0-arm64.tzst")
        val wine64Binary = File(wineDir, "bin/wine64")

        if (wineTzstFile.exists() && !wine64Binary.exists()) {
            extractAsset(WINE_ASSET, wineTzstFile)

            zstdDecompressor.decompressAndExtract(
                tzstFile = wineTzstFile,
                targetDir = wineDir
            ) { extractProgress, status ->
                progressCallback?.invoke(0.6f + extractProgress * 0.3f, status)
            }.onSuccess {
                // Set executable permissions
                File(wineDir, "bin/wine64").setExecutable(true)
                File(wineDir, "bin/wineserver").setExecutable(true)
                Log.i(TAG, "Wine binaries ready")
            }
        }

        progressCallback?.invoke(0.9f, "Wine ready")
    }
}
```

---

## 📦 Wine バイナリ準備

### 必要なファイル

**Option 1: 公式Wineビルド使用**
- 出典: https://dl.winehq.org/wine-builds/android/
- 問題: Android 9以前対応、古い (Wine 3.0)

**Option 2: Winlatorカスタムビルド使用** ⭐ 推奨
- 出典: Winlator GitHubリポジトリ
- 利点: Android 14+最適化、ARM64専用
- バージョン: Wine 9.0+ (Winlator 10.1使用)

**Option 3: 自前ビルド**
- 出典: Wine公式ソース + Android NDK
- 利点: 完全制御、最新版使用可能
- 欠点: ビルド複雑、時間かかる

**推奨アプローチ**:
1. Winlator v10.1 APKから抽出 (リバースエンジニアリング)
2. または Winlator開発者に問い合わせ
3. ライセンス確認 (Wine = LGPL, 商用利用OK)

### ファイルサイズ試算

```
Wine 9.0 ARM64 (推定):
├── bin/ (binaries) ~5MB
├── lib/ (libraries) ~10MB
└── share/ (data) ~5MB
合計: ~20MB (圧縮前)
.tzst圧縮後: ~8-10MB (zstd ratio ~2-2.5x)
```

---

## 🔮 Phase 4C-D 実装ロードマップ

### Phase 4C: Wine環境初期化

**目標**: Wine prefixの作成と初期化

**実装内容**:
```kotlin
suspend fun createContainer(config: EmulatorContainerConfig): Result<EmulatorContainer> {
    val containerDir = File(containersDir, containerId)
    val driveC = File(containerDir, "drive_c")

    // Directory structure
    driveC.mkdirs()
    File(driveC, "windows/system32").mkdirs()
    File(driveC, "Program Files").mkdirs()
    File(driveC, "users/Public").mkdirs()

    // Run wineboot --init
    val wineBinary = File(wineDir, "bin/wine64")
    val box64Binary = File(box64Dir, "box64")

    val env = mapOf(
        "WINEPREFIX" to containerDir.absolutePath,
        "WINEARCH" to "win64",
        "DISPLAY" to ":0"
    )

    executeCommand(
        command = listOf(box64Binary.path, wineBinary.path, "wineboot", "--init"),
        environment = env,
        workingDir = containerDir
    )

    // Create registry settings
    // Configure DXVK/VKD3D
}
```

**参考**:
- Wine初期化プロセス
- Winlator container構造

### Phase 4D: 実際のゲーム起動

**目標**: Windowsゲーム実行

**実装内容**:
```kotlin
suspend fun launchExecutable(
    container: EmulatorContainer,
    executable: File,
    arguments: List<String>
): Result<EmulatorProcess> {
    val wine64 = File(wineDir, "bin/wine64")
    val box64 = File(box64Dir, "box64")

    val command = buildList {
        add(box64.path)
        add(wine64.path)
        add(executable.absolutePath)
        addAll(arguments)
    }

    val env = buildMap {
        put("WINEPREFIX", container.rootPath.absolutePath)
        put("WINEARCH", "win64")
        put("WINEDLLOVERRIDES", "d3d11,dxgi=n") // DXVK
        put("DISPLAY", ":0")
        // Graphics driver settings
        put("TU_DEBUG", "noconform") // Turnip
    }

    val process = ProcessBuilder(command)
        .directory(container.rootPath)
        .apply { environment().putAll(env) }
        .start()

    return Result.success(EmulatorProcess(...))
}
```

---

## 📚 参考資料 (Sources)

### Winlator Architecture
- [Winlator GitHub](https://github.com/brunodev85/winlator) ⭐ メイン参考
- [Winlator: Windows Emulator for Android](https://winlator.com)
- [How to Download, Install & Setup Winlator](https://winlator.com/download-install-winlator/)
- [Winlator APK Download](https://winlator.com/download-winlator/)
- [Winlator GlibC Setup Guide](https://winlator.dev/winlator-glibc/)

### WorkManager Best Practices
- [Observe intermediate worker progress - Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe)
- [Support for long-running workers - Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Getting started with WorkManager - Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)
- [Work Manager in Android with Jetpack Compose](https://www.danigomez.dev/blog/work_manager_in_android_with_jetpack_compose_and_kotlin_coroutines)
- [Downloading Files using Work Manager](https://aayush.io/posts/downloading-via-work-manager/)
- [Step by Step Guide to Download Files With WorkManager](https://www.droidcon.com/2022/03/10/step-by-step-guide-to-download-files-with-workmanager/)
- [Android WorkManager: A Complete Technical Deep Dive](https://androidengineers.substack.com/p/android-workmanager-a-complete-technical)

### OkHttp & Progress Tracking
- [OkHttp Recipes](https://square.github.io/okhttp/recipes/)
- [Comprehensive Guide to OkHttp for Java and Kotlin](https://scrapfly.io/blog/posts/guide-to-okhttp-java-kotlin)
- [okhttp3-downloadprogress-interceptor](https://github.com/jobinlawrance/okhttp3-downloadprogress-interceptor)
- [Use okhttp to download file and show progress bar](https://www.cygonna.com/2024/02/use-okhttp-to-download-file-and-show.html)

### Wine for Android
- [Wine for Android - WineHQ](https://dl.winehq.org/wine-builds/android/)
- [Wine on arm64 for 32 and 64-bit apps - WineHQ Forums](https://forum.winehq.org/viewtopic.php?t=37000)
- [ARM64 Wiki - WineHQ](https://wiki.winehq.org/ARM64)

---

## 📝 まとめ

**Phase 4B Research 完了内容**:
- ✅ Winlator配布戦略分析 (All-in-one APK方式)
- ✅ WorkManager 2025ベストプラクティス調査
- ✅ OkHttp進捗トラッキング研究
- ✅ 実装戦略決定 (Option A: APKバンドル)
- ✅ アーキテクチャ設計完了
- ✅ Phase 4C-D ロードマップ作成

**技術的決定**:
- ✅ All-in-one APK戦略採用
- ✅ APKサイズ目標達成可能 (43MB < 50MB)
- ✅ 既存ZstdDecompressor再利用
- ✅ Box64パターン踏襲 (実績あり)

**次のステップ**:
1. Wine 9.0 ARM64バイナリ取得 (Winlator抽出 or ビルド)
2. .tzst圧縮してassetsに配置
3. WinlatorEmulator.initialize() 更新
4. Phase 4C: Wine prefix初期化実装

**現在の制限**:
- ⏳ Wine バイナリファイルがまだ無い
- ⏳ 実装はバイナリ取得後に実施

**ブロッカー解決方法**:
- Option 1: Winlator APKからバイナリ抽出 (要ライセンス確認)
- Option 2: Wine公式ソースからARM64ビルド
- Option 3: Winlator開発者に協力依頼

---

**Status**: Phase 4B リサーチ完了 📋
**次回**: Wine バイナリ取得 → Phase 4C 実装
**APKサイズ計画**: 23MB (現在) → 43MB (Wine追加) → <50MB (目標達成!)
