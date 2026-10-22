package com.superdoc.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superdoc.app.R
import com.superdoc.app.data.AutoNamer
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import java.io.File

fun ItemType.icon(): ImageVector = when (this) {
    ItemType.NOTE -> Icons.AutoMirrored.Filled.Notes
    ItemType.PDF -> Icons.Filled.PictureAsPdf
    ItemType.DOCX -> Icons.Filled.Description
}

fun ItemType.color(): Color = when (this) {
    ItemType.NOTE -> SeniorColors.Note
    ItemType.PDF -> SeniorColors.Danger
    ItemType.DOCX -> SeniorColors.Documents
}

@Composable
fun ItemType.label(): String = stringResource(
    when (this) {
        ItemType.NOTE -> R.string.type_note
        ItemType.PDF -> R.string.type_pdf
        ItemType.DOCX -> R.string.type_word
    }
)

/** A big card for one note or document: picture on the left, name, type and date on the right. */
@Composable
fun ItemCard(
    item: ItemEntity,
    file: File?,
    thumbnails: ThumbnailCache,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(4.dp, SeniorColors.Primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ItemThumbnail(item, file, thumbnails)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = buildString {
                        append(item.type.label())
                        if (item.type != ItemType.NOTE && item.pageCount > 0) {
                            append(" · ")
                            append(pagesLabel(item.pageCount))
                        }
                        append(" · ")
                        append(AutoNamer.formatDate(item.updatedAt, AppLocale.current()))
                    }
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (badge != null) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(SeniorColors.Tools, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
fun pagesLabel(count: Int): String =
    if (count == 1) stringResource(R.string.pages_one) else stringResource(R.string.pages_many, count)

@Composable
fun ItemThumbnail(item: ItemEntity, file: File?, thumbnails: ThumbnailCache, size: Int = 84) {
    var bitmap by remember(item.id, item.fileName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    if (item.type == ItemType.PDF && file != null) {
        LaunchedEffect(file, file.lastModified()) { bitmap = thumbnails.render(file, 0, 240) }
    }
    Box(
        Modifier
            .size(width = size.dp, height = (size * 1.3f).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(item.type.color().copy(alpha = 0.12f))
            .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.heightIn(min = 1.dp))
        } else {
            Icon(item.type.icon(), contentDescription = null, tint = item.type.color(), modifier = Modifier.size(44.dp))
        }
    }
}
