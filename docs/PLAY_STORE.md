# Publishing notes (read before submitting to Google Play)

This project can be built as a signed release (APK + AAB) with the "Release build (signed)" workflow.
Add repository secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

Known blockers for Google Play (honest list):
1. **targetSdk 28.** The embedded virtual engine needs it. Google Play only accepts new apps and updates that target a
   recent Android API level. Raising it needs engine work and may break cloned apps.
2. **Restricted permissions.** QUERY_ALL_PACKAGES and REQUEST_INSTALL_PACKAGES need a Play Console declaration and are
   usually refused for this kind of app.
3. **Policy.** Apps that load and run other apps, or modify and reinstall other apps, are reviewed under the Device and
   Network Abuse and Deceptive Behavior policies and are frequently rejected or removed.
4. A privacy policy URL (see PRIVACY.md), data-safety form, and store listing are still required.

Realistic distribution today: GitHub Releases, your own website, or third-party stores that allow such apps.
