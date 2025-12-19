# SteamDeck Mobile

**Steam特化のAndroidゲームエミュレーター - Winlator統合による軽量アプリ**

[![Android CI](https://github.com/atariryuma/steam-app/workflows/Android%20CI/badge.svg)](https://github.com/atariryuma/steam-app/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blue.svg)](https://developer.android.com/ndk/guides/abis)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-brightgreen.svg)](https://developer.android.com/jetpack/compose)

## 🔗 Quick Links

- [📥 Download Latest Release](https://github.com/atariryuma/steam-app/releases)
- [🐛 Report a Bug](https://github.com/atariryuma/steam-app/issues/new?template=bug_report.md)
- [💡 Request a Feature](https://github.com/atariryuma/steam-app/issues/new?template=feature_request.md)
- [📚 Contributing Guide](CONTRIBUTING.md)
- [🔒 Security Policy](SECURITY.md)
- [🎮 Winlator Project](https://github.com/brunodev85/winlator)

## 📱 概要

SteamDeck MobileはSteamライブラリのWindowsゲームをAndroidデバイス上で実行できる軽量アプリです。
Winlator（Wine + Box86/Box64）を統合し、Snapdragon搭載デバイスで快適なゲーム体験を提供します。

### 主要機能

- ✅ **Steamライブラリ統合**：Steam Web APIによる自動同期
- ✅ **Windowsゲーム実行**：Winlatorを使用したネイティブ実行
- ✅ **ファイルインポート**：USB OTG、SMB、FTP、ローカルストレージ対応
- ✅ **ゲームコントローラーサポート**：Bluetooth/USB接続コントローラー
- ✅ **高速ダウンロード管理**：マルチスレッド、一時停止/再開機能

## 🚀 技術スタック

- **言語**: Kotlin 2.1.0
- **UI**: Jetpack Compose（Material3）
- **アーキテクチャ**: Clean Architecture + MVVM
- **DI**: Hilt 2.52
- **DB**: Room 2.6.1
- **非同期**: Coroutines + Flow
- **ネットワーク**: Retrofit 2.11.0 + OkHttp 4.12.0
- **画像読み込み**: Coil 2.7.0
- **エミュレーション**: Winlator（Wine + Box86/Box64）

## 📋 システム要件

- **Android**: 8.0 (API 26) 以降
- **アーキテクチャ**: ARM64-v8a
- **推奨デバイス**: Snapdragon 8 Gen 1以上
- **最小解像度**: 1280x720 (HD)
- **ストレージ**: 最低1GB以上の空き容量

## 🛠️ 開発環境

### 必要なツール

- Android Studio Ladybug 2024.2.1+
- JDK 17+
- Git

### セットアップ手順

詳細な手順は [SETUP.md](SETUP.md) を参照してください。

#### クイックスタート

```bash
# 1. 環境チェック（初回のみ）
check-environment.bat

# 2. (オプション) 開発用API Key設定
# local.propertiesに以下を追加:
# STEAM_API_KEY=YOUR_32_CHAR_HEX_KEY

# 3. Debug APKビルド + インストール
build-debug.bat
```

#### Android Studioを使う場合

```bash
# リポジトリクローン
git clone https://github.com/atariryuma/steam-app.git
cd steam-app

# Android Studioでプロジェクトを開く
# File > Open > "steam app" フォルダを選択

# 自動的に依存関係がダウンロードされる
# Run > Run 'app' (Shift+F10) で実行
```

### ビルド方法

#### 利用可能なビルドスクリプト

```bash
# Debug APK (開発用 - 推奨)
build-debug.bat                # ビルド + adbインストール

# Release APK (配布用 - R8最適化)
build-release.bat              # ビルドのみ
build-and-install.bat          # ビルド + adbインストール

# 既存APKの再インストール
install-debug.bat              # ビルド済みDebug APKをインストール
```

#### Gradleコマンド

```bash
# Debug APKをビルド（開発用）
./gradlew assembleDebug

# Release APKをビルド（配布用、最適化済み）
./gradlew assembleRelease

# テスト実行
./gradlew test

# インストルメンテーションテスト
./gradlew connectedAndroidTest
```

#### ビルド成果物の場所

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Android App Bundle**: `app/build/outputs/bundle/release/app-release.aab`

## 📂 プロジェクト構造

```
SteamDeckMobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/steamdeck/mobile/
│   │   │   │   ├── presentation/       # UI層（Compose）
│   │   │   │   │   ├── ui/            # 画面
│   │   │   │   │   ├── viewmodel/     # ViewModels
│   │   │   │   │   └── theme/         # テーマ
│   │   │   │   ├── domain/            # ドメイン層
│   │   │   │   │   ├── model/         # ドメインモデル
│   │   │   │   │   ├── usecase/       # ユースケース
│   │   │   │   │   └── repository/    # リポジトリIF
│   │   │   │   ├── data/              # データ層
│   │   │   │   │   ├── local/         # ローカルデータ
│   │   │   │   │   ├── remote/        # リモートデータ
│   │   │   │   │   └── repository/    # リポジトリ実装
│   │   │   │   ├── core/              # コア機能
│   │   │   │   │   ├── winlator/      # Winlator統合
│   │   │   │   │   ├── fileimport/    # ファイルインポート
│   │   │   │   │   ├── download/      # ダウンロード管理
│   │   │   │   │   └── controller/    # コントローラー
│   │   │   │   └── di/                # 依存性注入
│   │   │   └── AndroidManifest.xml
│   │   └── test/                       # ユニットテスト
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml              # バージョンカタログ
├── build.gradle.kts
└── settings.gradle.kts
```

## 🎯 開発ロードマップ

### Phase 1: MVP ✅ 完了
- [x] プロジェクトセットアップ
- [x] Clean Architectureパッケージ構造
- [x] Room DB実装（ゲーム、コンテナ管理）
- [x] 基本UI（ホーム、詳細画面）
- [x] Winlator統合（スタブ実装）

### Phase 2: Steam統合 ✅ 完了

- [x] Steam Web API統合
- [x] Steam API Service実装
- [x] ライブラリ同期機能
- [x] Settings画面とSteam認証UI
- [x] DataStore統合

### Phase 3: ファイルインポート ✅ 完了
- [x] USB OTGサポート（libaums）
- [x] SMB/CIFS統合（jcifs-ng SMB2/3）
- [x] FTP/FTPS統合（Apache Commons Net）
- [x] SAFローカルストレージ

### Phase 4: ダウンロード管理 ✅ 完了

- [x] WorkManager統合
- [x] マルチスレッドダウンロード（8MBチャンク）
- [x] 一時停止/再開機能
- [x] バックグラウンドダウンロード
- [x] ダウンロードUI実装（Material3）

### Phase 4C: Wine統合 ✅ 完了

- [x] Winlator 10.1 APKからWine 9.0+抽出
- [x] XZ圧縮解凍サポート（Apache Commons Compress）
- [x] Wine rootfs (53MB) 展開実装
- [x] Box64 0.3.6バイナリ統合
- [x] R8最適化（63MB Release APK）
- [x] ProGuard rules（JNI/セキュリティ保護）

**成果**: 63MB APK (Winlatorの55%サイズ、141MB→63MB)

### Phase 5: コントローラーサポート ✅ 完了

- [x] InputDevice API統合（自動検出）
- [x] ボタンマッピングシステム（16ボタン + 4軸）
- [x] プロファイル管理（Room Database v3）
- [x] ジョイスティックリアルタイムプレビュー
- [x] Xbox/PlayStation/Nintendo自動検出（Vendor ID）
- [x] デッドゾーン調整機能（0-50%）
- [x] Material3 UI実装（ControllerSettingsScreen）
- [ ] バイブレーション対応（Phase 5.1で実装予定）

**成果**: 11ファイル追加（~1,813行）、APKサイズ据え置き（76MB）

### Phase 6: リリース準備
- [x] APK軽量化（目標: <80MB）✅ 達成（63MB）
- [x] R8最適化（-17%サイズ削減）
- [ ] UIテスト完全カバレッジ
- [ ] 実機動作検証（Wine実行テスト）

## 🤝 貢献

現在、個人開発プロジェクトですが、Issue報告は歓迎します。

### 報告方法

1. [Issues](https://github.com/atariryuma/steam-app/issues)ページを開く
2. 「New Issue」をクリック
3. バグ報告または機能リクエストのテンプレートを選択
4. 詳細を記入して送信

## 📄 ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は[LICENSE](LICENSE)ファイルを参照してください。

## 🙏 謝辞

- [Winlator](https://github.com/brunodev85/winlator) - Windowsエミュレーション
- [Steam Web API](https://steamcommunity.com/dev) - Steamライブラリ統合
- Android Jetpack Compose - モダンUI構築

## 📞 サポート

問題が発生した場合：

1. [既存のIssue](https://github.com/atariryuma/steam-app/issues)を検索
2. 該当するものがなければ新しいIssueを作成
3. [Contributing Guide](CONTRIBUTING.md)を参照

---

**現在の状態**: Phase 5（コントローラーサポート）完了 - MVP + Steam統合 + ファイルインポート + ダウンロード管理 + Wine統合 + コントローラーサポート完成

Made with ❤️ for Steam gamers on Android
