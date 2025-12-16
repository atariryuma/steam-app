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

```bash
# リポジトリクローン
git clone https://github.com/atariryuma/steam-app.git
cd steam-app

# Winlatorサブモジュール初期化（将来実装予定）
git submodule update --init --recursive

# Android Studioでプロジェクトを開く
# File > Open > steam-app/SteamDeckMobile
```

### ビルド

```bash
# Debug APK
./gradlew assembleDebug

# Release AAB（Google Play用）
./gradlew bundleRelease

# テスト実行
./gradlew test

# インストルメンテーションテスト
./gradlew connectedAndroidTest
```

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

### Phase 5: コントローラーサポート
- [ ] InputDevice API統合
- [ ] ボタンマッピング
- [ ] プロファイル管理

### Phase 6: リリース準備
- [ ] APK軽量化（目標: 50MB以下）
- [ ] パフォーマンス最適化
- [ ] テスト（UI、ユニット、実機）

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

**現在の状態**: Phase 4（ダウンロード管理）完了 - MVP + Steam統合 + ファイルインポート + ダウンロード管理完成

Made with ❤️ for Steam gamers on Android
