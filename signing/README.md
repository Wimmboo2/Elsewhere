# Signing

`elsewhere-test.keystore` is a test key (store and key password `android`, alias `androiddebugkey`).
It is committed on purpose so local builds and the GitHub release builds share one signature, so a newer
APK installs over an older one without uninstalling.

It is public, so anyone could sign an APK with it. That is fine for sideloading your own builds, but not for
a Play Store release. To sign GitHub releases with a private key instead, add these repository secrets
(Settings > Secrets and variables > Actions):

| secret | value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 your.keystore` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

The release workflow uses them when present. Note that switching keys means phones with the test-signed
build must uninstall it once.
