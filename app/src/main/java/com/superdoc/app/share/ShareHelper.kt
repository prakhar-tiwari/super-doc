package com.superdoc.app.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.superdoc.app.R
import com.superdoc.app.data.FamilyHelper
import com.superdoc.app.data.FileStore
import com.superdoc.app.util.AppLocale
import java.io.File

/** Sends notes and documents to other apps. WhatsApp gets its own button because most families use it. */
object ShareHelper {
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun whatsAppPackage(context: Context): String? = WHATSAPP_PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    fun mimeFor(extension: String) = when (extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    enum class Target { ANY_APP, WHATSAPP, FAMILY }

    /** Returns false when the chosen app could not be opened. */
    fun shareFile(
        context: Context,
        files: FileStore,
        source: File,
        title: String,
        target: Target,
        family: FamilyHelper? = null,
    ): Boolean {
        val extension = source.extension
        val copy = files.shareCopy(source, title, extension)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(context, intent, target, family)
    }

    fun shareText(context: Context, text: String, title: String, target: Target, family: FamilyHelper? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        return launch(context, intent, target, family)
    }

    private fun launch(context: Context, intent: Intent, target: Target, family: FamilyHelper?): Boolean {
        val wa = whatsAppPackage(context)
        val finalIntent = when (target) {
            Target.ANY_APP -> Intent.createChooser(intent, AppLocale.string(context, R.string.share_chooser_title))
            Target.WHATSAPP -> intent.setPackage(wa ?: return false)
            Target.FAMILY -> {
                val helper = family ?: return false
                intent.setPackage(wa ?: return false)
                // WhatsApp opens the chat with this person directly when "jid" is given.
                intent.putExtra("jid", "${helper.whatsAppNumber}@s.whatsapp.net")
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(finalIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun dial(context: Context, phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Opens a Word file in another app (e.g. Google Docs or WPS), since the app cannot show Word files itself. */
    fun openExternally(context: Context, files: FileStore, source: File, title: String): Boolean {
        val copy = files.shareCopy(source, title, source.extension)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(source.extension))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val canOpen = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        return canOpen && runCatching { context.startActivity(intent) }.isSuccess
    }
}
