# Multi App Cloner Pro

Android app (Java, no AndroidX) that clones installed apps by **APK repackaging**: each clone is a real, separate
installed app with its own package name, data and launcher icon.

## How a clone is made
1. Copy the installed app's base APK.
2. Rewrite the binary `AndroidManifest.xml` (`engine/ManifestPatcher`): new package name, class names fully qualified
   against the original package, custom permissions and provider authorities renamed, clone name as launcher label.
3. Re-zip without the old signature, with zipalign-style alignment (`engine/ApkRewriter`).
4. Sign with APK Signature Scheme v2 (`engine/ApkV2Signer`) using an RSA key generated once inside the Android Keystore.
5. Install through `PackageInstaller`; you confirm on the system dialog. Result is recorded by `InstallResultReceiver`.

Clone package = `<original>.clone<id>`.

## Also included
App inspector and compatibility registry, clone profiles (SQLite), proxy profiles (Keystore-encrypted password) and
test, public-IP checker, VPN provider references, backup/restore of profile metadata, diagnostics.

## Limits (be aware)
- Refused: system apps, split-APK/bundle apps, sharedUserId, device-admin, accessibility and VPN-service apps.
- Apps that verify their signature/package, or use Google Sign-In, Firebase, Play Integrity or licence checks, may
  install but fail at run time. Apps with hard-coded package names in native code can break.
- The clone icon is the original icon (the launcher label carries the clone name).
- Clones are signed with a per-device key: after uninstalling this app, uninstall its clones before recreating them.
- No virtual-app engine and no VPN tunnel. Network profile assignment is metadata only.
- Not for Google Play (uses `REQUEST_INSTALL_PACKAGES` and `QUERY_ALL_PACKAGES`); distribute the APK directly.
- Only clone apps you are entitled to; respect each app's terms.

## Build
- **GitHub Actions** (no local setup): push to GitHub; the *Build APK* workflow runs engine tests, builds the debug APK
  and uploads it as an artifact (tag `v*` to attach it to a release).
- **Android Studio**: open the folder, sync, run (minSdk 24, compileSdk/targetSdk 34, AGP 8.7.3, Gradle 8.9).
- **Engine tests only** (JDK needed): `./engine-test/run.sh`

## Verification status
Engine (manifest patching in UTF-8/UTF-16 pools, alignment, v2 signature checked by an independent verifier, tamper
detection) passes on a plain JVM. The Android UI/installer code has **not** been compiled or run on a device by the
author of this bundle; the first CI run will show any compile error. Please test on a simple app first.
