package me.sweettie.oma.ui.voicechat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.databinding.FragmentVoiceChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.network.services.voip.VoipForegroundService
import me.sweettie.oma.network.whip.ScreenCaptureService
import me.sweettie.oma.ui.chat.ChatFragmentArgs
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.ui.webrtc.WebRtcPlayerActivity
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.Utils.isServiceForegrounded
import me.sweettie.oma.utils.setOposit
import java.util.UUID
import java.util.concurrent.TimeUnit


class VoiceChatFragment : Fragment() {

    private var _binding: FragmentVoiceChatBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val args: ChatFragmentArgs by navArgs()


    @SuppressLint("SetTextI18n", "ServiceCast")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }
        binding.optionBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            val action = VoiceChatFragmentDirections.actionVoicechatToMembers(
                channelId = args.channelId,
                serverId = args.serverId
            )
            navController.navigate(action)
        }

        if (SessionManager.selectedServer!!.space.private) {
            binding.channelName.text = SessionManager.selectedServer!!.space.name
        } else {
            binding.channelName.text =
                "voice \"${SessionManager.selectedServer!!.hashChannels[args.channelId]!!.name}\""
        }
        renderVoiceMembers()

        viewLifecycleOwner.lifecycleScope.launch {
            SessionManager.viewModel.uiState.collect { state ->
                when (state) {
                    is MainViewModel.UiState.ChannelJoined -> {
                        if (SessionManager.selectedServer != null) {
                            if (!state.channel.channelId.equals(args.channelId))
                                return@collect
                            renderVoiceMembers()
                        }
                    }

                    is MainViewModel.UiState.ChannelLeft -> {
                        if (SessionManager.selectedServer != null) {
                            if (!state.channel.channelId.equals(args.channelId))
                                return@collect
                            renderVoiceMembers()
                        }
                    }

                    is MainViewModel.UiState.ThumbNail -> {

                    }

                    is MainViewModel.UiState.VoiceRelayReady -> {
                        println("Try to start activity...")
                        val vrr = state.voiceRelay
                        val connectionUUID = UUID.fromString(vrr.connectionId)
                        val spaceUUID = UUID.fromString(vrr.spaceId)
                        val channelUUID = UUID.fromString(vrr.channelId)

                        val intent =
                            Intent(requireContext(), VoipForegroundService::class.java).apply {
                                action = VoipForegroundService.ACTION_START
                                putExtra(VoipForegroundService.EXTRA_ADDRESS, vrr.address)
                                putExtra(
                                    VoipForegroundService.EXTRA_CONNECTION_ID,
                                    connectionUUID.toString()
                                )
                                putExtra(VoipForegroundService.EXTRA_SPACE_ID, spaceUUID.toString())
                                putExtra(
                                    VoipForegroundService.EXTRA_CHANNEL_ID,
                                    channelUUID.toString()
                                )
                                putExtra(
                                    VoipForegroundService.EXTRA_KEY_BYTES,
                                    vrr.key.toByteArray()
                                )
                            }
                        ContextCompat.startForegroundService(requireContext(), intent)
                    }

                    else -> Unit
                }
            }
        }

        binding.btnJoinCall.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                this@VoiceChatFragment.requestPermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                    ), 123
                )
            } else {
                if (SessionManager.startCallTime > 0) {
                    SessionManager.startCallTime = -1L
                    val stopIntent =
                        Intent(requireContext(), VoipForegroundService::class.java).apply {
                            action = VoipForegroundService.ACTION_STOP
                        }
                    requireContext().startService(stopIntent)
                }
                startVoiceChat()
            }
        }

//        SessionManager.iInternetReconnect = object : IInternetReconnect {
//            override fun reconnect(network: Network) {
//                lifecycleScope.launch {
//
//                    if (checkIfInVoice()) {
//                        val stopIntent =
//                            Intent(requireContext(), VoipForegroundService::class.java).apply {
//                                action = VoipForegroundService.ACTION_STOP
//                            }
//                        requireContext().startService(stopIntent)
//                    }
//                    startVoiceChat()
//                }
//            }
//        }

        binding.btnHangup.setOnClickListener {
            val isRunning =
                isServiceForegrounded(requireContext(), VoipForegroundService::class.java)
            changeBottomState()
            SessionManager.startCallTime = -1L
            if (isRunning) {
                val stopIntent = Intent(requireContext(), VoipForegroundService::class.java).apply {
                    action = VoipForegroundService.ACTION_STOP
                }
                requireContext().startService(stopIntent)

                val stopIntentScreenCapture =
                    Intent(requireContext(), ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_STOP
                    }
                requireContext().startService(stopIntentScreenCapture)
            }

        }

        if (SessionManager.muted) {
            binding.btnMute.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_mic
                )
            )
            lifecycleScope.launch {
                GrpcClient.setMuted(args.serverId, args.channelId, SessionManager.muted)
            }
        } else {
            binding.btnMute.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_mic_off
                )
            )
        }

        binding.btnMute.setOnClickListener {
            SessionManager.muted = !SessionManager.muted
            if (SessionManager.muted)
                binding.btnMute.setImageDrawable(
                    AppCompatResources.getDrawable(
                        requireContext(),
                        R.drawable.ic_mic
                    )
                )
            else
                binding.btnMute.setImageDrawable(
                    AppCompatResources.getDrawable(
                        requireContext(),
                        R.drawable.ic_mic_off
                    )
                )
            lifecycleScope.launch {
                GrpcClient.setMuted(args.serverId, args.channelId, SessionManager.muted)
            }
        }

        binding.btnSpeaker.setOnClickListener {
            val v = if (SessionManager.oldOutputType == 1) 2 else 1
            Utils.listOutputDevices(requireContext()).forEach {
                println("${it.id} | ${it.productName} | ${it.type}")
            }
            val intent = Intent(context, VoipForegroundService::class.java).apply {
                action = VoipForegroundService.ACTION_CHANGE_OUTPUT
                putExtra(VoipForegroundService.EXTRA_OUTPUT_TYPE, v)
                SessionManager.oldOutputType = v
            }
            requireContext().startService(intent)
        }

        binding.btnScreenShare.setOnClickListener {
            if (!checkIfStreaming()) {
//                onStartStreamClicked()
//                onStartCameraStreamClicked()

                val popup = PopupMenu(requireContext(), it)
                popup.menu.add(0, 1, 0, "Screen Share")
                popup.menu.add(0, 2, 1, "Camera Share")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            onStartStreamClicked()       // ваш метод для ScreenShare
                            true
                        }

                        2 -> {
                            onStartCameraStreamClicked() // ваш метод для CameraShare
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            } else {
                onStopStreamClicked()
            }

        }

        val isRunning = isServiceForegrounded(requireContext(), VoipForegroundService::class.java)
        if (isRunning) {
            changeBottomState()
            startCallTimer()
            startSpeakingIndicatorWorker()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceChat()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                val r = GrpcClient.getVideoRelay(args.serverId, args.channelId)
                if (r == null)
                    return@launch
                println("Address: ${r.address}")
                val adr = Utils.makeWriteURL(
                    r.address,
                    args.serverId,
                    args.channelId,
                    SessionManager.currentUser!!.id,
                    UUID.randomUUID().toString()
                )
                println("Nedded address: $adr")
                val intent = Intent(requireContext(), ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, result.data)
                    putExtra(ScreenCaptureService.EXTRA_WHIP_URL, adr)
                    putExtra(
                        ScreenCaptureService.EXTRA_AUTH,
                        SessionManager.currentUser!!.id + ":" + SessionManager.token
                    )
                }
                ContextCompat.startForegroundService(requireContext(), intent)
            }


        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        println("CAMERA GRANTED: $cameraGranted")
        if (cameraGranted) {

            startCameraService()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun onStartCameraStreamClicked() {
        permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun startCameraService() {
        lifecycleScope.launch {
            val r = GrpcClient.getVideoRelay(args.serverId, args.channelId)
            if (r == null)
                return@launch
            val adr = Utils.makeWriteURL(
                r.address,
                args.serverId,
                args.channelId,
                SessionManager.currentUser!!.id,
                UUID.randomUUID().toString()
            )
            val intent = Intent(requireContext(), ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_WHIP_URL, adr)
                putExtra(
                    ScreenCaptureService.EXTRA_AUTH,
                    SessionManager.currentUser!!.id + ":" + SessionManager.token
                )
                putExtra("isCamera", true)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
        }
    }

    fun onStartStreamClicked() {
        val pm =
            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = pm.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)

    }


    fun onStopStreamClicked() {
        val stop = Intent(requireContext(), ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        requireContext().startService(stop)
    }

    fun startVoiceChat() {
        lifecycleScope.launch {
            GrpcClient.getVoiceRequest(args.serverId, args.channelId)
        }

        changeBottomState()
        SessionManager.startCallTime = System.currentTimeMillis()
        startCallTimer()
        startSpeakingIndicatorWorker()
    }

    @SuppressLint("SetTextI18n")
    private fun startCallTimer() {
        var flag = false
        lifecycleScope.launch {
            while (SessionManager.startCallTime != -1L) {

                val now = System.currentTimeMillis()
                val elapsedMillis = now - SessionManager.startCallTime
                if (elapsedMillis < 0) {
                    binding.tvCallTime.text = "00:00"
                } else {
                    val inVoice = checkIfInVoice()
                    if (!inVoice && flag) {
                        changeBottomState()
                        return@launch
                    } else if (inVoice && !flag) {
                        flag = true
                    }
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)
                    binding.tvCallTime.text = timeString
                }
                delay(1000L)
            }
            binding.tvCallTime.text = "00:00"
        }
    }

    private fun changeBottomState() {
        if (binding.btnJoinCall.alpha > 0)
            binding.btnJoinCall.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnJoinCall.apply {
                    setOposit()
                    binding.btnMute.alpha = 0f
                    binding.btnHangup.alpha = 0f
                    binding.tvCallTime.alpha = 0f
                    binding.btnSpeaker.alpha = 0f
                    binding.btnScreenShare.alpha = 0f
                    binding.btnMute.animate().alpha(1f).setDuration(200).withStartAction {
                        binding.btnMute.visibility = View.VISIBLE
                    }.start()
                    binding.btnHangup.animate().alpha(1f).setDuration(200).withStartAction {
                        binding.btnHangup.visibility = View.VISIBLE
                    }.start()
                    binding.tvCallTime.animate().alpha(1f).setDuration(200).withStartAction {
                        binding.tvCallTime.visibility = View.VISIBLE
                    }.start()
                    binding.btnSpeaker.animate().alpha(1f).setDuration(200).withStartAction {
                        binding.btnSpeaker.visibility = View.VISIBLE
                    }.start()
                    binding.btnScreenShare.animate().alpha(1f).setDuration(200).withStartAction {
                        binding.btnScreenShare.visibility = View.VISIBLE
                    }.start()
                }
            }.start()
        else {
            speakingIndicatorJob?.cancel()
            SessionManager.startCallTime = -1L
            speakingIndicatorJob = null
            binding.btnMute.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnMute.visibility = View.GONE
            }.start()
            binding.btnHangup.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnHangup.visibility = View.GONE
            }.start()
            binding.btnSpeaker.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnSpeaker.visibility = View.GONE
            }.start()
            binding.btnScreenShare.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnScreenShare.visibility = View.GONE
            }.start()
            binding.tvCallTime.animate().alpha(0f).setDuration(200).withEndAction {
                binding.tvCallTime.visibility = View.GONE
                binding.btnJoinCall.animate().alpha(1f).setDuration(200).withStartAction {
                    binding.btnJoinCall.visibility = View.VISIBLE
                }.start()
            }.start()
        }

    }

    private val members = HashMap<UUID, View>()
    private fun renderVoiceMembers() {
        val cm = SessionManager.channelMembers.getOrDefault(args.channelId, ArrayList())
        val inflater = LayoutInflater.from(context)
        binding.voiceGrid.removeAllViews()
        members.clear()
        cm.forEach {
            val cv = inflater.inflate(R.layout.item_voicemember, binding.voiceGrid, false)

            val un = cv.findViewById<TextView>(R.id.tvVoiceChatMemberName)
            val member = SessionManager.selectedServer!!.getMemberById(it.id)
            if (member != null) {
                un.text =
                    member.username
                members.put(UUID.fromString(member.id), cv)
            } else {
                Utils.getCacheUser(it.id) { loadedUser ->
                    un.post {
                        if (loadedUser != null) {
                            un.text = loadedUser.username
                            members.put(UUID.fromString(loadedUser.id), cv)
                        }
                    }
                }
            }
            binding.voiceGrid.addView(cv)
        }
    }

    private fun checkIfInVoice(): Boolean {
        return isServiceForegrounded(requireContext(), VoipForegroundService::class.java)
    }

    private fun checkIfStreaming(): Boolean {
        return isServiceForegrounded(requireContext(), ScreenCaptureService::class.java)
    }

    private var speakingIndicatorJob: Job? = null

    private fun startSpeakingIndicatorWorker() {
        speakingIndicatorJob?.cancel() // Остановить предыдущий, если был

        speakingIndicatorJob = lifecycleScope.launch {
            while (isActive) {

                delay(200) // Обновляем каждые 200 мс, можно изменить на 100 или 300

                val newTint = if (checkIfStreaming()) {
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.colorAlizarin
                    )
                } else {
                    ContextCompat.getColor(requireContext(), R.color.white)
                }
                binding.btnScreenShare.setColorFilter(
                    newTint,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )

                members.forEach { (uuid, view) ->
                    val isSpeaking = if (SessionManager.currentUser!!.id.equals(uuid.toString()))
                        VoipForegroundService.AudioFragmentManager.isSelfSpeaking[0]
                    else
                        VoipForegroundService.AudioFragmentManager.audios[uuid]?.isNotEmpty() == true
                    val isMuted = SessionManager.mutedStates.getOrDefault(uuid.toString(), false)

                    val imageView = view.findViewById<ImageView>(R.id.tvVoiceChatMemberLogo)

                    val stream = SessionManager.streamers.getOrDefault(uuid.toString(), null)
                    val thumbnail = SessionManager.thumbnail.getOrDefault(uuid.toString(), null)
                    var needTint = true
                    if (stream == null) {
                        if (isMuted) {
                            imageView.setImageDrawable(
                                AppCompatResources.getDrawable(
                                    requireContext(),
                                    R.drawable.ic_mic_off
                                )
                            )
                        } else {
                            imageView.setImageDrawable(
                                AppCompatResources.getDrawable(
                                    requireContext(),
                                    R.drawable.ic_member
                                )
                            )
                        }
                    } else {
                        if (thumbnail == null || thumbnail.bitmap == null) {
                            imageView.setImageDrawable(
                                AppCompatResources.getDrawable(
                                    requireContext(),
                                    R.drawable.ic_output
                                )
                            )
                        } else {
                            needTint = false
                            imageView.colorFilter = null
                            imageView.imageTintList = null
                            imageView.setImageBitmap(thumbnail.bitmap)
                        }
                        imageView.setOnClickListener {
                            val intent = Intent(context, WebRtcPlayerActivity::class.java)
                            intent.putExtra(WebRtcPlayerActivity.EXTRA_TARGET_URL, stream.url)
                            intent.putExtra(
                                WebRtcPlayerActivity.EXTRA_AUTH,
                                "${SessionManager.currentUser!!.id}:${SessionManager.token}"
                            )
                            intent.putExtra(WebRtcPlayerActivity.EXTRA_ID, uuid.toString())
                            startActivity(intent)
                        }
                    }
                    if (needTint) {
                        val newTint = if (isSpeaking) {
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.colorMoreContrastBackground
                            )
                        } else {
                            ContextCompat.getColor(requireContext(), R.color.white)
                        }
                        imageView.setColorFilter(newTint, android.graphics.PorterDuff.Mode.SRC_IN)
                    }

                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SessionManager.iInternetReconnect = null
    }
}