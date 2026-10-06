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

Push a version tag. GitHub Desktop: *Repository → Create tag*, then push. With git: `git tag v0.3.0 && git push origin v0.3.0`.

The build workflow then:

1. runs every check;
2. builds the signed reviewer APK;
3. creates a **pre-release** with the APK, its SHA-256 checksum and the browser preview attached.

**Recommendation:** do not tag `v1.0` until the app has passed the smoke test on at least one real Android phone. Until then, use `v0.x` tags; they are marked as pre-releases.
