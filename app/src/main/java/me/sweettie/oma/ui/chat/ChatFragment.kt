package me.sweettie.oma.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.databinding.FragmentChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.clear
import me.sweettie.oma.utils.getEndText
import me.sweettie.oma.utils.isPresent

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val args: ChatFragmentArgs by navArgs()
    private lateinit var ml: MessageList

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.sendBtn.isEnabled = false
        val channelId = args.channelId
        val serverId = args.serverId

        binding.channelName.text =
            SessionManager.getServerBySpaceId(args.serverId)!!.hashChannels[args.channelId]!!.name
        binding.backBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            navController.popBackStack()
        }

        binding.optionBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            val action = ChatFragmentDirections.actionChatToMembers(
                channelId = channelId,
                serverId = serverId
            )
            navController.navigate(action)
        }

        SessionManager.selectedChannel = args.channelId

        if (SessionManager.cachedMessageList.containsKey(channelId)) {
            ml = SessionManager.cachedMessageList[channelId]!!
            onLoadMessage()
        } else {
            lifecycleScope.launch {
                GrpcClient.getMessages(serverId, channelId, 0,
                    onSuccess = {
                        ml = MessageList(serverId, channelId, it)
                        SessionManager.cachedMessageList[channelId] = ml
                        onLoadMessage()
                    }, onError = {

                    })
            }
        }

    }

    private lateinit var viewModel: MainViewModel


    private fun onLoadMessage() {
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = false
        binding.listMessages.layoutManager = layoutManager
        binding.listMessages.adapter =
            MessageListAdapter(viewLifecycleOwner.lifecycleScope, onMessageClick = {
                println("clicked on message!")
            })
        (binding.listMessages.adapter as MessageListAdapter).updateData(ml)
        binding.sendBtn.isEnabled = true

        binding.sendBtn.setOnClickListener {
            if (!binding.messageBox.isPresent())
                return@setOnClickListener
            val message = binding.messageBox.getEndText()
            lifecycleScope.launch {
                GrpcClient.sendMessage(args.serverId, args.channelId, message)
                binding.messageBox.clear()
            }
        }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]


        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MainViewModel.UiState.NewMessage -> {
                        if (state.message.channelId.equals(args.channelId) && state.message.spaceId.equals(
                                args.serverId
                            )
                        ) {
                            ml.addMessage(state.message.message)
                            (binding.listMessages.adapter as MessageListAdapter).updateData(ml)
                            binding.listMessages.post {
                                binding.listMessages.smoothScrollToPosition(ml.stackedMessageList.size)
                            }
                        }
//                        Toast.makeText(context, "📩 Новое сообщение: ${state.message.text}", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        }

    }

}