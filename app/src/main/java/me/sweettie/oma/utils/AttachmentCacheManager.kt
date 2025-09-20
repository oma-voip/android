package me.sweettie.oma.utils

import android.content.Context
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proto.OmaOuterClass
import java.io.File
import java.io.FileOutputStream

class AttachmentCacheManager(
    private val context: Context,
    private val downloadFunc: suspend (attachId: String) -> Iterator<OmaOuterClass.FileChunk>
) {

    private val cacheDir by lazy {
        File(context.cacheDir, "attachments_cache").apply { if (!exists()) mkdirs() }
    }

    suspend fun getFile(attach: OmaOuterClass.Attachment): File? {
        val file = File(cacheDir, attach.filename)
        if (file.exists()) {
            return file
        }

        val iterator = try {
            downloadFunc(attach.previewId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return saveFileFromChunks(attach, iterator)
    }

    private suspend fun saveFileFromChunks(
        attach: OmaOuterClass.Attachment,
        chunksIterator: Iterator<OmaOuterClass.FileChunk>
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, attach.filename)
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