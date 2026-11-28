# Multi App Cloner Pro — Project Report (Roman Urdu)

Developer: Sanaullah Khoso  |  Repo: github.com/sanaullah786-hub/MultiAppClonerPro  |  Date: 2026-11-28

## 1. Shuru mein kya tha
Original zip mein sirf ek "profile record" app thi: clone ka naam save hota tha, lekin asal mein koi app clone nahi hoti thi.

## 2. Ab tak jo kaam hua

### A. Real cloning engine (2 tareeqe)
1. **APK repackaging clone** (`engine/`): app ka manifest badal kar naya package banata hai, zip karta hai, sign karta hai (APK v2, AndroidKeyStore key) aur PackageInstaller se phone mein install karta hai. JVM par test hua (`engine-test/`), CI mein chalta hai.
2. **Virtual clone** (Parallel Space jaisa, NewBlackbox engine, Apache-2.0): app cloner ke andar chalti hai, har clone ka apna alag data area (virtual user id).

### B. Install system
- APK / XAPK / APKS / APKM file ya download link (https, max 3GB) se install.
- OBB game data copy, native libs copy.
- **Split APK merge** (naya, ARSCLib): bundle aur device ke split apps ko ek APK banata hai. Fail ho to purana tareeqa.

### C. Storage
- Guest app data cloner ke andar rehta hai.
- Settings → Storage se SD card select ho sakta hai (data copy ke saath).

### D. Google services
- Phone ke apne GMS / GSF / Play Store ko virtual user mein copy karta hai, login ke liye.
- Jin apps ko Google chahiye unme khud add hota hai.

### E. UI aur fixes
- App-styled popup sheets (grey menu hata diya).
- Clone naam numbering fix: "App - Clone 1, 2, 3" (limit 30).
- WhatsApp Business crash fix (AppOps ClassCastException, Android 11+).
- All-files-access ka prompt, low-memory guard, "Stop all running clones", "Copy engine log".
- Telemetry (engine ka Telegram log upload) band kiya.

### F. Build / CI / docs
- GitHub Actions: engine tests + debug APK, release workflow (signed).
- README, PRIVACY, PLAY_STORE notes, licenses (MIT + Apache-2.0).
- 58 Java files, ~6100 lines (host app), 11 commits. Aakhri 3 builds green.

## 3. Aaj ki haqeeqat (kya kaam nahi karta)
Main phone par test nahi kar sakta; sirf compile + JVM test hota hai. Isliye ye cheezen abhi unverified ya masle wali hain:

| Cheez | Haal |
|---|---|
| WhatsApp, WhatsApp Business | Chal gaye (user ne bataya) |
| TikTok | Hang/crash. Aksar virtual space pehchan leta hai. Evasion main nahi banaunga. Log chahiye |
| Chrome | Trichrome library dependency; merge se shayad hal na ho |
| ChatGPT | Split merge ke baad test nahi hua |
| Play Store clone | Invalid signature / internal error, hal nahi hua |
| Heavy games / anti-cheat | Aksar detect karte hain, nahi chalenge |
| Storage as RAM | Mumkin nahi |
| OPPO | Background engine maar deta hai: Battery Unrestricted + Auto-start |

## 4. Ab kya karna hoga
1. Latest APK install karein (Actions → latest run → artifact `MultiAppClonerPro-debug`). Purane clones delete karein.
2. Har masle wali app ke liye: clone kholein, fail ho, **Diagnostics → Copy engine log**, log bhejein. Log ke baghair andaza hai.
3. Phone settings: Battery Unrestricted, Auto-start ON, All files access ON.
4. Ek waqt mein 1-2 clones chalayein.
5. Merge fail ho raha ho to log mein "SplitMerger" dhundhein (ARSCLib API/version check karna pad sakta hai).

## 5. Baad ke kaam (aap ne kaha later)
- Play Store / APKPure publish: targetSdk 28 (engine ki wajah se) aur restricted permissions Play Store par rukawat hain; APKPure aasan hai.
- Privacy policy mein support email lagana (abhi placeholder).
- "Apna main Google account use na karein" warning dialog.
- Release signing keystore bana kar GitHub secrets mein dalna.
- Play Store clone aur Chrome ke liye alag research.
