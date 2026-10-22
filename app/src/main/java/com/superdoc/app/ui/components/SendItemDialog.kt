package com.superdoc.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.superdoc.app.R
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.share.ShareHelper
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.theme.SeniorColors
import java.io.File

/**
 * The "Send" choices for any item: to the family helper, on WhatsApp, or any other app.
 * Notes are sent as text; [onMakePdf] adds "Send as a document" for notes.
 */
@Composable
fun SendItemDialog(
    item: ItemEntity,
    file: File?,
    files: FileStore,
    onDismiss: () -> Unit,
    onMakePdf: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val family = LocalSettings.current.family
    val hasWhatsApp = remember { ShareHelper.whatsAppPackage(context) != null }
    var failed by remember { mutableStateOf(false) }

    fun send(target: ShareHelper.Target) {
        val ok = if (item.type == ItemType.NOTE || file == null) {
            ShareHelper.shareText(context, item.body, item.title, target, family)
        } else {
            ShareHelper.shareFile(context, files, file, item.title, target, family)
        }
        if (!ok) failed = true
    }

    if (failed) {
        MessageDialog(stringResource(R.string.note_send), stringResource(R.string.send_failed), onDismiss = { failed = false; onDismiss() })
        return
    }

    val isNote = item.type == ItemType.NOTE
    val choices = buildList {
        if (family != null && hasWhatsApp) {
            add(Choice(stringResource(R.string.send_to_family, family.name), Icons.Filled.Group, SeniorColors.WhatsApp) { send(ShareHelper.Target.FAMILY) })
        }
        if (hasWhatsApp) {
            add(
                Choice(
                    stringResource(if (isNote) R.string.send_whatsapp_text else R.string.send_whatsapp),
                    Icons.AutoMirrored.Filled.Send, SeniorColors.WhatsApp,
                ) { send(ShareHelper.Target.WHATSAPP) }
            )
        }
        add(
            Choice(
                stringResource(if (isNote) R.string.send_other_app_text else R.string.send_other_app),
                Icons.Filled.Share, SeniorColors.Primary,
            ) { send(ShareHelper.Target.ANY_APP) }
        )
        if (isNote && onMakePdf != null) {
            add(Choice(stringResource(R.string.send_as_pdf), Icons.Filled.PictureAsPdf, SeniorColors.Documents, onMakePdf))
        }
    }
    ChoiceDialog(
        title = stringResource(R.string.send_how_title),
        message = item.title,
        choices = choices,
        onDismiss = onDismiss,
    )
}
