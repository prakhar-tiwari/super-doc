# Research spike 1: Offline Hindi voice typing on budget phones

Goal: a senior taps one big button and speaks Hindi or English; text appears in the note.
Constraints: 3-4 GB RAM phones, Android 8+, audio should not leave the phone.

## Candidates

| Option | Hindi quality | Size on phone | Offline | Android versions | Notes |
|---|---|---|---|---|---|
| Android `SpeechRecognizer`, on-device (`createOnDeviceSpeechRecognizer`) | Good (same engine as Gboard voice typing) | 0 MB in our APK; language pack about 50-80 MB managed by Google | Yes, guaranteed | 12+ (API 31) | `triggerModelDownload()` (API 33) can download the Hindi pack for the user |
| Android `SpeechRecognizer`, default + `EXTRA_PREFER_OFFLINE` | Good | 0 MB in our APK | Yes when the offline pack is installed; best effort | 8+ | Without the pack it may fail or use the network depending on the engine |
| Vosk + `vosk-model-small-hi-0.22` | Fair (reported WER about 20%+; struggles with names and mixed Hinglish) | about 45 MB model + about 8 MB native libraries | Yes | 5+ | Extra download, extra code, no punctuation |
| whisper.cpp tiny/base | Poor for Hindi at tiny/base; small is too slow on budget CPUs | 75-150 MB | Yes | 7+ | Real-time is not possible on budget phones |

## Decision

1. Use Android's on-device recognizer when available (Android 12+). It is the only option
   that is both accurate for Hindi and guaranteed to stay on the phone.
2. Otherwise use the default recognizer with `EXTRA_PREFER_OFFLINE = true`. With the setting
   "Voice stays on this phone" turned on (the default), network errors are shown as "the
   Hindi voice pack is not downloaded" with instructions, instead of silently going online.
3. Keep Vosk as a documented fallback for phones without Google services. It is not
   shipped in v1, because it doubles the download size and is less accurate.

Implementation: `app/src/main/java/com/superdoc/app/voice/VoiceTyping.kt`.

## Senior-specific findings applied

- Long pauses are normal, so the silence timeout is raised to 3 seconds
  (`EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`). Some engines ignore this.
- Partial results are shown live in a yellow box, so the user knows the phone is listening.
- The voice language is chosen with a visible "हिन्दी / English" switch in the editor,
  not hidden in settings.
- Every failure has a plain-language message telling the user what to do next.

## To verify on your father's phone

- Settings › Google › Voice typing (or Gboard › Voice typing) › Offline speech recognition:
  download Hindi (India) and English (India).
- Speak a 3-sentence Hindi paragraph and check the text matches.
