# Research: PDF page tools (Iteration 4)

## Question
How do we join, split, remove, add and reorder PDF pages on a 3–4 GB RAM phone, fully offline,
in a way a first-time smartphone user can follow?

## Library
- **PdfBox-Android 2.0.27** (already used for photo → PDF). `PDDocument.importPage` copies a page
  with its resources, so remove / reorder / split are all "select these pages into a new file",
  and insert is "pages before + new pages + pages after". `PDFMergerUtility` joins files.
- Memory: every document is opened with `MemoryUsageSetting.setupTempFileOnly()` pointed at
  `cache/work`, so large scans spill to disk instead of the Java heap. `OutOfMemoryError` is still
  caught and shown as "too big for this phone, try fewer pages".
- Password-protected PDFs throw `InvalidPasswordException`; we surface a clear message
  (`pdf_locked`) instead of trying to decrypt.
- Thumbnails use Android's own `PdfRenderer` (one page open at a time, so rendering is
  serialised behind a mutex and cached in a small LRU that is cleared on low memory).

## Interaction design decisions
- **No drag and drop.** Dragging thumbnails needs fine motor control and is hard to discover.
  Reorder uses big "Move up" / "Move down" buttons, and the last-moved page gets a yellow border
  so the user does not lose track of it.
- **Tap to mark**, with an unmistakable mark: a red cross for "remove", a green tick for
  "choose". Each page also exposes a TalkBack state ("Will be removed" / "Will be kept").
- **Positions are buttons, not numbers.** For "Add pages" and "Cut into two" the user taps a
  full-width button between page pictures ("Put new pages here", "Cut here") instead of typing a
  page number.
- **Split offers three plain choices**: every page separately, choose some pages, cut into two.
  Free-form ranges like "1-3, 5" were rejected as too technical.
- **Join order = tap order**, shown as a "Number 1 / Number 2" badge on each card, and repeated
  in the confirmation dialog.
- **The original is never changed.** Every tool writes a new document named automatically:
  `Changed - <name> - <date>`, `Joined - …`, `Part 1 - …`. The middle part comes from
  `AutoNamer.core`, which strips the old prefix and date. This removes the need for an Undo:
  the old document is still there.
- Every tool confirms in words before running ("A new document will be made without these
  pages: 2, 3. Your original document stays as it is.") and ends on a "Done!" screen with an
  "Open" button for each new document.
- New pages can come from the camera (ML Kit scanner), phone photos, another document in
  "My Documents", or any PDF on the phone (system file picker, read-only, no storage permission).

## Verified on the emulator
Add 5 pages from a phone PDF to a 1-page PDF (6 pages), remove pages 2–3 (4 pages, correct
content), move page 1 down, cut into two after page 2 (2 + 2 pages), join Part 2 + Part 1 in tap
order (4 pages).
