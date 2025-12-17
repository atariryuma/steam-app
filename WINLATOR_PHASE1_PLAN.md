# Winlator Integration - Phase 1 Implementation Plan

## 現在の状況

### ✅ 完了したこと
1. **Winlatorソースコード分析**
   - リポジトリをクローン: https://github.com/brunodev85/winlator
   - Container, ContainerManager クラスの構造を理解
   - 必要なコンポーネントを特定

2. **基盤コード作成**
   - `WineContainer.kt` - コンテナ設定モデル
   - `WineLauncher.kt` - .exe起動インターフェース（スケルトン）

### ❌ 未完成（Phase 1の残り作業）

## Phase 1の目標: 最小限の.exe起動

**ゴール**: 簡単なWindows実行ファイル（例: notepad.exe）を起動できるようにする

### 必要な作業

#### 1. Wine/Box64バイナリの入手 🔥 最優先

**オプションA: Winlator APKから抽出**
```bash
# 1. Winlator APKをダウンロード
wget https://github.com/brunodev85/winlator/releases/latest/download/winlator.apk

# 2. APKを解凍
unzip winlator.apk -d winlator_extracted

# 3. 必要なファイルを探す
cd winlator_extracted
find . -name "*wine*"
find . -name "*box*"

# 4. バイナリを抽出
# 場所: lib/arm64-v8a/ または assets/
```

**オプションB: 事前ビルドされたバイナリを使用**
- Wine for ARM64: https://github.com/brunodev85/wine
- Box64: https://github.com/ptitSeb/box64/releases

**必要なファイル:**
```
app/src/main/assets/
├── wine/
│   ├── bin/
│   │   ├── wine64          # Wine 64-bit launcher
│   │   ├── wine            # Wine 32-bit launcher
│   │   └── wineserver      # Wine server
│   └── lib/                # Wine libraries
└── box64/
    ├── box64               # ARM64 translator
    └── box86               # ARM32 translator (optional)
```

**ファイルサイズ見積もり:**
- Wine: ~50MB
- Box64: ~2MB
- 合計: ~52MB（APKサイズ増加）

#### 2. WineLauncherの実装

```kotlin
// WineLauncher.kt の実装
suspend fun launchExecutable(
    container: WineContainer,
    exePath: String,
    args: List<String>
): Result<WineProcess> {
    // 1. 環境変数を設定
    val env = container.envVars.toMutableMap().apply {
        put("WINEPREFIX", container.getWinePrefix())
        put("WINEDEBUG", "-all")  // デバッグログを無効化
    }

    // 2. コマンドを構築
    val wineCommand = listOf(
        "${getBox64Path()}/box64",
        "${getWinePath()}/bin/wine64",
        exePath
    ) + args

    // 3. プロセスを起動
    val processBuilder = ProcessBuilder(wineCommand)
    processBuilder.environment().putAll(env)
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val pid = getPid(process)  // ProcessのPIDを取得

    return Result.success(
        WineProcess(pid = pid, exePath = exePath, process = process)
    )
}
```

#### 3. コンテナ初期化

```kotlin
// ContainerManager.kt を作成
class ContainerManager @Inject constructor(
    private val context: Context
) {
    suspend fun createContainer(name: String): Result<WineContainer> {
        val containerId = System.currentTimeMillis()
        val containerDir = File(context.dataDir, "containers/$containerId")

        // 1. ディレクトリ構造を作成
        File(containerDir, "drive_c/windows").mkdirs()
        File(containerDir, "drive_c/Program Files").mkdirs()
        File(containerDir, "drive_c/users").mkdirs()

        // 2. Wineプレフィックスを初期化
        // box64 wine64 wineboot --init
        val initProcess = ProcessBuilder(
            "${getBox64Path()}/box64",
            "${getWinePath()}/bin/wine64",
            "wineboot",
            "--init"
        ).apply {
            environment()["WINEPREFIX"] = containerDir.absolutePath
        }.start()

        initProcess.waitFor()

        val container = WineContainer(
            id = containerId,
            name = name,
            rootPath = containerDir.absolutePath
        )

        return Result.success(container)
    }
}
```

#### 4. テスト用UIの作成

```kotlin
// WineTestScreen.kt
@Composable
fun WineTestScreen(
    viewModel: WineTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { viewModel.testWine() }) {
            Text("Test Wine Installation")
        }

        when (val state = uiState) {
            is WineTestUiState.Loading -> CircularProgressIndicator()
            is WineTestUiState.Success -> Text("✅ Wine works!")
            is WineTestUiState.Error -> Text("❌ ${state.message}")
        }
    }
}
```

### 実装手順（優先順位順）

1. **[Week 1] バイナリ入手**
   - [ ] Winlator APKから Wine/Box64 を抽出
   - [ ] `app/src/main/assets/` に配置
   - [ ] 実行権限の設定方法を確認

2. **[Week 1-2] WineLauncher実装**
   - [ ] `launchExecutable()` を実装
   - [ ] 環境変数の設定
   - [ ] プロセス管理

3. **[Week 2] ContainerManager実装**
   - [ ] コンテナ作成機能
   - [ ] Wine初期化 (`wineboot --init`)
   - [ ] コンテナ一覧取得

4. **[Week 2] テストUI**
   - [ ] WineTestScreen作成
   - [ ] 簡単な.exeファイルでテスト

5. **[Week 3] デバッグ & 最適化**
   - [ ] エラーハンドリング
   - [ ] ログ出力
   - [ ] パフォーマンス改善

### テスト計画

**テストケース1: Wine起動確認**
```bash
# wineserver のバージョン確認
box64 wine64 --version
```

**テストケース2: 簡単な.exe実行**
```bash
# Windows Notepadを起動（Wineに含まれる）
box64 wine64 notepad
```

**テストケース3: 実際のゲーム**
```bash
# 軽量ゲーム（例: Solitaire）
box64 wine64 /path/to/game.exe
```

## 技術的な課題

### 課題1: バイナリのAPK統合
**問題**: Wine/Box64バイナリは50MB+で、APKサイズが大幅に増加
**解決策**:
- Android App Bundle (AAB) 使用でデバイスごとに最適化
- 初回起動時にダウンロード（ユーザー体験は悪化）

### 課題2: 実行権限
**問題**: Androidは `/data/data/` 内のファイルに実行権限が必要
**解決策**:
```kotlin
// 初回起動時にバイナリをコピーし、実行権限を付与
fun setupWineBinaries() {
    val wineDir = File(context.dataDir, "wine")
    context.assets.open("wine/bin/wine64").use { input ->
        File(wineDir, "bin/wine64").apply {
            parentFile?.mkdirs()
            outputStream().use { input.copyTo(it) }
            setExecutable(true, false)  // 実行権限
        }
    }
}
```

### 課題3: ネイティブライブラリ
**問題**: Wine/Box64はネイティブライブラリに依存
**解決策**:
- `app/src/main/jniLibs/arm64-v8a/` に配置
- CMake/NDKでビルド（高度）

## 次のステップ

### Phase 1完了後
- ✅ Wine/Box64が動作
- ✅ 簡単な.exeが起動できる
- ✅ コンテナ管理ができる

### Phase 2へ
- Steam自動インストール
- ゲーム検出機能
- UI改善

## リソース

### 参考リンク
- [Winlator GitHub](https://github.com/brunodev85/winlator)
- [Box64 GitHub](https://github.com/ptitSeb/box64)
- [Wine for ARM](https://github.com/brunodev85/wine)

### サンプルコード場所
- Container管理: `/tmp/winlator/app/src/main/java/com/winlator/container/`
- Wine起動: `/tmp/winlator/app/src/main/java/com/winlator/core/`

---

**現在の状態**: Phase 1準備完了、実装開始可能
**次のタスク**: Winlator APKからバイナリを抽出
