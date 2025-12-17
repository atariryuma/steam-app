====================================================
  SteamDeck Mobile - ログ確認ツール
====================================================

📱 adb の場所:
  C:\Android\sdk\platform-tools\adb.exe

🚀 使い方（簡単！）:

  1. USBでAndroidデバイスを接続
  2. 以下のファイルをダブルクリック:

     view-logs.bat      → リアルタイムでログ表示
     save-logs.bat      → ログをファイルに保存
     debug-info.bat     → デバッグ情報を一括取得

  3. アプリでSteam認証を試す
  4. ログを確認

📖 詳細な説明:
  LOG_VIEWER_GUIDE.md を参照

🔧 初回セットアップ（デバイス側）:
  1. 設定 → デバイス情報 → ビルド番号を7回タップ
  2. 設定 → 開発者向けオプション → USBデバッグをON
  3. PCにUSB接続して「許可」

✅ 接続確認:
  C:\Android\sdk\platform-tools\adb.exe devices

====================================================
