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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.databinding.FragmentChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.ui.progress.ProgressDialogFragment
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
    private lateinit var pd: ProgressDialogFragment


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pd = ProgressDialogFragment()
        pd.show(parentFragmentManager, "progress_dialog")
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

    private var updateChat = false
    private fun onLoadMessage() {
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = false
        binding.listMessages.layoutManager = layoutManager
        binding.listMessages.adapter =
            MessageListAdapter(
                requireActivity(),
                viewLifecycleOwner.lifecycleScope,
                onMessageClick = {
                println("clicked on message!")
            })
        (binding.listMessages.adapter as MessageListAdapter).updateData(ml).apply {
            pd.dismiss()
            binding.listMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val firstVisibleItemPosition =
                        layoutManager.findLastCompletelyVisibleItemPosition()
                    println(firstVisibleItemPosition)
                    if (firstVisibleItemPosition == 0 && !updateChat) {
                        updateChat = true
                        pd.show(parentFragmentManager, "progress_dialog")
                        lifecycleScope.launch {
                            GrpcClient.getMessages(
                                args.serverId,
                                args.channelId,
                                SessionManager.cachedMessageList[args.channelId]!!.messageLis.size.toLong(),
                                onSuccess = { it ->
                                    ml.addAll(it.messagesList)
                                    SessionManager.cachedMessageList[args.channelId] = ml
                                    (binding.listMessages.adapter as MessageListAdapter).updateData(
                                        ml
                                    )
                                    pd.dismiss()
                                    updateChat = false
//                                    binding.listMessages.post {
//                                        binding.listMessages.smoothScrollToPosition(oldSize)
//                                    }
                                },
                                onError = {
                                    updateChat = false
                                    pd.dismiss()
                                })
                        }
                    }
                }
            })
        }

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
                    }
                    else -> Unit
                }
            }
        }

    }

}