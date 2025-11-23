package me.sweettie.oma.network.whip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WHIPPublisher(
    private val context: Context,
    private val whipUrl: String,
    private val authToken: String? = null
) {

    private val TAG = "WhipScreenStreamer"
    private val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null

    @Volatile
    private var resourceUrl: String? = null

    // Whether the answer from server contained ICE candidates
    private val remoteHasCandidates = AtomicBoolean(false)

    // Buffer local candidates until we decide to send them
    private val candidateBuffer = mutableListOf<IceCandidate>()

    private val executor = Executors.newSingleThreadExecutor()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.Q)
    fun initialize() {
        val initOptions =
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)


    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun startScreenShare(
        resultCode: Int,
        data: Intent,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 10
    ) {
        executor.execute {

            val pm =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection: MediaProjection? = try {
                pm.getMediaProjection(resultCode, data)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }


            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)


            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                .let { if (it > 0) it else sampleRate * 2 }



            factory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(
                    JavaAudioDeviceModule.builder(context)
                        .setUseHardwareNoiseSuppressor(false)
                        .setUseHardwareAcousticEchoCanceler(false)
                        .createAudioDeviceModule()
                )
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            if (factory == null) {
                println("Call initialize() first")
                return@execute
            }

            createPeerConnectionIfNeeded()

            surfaceTextureHelper =
                SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
            videoSource = factory!!.createVideoSource(true)

            screenCapturer =
                ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                    override fun onStop() {
                        println("MediaProjection stopped")
                        stop()
                    }
                })

            screenCapturer!!.initialize(
                surfaceTextureHelper,
                context.applicationContext,
                videoSource!!.capturerObserver
            )
            screenCapturer!!.startCapture((width * 1).toInt(), (height * 1).toInt(), 60)
            videoTrack = factory!!.createVideoTrack("ARDAMSv0", videoSource)
            videoTrack!!.setEnabled(true)


//            val audioSource = factory!!.createAudioSource(MediaConstraints())
//            val audioTrack = factory!!.createAudioTrack("ARDAMSa0", audioSource)
//            audioTrack?.setEnabled(true)
//            peerConnection!!.addTrack(audioTrack, listOf("audio"))
            peerConnection!!.addTrack(videoTrack, listOf("screen"))

            val sender = peerConnection!!.senders.find { it.track()?.kind() == "video" }
            sender?.let { s ->
                val params = s.parameters
                // Устанавливаем желаемые значения
                params.encodings.forEach { enc ->
                    enc.maxBitrateBps = 4_000_000 // 4 Mbps
                    enc.maxFramerate = 60
                    enc.scaleResolutionDownBy = 1.0 // 1.0 = оригинальное разрешение
                    enc.active = true
                }
                s.parameters = params
            }

            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            peerConnection!!.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    peerConnection!!.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // send offer to WHIP server (HTTP POST)
                            executor.execute { sendOfferToWhip(desc) }
                        }

                        override fun onSetFailure(p0: String?) {
                            println("setLocalDescription failed: $p0")
                        }

                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, desc)
                }

                override fun onCreateFailure(error: String?) {
                    println("createOffer failed: $error")
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, mediaConstraints)
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createAudioRecordOnQOrHigher(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSizeInBytes: Int,
        mediaProjection: MediaProjection
    ): AudioRecord {
        val audioConfig: AudioPlaybackCaptureConfiguration =
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        val audioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setAudioPlaybackCaptureConfig(audioConfig)
            .build()
        return audioRecord
    }

    private fun createPeerConnectionIfNeeded() {
        if (peerConnection != null) return

        // IMPORTANT: do not pass STUN/TURN servers — server is public and provides routing.
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection =
            factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                    println("Signaling: $newState")
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    println("IceConnection: $newState")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                    println("IceGathering: $newState")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate == null) return
                    // decide whether to send candidate: if remote provided candidates, we can skip sending
                    if (remoteHasCandidates.get()) {
                        // remote provided candidates - usually no need to send ours
                        println("Remote already provided candidates; skipping sending local candidate")
                        return
                    }
                    // if resource URL not known yet, buffer
                    val res = resourceUrl
                    if (res == null) {
                        synchronized(candidateBuffer) { candidateBuffer.add(candidate) }
                        println("Buffered local candidate (resource not known yet)")
                    } else {
                        // send immediately via PATCH
                        sendLocalCandidate(candidate)
                    }
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {
                    println("Renegotiation needed")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            })
    }


    private fun sendOfferToWhip(localDesc: SessionDescription) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(whipUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/sdp")
                if (!authToken.isNullOrEmpty()) setRequestProperty(
                    "Authorization",
                    "Bearer $authToken"
                )
            }

            conn.outputStream.use { os ->
                val bytes = localDesc.description.toByteArray(Charsets.UTF_8)
                os.write(bytes)
                os.flush()
            }

            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            if (loc != null) {
                resourceUrl = loc
                println("WHIP resource location: $loc")
            } else {
                println("WHIP server did not return Location header")
            }

            val responseBody = readResponseSafely(conn)
            if (!responseBody.isNullOrBlank()) {
                // server returned answer SDP in body
                // check whether answer contains a=candidate
                val hasCandidates =
                    responseBody.contains("\na=candidate") || responseBody.contains("\r\na=candidate")
                remoteHasCandidates.set(hasCandidates)
                if (hasCandidates) println("Server answer contains ICE candidates; we will not send local candidates.")
                val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, responseBody)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        println("Remote description set (answer)")
                        // if server didn't include candidates but resource exists -> send buffered candidates
                        flushCandidateBufferIfNeeded()
                    }

                    override fun onSetFailure(p0: String?) {
                        println("setRemoteDescription failed: $p0")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, answerDesc)
            } else {
                // no body: try GET on resourceUrl (if present)
                resourceUrl?.let {
                    fetchAnswerFromResource(it)
                } ?: run {
                    // No answer body and no resource: we'll wait — candidates will be buffered.
                    println("No answer body and no resource URL; candidates will be buffered until resource becomes available.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to POST offer to WHIP")
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchAnswerFromResource(resource: String) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(resource)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                if (!authToken.isNullOrEmpty()) setRequestProperty(
                    "Authorization",
                    "Bearer $authToken"
                )
            }
            val code = conn.responseCode
            val body = readResponseSafely(conn)
            if (!body.isNullOrBlank()) {
                val hasCandidates =
                    body.contains("\na=candidate") || body.contains("\r\na=candidate")
                remoteHasCandidates.set(hasCandidates)
                if (hasCandidates) println("Server GET answer contains ICE candidates; we will not send local candidates.")
                val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, body)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        println("Remote description set (GET answer)")
                        flushCandidateBufferIfNeeded()
                    }

                    override fun onSetFailure(p0: String?) {
                        println("setRemoteDescription failed: $p0")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, answerDesc)
            } else {
                println("GET on resource returned empty body")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to GET answer from WHIP resource")
        } finally {
            conn?.disconnect()
        }
    }

    private fun flushCandidateBufferIfNeeded() {
        // If remote provided candidates, skip sending local candidates.
        if (remoteHasCandidates.get()) {
            synchronized(candidateBuffer) { candidateBuffer.clear() }
            return
        }
        val res = resourceUrl ?: return
        val toSend = synchronized(candidateBuffer) {
            val copy = candidateBuffer.toList()
            candidateBuffer.clear()
            copy
        }
        for (c in toSend) {
            sendLocalCandidate(c)
        }
    }

    private fun sendLocalCandidate(candidate: IceCandidate) {
        val res = resourceUrl ?: run {
            println("Resource URL is null, cannot PATCH candidate yet")
            return
        }

        val frag = "a=${candidate.sdp}\r\n"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(res)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                try {
                    requestMethod = "PATCH"
                } catch (pe: ProtocolException) {
                    requestMethod = "POST"
                    setRequestProperty("X-HTTP-Method-Override", "PATCH")
                }
                setRequestProperty("Content-Type", "application/trickle-ice-sdpfrag")
                if (!authToken.isNullOrEmpty()) setRequestProperty(
                    "Authorization",
                    "Bearer $authToken"
                )
            }

            conn.outputStream.use { os ->
                os.write(frag.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                println("PATCH candidate failed: $code ${conn.responseMessage}")
            } else {
                println("PATCH candidate ok")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to PATCH candidate")
        } finally {
            conn?.disconnect()
        }
    }

    private fun deleteResource(resource: String) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(resource)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "DELETE"
                if (!authToken.isNullOrEmpty()) setRequestProperty(
                    "Authorization",
                    "Bearer $authToken"
                )
            }
            val code = conn.responseCode
            println("WHIP DELETE returned $code")
        } catch (e: Exception) {
            e.printStackTrace()
            println("WHIP DELETE failed")
        } finally {
            conn?.disconnect()
        }
    }

    private fun readResponseSafely(conn: HttpURLConnection): String? {
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to read response body")
            null
        }
    }

    // ----------------- Lifecycle -----------------

    fun stop() {
        executor.execute {
            try {
                screenCapturer?.stopCapture()
            } catch (e: Exception) { /* ignore */
            }
            videoTrack?.setEnabled(false)
            videoTrack = null
            videoSource?.dispose()
            videoSource = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            peerConnection?.close()
            peerConnection = null

            resourceUrl?.let { url ->
                // request DELETE on resource if server supports it
                executor.execute { deleteResource(url) }
            }
        }
    }

    fun release() {
        stop()
        factory?.dispose()
        factory = null
        eglBase.release()
        executor.shutdown()
    }
}