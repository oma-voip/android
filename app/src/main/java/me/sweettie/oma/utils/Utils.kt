package me.sweettie.oma.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
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
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import proto.OmaOuterClass
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    fun savePhotoIntoGallery(path: String, context: Context) {
        val values = ContentValues()
        values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(Images.Media.MIME_TYPE, "image/jpeg")
        values.put(MediaStore.MediaColumns.DATA, path)
        context.contentResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values)
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
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }

    fun getDeviceByType(context: Context, type: Int): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == type }
    }


}