# Release signing (for the project owner only)

Android requires release APKs to be signed. The signing key proves that later updates come from the same person. **Keep it private.**

- Never commit it.
- Never send it to anyone, including an AI assistant.
- Back it up somewhere safe: if it is lost, existing installs cannot be updated.

## 1. Create the key (once)

`keytool` comes with any Java JDK (17+). Run:

```bash
keytool -genkeypair -v -keystore cognisense-release.jks -alias cognisense \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong password and remember the alias (`cognisense` above).

## 2. Give it to GitHub Actions as secrets

In the repository: *Settings → Secrets and variables → Actions → New repository secret*. Add four:

| Secret | Value |
|---|---|
| `COGNISENSE_KEYSTORE_BASE64` | The keystore as text. macOS/Linux: `base64 -i cognisense-release.jks`. Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cognisense-release.jks"))` |
| `COGNISENSE_KEYSTORE_PASSWORD` | The keystore password |
| `COGNISENSE_KEY_ALIAS` | `cognisense` |
| `COGNISENSE_KEY_PASSWORD` | The key password (often the same) |

Secrets are encrypted by GitHub and never appear in logs.

## 3. Make a release

Push a version tag. GitHub Desktop: *Repository → Create tag*, then push. With git: `git tag v0.6.1 && git push origin v0.6.1` (use the current version number).

The build workflow then:

1. runs every check;
2. builds the signed reviewer APK;
3. creates a **pre-release** with the APK, its SHA-256 checksum and the browser preview attached.

**If the four secrets are not set**, the job "Signed reviewer APK on a version tag" does not fail. It shows a notice, "Signed reviewer APK skipped", naming the missing secrets, and finishes green without building an APK or creating a release. The checks and the unsigned debug builds still run as usual.

**Recommendation:** do not tag `v1.0` until the app has passed the smoke test on at least one real Android phone. That condition is now met for one phone, a Samsung Galaxy A15 with Android 16 (`docs/device_tests.md`). Until `v1.0` is decided, use `v0.x` tags; they are marked as pre-releases.
