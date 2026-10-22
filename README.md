# Super Doc

An Android app for writing notes and keeping documents, made for older people and anyone who
finds phones confusing. Hindi and English throughout. Everything stays on the phone.

## What it does

- **Notes** that save themselves, with voice typing in Hindi or English (offline when the phone
  has the voice pack) and read-aloud.
- **Photo to document**: scan papers with the camera into a PDF. Text in the photo is read to give
  the file a meaningful name (e.g. "Electricity bill - 22 Oct 2026").
- **My Documents**: one list with search and a Recycle Bin (30 days). Files open inside the app,
  so there is no file manager to learn.
- **PDF or Word from a note**: plain page, letter/application (date, "To", subject, your name),
  or list, with correct Hindi shaping.
- **Page tools**: join, split, remove, add and reorder pages, using a picture grid of the pages.
- **Send** to WhatsApp or any app, with a one-tap "send to family helper".
- **AI helper (optional, on-device)**: fix spelling, make it neat, make it a letter, make it shorter,
  by tapping a button or saying what you want. It only appears on phones that can run it.
- **Help everywhere**: a guided tour, and help for each screen with real screenshots, the button
  highlighted, and a recorded voice in Hindi or English. There are also practice examples that
  are safe to try things on.
- **Backup**: one file with every document, kept wherever you choose and restored on a new phone.

Design rules: big labelled buttons (no icon-only controls), Back/Help/Home on every screen,
confirmation before anything is lost, undo where possible, and layouts that still work at 200%
system text size.

## Privacy

There are no accounts, servers, analytics or ads. Notes, scans, voice and AI never leave the phone.
The only network use is the optional AI model download. It is HTTPS only, from an allowlisted host,
and checked against a SHA-256 checksum. Android's automatic cloud backup is off
(`allowBackup=false`). Backups happen only when the user saves one. See
[docs/release/privacy-policy.md](docs/release/privacy-policy.md).

## Requirements

- Android 8.0+ (minSdk 26), targetSdk 36.
- Built for budget phones with 3–4 GB RAM. The AI helper needs a 64-bit phone with about 2.6 GB or more.
- To build: JDK 17 and the Android SDK (platform 36). Gradle comes with the wrapper.

## Build and run

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected phone or emulator
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest   # device tests (Hindi PDF golden test, AI probe)
```

The debug APK is large (~180 MB) because it carries native libraries for every CPU type,
including the x86 ones for emulators. Release builds drop those.

### Release

Signing values come only from environment variables. Keep the keystore outside the repo and
never commit it.

```bash
export SUPERDOC_KEYSTORE_PATH=~/keys/superdoc-upload.p12
export SUPERDOC_KEY_ALIAS=upload
export SUPERDOC_KEYSTORE_PASSWORD=...   # from your password manager
export SUPERDOC_KEY_PASSWORD=...
tools/release/build-release.sh          # tests, signed .aab and .apk, signature check
```

The release build uses R8, contains arm64-v8a and armeabi-v7a only, and keeps both languages in
every bundle split. Play Console steps are in
[docs/release/play-internal-test.md](docs/release/play-internal-test.md).

### AI model

The app works fully without it. In Settings → "Set up the AI helper", the user either downloads
the recommended model (Qwen2.5 0.5B, about 550 MB, Wi-Fi suggested) or adds a `.task` file
already on the phone. The model list, checksums and RAM needs are in
`app/src/main/assets/ai/models.json`. The current small model is good at English corrections but
can't write Hindi. With this model the AI works on English text only, the app says so, and the
Translate action is hidden.

## Project layout

```
app/src/main/java/com/superdoc/app/
  ai/        model catalogue, download + checksum, MediaPipe engine, actions, voice intents
  backup/    backup zip format (hardened reader) and backup/restore
  data/      Room database, file store, settings (DataStore), auto-naming
  export/    note → PDF (PDFBox, Hindi shaping) and → DOCX
  help/      help content, highlight boxes, recorded voice, practice examples
  pdf/       page tools and thumbnails
  scan/      ML Kit document scanner and text recognition
  share/     sending and WhatsApp
  voice/     speech recognition and read-aloud
  ui/        Jetpack Compose screens and the shared senior-friendly components
app/src/main/assets/
  ai/models.json            AI model list
  help/help.json            help text (en + hi); {string_name} is filled from app strings
  help/img, help/audio      screenshots and narration per language
  help/boxes.json           where to highlight on each screenshot (generated)
tools/help/capture.py       drives the emulator to take the help screenshots + boxes
tools/help/make_audio.py    records narration with macOS voices (Lekha, Rishi)
tools/release/              release build script
docs/research/              findings behind each iteration (voice, scanning, PDF, AI, help, release)
docs/release/               Play internal test guide, privacy policy draft
```

Stack: Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, Navigation Compose,
ML Kit (document scanner, Latin + Devanagari text recognition), PdfBox-Android, MediaPipe LLM
Inference.

## Updating the help

1. Edit `app/src/main/assets/help/help.json`. Each step has an `image`, an optional `target`
   (an app string name to highlight), and `text.en` / `text.hi`.
2. New or changed picture: add a recipe in `tools/help/capture.py`, then run
   `python3 tools/help/capture.py --keep-data --images <name>` with the app on an emulator.
3. Run `python3 tools/help/make_audio.py`. It records only the steps whose text changed.
4. Run `./gradlew testDebugUnitTest`. `HelpContentTest` checks topics, placeholders and both languages.

## Languages

All strings are in `res/values` (English) and `res/values-hi` (Hindi). The language is chosen
on first launch and can be changed in Settings. It is a per-app language, so it works on Android 8+.
Hindi text uses simple everyday words. Hindi PDFs embed Noto Sans Devanagari
([OFL](docs/licenses/NotoSansDevanagari-OFL.txt)).

## Known limitations

- The 0.5B AI model sometimes invents details when making a letter. The user always sees a
  before/after review with an undo. The letter template under "Make PDF or Word file" is the
  reliable route.
- Offline Hindi voice typing depends on the phone's speech services and its Hindi offline pack.
- Not yet tried on a physical budget phone or an Android 16 device. See the checklist in
  `docs/release/play-internal-test.md`.
