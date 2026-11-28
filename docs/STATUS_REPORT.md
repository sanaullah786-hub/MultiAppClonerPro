# Status report — 2026-11-28 (target: OPPO A3x, Android 14, 4 GB RAM)

Rule used: an item is "verified" only if a JVM test or the CI build proved it. Nothing was run on a phone.

## Changed in this round
- Diagnostics system (new): crash recorder in every process, persistent redacted event log, automatic diagnosis
  (missing library, shared-library dependency, out of memory, signature, install failure, engine API mismatch,
  permission, storage, died process, crash), "Copy diagnostics report", "Clear diagnostics history",
  crash prompt on next app start.
- Install errors: PackageInstaller status codes now reported with their number (incl. timeout); virtual install
  failures, split-merge failures and launch refusals are recorded with package and user id.
- Pre-install checks: CPU ABI mismatch, split APKs that could not be merged, static shared-library dependency (Trichrome).
- Clone data isolation: virtual user ids are never reused (a deleted clone's id cannot return), stale data folders
  are wiped before a new clone uses an id, and a removed clone's folders are deleted. Only the clone's own package
  and user id are touched.
- Removed an unused anti-detection stub (AntiVirtualDetectProxy) from the engine.
- Tests: new DiagTest (redaction, analyzer, log rotation, ABI scan, static-library scan, user ids).

## Verified (by JVM test or CI)
- Cloning engine tests, diagnostics tests: pass. Android build: pass. Debug APK artifact is produced (7 MB).

## Not verified (needs a phone)
WhatsApp, WhatsApp Business, Facebook, TikTok, Chrome, ChatGPT, games, Google login, Play Store clone,
split-APK merge result, low-memory sheet, stop-all, SD-card storage, crash prompt.

## Not done in this round
- Memory-leak hunt, activity/service-launch rewrites and deeper Android 14 engine changes: not started, because
  without device logs there is no reproducible fault to fix. The new report will show them.
- Process cap for concurrent clones.
