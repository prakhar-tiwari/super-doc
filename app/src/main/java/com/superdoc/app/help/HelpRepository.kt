package com.superdoc.app.help

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelpRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var content: HelpContent? = null
    private val listings = mutableMapOf<String, Set<String>>()

    suspend fun content(): HelpContent = mutex.withLock {
        content ?: withContext(Dispatchers.IO) {
            val help = context.assets.open("help/help.json").bufferedReader().use { it.readText() }
            val boxes = runCatching { context.assets.open("help/boxes.json").bufferedReader().use { it.readText() } }.getOrNull()
            HelpContent.parse(help, boxes)
        }.also { content = it }
    }

    /** Step text in the app's language, with `{string_name}` replaced by the real button labels. */
    @SuppressLint("DiscouragedApi") // names come from help.json, so they can't be R.string constants
    fun text(step: HelpStep, hindi: Boolean): String {
        val localized = AppLocale.localized(context)
        return HelpContent.fill(step.text.get(hindi)) { name ->
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            if (id == 0) null else localized.getString(id)
        }
    }

    suspend fun image(lang: String, image: String): Bitmap? = withContext(Dispatchers.IO) {
        val path = HelpAssets.image(lang, image)
        if (!exists(path)) return@withContext null
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    fun exists(path: String): Boolean {
        val dir = path.substringBeforeLast('/')
        val names = synchronized(listings) {
            listings.getOrPut(dir) { context.assets.list(dir)?.toSet().orEmpty() }
        }
        return path.substringAfterLast('/') in names
    }

    companion object {
        fun lang(hindi: Boolean) = if (hindi) "hi" else "en"
    }
}
