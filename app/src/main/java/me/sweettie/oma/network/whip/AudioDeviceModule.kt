package me.sweettie.oma.network.whip

import android.media.projection.MediaProjection
import org.webrtc.audio.AudioDeviceModule


interface AudioDeviceModule : AudioDeviceModule {

    fun setMediaProjection(mediaProjection: MediaProjection?)
}