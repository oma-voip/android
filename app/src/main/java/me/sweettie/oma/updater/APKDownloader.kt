package me.sweettie.oma.updater

import android.content.Context
import java.io.File

class APKDownloader(val context: Context, val update: VersionInfo) {
    private lateinit var downloadHelper: DownloadHelper

    fun downloadAPK(
        onProgress: (Int) -> Unit = {},
        onComplete: (File?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        downloadHelper = DownloadHelper(context, update.version.toString())
        downloadHelper.startDownload(
            update.updateLink,
            onProgress = onProgress,
            onComplete = onComplete,
            onError = onError
        )
    }

    fun openApk(file: File) {
        downloadHelper.openApk(file)
    }

}