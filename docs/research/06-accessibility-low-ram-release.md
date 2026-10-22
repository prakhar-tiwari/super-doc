# Iteration 7: accessibility, low-RAM phones, backup and release

Measured on the Pixel emulator (API 35, arm64) on an overloaded Mac, so absolute times are
pessimistic; the comparisons between builds are what matter.

## Large text (200% system font)

Older users often set the largest font. At 200% three things broke and were fixed in shared code:

| Problem | Fix |
|---|---|
| Top-bar title squeezed to "N…" by four labelled buttons | Top bar caps its own text at 1.3× (it is already large) |
| Two side-by-side buttons broke words ("Engli / sh") | `ButtonPair` stacks vertically from 1.5× |
| Chip rows (text size, filters, voice language) overflowed into an empty tall box | `FlowRow`, so chips wrap to the next line |

Checked after the fix in English and Hindi: Home, Note, Settings, My Documents, viewer, Help.

## TalkBack

A uiautomator scan of every main screen looked for clickable or checkable nodes with no text or
description, and for touch targets under 48 dp. Result: none, apart from cards cut off by the
screen edge (their label is off-screen, which is expected). The top-bar buttons announce their
label once (icon has no description; the text is merged into the button).

Not done: a full listen-through with TalkBack speaking Hindi. Do that on the real phone.

## Low-RAM behaviour

| Measurement | Result |
|---|---|
| App memory (PSS) with the AI model loaded | ~890 MB |
| After Android's "memory running low" or the app going to background | ~105 MB (model and thumbnails released) |
| Emulator restarted with 3 GB (`-memory 3072`, reports 2.9 GB) | "Make it a letter" finished in about a minute; Android's low-memory killer closed *other* cached apps (Google Search, contacts) and not Super Doc |
| App killed in the background, then reopened from Recents | Returns to the same note with the text intact (autosave + saved navigation state) |

The AI stays hidden on low-RAM-flagged phones, phones under 2.6 GB, and now also on 32-bit app
processes: a 32-bit process cannot reliably map a 500 MB+ model.

Quality note: on a short note, the 0.5B model's "Make it a letter" still adds invented content and
placeholders like "[Name]". The before/after screen and "the AI can make mistakes" warning stay;
the template letter in "Make PDF or Word file" is the reliable path for real letters.

## Startup and size

| | Debug | Release (R8) |
|---|---|---|
| Cold start (`am start -W`) | 10.5 s | 2.0–3.6 s, 0.7 s once warmed |
| Code (dex) | 42 MB | 6.4 MB |
| APK | 183 MB | 79 MB (arm64 + armv7 only) |
| App Bundle for Play | | 45 MB; phones download one CPU type only |

Native libraries dominate: MediaPipe LLM (27 MB arm64) and ML Kit OCR (11 MB arm64). x86 builds,
which exist only for emulators, are filtered out of release builds.

Checked under R8: language choice, Hindi help with recorded voice, backup restore, practice letter
(Hindi PDF via PDFBox with correct conjuncts), viewer, model import with SHA-256 check, AI "Fix spelling".

## Backup and restore

One zip the user puts where they like (Google Drive, Downloads, a pen drive) via the system file
picker, so the app needs no storage permission:

- `superdoc-backup.json` (titles, note text, dates, family helper, signature name) plus `files/<name>`.
- Practice examples and Recycle Bin items are left out. The AI model is left out (it is large and can
  be downloaded again).
- Restore only adds: items already present (same type, title and creation time) are skipped, and
  nothing is deleted. The family helper and signature are filled only if empty.

The zip may come from anywhere, so reading is defensive:

- Zip-slip: only `superdoc-backup.json` and `files/<name>` are read. Names must match
  `[A-Za-z0-9][A-Za-z0-9._-]*` and contain no `..`. The canonical target path must sit directly
  in the staging folder. Restored files get fresh names with an extension chosen from the item
  type, never from the zip.
- Zip bombs: at most 10,000 entries and a 32 MB manifest, and the total written is capped at free
  space minus 200 MB.
- Unknown format or newer version: refused with "Nothing was changed".

The backup is not encrypted. A password the user can forget would lock out the very person it is
for, so the "saved" message says plainly that anyone holding the file can read the documents.
If encryption is wanted later, use AES-256-GCM with a key derived by a memory-hard KDF.

`android:allowBackup="false"` stays, so documents never go to automatic cloud backup without the
user choosing to.

## Release configuration

- R8 minify + resource shrinking. Keep rules cover PDFBox (reflection) and MediaPipe/protobuf (JNI).
- `targetSdk 36` (Play requirement since 31 Aug 2026). AGP 8.7 needs
  `android.suppressUnsupportedCompileSdk=36`. Android 16 behaviour (edge-to-edge always on,
  predictive back) still needs a check on an Android 16 phone; the app is already edge-to-edge
  and uses Compose back handling.
- Signing only from environment variables. `tools/release/build-release.sh` refuses to run
  without them and refuses a keystore inside the project folder.
- The local test key was ECDSA P-256 / SHA256withECDSA, self-signed, valid to 2054, and kept in
  `~/.superdoc-signing` outside the repo.

See `docs/release/play-internal-test.md` for the Play Console steps.
