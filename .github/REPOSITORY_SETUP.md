# GitHub Repository Setup Guide

This document provides step-by-step instructions for optimizing the SteamDeck Mobile GitHub repository based on 2025 best practices.

## 📋 Repository Settings Checklist

### 🔹 General Settings

#### Repository Name & Description

**Current**: `steam-app`

**Recommended Description**:
```
Steam-specialized Android game emulator with Winlator integration - Lightweight (<50MB) launcher for running Windows games on Snapdragon devices
```

**Key elements**:
- Clear purpose (Steam game emulator)
- Platform (Android)
- Core technology (Winlator)
- Unique selling point (Lightweight, <50MB)
- Target hardware (Snapdragon)

#### Topics (Tags)

Add the following topics for discoverability:

**Primary Topics**:
- `android`
- `kotlin`
- `jetpack-compose`
- `steam`
- `game-emulator`
- `winlator`

**Technology Topics**:
- `clean-architecture`
- `mvvm`
- `hilt`
- `room-database`
- `material3`
- `kotlin-coroutines`
- `retrofit`

**Platform Topics**:
- `snapdragon`
- `android-app`
- `wine`
- `box64`

**Use Case Topics**:
- `gaming`
- `game-launcher`
- `steam-library`

**How to add** (on GitHub):
1. Go to repository homepage
2. Click ⚙️ (gear icon) next to "About"
3. Add topics in "Topics" field
4. Click "Save changes"

### 🔹 Features to Enable

**Enable these features** (Settings → General):

- ✅ **Issues**: Bug tracking and feature requests
- ✅ **Discussions**: Community Q&A and ideas
- ✅ **Projects**: Development roadmap tracking
- ✅ **Wiki**: Optional - for extended documentation
- ✅ **Sponsorships**: Optional - for future support

**Disable**:
- ❌ **Packages**: Not needed yet
- ❌ **Environments**: Not needed for mobile app

### 🔹 Branch Protection Rules

**For `main` branch** (Settings → Branches → Add rule):

```
Branch name pattern: main

✅ Require a pull request before merging
  ✅ Require approvals: 1 (can be self-approval for solo dev)
  ✅ Dismiss stale pull request approvals when new commits are pushed

✅ Require status checks to pass before merging
  ✅ Require branches to be up to date before merging
  Status checks required:
    - build (from GitHub Actions)

✅ Require conversation resolution before merging
✅ Require linear history
✅ Include administrators (recommended for consistency)
```

**For `develop` branch** (optional, less strict):
```
Branch name pattern: develop

✅ Require pull request before merging
✅ Require status checks: build
```

### 🔹 GitHub Actions Permissions

**Settings → Actions → General**:

```
✅ Allow all actions and reusable workflows

Workflow permissions:
  ⚪ Read repository contents and packages permissions
  ⚫ Read and write permissions

✅ Allow GitHub Actions to create and approve pull requests
```

### 🔹 Security Settings

**Settings → Security**:

**Dependabot**:
```
✅ Enable Dependabot alerts
✅ Enable Dependabot security updates
✅ Enable Dependabot version updates
```

Create `.github/dependabot.yml`:
```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

**Code scanning**:
```
✅ Enable CodeQL analysis (if available)
```

**Secret scanning**:
```
✅ Enable secret scanning alerts
```

### 🔹 Collaborators & Teams

(For future when expanding team)

Currently solo development - skip this section.

## 📄 Essential Files Checklist

### ✅ Already Created:

- [x] `README.md` - Comprehensive project overview
- [x] `LICENSE` - MIT License
- [x] `.gitignore` - Android-specific ignores
- [x] `.github/workflows/android-build.yml` - CI/CD pipeline
- [x] `.github/ISSUE_TEMPLATE/bug_report.md`
- [x] `.github/ISSUE_TEMPLATE/feature_request.md`
- [x] `.github/ISSUE_TEMPLATE/config.yml`
- [x] `.github/PULL_REQUEST_TEMPLATE.md`
- [x] `CONTRIBUTING.md` - Contribution guidelines

### 📝 To Create (Future):

- [ ] `CHANGELOG.md` - Version history (create on first release)
- [ ] `CODE_OF_CONDUCT.md` - Community guidelines (optional)
- [ ] `SECURITY.md` - Security policy
- [ ] `.github/dependabot.yml` - Dependency updates

## 🎯 README Optimization

### Current Status

The existing README.md is comprehensive and includes:
- ✅ Project overview
- ✅ Features list
- ✅ Technology stack
- ✅ System requirements
- ✅ Development setup
- ✅ Build instructions
- ✅ Project structure
- ✅ Development roadmap
- ✅ License

### Recommended Enhancements

Add these sections at the top of README.md:

#### Shields/Badges

```markdown
# SteamDeck Mobile

[![Android CI](https://github.com/atariryuma/steam-app/workflows/Android%20CI/badge.svg)](https://github.com/atariryuma/steam-app/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blue.svg)](https://developer.android.com/ndk/guides/abis)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-brightgreen.svg)](https://developer.android.com/jetpack/compose)
```

#### Quick Links Section

```markdown
## 🔗 Quick Links

- [📥 Download Latest Release](https://github.com/atariryuma/steam-app/releases)
- [🐛 Report a Bug](https://github.com/atariryuma/steam-app/issues/new?template=bug_report.md)
- [💡 Request a Feature](https://github.com/atariryuma/steam-app/issues/new?template=feature_request.md)
- [💬 Discussions](https://github.com/atariryuma/steam-app/discussions)
- [🎮 Winlator Project](https://github.com/brunodev85/winlator)
```

#### Screenshots Section

```markdown
## 📸 Screenshots

<!-- Add screenshots when UI is finalized -->
> 🚧 Coming soon - currently in active development (Phase 2)
```

## 🏷️ GitHub Labels

**Recommended labels to create** (Settings → Issues → Labels):

**Priority**:
- `priority: critical` (red) - Blocks core functionality
- `priority: high` (orange) - Important features/fixes
- `priority: medium` (yellow) - Standard priority
- `priority: low` (light blue) - Nice to have

**Type**:
- `type: bug` (red) - Something isn't working
- `type: feature` (green) - New feature request
- `type: enhancement` (blue) - Improvement to existing feature
- `type: documentation` (light gray) - Documentation updates
- `type: refactor` (purple) - Code refactoring
- `type: performance` (orange) - Performance improvement

**Component**:
- `component: ui` - UI/UX related
- `component: steam` - Steam integration
- `component: winlator` - Winlator/game execution
- `component: database` - Room database
- `component: import` - File import functionality
- `component: download` - Download manager
- `component: controller` - Controller support

**Status**:
- `status: needs-investigation` - Requires investigation
- `status: in-progress` - Currently being worked on
- `status: blocked` - Blocked by external factors
- `status: ready` - Ready for implementation

**Special**:
- `good first issue` - Good for newcomers
- `help wanted` - Community help needed
- `duplicate` - Duplicate issue
- `wontfix` - Will not be fixed

## 📊 GitHub Projects Setup

**Create a project board** (Projects → New project):

**Board name**: "SteamDeck Mobile Development"

**Columns**:
1. 📋 **Backlog** - Future tasks
2. 📝 **Todo** - Ready to start
3. 🔄 **In Progress** - Currently working
4. 👀 **In Review** - PR submitted
5. ✅ **Done** - Completed

**Automation** (for each column):
- Todo → In Progress: When issue assigned
- In Progress → In Review: When PR linked
- In Review → Done: When PR merged

## 🔔 Notifications Setup

**For repository owner** (Watch → Custom):
- ✅ Issues
- ✅ Pull Requests
- ✅ Releases
- ✅ Discussions
- ✅ Security alerts

## 📈 Repository Insights

**Enable these analytics** (Insights):
- ✅ **Traffic**: Monitor visitors
- ✅ **Commits**: Track development activity
- ✅ **Code frequency**: Visualize contributions
- ✅ **Dependency graph**: Track dependencies
- ✅ **Network**: Visualize forks and branches

## 🚀 Next Steps

### Immediate Actions (Today):

1. ✅ Add repository description and topics
2. ✅ Enable Issues and Discussions
3. ✅ Verify CI/CD workflow success
4. ✅ Add branch protection rules
5. ✅ Create labels
6. ✅ Set up project board

### Short-term (This Week):

1. Add README badges
2. Create SECURITY.md
3. Create dependabot.yml
4. Add screenshots when UI finalized

### Long-term (Next Sprint):

1. Set up GitHub Releases for versioned builds
2. Create wiki documentation (optional)
3. Enable Discussions for community
4. Set up automated release notes

## 📚 Reference Links

- [GitHub Best Practices](https://docs.github.com/en/repositories/creating-and-managing-repositories/best-practices-for-repositories)
- [README Best Practices](https://github.com/jehna/readme-best-practices)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [Keep a Changelog](https://keepachangelog.com/)

---

**Last Updated**: 2025-01-16
**Status**: ✅ Ready for implementation
