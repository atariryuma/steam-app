# SteamDeck Mobile - セットアップ手順

## 📋 必要な環境

### 1. JDK 17 のインストール

**Windows:**
```powershell
# Temurin JDK 17 をダウンロード
# https://adoptium.net/temurin/releases/?version=17

# インストール後、環境変数を設定
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot", "User")

# 確認
java -version
```

### 2. Android SDK のインストール

**方法1: Android Studio経由 (推奨)**
1. [Android Studio](https://developer.android.com/studio) をダウンロード
2. インストール時に Android SDK も自動的にインストールされる
3. デフォルトパス: `C:\Users\<ユーザー名>\AppData\Local\Android\Sdk`

**方法2: Command Line Tools のみ**
1. [SDK Command Line Tools](https://developer.android.com/studio#command-tools) をダウンロード
2. 任意のフォルダに解凍 (例: `C:\Android\sdk`)
3. SDK Manager で必要なコンポーネントをインストール

### 3. 環境変数の設定

```powershell
# ANDROID_HOME を設定
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\<ユーザー名>\AppData\Local\Android\Sdk", "User")

# PATH に追加
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$currentPath;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin"
[System.Environment]::SetEnvironmentVariable("Path", $newPath, "User")

# 確認
adb version
```

---

## 🔧 ビルド方法

### 方法1: Android Studio を使う (推奨)

1. **プロジェクトを開く**
   ```
   Android Studio → File → Open
   → "f:\atari\OneDrive\ドキュメント\My Projects\steam app" を選択
   ```

2. **Gradleの同期**
   - 自動的に依存関係がダウンロードされる
   - または: `File → Sync Project with Gradle Files`

3. **ビルド**
   - Debug APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`
   - Release APK: `Build → Generate Signed Bundle / APK...`

4. **実行**
   - エミュレータを作成: `Tools → Device Manager → Create Device`
   - または実機を接続して USB デバッグを有効化
   - `Run → Run 'app'` (Shift+F10)

---

### 方法2: コマンドラインを使う

#### Debug APKをビルド

```bash
# Windowsの場合
cd "f:\atari\OneDrive\ドキュメント\My Projects\steam app"
.\gradlew assembleDebug

# ビルドされたAPKの場所
# app\build\outputs\apk\debug\app-debug.apk
```

#### Release APKをビルド (署名付き)

```bash
# キーストアを作成 (初回のみ)
keytool -genkey -v -keystore steamdeck-mobile.keystore -alias steamdeck -keyalg RSA -keysize 2048 -validity 10000

# Release APKをビルド
.\gradlew assembleRelease

# 署名
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore steamdeck-mobile.keystore app\build\outputs\apk\release\app-release-unsigned.apk steamdeck

# 最適化 (zipalign)
zipalign -v 4 app\build\outputs\apk\release\app-release-unsigned.apk app\build\outputs\apk\release\app-release.apk
```

---

## 📱 実機・エミュレータでテスト

### エミュレータで実行

```bash
# エミュレータのリストを確認
emulator -list-avds

# エミュレータを起動
emulator -avd <エミュレータ名>

# APKをインストール
adb install app\build\outputs\apk\debug\app-debug.apk

# または直接実行
.\gradlew installDebug
```

### 実機で実行

1. **USBデバッグを有効化**
   - 設定 → 端末情報 → ビルド番号を7回タップ (開発者モードを有効化)
   - 設定 → 開発者向けオプション → USBデバッグを有効化

2. **実機を接続**
   ```bash
   # デバイスの確認
   adb devices

   # APKをインストール
   adb install app\build\outputs\apk\debug\app-debug.apk

   # または
   .\gradlew installDebug
   ```

---

## 🐛 トラブルシューティング

### 問題1: `JAVA_HOME is not set`

```powershell
# JDK 17 のパスを確認
dir "C:\Program Files\Eclipse Adoptium\"

# 環境変数を設定
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot", "User")

# PowerShellを再起動
```

### 問題2: `SDK location not found`

プロジェクトルートに `local.properties` ファイルを作成:

```properties
sdk.dir=C\:\\Users\\<ユーザー名>\\AppData\\Local\\Android\\Sdk
```

### 問題3: ビルドエラー

```bash
# Gradleキャッシュをクリア
.\gradlew clean

# 依存関係を再ダウンロード
.\gradlew --refresh-dependencies

# ビルドキャッシュを削除
rm -rf .gradle
rm -rf app\build
```

### 問題4: `Android SDK is missing`

```bash
# SDK Managerで必要なコンポーネントをインストール
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

---

## 📦 APKサイズの最適化

現在の設定でAPKサイズを50MB以下に抑えるための最適化:

1. **ProGuard/R8が有効** (release buildで自動適用)
2. **ARM64-v8aのみ対応** (build.gradle.kts で設定済み)
3. **未使用リソースの削除** (`shrinkResources = true`)

---

## 🚀 CI/CDパイプライン (GitHub Actions)

プロジェクトルートに `.github/workflows/android.yml` を作成:

```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew assembleDebug

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📝 チェックリスト

開発環境セットアップ完了確認:

- [ ] JDK 17 がインストールされている (`java -version`)
- [ ] Android SDK がインストールされている (`adb version`)
- [ ] JAVA_HOME が設定されている
- [ ] ANDROID_HOME が設定されている
- [ ] `.\gradlew assembleDebug` が成功する
- [ ] エミュレータまたは実機でアプリが起動する

---

## 🔗 参考リンク

- [Android Developer Guide](https://developer.android.com/guide)
- [Gradle Plugin User Guide](https://developer.android.com/studio/build)
- [ProGuard/R8 Documentation](https://developer.android.com/studio/build/shrink-code)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
