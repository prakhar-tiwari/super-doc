package com.superdoc.app.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** What came back from the camera: either a ready PDF (ML Kit) or plain photos (fallback picker). */
data class ScanResult(val pdf: Uri?, val pages: List<Uri>)

private const val MAX_PAGES = 50

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Returns a function that opens Google's document scanner (edge detection, crop, multi-page).
 * If the scanner can't start on this phone, the photo picker opens instead.
 */
@Composable
fun rememberScanner(onResult: (ScanResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)

    val scanner: GmsDocumentScanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(MAX_PAGES)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)) { uris ->
        if (uris.isNotEmpty()) latest(ScanResult(pdf = null, pages = uris))
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
            val pages = scan.pages?.map { it.imageUri }.orEmpty()
            latest(ScanResult(pdf = scan.pdf?.uri, pages = pages))
        }
    }

    val openPicker = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    return remember {
        {
            val activity = context.findActivity()
            if (activity == null) {
                openPicker()
            } else {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { openPicker() }
            }
        }
    }
}

/** Opens only the photo picker (used for "add pages from a photo"). */
@Composable
fun rememberPhotoPicker(onResult: (List<Uri>) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)) { uris ->
        if (uris.isNotEmpty()) latest(uris)
    }
    return remember { { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}
