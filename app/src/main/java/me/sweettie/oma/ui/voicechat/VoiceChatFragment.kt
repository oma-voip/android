package me.sweettie.oma.ui.voicechat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.databinding.FragmentVoiceChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.network.services.voip.VoipForegroundService
import me.sweettie.oma.ui.chat.ChatFragmentArgs
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.setOposit
import java.util.UUID
import java.util.concurrent.TimeUnit


class VoiceChatFragment : Fragment() {

    private var _binding: FragmentVoiceChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val args: ChatFragmentArgs by navArgs()

    @SuppressLint("SetTextI18n")
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

        binding.channelName.text =
            "voice \"${SessionManager.selectedServer!!.hashChannels[args.channelId]!!.name}\""
        renderVoiceMembers()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
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
//            Toast.makeText(context, "Временно недоступно :(", Toast.LENGTH_LONG).show()
//            return@setOnClickListener
            //TODO: планирует разблокировка в следующих версиях

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
            } else
                startVoiceChat()
        }

        binding.btnHangup.setOnClickListener {

            changeBottomState()
            SessionManager.startCallTime = -1L
            val stopIntent = Intent(requireContext(), VoipForegroundService::class.java).apply {
                action = VoipForegroundService.ACTION_STOP
            }
            requireContext().startService(stopIntent)
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

    fun startVoiceChat() {
        lifecycleScope.launch {
            GrpcClient.getVoiceRequest(args.serverId, args.channelId)
        }

        changeBottomState()
        SessionManager.startCallTime = System.currentTimeMillis()
        startCallTimer()
    }

    @SuppressLint("SetTextI18n")
    private fun startCallTimer() {
        lifecycleScope.launch {
            while (SessionManager.startCallTime != -1L) {
                val now = System.currentTimeMillis()
                val elapsedMillis = now - SessionManager.startCallTime
                if (elapsedMillis < 0) {
                    binding.tvCallTime.text = "00:00"
                } else {
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
                }
            }.start()
        else {
            binding.btnMute.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnMute.visibility = View.GONE
            }.start()
            binding.btnHangup.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnHangup.visibility = View.GONE
            }.start()
            binding.btnSpeaker.animate().alpha(0f).setDuration(200).withEndAction {
                binding.btnSpeaker.visibility = View.GONE
            }.start()
            binding.tvCallTime.animate().alpha(0f).setDuration(200).withEndAction {
                binding.tvCallTime.visibility = View.GONE
                binding.btnJoinCall.animate().alpha(1f).setDuration(200).withStartAction {
                    binding.btnJoinCall.visibility = View.VISIBLE
                }.start()
            }.start()
        }

    }

    private fun renderVoiceMembers() {
        val cm = SessionManager.channelMembers.getOrDefault(args.channelId, ArrayList())
        val inflater = LayoutInflater.from(context)
        binding.voiceGrid.removeAllViews()
        cm.forEach {
            val cv = inflater.inflate(R.layout.item_voicemember, binding.voiceGrid, false)

            val un = cv.findViewById<TextView>(R.id.tvVoiceChatMemberName)
            if (SessionManager.selectedServer!!.getMemberById(it.id) != null) {
                un.text =
                    SessionManager.selectedServer!!.getMemberById(it.id)!!.username
            } else {
                Utils.getCacheUser(it.id) { loadedUser ->
                    un.post {
                        if (loadedUser != null)
                            un.text = loadedUser.username
                    }
                }
            }

            binding.voiceGrid.addView(cv)
        }
    }
}