# Research: on-device AI helper (Iteration 5)

## Question
Can a 3–4 GB RAM Android phone fix, tidy and rewrite notes in Hindi and English with a language
model that runs entirely on the phone, offline, so that nothing in a note ever leaves the device?

## Runtime
- **MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai` 0.10.35)**, CPU backend.
  It loads `.task` bundles from LiteRT Community on Hugging Face, streams partial answers, can be
  cancelled, and reports prompt size in tokens (`sizeInTokens`) so over-long inputs are refused
  before generation instead of failing half-way.
- Rejected: llama.cpp via JNI (needs our own native build and memory tuning per ABI), ML Kit
  GenAI / Gemini Nano (only on a few flagship phones, not budget ones), any cloud API (breaks
  the "nothing leaves the phone" requirement).
- The model is loaded on first use and closed after 2 idle minutes or when Android reports low
  memory (`onTrimMemory`), so the rest of the app is not starved.

## Candidate models

| Model | Size | Min RAM we allow | Hindi | Download |
|---|---|---|---|---|
| Qwen 2.5 0.5B Instruct q8 | 521 MB | 2.6 GB | No (see below) | Direct, Apache-2.0 |
| Qwen 2.5 1.5B Instruct q8 | 1.5 GB | 5.5 GB | No | Direct, Apache-2.0 |
| Gemma 3 1B IT int4 | 529 MB | 2.6 GB | Yes | Import only (licence must be accepted on Hugging Face) |

Phones with less than 2.6 GB total RAM, or that Android marks as low-RAM, do not see the AI
helper at all; every other feature works without it.

## Results on the emulator (4 GB RAM, arm64, Apple-silicon host)
Speed test (fix spelling of one English sentence): load 4.0 s, first word 0.7 s, total 1.4 s,
about 36 words per second. A real budget phone will be several times slower; the speed test in
AI settings exists so a family member can check on the actual phone.

Quality probe (`AiQualityProbe`, every action on an English and a Hindi sample):
- **English, Qwen 0.5B:** spelling fixes are reliable (tomorow → tomorrow, docter → doctor,
  glases → glasses). It leaves lower-case sentence starts, so English answers get a small
  deterministic sentence-case pass. "Make it shorter" keeps the facts. "Make it neat" often
  changes nothing. "Make it a letter" produces a proper letter but sometimes adds a reason that
  was not in the note; the prompt now forbids added facts, and the before/after screen with
  "No, keep my old text" plus a 30-second UNDO stays the safety net.
- **Hindi, Qwen 0.5B: unusable.** Two separate problems:
  1. MediaPipe sometimes passes Qwen's byte-level tokens through undecoded, so Devanagari comes
     out as `Ġà¤°…`. `ByteLevelText.repair` maps these back to UTF-8 (unit-tested), which recovers
     most of the text.
  2. Even after repair, the 0.5B model drops sentences, changes meaning and loops when
     translating into Hindi.
  So the catalog marks each model with `"hindi": true/false`. With an English-only model the AI
  panel hides "Translate" and shows a plain message for Hindi notes instead of offering actions
  that would damage them.
- **Spoken instructions:** the 0.5B model classified both "isko thoda saaf kar do" and "make
  this into something I can send to the bank manager" as SEND. Keyword matching (now including
  Hinglish written in Latin letters: saaf, galti, chitthi, bhejo…) handles the common phrases
  instantly. When only the model's guess is available, the app asks "Did you mean this?" before
  doing anything.

## Recommendation for the father's phone
Gemma 3 1B int4 is the right model for a Hindi-first user: same size as Qwen 0.5B, SentencePiece
tokenizer handled natively by MediaPipe, and much stronger Hindi. Hugging Face gates the download,
but the Gemma Terms of Use allow redistribution (with the licence text, a NOTICE file and the use
restrictions passed on). So the publisher accepts the licence once and ships the model as a Google
Play asset pack, which the app requests by itself on phones that can run it — see
`docs/release/bundled-model.md`. "Add a helper file from the phone" remains for non-Play installs.
Re-run `AiQualityProbe` with that file before relying on Hindi output.

## Download safety
- HTTPS only, host allow-list (`huggingface.co`), redirects must stay on HTTPS.
- SHA-256 of every file is pinned in `assets/ai/models.json`; the hash is computed while the
  file streams and the `.part` file is only renamed after it matches. Resume re-hashes the
  existing part first.
- Certificates are checked against Android's normal trust store; the app adds no custom trust
  and never disables validation.
- On this development network the in-app download fails with "Trust anchor for certification
  path not found": a network proxy intercepts HTTPS and re-signs huggingface.co with its own
  certificate that the Mac trusts but the emulator does not. That
  is the correct behaviour; on a normal home Wi-Fi the download uses the real certificate.
  For testing, the same SHA-verified file was added through the import button instead.
- Imported files are matched to the catalog by SHA-256 when possible.

## Interaction design decisions
- No chat box. Five fixed buttons (fix, neat, letter, shorter, translate) plus "say what you
  want", because an open prompt is hard to use and easy to misuse.
- Every change is shown as Before / After and must be accepted. After "Keep", an UNDO bar stays
  for 30 seconds (the default 10 s is too short for older users).
- Long notes are processed a few paragraphs at a time, with "Part 2 of 3" progress and a big
  Stop button.
