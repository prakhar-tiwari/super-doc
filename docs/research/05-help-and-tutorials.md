# Research: help, tour and practice (Iteration 6)

## Question
How do we teach a first-time smartphone user, who may read Hindi slowly, to use every screen,
without a person sitting next to them?

## Findings that shaped the design
- **Show the real screen, mark one thing.** Generic illustrations make users map a drawing to the
  phone in front of them. Every help step uses a real screenshot of this app, in the user's
  language, with one button spotlighted (the rest is dimmed) and one short instruction.
- **Recorded voice, not the phone's text-to-speech.** Hindi TTS voices are missing, robotic or need
  a download on many budget phones (the same finding as in Iteration 1). Narration is recorded
  ahead of time and bundled: 80 clips, about 2 MB as 32 kbps mono AAC. If a clip is ever missing,
  the app falls back to text-to-speech so the user still hears something.
- **Tall screenshots are cropped around the highlight.** A full phone screenshot shown inside
  another phone screen is too small to read. Each step shows a band (42% of the screen height)
  centred on the marked button, at roughly real size.
- **Fixed Previous / Next buttons.** The step buttons stay at the bottom of the screen, so the user
  never has to scroll to find "Next". The voice starts on its own for each step, with
  "Listen again" / "Stop the voice".
- **A person to call.** Every help page ends with "Still not sure? Call <family helper>", one tap
  to the phone dialler, or a tip on how to add a family helper.
- **Practice without fear.** "Practice with examples" adds a sample note (with spelling mistakes to
  try the AI helper on) and a sample letter PDF, both badged "Practice". They can be opened, sent,
  changed or deleted, and removed again from Settings. The tour ends with "Try it now".

## Content pipeline (help can change without code changes)
- `assets/help/help.json`: topics, steps, English and Hindi text. Button names are written as
  `{string_name}` and filled from the app's own strings, so help always uses the exact label shown
  on screen in each language. A unit test checks every placeholder exists in both languages and
  every `help("topic")` link in the code has a topic.
- `tools/help/capture.py`: drives the app on the emulator, takes each screenshot in both languages
  (WebP, 720 px wide, about 40 KB each), and measures each step's highlight from the live layout
  (uiautomator bounds of the target button) into `assets/help/boxes.json`. Re-run after UI changes.
- `tools/help/make_audio.py`: records narration with macOS voices Lekha (Hindi) and Rishi (Indian
  English) at a slightly slower rate, only for steps whose text changed.

## Notes from capturing
- Compose only reports on-screen items to uiautomator, so pictures of things lower on a screen
  (Recycle Bin button, AI helper button) are separate scrolled screenshots.
- The AI pictures in Hindi use the English practice note: the on-device model available today
  only handles English (see `04-on-device-ai.md`). With the Gemma model they should be re-taken
  on the Hindi note.

## Open points for the usability check with the father
- Is the narration speed right? (Rates are set in `make_audio.py`.)
- Does he understand the spotlight, or does he try to tap the picture? If so, add "This is only a
  picture" to the first step.
