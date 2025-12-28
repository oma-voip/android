package me.sweettie.oma.utils

import android.content.Context
import android.net.Uri
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proto.OmaOuterClass
import java.io.File
import java.io.FileOutputStream
import java.net.URI

class AttachmentCacheManager(
    private val context: Context,
    private val downloadFunc: suspend (attachId: String) -> Iterator<OmaOuterClass.FileChunk>
) {


    private val cacheDir by lazy {
        File(context.cacheDir, "attachments_cache").apply { if (!exists()) mkdirs() }
    }

    fun attachmentIsCached(attach: OmaOuterClass.Attachment): Boolean {
        val extension = attach.filename.substringAfterLast('.', "")
        val fileName = if (extension.isNotEmpty()) {
            "${attach.id}.$extension"
        } else {
            attach.id
        }
        val file = File(cacheDir, fileName)
        return file.exists()
    }

    fun attachmentIsCachedExtra(attachId: String): Boolean {
        val fileName = attachId
        val file = File(cacheDir, fileName)
        return file.exists()
    }

    fun deletePhoto(uri: Uri): Boolean {
        val file = File(URI(uri.path))
        if (!file.exists())
            return true
        return file.delete()
    }
    suspend fun getFile(attach: OmaOuterClass.Attachment): File? {
        val extension = attach.filename.substringAfterLast('.', "")
        val fileName = if (extension.isNotEmpty()) {
            "${attach.id}.$extension"
        } else {
            attach.id
        }
        val file = File(cacheDir, fileName)
        if (file.exists()) {
            return file
        }

        val iterator = try {
            downloadFunc(attach.id)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return saveFileFromChunks(fileName, iterator)
    }

    suspend fun getFileById(attachId: String): File? {
        val fileName = attachId
        val file = File(cacheDir, fileName)
        if (file.exists()) {
            return file
        }

        val iterator = try {
            downloadFunc(attachId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return saveFileFromChunks(fileName, iterator)
    }

    private suspend fun saveFileFromChunks(
        fileName: String,
        chunksIterator: Iterator<OmaOuterClass.FileChunk>
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, fileName)
                FileOutputStream(file).use { fos ->
                    while (chunksIterator.hasNext()) {
                        val chunk = chunksIterator.next()
                        val data: ByteString = chunk.content
                        fos.write(data.toByteArray())
                    }
                    fos.flush()
                }
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}