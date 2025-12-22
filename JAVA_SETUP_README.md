# Java Environment Variables Setup Guide

## Overview
This project requires Java 21 for building. This guide provides scripts to permanently configure JAVA_HOME and PATH.

## Prerequisites
- Java 21 (Eclipse Adoptium) must be installed at the following path:
  ```
  C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot
  ```

## Setup Methods

### Method 1: PowerShell (Recommended) - No Admin Rights Required

1. Open PowerShell (normal user privileges)
2. Execute the following commands:
   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
   .\setup-java-env.ps1
   ```

### Method 2: Batch File - Admin Rights Required

1. Right-click `setup-java-env.bat` and select **Run as administrator**

## Environment Variables Set

### JAVA_HOME
```
C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot
```

### PATH
The following is added to existing PATH:
```
%JAVA_HOME%\bin
```

## Verification

**Important**: After running the script, **restart your terminal/IDE**.

Execute the following in a new terminal:

### PowerShell
```powershell
java -version
echo $env:JAVA_HOME
```

### Command Prompt
```cmd
java -version
echo %JAVA_HOME%
```

### Expected Output
```
openjdk version "21.0.5" 2024-10-15
OpenJDK Runtime Environment Temurin-21.0.5+11 (build 21.0.5+11)
OpenJDK 64-Bit Server VM Temurin-21.0.5+11 (build 21.0.5+11, mixed mode, sharing)
```

## Building

After setting environment variables, you can build with:

```cmd
gradlew.bat assembleDebug
```

## Troubleshooting

### "java: command not found" error
- Did you restart your terminal/IDE?
- Verify environment variables are set correctly:
  ```powershell
  [System.Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
  [System.Environment]::GetEnvironmentVariable("Path", "User")
  ```

### Cannot execute PowerShell script
Change execution policy:
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### No admin rights
- Use the PowerShell version (setup-java-env.ps1)
- Sets user environment variables (current user only, not system-wide)

## References

- [Eclipse Adoptium](https://adoptium.net/)
- [Gradle Documentation](https://docs.gradle.org/)
- Project configuration: [CLAUDE.md](./CLAUDE.md)
