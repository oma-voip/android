package me.sweettie.oma.utils

import android.app.ActivityManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.webkit.MimeTypeMap
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import proto.OmaOuterClass
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random


object Utils {
    fun getVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            pInfo.versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            -1L
        } as Int
    }

    fun openActivity(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun showConfirmationDialog(
        context: Context,
        message: String,
        positiveText: String,
        negativeText: String,
        onPositiveClick: (DialogInterface) -> Unit,
        onNegativeClick: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context, R.style.Theme_Oma_Dialog)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                onPositiveClick.invoke(dialog)
                dialog.dismiss()
            }
            .setNegativeButton(negativeText) { dialog, _ ->
                onNegativeClick?.invoke()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private val cachedUsers: HashMap<String, OmaOuterClass.User> = HashMap()
    fun getCacheUser(userId: String, onGet: (OmaOuterClass.User?) -> Unit) {
        if (cachedUsers.containsKey(userId))
            onGet(cachedUsers[userId]!!)
        else {
            CoroutineScope(Dispatchers.IO).launch {
                val r2 = GrpcClient.getUser(userId)
                if (r2 == null) {
                    withContext(Dispatchers.Main) {
                        onGet(null)
                    }
                    return@launch

                }
                withContext(Dispatchers.Main) {
                    cachedUsers.put(userId, r2)
                    onGet(cachedUsers[userId]!!)
                }

            }
        }
    }

    fun createBitmapFromRGBA(
        attach: OmaOuterClass.ImagePreview,
        stride: Int
    ): Bitmap {
        val content = attach.content
        val width: Int
        val height: Int
        println(content.size())
        if (content.size() == 0) {
            width = 200
            height = 100
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK) // фон чёрный

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val random = Random.Default
            val centerX = width / 2f
            val centerY = height / 2f

            val colors = listOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA
            )

            for (color in colors) {
                val radius = random.nextFloat() * width / 3f + width / 5f
                val radGrad = RadialGradient(
                    centerX + (random.nextFloat() - 0.5f) * width / 2f,
                    centerY + (random.nextFloat() - 0.5f) * height / 2f,
                    radius,
                    color,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                paint.shader = radGrad
                paint.alpha = 180

                canvas.drawCircle(
                    centerX + (random.nextFloat() - 0.5f) * width / 2f,
                    centerY + (random.nextFloat() - 0.5f) * height / 2f,
                    radius,
                    paint
                )
            }

            return bitmap
        } else {
            width = stride / 4
            height = (content.size() / 4) / width

            val pixels = IntArray(width * height)
            val bytes: ByteArray = content.toByteArray()

            for (y in 0 until height) {
                val offset = y * stride
                for (x in 0 until width) {
                    val i = offset + x * 4
                    val r = bytes[i].toInt() and 0xFF
                    val g = bytes[i + 1].toInt() and 0xFF
                    val b = bytes[i + 2].toInt() and 0xFF
                    val a = bytes[i + 3].toInt() and 0xFF
                    // Android bitmap uses ARGB format
                    pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }


    fun isVideoByMime(context: Context, uri: Uri): Boolean {
        val cr = context.contentResolver
        var type = cr.getType(uri)
        if (type == null) {
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
        return type?.startsWith("video/") == true
    }

    fun savePhotoIntoGallery(path: String, context: Context) {
        val values = ContentValues()
        values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(Images.Media.MIME_TYPE, "image/jpeg")
        values.put(MediaStore.MediaColumns.DATA, path)
        context.contentResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun saveUriToDownloads(context: Context, fileName: String, mime: String, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Oma")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val targetUri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

            try {
                resolver.openOutputStream(targetUri)?.use { out ->
                    resolver.openInputStream(sourceUri)?.use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
                targetUri
            } catch (e: Exception) {
                resolver.delete(targetUri, null, null)
                e.printStackTrace()
                null
            }
        } else {
            try {
                val downloads =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val folder = File(downloads, "Oma")
                if (!folder.exists()) folder.mkdirs()
                val outFile = File(folder, fileName)
                resolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                        fos.flush()
                    }
                }
                outFile.toUri()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun saveVideoToGallery(videoFile: File, filename: String, context: Context): Boolean {
        try {

            val filename = filename
            val mimeType = "video/mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.TITLE, filename)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Oma/Video")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uriSavedVideo = context.contentResolver.insert(collection, values) ?: return false
            val pfd: ParcelFileDescriptor =
                context.contentResolver.openFileDescriptor(uriSavedVideo, "w")!!
            val out = FileOutputStream(pfd.fileDescriptor)


            val inf = FileInputStream(videoFile);
            val buf = ByteArray(8192)
            var len: Int
            while ((inf.read(buf).also { len = it }) > 0) {
                out.write(buf, 0, len)
            }
            out.close()
            inf.close()
            pfd.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uriSavedVideo, values, null, null)
            } else {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = uriSavedVideo
                    }
                )
            }

//            inputStream.close()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false

        }
    }


    fun saveImageToGallery(uri: Uri, context: Context): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false

            val filename = "IMG_" + System.currentTimeMillis() + ".jpg"
            val mimeType = "image/jpeg"

            val values = ContentValues().apply {
                put(Images.Media.DISPLAY_NAME, filename)
                put(Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                Images.Media.EXTERNAL_CONTENT_URI
            }

            val newImageUri = context.contentResolver.insert(collection, values) ?: return false

            context.contentResolver.openOutputStream(newImageUri).use { outputStream ->
                inputStream.copyTo(outputStream!!)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(Images.Media.IS_PENDING, 0)
                context.contentResolver.update(newImageUri, values, null, null)
            } else {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = newImageUri
                    }
                )
            }

            inputStream.close()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun loadGalleryImages(context: Context): List<Uri> {
        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATE_ADDED
        )

        val sortOrder = "${Images.Media.DATE_ADDED} DESC"

        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            Images.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(queryUri, projection, null, null, sortOrder).use { cursor ->
            val idColumn = cursor?.getColumnIndexOrThrow(Images.Media._ID) ?: return emptyList()
            val imageUris = mutableListOf<Uri>()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(contentUri)
            }
            return imageUris
        }
    }


    fun uriToFile(context: Context, uri: Uri, fileName: String? = null): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            val name = fileName ?: run {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) it.getString(index) else "temp_file"
                    } else {
                        "temp_file"
                    }
                } ?: "temp_file"
            }

            val file = File(context.cacheDir, name)
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                outputStream.flush()
            }
            inputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listOutputDevices(context: Context): List<AudioDeviceInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }.toList()
    }

    fun getDeviceByType(context: Context, type: Int): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == type }
    }

    fun changeOutputDevice(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val cd = am.communicationDevice
        if (cd != null) {
            println("Current AM: ${cd.type}")
        }
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> println("Наушники (проводные)")
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> println("Наушники (проводные, без микрофона)")
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> println("Bluetooth (A2DP)")
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> println("Bluetooth (SCO, гарнитура)")
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> println("Встроенный динамик (у уха)")
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> println("Встроенный спикер (громкая связь)")
                AudioDeviceInfo.TYPE_USB_HEADSET -> println("USB-наушники")
                AudioDeviceInfo.TYPE_USB_DEVICE -> println("USB-устройство")
                // и другие типы, если нужно
                else -> println("Неизвестное устройство вывода: ${device.type}")
            }
        }
        var newDevice = 1
        loop@ for (i in SessionManager.oldOutputType + 1..30) {
            devices.forEach {
                if (it.type == i && it.type != 18 && it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    newDevice = it.type
                    break@loop
                }
            }
        }
        println("Current: $newDevice")

        return newDevice
    }

    fun isServiceForegrounded(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }


    fun saveLogcatToFile(context: Context, filename: String = "logcat.txt") {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // Запросить разрешение, если нужно
            println("Нет разрешения на запись")
            return
        }

        try {
            val logFile = File(
                context.getExternalFilesDir(null),
                filename
            ) // Используем.getExternalFilesDir для Android 10+

            val process =
                Runtime.getRuntime().exec("logcat -d") // -d — вывести текущий буфер и выйти
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            val writer = FileWriter(logFile)
            var line: String?

            while (bufferedReader.readLine().also { line = it } != null) {
                writer.write(line + "\n")
            }

            writer.close()
            bufferedReader.close()

            println("Лог сохранен в: ${logFile.absolutePath}")

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun makeReadURL(
        relay: String,
        spaceID: String,
        channelID: String,
        userID: String,
        streamID: String
    ): String {
        val base = URI(relay)
        val segments = listOf(spaceID, channelID, userID, streamID, "whep")
        val path = joinPaths(base.path ?: "", segments)
        val userParam =
            URLEncoder.encode(SessionManager.currentUser!!.id, StandardCharsets.UTF_8.name())
        val newUri = URI(base.scheme, base.authority, path, "user=$userParam", null)

        return newUri.toString()
    }

    fun makeWriteURL(
        relay: String,
        spaceID: String,
        channelID: String,
        userID: String,
        streamID: String
    ): String {
        val base = URI(relay)
        val segments = listOf(spaceID, channelID, userID, streamID, "whip")
        val path = joinPaths(base.path ?: "", segments)
        val userParam =
            URLEncoder.encode(SessionManager.currentUser!!.id, StandardCharsets.UTF_8.name())
        val newUri = URI(base.scheme, base.authority, path, "user=$userParam", null)

        return newUri.toString()
    }


    private fun joinPaths(basePath: String, segments: List<String>): String {
        val base =
            if (basePath.isEmpty()) "/" else if (basePath.startsWith("/")) basePath else "/$basePath"
        val prefix = if (base.endsWith("/")) base else "$base/"
        val encodedSegments = segments.joinToString("/") { encodePathSegment(it) }
        return prefix + encodedSegments
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}