# Security Policy

## 🔒 Supported Versions

Currently, SteamDeck Mobile is in active development (Phase 2). Security updates will be provided for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1   | :x:                |

## 🛡️ Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please follow these steps:

### 1. **Do NOT** Open a Public Issue

Security vulnerabilities should not be disclosed publicly until a fix is available.

### 2. Report Privately

**Preferred method**: Use GitHub Security Advisories
- Go to: https://github.com/atariryuma/steam-app/security/advisories
- Click "Report a vulnerability"
- Provide detailed information (see below)

**Alternative method**: Email (if Security Advisories not available)
- Contact repository owner directly
- Subject: `[SECURITY] SteamDeck Mobile Vulnerability Report`

### 3. Information to Include

Please provide as much detail as possible:

```
**Vulnerability Type**: [e.g., SQL Injection, XSS, Authentication Bypass]

**Affected Component**: [e.g., Steam API integration, File import, Database]

**Severity**: [Critical / High / Medium / Low]

**Description**:
[Clear description of the vulnerability]

**Steps to Reproduce**:
1.
2.
3.

**Impact**:
[What could an attacker accomplish?]

**Proof of Concept**:
[Code snippet, screenshots, or video if applicable]

**Suggested Fix** (optional):
[Your recommendations for fixing the issue]

**Environment**:
- App version:
- Android version:
- Device:
```

### 4. Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Depends on severity
  - Critical: 24-72 hours
  - High: 1-2 weeks
  - Medium: 2-4 weeks
  - Low: Next release cycle

### 5. Disclosure Policy

- We will work with you to understand and reproduce the issue
- A fix will be developed and tested
- A security advisory will be published after the fix is released
- You will be credited (if desired) in the advisory

## 🔐 Security Best Practices

### For Users

1. **Download from Official Sources**:
   - Only install APKs from official GitHub Releases
   - Verify SHA256 checksums

2. **Steam Credentials**:
   - Use Steam Web API Key (not password)
   - Store credentials securely on device
   - Credentials are encrypted with Android EncryptedSharedPreferences

3. **Permissions**:
   - Review requested permissions before installation
   - Grant only necessary permissions

4. **Updates**:
   - Keep the app updated to latest version
   - Enable Dependabot notifications (for developers)

### For Developers

1. **Secure Coding**:
   - Follow OWASP Mobile Top 10
   - Never hardcode API keys or secrets
   - Use Android Keystore for sensitive data
   - Validate all user inputs

2. **Dependencies**:
   - Regularly update dependencies
   - Review Dependabot alerts
   - Audit third-party libraries

3. **Code Review**:
   - All PRs require review
   - Use static analysis tools (Android Lint)
   - Enable ProGuard/R8 in release builds

4. **Testing**:
   - Write security-focused tests
   - Test permission handling
   - Validate network security

## 🚨 Known Security Considerations

### Steam Web API Key

- **Storage**: API keys are stored in EncryptedSharedPreferences (AES-256)
- **Transmission**: All API calls use HTTPS
- **Recommendation**: Users should use API keys with minimal permissions

### Winlator Integration

- **Wine Environment**: Games run in isolated Wine containers
- **File Access**: Limited to app's internal storage and explicitly granted paths
- **Network**: Games use app's network permissions

### External File Import

- **USB OTG**: Read-only access to USB devices
- **SMB/FTP**: Credentials stored securely
- **Local Storage**: Uses Android SAF (Scoped Storage)

### Network Security

- **Certificate Pinning**: Not currently implemented (future consideration)
- **HTTPS**: Required for all network calls
- **OkHttp**: Configured with security best practices

## 📋 Security Checklist (For Releases)

Before each release, verify:

- [ ] All dependencies updated to latest secure versions
- [ ] ProGuard/R8 enabled and configured correctly
- [ ] No hardcoded secrets in code
- [ ] Network security config properly configured
- [ ] Permissions minimized and documented
- [ ] APK signed with release key
- [ ] Static analysis (Android Lint) passes with no critical issues
- [ ] Security-focused tests pass

## 🔗 Resources

- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [Kotlin Security Guidelines](https://kotlinlang.org/docs/security.html)

## 📜 Security Advisories

Past security advisories will be listed here:

> 🟢 No security advisories published yet

## 🙏 Acknowledgments

We appreciate security researchers who responsibly disclose vulnerabilities. Contributors will be acknowledged (with permission) in:

- Security advisories
- Release notes
- CONTRIBUTORS.md (future)

---

**Last Updated**: 2025-01-16
**Contact**: GitHub Security Advisories (preferred)
