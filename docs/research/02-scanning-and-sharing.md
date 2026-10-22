# Research spike 2: Photos to PDF, automatic names and sharing

## Scanning options

| Option | Edge detection / crop | Effort | Budget phones | Notes |
|---|---|---|---|---|
| ML Kit Document Scanner (`play-services-mlkit-document-scanner`) | Yes, automatic, plus filters, rotate and cleanup | Very low (Google's own camera screen) | Needs about 1.7 GB RAM and Google Play services; the model is downloaded once by Play services | Returns JPEG pages and a ready PDF. No camera permission needed in our app |
| CameraX + custom edge detection (OpenCV) | Must build it ourselves | High | Works everywhere | Large native library; hard to make as reliable as ML Kit |
| System photo picker + our own PDF builder | No crop | Low | Works everywhere, no Play services needed | Good fallback |

**Decision:** use the ML Kit Document Scanner (full mode, gallery import allowed, up to 50
pages). If it can't start (no Play services, too little RAM, or the model isn't downloaded
yet), automatically fall back to the Android photo picker. Then build the PDF ourselves
with PdfBox, using JPEG compression, so files stay small enough for WhatsApp.

Older users do not know what "scan" means, so the Home tile says "Photo to Document".
A short picture-free explanation is shown before the camera opens: point, press the round
button, press Done.

## Automatic names from the paper itself (OCR)

- ML Kit Text Recognition v2 with the **Devanagari** model also recognises Latin script,
  so one recogniser covers both Hindi and English papers. It is bundled in the app (about
  4 MB per ABI), so it works offline from the first launch.
- The first page is read. The name uses the most prominent line near the top (largest
  text height in the top 40% of the page, with at least 3 letters). This is usually
  the heading, like "Electricity Bill" or "आधार कार्ड".
- Names look like `Scan - Electricity Bill - 22 Oct 2026`. A number is added if the
  name already exists.

## Viewer

`android.graphics.pdf.PdfRenderer` (Android 5+) renders pages to bitmaps. It is
zero-dependency and fast enough on budget phones when rendered at screen width. Pinch zoom
is hard for many seniors, so there are also big "Bigger / Smaller" buttons and big
"Previous page / Next page" buttons.

## Sharing

- Files are shared through `FileProvider`, from a copy named after the document title, so
  the person receiving it sees "Scan - Electricity Bill - 22 Oct 2026.pdf" instead of
  a random ID.
- A dedicated WhatsApp button (`com.whatsapp` / `com.whatsapp.w4b`) and a general "Send
  using another app" button, which opens the Android share sheet.
- **Family helper:** saved once in Settings. "Send to <name> on WhatsApp" uses
  WhatsApp's `jid` extra (`<number>@s.whatsapp.net`) to open that person's chat
  directly. The `jid` extra is widely used but undocumented; if WhatsApp ignores it, the
  user simply picks the chat.
- "Save a copy to phone" writes to `Download/SuperDoc` with MediaStore (Android 10+), or
  with the legacy storage permission on Android 8-9. This way, other apps can find the file too.
