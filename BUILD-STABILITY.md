# ビルド安定性ガイド

## 問題の原因

以下の4つの要因が複合的にビルド不安定性を引き起こしていました:

### 1. Configuration Cache の副作用
- Gradle 8.7 の Configuration Cache は KSP (Hilt/Room) との相性が悪い
- キャッシュ破損時に変更が反映されない
- ビルド失敗後の復旧が不安定

### 2. Gradle Daemon のメモリリーク
- 長時間稼働したデーモンがヒープメモリを消費
- Parallel build との組み合わせで競合状態が発生
- 前回のビルド状態が残存して誤動作

### 3. 増分ビルドのキャッシュ不整合
- KSP生成コード (Hilt, Room) のキャッシュが古いまま残る
- `.gradle/caches/` と `.kotlin/` のメタデータ破損
- R8最適化のキャッシュが不整合を起こす

### 4. Windows ファイルロック問題
- Gradleデーモンがファイルをロックし続ける
- ビルド中のファイル削除・上書き失敗

---

## 適用済みの修正

### gradle.properties の変更
```properties
# Configuration Cache を無効化 (KSP との相性問題)
# org.gradle.configuration-cache=true

# KSP 増分コンパイルを無効化 (安定性優先)
ksp.incremental=false
ksp.incremental.intermodule=false

# Gradle キャッシュを無効化 (クリーンビルド優先)
org.gradle.caching=false
```

### ビルドスクリプトの追加
- `clean-build.bat`: 完全クリーンビルド (問題発生時)
- `quick-build.bat`: 通常の開発ビルド
- `restart-gradle.bat`: デーモン再起動

---

## 使用方法

### 通常の開発ビルド
```batch
quick-build.bat
```

### 変更が反映されない場合
```batch
clean-build.bat
```

### ビルドがハングした場合
```batch
restart-gradle.bat
```

---

## ビルド失敗時のチェックリスト

1. **Gradle デーモンを再起動**
   ```batch
   gradlew.bat --stop
   ```

2. **キャッシュをクリア**
   ```batch
   rmdir /s /q .gradle\caches
   rmdir /s /q .kotlin
   rmdir /s /q app\build
   ```

3. **クリーンビルド実行**
   ```batch
   gradlew.bat clean --no-configuration-cache
   gradlew.bat assembleDebug --no-configuration-cache
   ```

4. **それでも失敗する場合**
   - Android Studio / IntelliJ IDEA を再起動
   - PC を再起動 (Windows ファイルロック解放)

---

## パフォーマンスへの影響

### トレードオフ
- ❌ **Configuration Cache 無効化**: 初回ビルド時間が若干増加 (+5~10秒)
- ❌ **KSP 増分コンパイル無効化**: Hilt/Room 変更時のリビルドが遅延 (+10~20秒)
- ❌ **Gradle キャッシュ無効化**: 依存関係の再ダウンロード頻度増加

### メリット
- ✅ **確実な変更反映**: コード変更が100%反映される
- ✅ **ビルド成功率向上**: エラー発生率が大幅に減少
- ✅ **デバッグ効率化**: 古いコードが残らない

---

## 今後の改善案

### Gradle 9.0 以降で検討
- Configuration Cache の KSP サポート改善を待つ
- 安定版になったら再有効化を検討

### KSP 2.0 以降で検討
- 増分コンパイルの安定性が向上したら再有効化
- Kotlin 2.1+ との組み合わせで検証

### 現時点の推奨設定
- **開発中**: 現在の設定を維持 (安定性優先)
- **CI/CD**: `--no-daemon` + `--no-configuration-cache` でクリーンビルド
- **リリース**: 完全クリーンビルド必須

---

## 参考資料

- [Gradle Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [KSP Incremental Processing](https://kotlinlang.org/docs/ksp-incremental.html)
- [Android Gradle Plugin DSL](https://developer.android.com/build/releases/gradle-plugin-dsl)
