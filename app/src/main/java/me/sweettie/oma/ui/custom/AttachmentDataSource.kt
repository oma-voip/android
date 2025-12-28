package me.sweettie.oma.ui.custom

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import proto.OmaOuterClass
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@UnstableApi
class AttachmentDataSource(
    private val attachId: String,
    private val downloadFunc: suspend (String) -> Iterator<OmaOuterClass.FileChunk>
) : DataSource {

    private val transferListeners = CopyOnWriteArrayList<TransferListener>()

    private var uri: Uri? = null
    private var dataSpec: DataSpec? = null

    @Volatile
    private var opened = false

    // Итератор и текущий чанк
    private var iterator: Iterator<OmaOuterClass.FileChunk>? = null
    private var currentChunk: ByteArray? = null
    private var chunkPos = 0
    private var totalBytesRead: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.uri = dataSpec.uri

        transferListeners.forEach { it.onTransferInitializing(this, dataSpec, false) }

        // Получаем iterator синхронно
        try {
            iterator = try {
                runBlocking { downloadFunc(attachId) }
            } catch (e: Exception) {
                throw IOException("Failed to obtain attachment iterator", e)
            }
        } catch (e: IOException) {
            throw e
        }

        // начальные значения
        currentChunk = null
        chunkPos = 0
        totalBytesRead = 0L
        opened = true

        // уведомляем о старте
        transferListeners.forEach { it.onTransferStart(this, dataSpec, false) }

        // Если ExoPlayer запросил позицию (seek), пропускаем нужное количество байт
        val startPosition = dataSpec.position
        if (startPosition > 0) {
            skipToPosition(startPosition)
        }

        // Длина неизвестна (стрим)
        return C.LENGTH_UNSET.toLong()
    }

    private fun skipToPosition(position: Long) {
        var toSkip = position
        val tmp = ByteArray(64 * 1024)
        while (toSkip > 0) {
            val read = read(tmp, 0, minOf(tmp.size, toSkip.toInt()))
            if (read <= 0) break
            toSkip -= read.toLong()
        }
        // если не удалось пропустить до нужной позиции — просто вернемся, ExoPlayer может не поддержать seek
    }


    override fun getUri(): Uri? = uri


    override fun close() {
        if (!opened) return
        opened = false
        // освобождаем ресурсы
        iterator = null
        currentChunk = null
        chunkPos = 0
        dataSpec?.let {
            transferListeners.forEach { tl -> tl.onTransferEnd(this, it, /* isNetwork = */ false) }
        }
        dataSpec = null
        uri = null
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) throw IOException("AttachmentDataSource is not opened")

        if (length == 0) return 0

        var bytesCopiedTotal = 0

        while (bytesCopiedTotal == 0) {
            val chunk = currentChunk
            if (chunk != null && chunkPos < chunk.size) {
                val toCopy = minOf(length - 0, chunk.size - chunkPos)
                System.arraycopy(chunk, chunkPos, buffer, offset + 0, toCopy)
                chunkPos += toCopy
                bytesCopiedTotal += toCopy
                totalBytesRead += toCopy
                val ds = dataSpec ?: throw IOException("DataSpec is null")
                transferListeners.forEach { it.onBytesTransferred(this, ds, false, toCopy) }
                if (chunkPos >= chunk.size) {
                    currentChunk = null
                    chunkPos = 0
                }
                if (bytesCopiedTotal > 0) return bytesCopiedTotal
            } else {
                val it = iterator ?: return -1
                val hasNext = try {
                    it.hasNext()
                } catch (e: Exception) {
                    throw IOException("Error while checking iterator.hasNext()", e)
                }
                if (!hasNext) {
                    return -1
                }
                val nextChunk = try {
                    it.next().content.toByteArray()
                } catch (e: Exception) {
                    throw IOException("Error while reading next chunk", e)
                }
                if (nextChunk.isEmpty()) {
                    continue
                }
                currentChunk = nextChunk
                chunkPos = 0
            }
        }
        return bytesCopiedTotal
    }


}


@UnstableApi
class AttachmentDataSourceFactory(
    private val attachId: String,
    private val downloadFunc: suspend (String) -> Iterator<OmaOuterClass.FileChunk>
) : DataSource.Factory {

    private val transferListeners = CopyOnWriteArrayList<TransferListener>()
    override fun createDataSource(): DataSource {
        val ds = AttachmentDataSource(attachId, downloadFunc)
        // передаём слушателей в новый DataSource
        transferListeners.forEach { ds.addTransferListener(it) }
        return ds
    }

    fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
    }
}