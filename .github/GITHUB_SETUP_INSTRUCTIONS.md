# GitHub Repository Setup Instructions

このドキュメントでは、GitHubのWeb UIで手動で設定する必要がある項目を説明します。

## 📋 完了チェックリスト

### ✅ 自動で完了した項目（コミット済み）

- [x] Issue Templates（Bug Report, Feature Request）
- [x] Pull Request Template
- [x] CONTRIBUTING.md
- [x] SECURITY.md
- [x] Dependabot設定
- [x] GitHub Actions CI/CD
- [x] README.md（バッジ、Quick Links）

### 🔧 手動設定が必要な項目（GitHub Web UIで設定）

以下の設定をGitHub.com上で手動で行う必要があります。

---

## 1. リポジトリ設定（About）

### 📍 場所
リポジトリトップページ → 右上の⚙️（歯車アイコン）

### 設定内容

**Description（説明）**:
```
Steam-specialized Android game emulator with Winlator integration - Lightweight (<50MB) launcher for running Windows games on Snapdragon devices
```

**Website（任意）**:
```
https://github.com/atariryuma/steam-app
```

**Topics（タグ）** - 以下をすべて追加:

**Primary**:
- `android`
- `kotlin`
- `jetpack-compose`
- `steam`
- `game-emulator`
- `winlator`

**Technology**:
- `clean-architecture`
- `mvvm`
- `hilt`
- `room-database`
- `material3`
- `kotlin-coroutines`
- `retrofit`

**Platform**:
- `snapdragon`
- `android-app`
- `wine`
- `box64`

**Use Case**:
- `gaming`
- `game-launcher`
- `steam-library`

**チェックボックス**:
- ☑️ **Releases**: バージョンリリース用
- ☑️ **Packages**: 将来のライブラリ公開用（現在は不要）
- ☑️ **Deployments**: 将来のデプロイ用（現在は不要）

**保存**: "Save changes"をクリック

---

## 2. Features（機能）の有効化

### 📍 場所
Settings → General → Features

### 有効化する機能

#### ✅ Issues
- チェックを入れて有効化
- バグ報告・機能リクエスト用

#### ✅ Discussions（推奨）
- チェックを入れて有効化
- Q&A、アイデア共有用
- カテゴリ:
  - 💡 Ideas（アイデア）
  - 🙏 Q&A（質問）
  - 📢 Announcements（お知らせ）
  - 🎉 Show and tell（成果共有）

#### ⚠️ Wiki（任意）
- 拡張ドキュメント用（現在は不要）
- 必要に応じて後で有効化

#### ❌ Projects（任意）
- プロジェクトボード用
- 必要に応じて後で有効化

#### ❌ Sponsorships（将来的に）
- 現在は不要

---

## 3. Branch Protection Rules（ブランチ保護）

### 📍 場所
Settings → Branches → Add branch protection rule

### Main ブランチの保護設定

**Branch name pattern**:
```
main
```

**設定内容**:

#### Protect matching branches

☑️ **Require a pull request before merging**
  - Required approvals: `1`
  - ☑️ Dismiss stale pull request approvals when new commits are pushed
  - ☑️ Require review from Code Owners（CODEOWNERS作成後）

☑️ **Require status checks to pass before merging**
  - ☑️ Require branches to be up to date before merging
  - Status checks required: `build`（GitHub Actionsのジョブ名）

☑️ **Require conversation resolution before merging**
  - PRのコメントをすべて解決してからマージ

☑️ **Require linear history**
  - マージコミットを防ぎ、クリーンな履歴を維持

☑️ **Include administrators**
  - 管理者にもルールを適用（推奨）

☑️ **Allow force pushes** → **Specify who can force push**
  - 誰も選択しない（force pushを禁止）

☑️ **Allow deletions**
  - チェックを外す（ブランチ削除を禁止）

**保存**: "Create"をクリック

### Develop ブランチの保護設定（任意）

**Branch name pattern**:
```
develop
```

**設定内容**（mainより緩い設定）:

☑️ **Require a pull request before merging**
  - Required approvals: `0`（self-approvalでOK）

☑️ **Require status checks to pass before merging**
  - Status checks: `build`

**保存**: "Create"をクリック

---

## 4. GitHub Actions設定

### 📍 場所
Settings → Actions → General

### Workflow permissions

⚫ **Read and write permissions**
  - GitHub Actionsがリポジトリに書き込めるように

☑️ **Allow GitHub Actions to create and approve pull requests**
  - Dependabotの自動PR用

### Actions permissions

⚫ **Allow all actions and reusable workflows**

**保存**: "Save"をクリック

---

## 5. Security設定

### 📍 場所
Settings → Security & analysis

### Dependabot

#### Dependabot alerts
☑️ **Enable**
  - 脆弱性のある依存関係を自動検出

#### Dependabot security updates
☑️ **Enable**
  - セキュリティアップデートを自動PR

#### Grouped security updates
☑️ **Enable**（推奨）
  - 複数の依存関係を1つのPRにまとめる

### Code scanning（利用可能な場合）

#### CodeQL analysis
☑️ **Set up**
  - コード品質・セキュリティ分析
  - デフォルト設定でOK

### Secret scanning

#### Secret scanning
☑️ **Enable**
  - 誤ってコミットされた秘密情報を検出

#### Push protection
☑️ **Enable**（推奨）
  - プッシュ時に秘密情報を検出してブロック

**保存**: 各設定で自動保存されます

---

## 6. Labels（ラベル）の作成

### 📍 場所
Issues → Labels → New label

### 作成するラベル

以下のラベルを作成してください：

#### Priority（優先度）
| Name | Description | Color |
|------|-------------|-------|
| `priority: critical` | Blocks core functionality | `#d73a4a` (red) |
| `priority: high` | Important features/fixes | `#ff9800` (orange) |
| `priority: medium` | Standard priority | `#ffd700` (yellow) |
| `priority: low` | Nice to have | `#add8e6` (light blue) |

#### Type（種類）
| Name | Description | Color |
|------|-------------|-------|
| `type: bug` | Something isn't working | `#d73a4a` (red) |
| `type: feature` | New feature request | `#28a745` (green) |
| `type: enhancement` | Improvement to existing feature | `#0052cc` (blue) |
| `type: documentation` | Documentation updates | `#d3d3d3` (light gray) |
| `type: refactor` | Code refactoring | `#9c27b0` (purple) |
| `type: performance` | Performance improvement | `#ff9800` (orange) |

#### Component（コンポーネント）
| Name | Description | Color |
|------|-------------|-------|
| `component: ui` | UI/UX related | `#0075ca` (blue) |
| `component: steam` | Steam integration | `#1e3a8a` (dark blue) |
| `component: winlator` | Winlator/game execution | `#6b21a8` (purple) |
| `component: database` | Room database | `#f59e0b` (amber) |
| `component: import` | File import functionality | `#10b981` (emerald) |
| `component: download` | Download manager | `#06b6d4` (cyan) |
| `component: controller` | Controller support | `#8b5cf6` (violet) |

#### Status（状態）
| Name | Description | Color |
|------|-------------|-------|
| `status: needs-investigation` | Requires investigation | `#fef3c7` (yellow-light) |
| `status: in-progress` | Currently being worked on | `#3b82f6` (blue) |
| `status: blocked` | Blocked by external factors | `#ef4444` (red) |
| `status: ready` | Ready for implementation | `#22c55e` (green) |

#### Special（特殊）
| Name | Description | Color |
|------|-------------|-------|
| `good first issue` | Good for newcomers | `#7057ff` (purple) |
| `help wanted` | Community help needed | `#008672` (teal) |
| `duplicate` | Duplicate issue | `#cfd3d7` (gray) |
| `wontfix` | Will not be fixed | `#ffffff` (white) |

**作成方法**:
1. "New label"をクリック
2. Label name、Description、Colorを入力
3. "Create label"をクリック
4. 上記すべてのラベルを作成

---

## 7. Notifications（通知設定）

### 📍 場所
リポジトリトップページ → 右上 "Watch" ドロップダウン

### 推奨設定

⚫ **Custom**を選択

☑️ **Issues**: Issue作成・コメント
☑️ **Pull requests**: PR作成・レビュー
☑️ **Releases**: リリース公開
☑️ **Discussions**: ディスカッション（有効化した場合）
☑️ **Security alerts**: セキュリティアラート

---

## 8. Projects（プロジェクトボード）- 任意

### 📍 場所
Projects → New project

### プロジェクト作成（任意）

**Project name**:
```
SteamDeck Mobile Development
```

**Template**: Board

**Columns**:
1. 📋 Backlog
2. 📝 Todo
3. 🔄 In Progress
4. 👀 In Review
5. ✅ Done

**Automation**（各カラムで設定）:
- Todo → In Progress: When issue assigned
- In Progress → In Review: When PR opened
- In Review → Done: When PR merged

---

## 9. Repository Visibility（リポジトリの公開設定）

### 📍 場所
Settings → General → Danger Zone

### 現在の設定確認

- **Public**: 誰でもアクセス可能（推奨）
- **Private**: 招待されたユーザーのみ

オープンソースプロジェクトとして公開する場合は **Public** のまま。

---

## 10. Social Preview（ソーシャルプレビュー画像）- 任意

### 📍 場所
リポジトリトップページ → Settings → General → Social preview

### カスタム画像アップロード（将来的に）

- サイズ: 1280x640px推奨
- フォーマット: PNG or JPG
- 内容: アプリロゴ、スクリーンショット、キャッチコピー

現在は設定不要（デフォルトのGitHub OGP画像が使用されます）

---

## ✅ 完了確認チェックリスト

すべて設定したら、以下を確認してください：

### Repository（About）
- [ ] Description設定完了
- [ ] Topics（20個）すべて追加完了
- [ ] Releases有効化

### Features
- [ ] Issues有効化
- [ ] Discussions有効化（推奨）

### Branches
- [ ] main ブランチ保護ルール設定完了
- [ ] Status check "build" 設定完了

### Actions
- [ ] Read/write permissions設定完了
- [ ] PR作成許可設定完了

### Security
- [ ] Dependabot alerts有効化
- [ ] Dependabot security updates有効化
- [ ] Secret scanning有効化

### Labels
- [ ] すべてのカスタムラベル作成完了

### Notifications
- [ ] Watch設定をCustomに変更
- [ ] 必要な通知を有効化

---

## 🎉 設定完了後

すべての設定が完了したら：

1. **リポジトリトップページを確認**
   - About欄にDescription、Topics、リンクが表示されているか
   - Issuesタブが表示されているか
   - バッジが正しく表示されているか

2. **テスト**
   - 新しいIssueを作成してテンプレートを確認
   - GitHub Actionsが正常に動作しているか確認

3. **ドキュメント確認**
   - README.mdが正しくレンダリングされているか
   - CONTRIBUTING.mdが読みやすいか
   - SECURITY.mdが表示されているか

---

**Last Updated**: 2025-01-16
**Status**: 手動設定待ち
**Repository**: https://github.com/atariryuma/steam-app

---

## 🔗 参考リンク

- [GitHub Best Practices](https://docs.github.com/en/repositories/creating-and-managing-repositories/best-practices-for-repositories)
- [Managing Branch Protection Rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule)
- [About Dependabot](https://docs.github.com/en/code-security/dependabot)
- [GitHub Labels](https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/managing-labels)
