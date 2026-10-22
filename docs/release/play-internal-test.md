# Putting Super Doc on the Play Store internal test track

Internal testing lets up to 100 people (family members, by Gmail address) install the app from the
Play Store, with automatic updates, before anything is public. There is no review wait and no
minimum tester count for this track.

## 1. One-time: the upload key

Play App Signing is used: Google keeps the key that signs what phones install. We keep only an
**upload key**, which proves an upload came from us. If it is lost, Play support can reset it.

Create it **outside the project folder** and store the password in a password manager:

```bash
mkdir -p ~/keys && cd ~/keys
keytool -genkeypair -storetype PKCS12 -keystore superdoc-upload.p12 -alias upload \
  -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 10000 \
  -dname "CN=Super Doc upload"
```

- ECDSA P-256 with SHA-256 is used (no SHA-1, no RSA PKCS#1 v1.5). If Play Console ever refuses
  an EC upload certificate, make an RSA 3072 key instead (`-keyalg RSA -keysize 3072 -sigalg SHA256withRSA`).
- The certificate is self-signed. That is normal for Android signing keys; it is not used for TLS.
- 10000 days means it is valid until about 2054 (Play needs validity past 2033).
- Check it with `keytool -list -v -keystore superdoc-upload.p12` (algorithm, size, dates).
- `*.jks`, `*.keystore`, `*.p12` and `*.pfx` are in `.gitignore`. Never commit a key or a password.

## 2. Build

```bash
export SUPERDOC_KEYSTORE_PATH=~/keys/superdoc-upload.p12
export SUPERDOC_KEY_ALIAS=upload
export SUPERDOC_KEYSTORE_PASSWORD="$(your-password-manager get superdoc-upload)"
export SUPERDOC_KEY_PASSWORD="$SUPERDOC_KEYSTORE_PASSWORD"
tools/release/build-release.sh
```

Outputs:

| File | Use | Size (Oct 2026) |
|---|---|---|
| `app/build/outputs/bundle/release/app-release.aab` | upload to Play | 45 MB |
| `app/build/outputs/apk/release/app-release.apk` | install directly with `adb install` | 79 MB |

Play delivers only the phone's own CPU part of the bundle, so a download is far smaller than the APK.
Both languages are always included (`bundle.language.enableSplit = false`), because the language
is changed inside the app.

Before every upload, raise `versionCode` in `app/build.gradle.kts` (and `versionName` if you like).

## 3. Play Console steps

1. Create the app: name "Super Doc", default language English (India), app, free.
2. **Testing → Internal testing → Testers**: create a list with the family's Gmail addresses.
3. **Create release**: upload the `.aab`, accept Play App Signing, add release notes in
   English and Hindi (e.g. "पहला परीक्षण संस्करण / First test version").
4. Fill in **App content** (needed even for internal tests):
   - **Privacy policy**: host `docs/release/privacy-policy.md` (e.g. GitHub Pages) and paste its URL.
   - **Ads**: no ads.
   - **App access**: everything works without an account.
   - **Content rating**: questionnaire, category "Utility / productivity"; no violence, chat, or gambling.
   - **Target audience**: 18 and over (designed for older adults; not for children).
   - **Data safety**: see below.
   - **Government / financial / health apps**: no.
5. **Roll out** to internal testing, then send testers the opt-in link from the Testers tab.

### Data safety answers

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share user data? | No | Notes, scans, voice and AI all stay on the phone. Our code sends nothing anywhere. |
| Is data encrypted in transit? | Yes (only download) | The optional AI model is downloaded over HTTPS from an allowlisted host; nothing is uploaded. |
| Can users delete data? | Yes | Delete / Recycle Bin, or uninstall. |

Check before submitting: the ML Kit document scanner and text recognition run through Google Play
services / ML Kit, which can send Google anonymous diagnostics about API use. Read Google's current
"ML Kit data disclosure" page and declare whatever it says must be declared.

Permissions shown to reviewers: microphone (voice typing, asked only on tap), internet and network
state (optional AI model download), storage write only on Android 8-9 ("Save copy to phone").

## 4. Rules to know

- **Target API level**: since 31 Aug 2026 new apps and updates must target Android 16 (API 36).
  The app targets 36 (`android.suppressUnsupportedCompileSdk=36` because AGP 8.7 predates it;
  upgrade AGP when convenient).
- **Going public later**: new personal developer accounts must first run a *closed* test with at
  least 12 testers for 14 days before production access is granted. Internal testing does not count.
- Check the Console's **Pre-launch report** after upload; it runs the app on real devices.

## 5. Test on the real phone before inviting others

- Install from the Play link (not adb), choose Hindi, take the tour with sound.
- Scan a real bill, open it, send it on WhatsApp to the family helper.
- Write a note by voice in Hindi with flight mode on.
- Settings → Backup → save to Google Drive; then Restore on another phone.
- If AI is offered: download it on Wi-Fi, run "Fix spelling", then put the app in the background,
  open WhatsApp and the camera, and come back: the note must still be there.
