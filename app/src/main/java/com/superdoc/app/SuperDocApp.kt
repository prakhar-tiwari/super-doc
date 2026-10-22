package com.superdoc.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.pdf.ThumbnailCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SuperDocApp : Application() {
    @Inject lateinit var documents: DocumentsRepository
    @Inject lateinit var thumbnails: ThumbnailCache

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        scope.launch { documents.purgeOldRecycleBin() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) thumbnails.clear()
    }
}
