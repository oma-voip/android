package me.sweettie.oma.ui.photoview

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.SimpleExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import proto.OmaOuterClass

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    var player: SimpleExoPlayer? = null

    // какой media сейчас установлен в плеере (attachId)
    var currentAttachId: String? = null
    var lastPosition: Long = 0L
    var playWhenReady: Boolean = true

    fun getOrCreatePlayer(): SimpleExoPlayer {
        if (player == null) {
            player = SimpleExoPlayer.Builder(getApplication()).build()
        }
        return player!!
    }

    // Установить media только если другой attachId
    fun prepareIfNeeded(
        attachId: String,
        downloadFunc: suspend (String) -> Iterator<OmaOuterClass.FileChunk>
    ) {
        val p = getOrCreatePlayer()
        if (currentAttachId == attachId && p.mediaItemCount > 0) {
            // уже подготовлено — восстанавливаем позицию
            p.seekTo(lastPosition)
            p.playWhenReady = playWhenReady
            return
        }
        currentAttachId = attachId
        val isLoaded =
            SessionManager.cacheManager.attachmentIsCachedExtra(currentAttachId!!)
        println("VIDEO WITH ID ${currentAttachId!!} IS DOWNLOADED: $isLoaded")
        if (isLoaded) {

            viewModelScope.launch(Dispatchers.Main) {
                val file =
                    SessionManager.cacheManager.getFileById(currentAttachId!!)

                val mediaItem = MediaItem.fromUri(file!!.toUri())
                val dataSourceFactory = DefaultDataSource.Factory(getApplication())
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
                p.setMediaSource(mediaSource)
                p.prepare()
                p.seekTo(lastPosition)
                p.playWhenReady = playWhenReady
            }
        } else {
            viewModelScope.launch(Dispatchers.Main) {

                val videoLink = GrpcClient.getFileLink(attachId)
                if (videoLink == null) {
                    return@launch
                }

                val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("OmaAndroid/${Utils.getVersionCode(getApplication())}")

                val dataSourceFactory = DefaultDataSource.Factory(
                    /* context = */ getApplication(),
                    httpFactory
                )

                val mediaItem = MediaItem.fromUri(videoLink.url)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)


                p.setMediaSource(mediaSource)
                p.prepare()
                p.seekTo(lastPosition)
                p.playWhenReady = playWhenReady
            }
        }
    }

    fun saveStateFromPlayer() {
        player?.let {
            lastPosition = it.currentPosition
            playWhenReady = it.playWhenReady
        }
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}