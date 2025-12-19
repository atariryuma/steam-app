import pathlib

# Comprehensive UI translations
translations = {
    # Status and state strings
    "未同期": "Never synced",
    "準備完了": "Ready",
    "実行中": "Running",
    "利用可能": "Available",
    "エラー": "Error",
    "成功": "Success",
    "失敗": "Failed",
    "警告": "Warning",
    "完了": "Complete",
    "読み込み中": "Loading",
    
    # Time strings
    "今日": "Today",
    "昨日": "Yesterday",
    "日前": " days ago",
    "時間前": " hours ago",
    "分前": " minutes ago",
    "週間前": " weeks ago",
    "ヶ月前": " months ago",
    "1minutes以内": "Less than 1 minute ago",
    
    # UI elements
    "コンテナ管理": "Container Management",
    "ダウンロード管理": "Download Manager",
    "ゲーム追加": "Add Game",
    "ゲーム詳細": "Game Details",
    "設定": "Settings",
    "ログイン": "Login",
    "ログアウト": "Logout",
    "同期": "Sync",
    "認証": "Auth",
    "保存": "Save",
    "削除": "Delete",
    "キャンセル": "Cancel",
    "閉じる": "Close",
    "確認": "Confirm",
    "戻る": "Back",
    "追加": "Add",
    "編集": "Edit",
    "更新": "Update",
    "再試行": "Retry",
    "検索": "Search",
    "お気に入り": "Favorites",
    "最近プレイしたゲーム": "Recently Played",
    "インポートしたゲーム": "Imported Games",
    "インポート": "Import",
    "インストール": "Install",
    "アンインストール": "Uninstall",
    "起動": "Launch",
    "振動": "Vibration",
    "有効": "Enabled",
    "無効": "Disabled",
    "左スティック": "Left Stick",
    "右スティック": "Right Stick",
    "トリガー": "Trigger",
    "テスト": "Test",
    "最近プレイした": "Recently Played",
    "お気に入りのゲーム": "Favorite Games",
    "インポート済み": "Imported",
    "免責事項": "Disclaimer",
    "注意事項": "Notes",
    "同意する": "I Agree",
    "同意しない": "Disagree",
    "完了済みクリア": "Clear Completed",
    "進行中": "In Progress",
    "ダウンロード履歴なし": "No download history",
    "一時停止": "Pause",
    "再開": "Resume",
    "コンテナ": "Container",
    "コンテナがありません": "No containers",
    "新しいコンテナを作成": "Create New Container",
    "コンテナ名": "Container Name",
    "コンテナ名を入力してください": "Enter container name",
    "コンテナを削除": "Delete Container",
    "この操作は取り消せません": "This action cannot be undone",
    "作成": "Create",
    "サイズ": "Size",
    "ゲーム情報": "Game Info",
    "プレイ時間": "Play Time",
    "最終プレイ日時": "Last Played",
    "ソース": "Source",
    "ファイルパス": "File Paths",
    "実行ファイル": "Executable",
    "インストールパス": "Install Path",
    "Steam情報": "Steam Info",
    "ゲームを起動中": "Launching game",
    "ゲームを起動できません": "Cannot launch game",
    "直接起動": "Direct Launch",
    "Steam経由で起動": "Launch via Steam",
    "Steamクライアントを開く": "Open Steam Client",
    "設定を開く": "Open Settings",
    "未インストール": "Not Installed",
    "未設定": "Not Set",
    "コントローラー設定": "Controller Settings",
    "コントローラーが検出されません": "No controller detected",
    "デフォルトに戻す": "Reset to default",
    "デッドゾーン": "Deadzone",
    "テスト成功": "Test Successful",
    "不明なエラー": "Unknown error",
    "検索エラー": "Search error",
    "ゲームが既に存在します": "Game already exists",
    "同期を開始しています": "Starting sync",
    "同期中": "Syncing",
    "API Keyを入力してください": "Please enter API Key",
    "無効なAPI Keyです": "Invalid API Key",
    "API Keyを保存しました": "API Key saved",
    "Steam設定をクリアしました": "Steam Settings cleared",
    "クリアに失敗しました": "Clear failed",
    "Steam Clientがインストールされていません": "Steam Client is not installed",
    "インストール状態の確認に失敗しました": "Failed to check installation status",
    "予期しないエラーが発生しました": "Unexpected error occurred",
    "Steam Clientを起動しました": "Steam Client launched",
    "Steam Clientがアンインストールされました": "Steam Client has been uninstalled",
    "アンインストールに失敗しました": "Uninstall failed",
    "Winlator初期化": "Winlator initialization",
    "Winlatorを初期化中": "Initializing Winlator",
    "初期化に失敗しました": "Initialization failed",
    "初期化中にエラーが発生しました": "Error occurred during initialization",
    "コントローラー": "Controller",
}

base = pathlib.Path("app/src/main/java/com/steamdeck/mobile/presentation")
updated = []

for f in base.rglob("*.kt"):
    try:
        content = f.read_text(encoding="utf-8")
        original = content
        for jp, en in translations.items():
            content = content.replace(jp, en)
        if content != original:
            f.write_text(content, encoding="utf-8")
            updated.append(f.name)
    except Exception as e:
        print(f"Error {f.name}: {e}")

print(f"Updated {len(updated)} files")
for f in updated:
    print(f"  - {f}")
