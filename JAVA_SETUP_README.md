# Java環境変数セットアップガイド

## 概要
このプロジェクトのビルドにはJava 21が必要です。JAVA_HOMEとPATHを永続的に設定するスクリプトを提供します。

## 前提条件
- Java 21 (Eclipse Adoptium) が以下のパスにインストールされていること:
  ```
  C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot
  ```

## セットアップ方法

### 方法1: PowerShell (推奨) - 管理者権限不要

1. PowerShellを開く (通常の権限でOK)
2. 以下のコマンドを実行:
   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
   .\setup-java-env.ps1
   ```

### 方法2: バッチファイル - 管理者権限必要

1. `setup-java-env.bat` を**管理者として実行**
   - ファイルを右クリック → 「管理者として実行」

## 設定される環境変数

### JAVA_HOME
```
C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot
```

### PATH
既存のPATHに以下が追加されます:
```
%JAVA_HOME%\bin
```

## 確認方法

**重要**: スクリプト実行後、**必ずターミナル/IDEを再起動**してください。

新しいターミナルで以下を実行:

### PowerShell
```powershell
java -version
echo $env:JAVA_HOME
```

### コマンドプロンプト
```cmd
java -version
echo %JAVA_HOME%
```

### 期待される出力
```
openjdk version "21.0.5" 2024-10-15
OpenJDK Runtime Environment Temurin-21.0.5+11 (build 21.0.5+11)
OpenJDK 64-Bit Server VM Temurin-21.0.5+11 (build 21.0.5+11, mixed mode, sharing)
```

## ビルド実行

環境変数設定後、以下のコマンドでビルド可能になります:

```cmd
gradlew.bat assembleDebug
```

## トラブルシューティング

### "java: command not found" エラーが出る
- ターミナル/IDEを再起動しましたか?
- 環境変数が正しく設定されているか確認:
  ```powershell
  [System.Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
  [System.Environment]::GetEnvironmentVariable("Path", "User")
  ```

### PowerShellスクリプトが実行できない
実行ポリシーを変更:
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### 管理者権限がない
- PowerShell版 (setup-java-env.ps1) を使用してください
- ユーザー環境変数として設定されます (システム全体ではなく現在のユーザーのみ)

## 参考情報

- [Eclipse Adoptium](https://adoptium.net/)
- [Gradle Documentation](https://docs.gradle.org/)
- プロジェクト構成: [CLAUDE.md](./CLAUDE.md)
