# 🚀 SteamDeck Mobile - クイックスタートガイド

このガイドは、初めてAndroidアプリをビルドする方向けの簡単な手順です。

---

## 📱 最も簡単な方法: Android Studioを使う

### ステップ1: Android Studioのインストール

1. **Android Studioをダウンロード**
   - https://developer.android.com/studio
   - インストーラーを実行して、すべてデフォルト設定でOK

2. **初回起動時の設定**
   - Android SDKが自動的にインストールされる
   - すべてデフォルトで進める

### ステップ2: プロジェクトを開く

1. Android Studioを起動
2. `Open` をクリック
3. このフォルダを選択: `f:\atari\OneDrive\ドキュメント\My Projects\steam app`
4. 数分待つと、依存関係のダウンロードが完了

### ステップ3: ビルド＆実行

1. **エミュレータを作成** (初回のみ)
   - メニュー: `Tools → Device Manager`
   - `Create Device` をクリック
   - 機種: `Pixel 6 Pro` を選択
   - システムイメージ: `API 35` (Android 15) を選択
   - `Finish` をクリック

2. **アプリを実行**
   - 上部の緑色の再生ボタン ▶️ をクリック
   - または `Shift + F10`

3. **エミュレータでアプリが起動！** 🎉

---

## 💻 コマンドラインを使う方法

この方法は、Android Studioをインストールしたくない場合に使用します。

### ステップ1: 必要なツールをインストール

#### A. JDK 17 のインストール

1. https://adoptium.net/temurin/releases/?version=17 を開く
2. `Windows x64` の `.msi` ファイルをダウンロード
3. インストーラーを実行（デフォルト設定でOK）

#### B. Android SDK Command Line Tools のインストール

1. https://developer.android.com/studio#command-tools を開く
2. `Command line tools only` セクションから `Windows` 版をダウンロード
3. 任意のフォルダに解凍 (例: `C:\Android`)
4. フォルダ構造:
   ```
   C:\Android\
   └── cmdline-tools\
       └── latest\
           ├── bin\
           ├── lib\
           └── ...
   ```

#### C. 環境変数を設定 (PowerShell管理者権限で実行)

```powershell
# JDK 17 のパスを確認 (インストールしたバージョンに合わせる)
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot"
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "User")

# Android SDK のパス
$androidSdk = "C:\Android"
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $androidSdk, "User")

# PATHに追加
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$currentPath;$androidSdk\cmdline-tools\latest\bin;$androidSdk\platform-tools"
[System.Environment]::SetEnvironmentVariable("Path", $newPath, "User")

# PowerShellを再起動して設定を反映
```

#### D. Android SDK コンポーネントをインストール

```bash
# PowerShellまたはコマンドプロンプトで実行
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### ステップ2: 環境チェック

```bash
cd "f:\atari\OneDrive\ドキュメント\My Projects\steam app"
.\check-environment.bat
```

すべて `[OK]` が表示されればOK！

### ステップ3: APKをビルド

```bash
# Debug APKをビルド
.\build-apk.bat
```

成功すると、APKファイルが以下の場所に作成されます:
```
app\build\outputs\apk\debug\app-debug.apk
```

### ステップ4: 実機にインストール

#### 実機の準備

1. **開発者向けオプションを有効化**
   - 設定 → 端末情報 → ビルド番号を7回タップ

2. **USBデバッグを有効化**
   - 設定 → 開発者向けオプション → USBデバッグをON

3. **PCに接続**
   - USBケーブルで接続
   - 「USBデバッグを許可しますか？」→ 許可

#### インストール

```bash
# デバイスが認識されているか確認
adb devices

# APKをインストール
adb install app\build\outputs\apk\debug\app-debug.apk

# アプリを起動
adb shell am start -n com.steamdeck.mobile/.presentation.MainActivity
```

---

## 🎮 エミュレータで実行する場合

### エミュレータの作成 (Android Studioなし)

```bash
# システムイメージをダウンロード
sdkmanager --install "system-images;android-35;google_apis;x86_64"

# AVD (Android Virtual Device) を作成
avdmanager create avd -n Pixel6Pro -k "system-images;android-35;google_apis;x86_64" -d "pixel_6_pro"

# エミュレータを起動
emulator -avd Pixel6Pro

# 別のターミナルでAPKをインストール
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## ❓ トラブルシューティング

### Q1: `JAVA_HOME is not set` エラー

**A:** JDK 17をインストールして、環境変数を設定してください。

```powershell
# インストール場所を確認
dir "C:\Program Files\Eclipse Adoptium"

# 環境変数を設定 (PowerShellで)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot", "User")

# PowerShellを再起動
```

### Q2: ビルドが失敗する

**A:** キャッシュをクリアして再ビルド

```bash
.\gradlew clean
.\gradlew --refresh-dependencies
.\gradlew assembleDebug
```

### Q3: `adb devices` でデバイスが表示されない

**A:** USBデバッグの確認

1. 実機側で「USBデバッグを許可しますか？」のダイアログが出ているか確認
2. USBケーブルを抜き差し
3. ADBサーバーを再起動:
   ```bash
   adb kill-server
   adb start-server
   adb devices
   ```

### Q4: Android Studioが重い

**A:** メモリ設定を調整

1. `Help → Edit Custom VM Options`
2. 以下を追加:
   ```
   -Xmx4096m
   -XX:MaxMetaspaceSize=512m
   ```
3. Android Studioを再起動

---

## 📚 次のステップ

アプリが動いたら:

1. **Steam API キーを取得**
   - https://steamcommunity.com/dev/apikey
   - アプリの設定画面でAPI Keyを入力

2. **コードを編集**
   - `app/src/main/java/com/steamdeck/mobile/` 配下のファイルを編集
   - 変更後、再ビルドして動作確認

3. **プロジェクトガイドを読む**
   - [CLAUDE.md](CLAUDE.md) - AI支援コーディングガイド
   - [SETUP.md](SETUP.md) - 詳細なセットアップ手順
   - [CONTRIBUTING.md](CONTRIBUTING.md) - 開発ガイドライン

---

## 🎉 成功おめでとうございます！

アプリが動作したら、ぜひプロジェクトにコントリビュートしてください！

- 🐛 バグ報告: [Issues](https://github.com/atariryuma/steam-app/issues)
- 💡 機能提案: [Discussions](https://github.com/atariryuma/steam-app/discussions)
- 🔧 プルリクエスト: [Contributing Guide](CONTRIBUTING.md)

Happy Coding! 🚀
