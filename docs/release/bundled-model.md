# Shipping Gemma inside the Play release

Users never visit Hugging Face. You, the publisher, accept the Gemma licence once; the model then
travels inside the app bundle as the `gemma_pack` asset pack and Google Play delivers it.

## How it reaches the phone
- `gemma_pack` is an **on-demand** Play asset pack (up to 1.5 GB allowed; ours is about 530 MB).
- On first start, if the phone can run the AI helper (64-bit, at least 2.6 GB RAM, not a low-RAM
  device), the app asks Play for the pack once. Play downloads it in the background over Wi-Fi; on
  mobile data it waits, and AI settings offers "Download with mobile data" (Play's own dialog).
- Phones that can't run it never download it. Deleting it in AI settings removes the pack, and it is
  not fetched again unless the user taps Download.
- Builds not installed from Play (debug runs, a copied APK) can't get packs; AI settings says so and
  "Add a helper file from the phone" still works.

## Before every release build (one-time per model version)
1. Sign in to Hugging Face, open `litert-community/Gemma3-1B-IT`, accept the Gemma licence.
2. Download `gemma3-1b-it-int4.task` and put it at
   `gemma_pack/src/main/assets/gemma3-1b-it-int4.task` (git-ignored; never commit it).
3. Save the official text of https://ai.google.dev/gemma/terms as
   `gemma_pack/src/main/assets/GEMMA_TERMS.txt` (the licence requires a copy to go with the model).
   `NOTICE` is already there.
4. `./gradlew :app:bundleRelease` — the build stops if the model is missing or its size differs
   from `sizeBytes` in `app/src/main/assets/ai/models.json`.
5. Re-run `AiQualityProbe` on a real phone with the bundled file before relying on Hindi output.

## Testing the Play delivery locally
```
./gradlew :app:bundleRelease
java -jar bundletool-all.jar build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=superdoc.apks --local-testing
java -jar bundletool-all.jar install-apks --apks=superdoc.apks
```
Or upload to the internal test track (see `play-internal-test.md`) and install from Play.

## Licence conditions (Gemma Terms of Use, section 3.1)
- Use restrictions are part of the user-facing terms: see "AI helper licence" in `privacy-policy.md`.
- A copy of the Terms and the `NOTICE` text travel with the model (step 3 above).
- The `.task` file is shipped unmodified.
