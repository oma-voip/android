package me.sweettie.oma.updater

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadHelper(
    private val context: Context,
    private val version: String
) {

    private val cacheDir by lazy {
        File(context.cacheDir, "updates_cache").apply { if (!exists()) mkdirs() }
    }
    private var wakeLock: WakeLock? = null

    fun startDownload(
        urlString: String,
        onProgress: (progress: Int) -> Unit,
        onComplete: (file: File?) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                acquireWakeLock()
                val file = downloadFile(urlString, onProgress)
                withContext(Dispatchers.Main) {
                    releaseWakeLock()
                    if (file != null) {
                        onComplete(file)
                    } else {
                        onError("Download failed or cancelled")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    releaseWakeLock()
                    e.printStackTrace()
                    onError("Error: ${e.message}")
                }
            }
        }
    }


    private fun acquireWakeLock() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DownloadHelper::WakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 минут*/)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private suspend fun downloadFile(
        urlString: String,
        onProgress: (progress: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                doInput = true
                connect()
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val fileLength = connection.contentLength

            input = connection.inputStream

            val apkFile = File(cacheDir, "update_$version.apk")
            if (apkFile.exists()) apkFile.delete()
            output = FileOutputStream(apkFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (true) {
                count = input.read(data)
                if (count == -1) break
                output.write(data, 0, count)
                total += count

                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
            output.flush()
            apkFile
        } finally {
            try {
                output?.close()
            } catch (_: Exception) {
            }
            try {
                input?.close()
            } catch (_: Exception) {
            }
            connection?.disconnect()
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    fun openApk(file: File) {
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE)
        println(Uri.fromFile(file).toString())
        println(context.packageName)
        install.setDataAndType(
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file),
            "application/vnd.android.package-archive"
        )
        install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(install)
    }
}