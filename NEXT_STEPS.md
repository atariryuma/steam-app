# 次のステップ - テスト実行とビルド検証

**日付**: 2025-01-17
**ステータス**: ✅ テスト実装完了、環境セットアップ待ち

---

## 🎯 現在の状況

### ✅ 完了した作業

1. **コードレビュー修正** - 6件の致命的なバグを修正
2. **UI実装確認** - すべての主要画面が実装済み
3. **テスト実装** - 112件のテストケースを作成
   - ViewModel Unit Tests: 52件
   - UseCase Unit Tests: 30件
   - Repository Integration Tests: 36件

### 📝 残りの作業

1. **Java環境セットアップ** (必須)
2. **テスト実行と検証**
3. **ビルド確認**
4. **実機/エミュレータテスト**

---

## 🛠️ 環境セットアップ手順

### ステップ1: Java JDK 17のインストール

#### オプションA: Eclipse Temurin (推奨)

1. [Adoptium Temurin](https://adoptium.net/temurin/releases/?version=17) にアクセス
2. **JDK 17 (LTS)** の最新版をダウンロード
   - Operating System: Windows
   - Architecture: x64
   - Package Type: JDK
3. インストーラーを実行
4. インストール時に「Set JAVA_HOME variable」にチェック

#### オプションB: Oracle JDK

1. [Oracle JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) をダウンロード
2. インストーラーを実行

### ステップ2: JAVA_HOME環境変数の設定

#### Windows PowerShell (一時的)

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot"
```

#### Windows システム環境変数 (永続的)

1. **Windowsキー** → 「環境変数」と検索
2. 「システム環境変数の編集」を開く
3. 「環境変数」ボタンをクリック
4. **システム環境変数**セクションで「新規」をクリック
   - 変数名: `JAVA_HOME`
   - 変数値: `C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot`
     (実際のインストールパスに変更)
5. **Path**変数を編集して`%JAVA_HOME%\bin`を追加
6. すべてのウィンドウで「OK」をクリック
7. **コマンドプロンプトを再起動**

### ステップ3: 環境確認

```bash
# プロジェクトディレクトリに移動
cd "f:\atari\OneDrive\ドキュメント\My Projects\steam app"

# 環境チェックスクリプトを実行
.\check-environment.bat
```

**期待される出力:**
```
========================================
SteamDeck Mobile - Environment Check
========================================

[1/5] Checking Java...
[OK] Java found: version "17.0.10" 2024-01-16

[2/5] Checking JAVA_HOME...
[OK] JAVA_HOME: C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot

[3/5] Checking Android SDK...
[!] WARNING: ANDROID_HOME is not set
    (Optional for unit tests)

[4/5] Checking Gradle...
[OK] Gradle wrapper found

[5/5] Checking Git...
[OK] Git version 2.x.x

========================================
Environment Status: READY (with 1 warning)
```

---

## 🧪 テスト実行手順

### ステップ1: Unit Testsの実行

```bash
# すべてのUnit Testsを実行
.\gradlew test

# または Windows PowerShell
./gradlew.bat test
```

**期待される結果:**
```
> Task :app:testDebugUnitTest

HomeViewModelTest > loadGames emits Loading then Success PASSED
HomeViewModelTest > loadGames emits Empty when no games PASSED
...

BUILD SUCCESSFUL in 45s
112 tests completed, 0 failed
```

**テストレポート:**
- 場所: `app/build/reports/tests/testDebugUnitTest/index.html`
- ブラウザで開いて結果を確認

### ステップ2: Integration Testsの実行 (オプション)

**⚠️ 注意**: Android Instrumentation Testsにはエミュレータまたは実機が必要です

#### Android Studio セットアップ (推奨)

1. [Android Studio](https://developer.android.com/studio) をダウンロード・インストール
2. プロジェクトを開く: File → Open → `steam app`フォルダを選択
3. SDK Manager で必要なコンポーネントをインストール
4. AVD Manager でエミュレータを作成 (Pixel 7, API 35推奨)

#### エミュレータでテスト実行

```bash
# エミュレータ起動確認
adb devices

# Integration Testsを実行
.\gradlew connectedDebugAndroidTest
```

**期待される結果:**
```
> Task :app:connectedDebugAndroidTest

GameRepositoryImplTest > insertAndRetrieveGame PASSED
GameRepositoryImplTest > getAllGamesReturnsAllInsertedGames PASSED
...

BUILD SUCCESSFUL in 3m 20s
36 instrumentation tests completed, 0 failed
```

---

## 🏗️ ビルド実行手順

### Debug APKビルド

```bash
# 環境確認
.\check-environment.bat

# Debug APKビルド
.\build-apk.bat
```

**期待される結果:**
```
========================================
SteamDeck Mobile - APK Build Script
========================================

[1/5] Checking Java environment...
openjdk version "17.0.10" 2024-01-16

[2/5] Cleaning build cache...
BUILD SUCCESSFUL

[3/5] Resolving dependencies...

[4/5] Building Debug APK...
BUILD SUCCESSFUL in 2m 15s

[5/5] Build successful!

========================================
APK Location:
app\build\outputs\apk\debug\app-debug.apk
========================================

APK Size: 12 MB
```

### Release APKビルド

```bash
.\build-release.bat
```

**⚠️ 注意**: Release APKは署名されていません。配布前に署名が必要です。

---

## 🔍 トラブルシューティング

### 問題1: `JAVA_HOME is not set`

**原因**: Java環境変数が設定されていない

**解決策**:
```bash
# PowerShell (一時的)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot"

# システム環境変数で永続的に設定 (上記ステップ2参照)
```

### 問題2: `Task 'test' not found`

**原因**: Gradleプロジェクトが正しく認識されていない

**解決策**:
```bash
# Gradle Syncを実行
.\gradlew --refresh-dependencies

# キャッシュをクリア
.\gradlew clean
```

### 問題3: テストが失敗する

**原因**: 依存関係の問題、またはコードのバグ

**解決策**:
```bash
# 詳細なログを表示
.\gradlew test --info

# 特定のテストクラスのみ実行
.\gradlew test --tests HomeViewModelTest

# スタックトレース表示
.\gradlew test --stacktrace
```

### 問題4: `Unable to find method` エラー

**原因**: Gradle/Kotlin/Kotlinプラグインのバージョン不整合

**解決策**:
```bash
# Gradle Wrapperを最新化
.\gradlew wrapper --gradle-version=8.7

# 依存関係を再同期
.\gradlew --refresh-dependencies
```

### 問題5: メモリ不足エラー

**原因**: Gradleのヒープサイズが小さい

**解決策**:
`gradle.properties`に追加:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

---

## 📊 テスト結果の確認方法

### Unit Test レポート

1. テスト実行後、以下のファイルを開く:
   ```
   app/build/reports/tests/testDebugUnitTest/index.html
   ```

2. ブラウザで確認できる内容:
   - 成功/失敗したテスト数
   - 実行時間
   - 各テストの詳細結果
   - 失敗したテストのスタックトレース

### Integration Test レポート

1. テスト実行後、以下のファイルを開く:
   ```
   app/build/reports/androidTests/connected/index.html
   ```

2. 確認できる内容:
   - デバイス情報
   - テスト実行時間
   - 各テストの詳細結果

---

## ✅ 検証チェックリスト

環境セットアップ完了後、以下を確認してください:

- [ ] Java JDK 17がインストール済み
- [ ] JAVA_HOME環境変数が設定済み
- [ ] `.\check-environment.bat` が成功
- [ ] `.\gradlew test` が成功 (112 tests passed)
- [ ] テストレポートが生成されている
- [ ] `.\build-apk.bat` が成功
- [ ] Debug APKが生成されている (`app\build\outputs\apk\debug\app-debug.apk`)
- [ ] (オプション) Integration testsが成功 (36 tests passed)

---

## 🎓 参考情報

### テストコマンド一覧

```bash
# すべてのUnit Tests
.\gradlew test

# 特定のテストクラス
.\gradlew test --tests HomeViewModelTest

# 特定のテストメソッド
.\gradlew test --tests "HomeViewModelTest.loadGames emits Success"

# 詳細ログ付き
.\gradlew test --info

# Integration Tests (要エミュレータ)
.\gradlew connectedAndroidTest

# クリーン + テスト
.\gradlew clean test
```

### ビルドコマンド一覧

```bash
# Debug APK
.\gradlew assembleDebug
# または
.\build-apk.bat

# Release APK
.\gradlew assembleRelease
# または
.\build-release.bat

# クリーンビルド
.\gradlew clean assembleDebug

# 依存関係更新
.\gradlew --refresh-dependencies
```

---

## 📞 サポート

### 問題が解決しない場合

1. **エラーログを確認**:
   ```bash
   .\gradlew test --stacktrace > test-log.txt
   ```

2. **Gradle ログを確認**:
   ```bash
   .\gradlew test --debug > gradle-debug.txt
   ```

3. **GitHub Issues**:
   - プロジェクトの問題を報告
   - エラーログを添付

### ドキュメント

- [SETUP.md](SETUP.md) - 詳細なセットアップガイド
- [QUICKSTART.md](QUICKSTART.md) - クイックスタートガイド
- [TASK_COMPLETION_REPORT.md](TASK_COMPLETION_REPORT.md) - 完了レポート
- [TEST_IMPLEMENTATION_SUMMARY.md](TEST_IMPLEMENTATION_SUMMARY.md) - テスト詳細

---

**最終更新**: 2025-01-17
**ステータス**: 環境セットアップ待ち
**次のアクション**: Java JDK 17をインストールして`.\check-environment.bat`を実行
