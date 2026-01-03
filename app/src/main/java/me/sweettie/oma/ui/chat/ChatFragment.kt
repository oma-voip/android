package me.sweettie.oma.ui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.databinding.FragmentChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.ui.progress.ProgressDialogFragment
import me.sweettie.oma.ui.utils.AttachmentBottomSheetFragment
import me.sweettie.oma.utils.AttachmentStorage
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.clear
import me.sweettie.oma.utils.getEndText
import me.sweettie.oma.utils.hideKeyboard
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
    private var bottomSheetDialog: AttachmentBottomSheetFragment? = null


    private val SHEET_TAG = "attachment_bottom_sheet"

    private fun showAttachmentSheet() {
        // если уже показан, просто вернёмся или закроем
        val existing =
            parentFragmentManager.findFragmentByTag(SHEET_TAG) as? AttachmentBottomSheetFragment
        if (existing?.isAdded == true) {
            existing.reCreate()
            return
        }
        val sheet = AttachmentBottomSheetFragment(
            onSlide = { slideOffset -> /* ваш код */ },
            update = { updateAttachmentText() },
            (binding.root.height / 1.75).toInt()
        )
        println("CREATE NEW FRAGMENT")
        sheet.show(parentFragmentManager, SHEET_TAG)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AttachmentStorage.clearSelectedFiles()




        pd = ProgressDialogFragment()
        pd.show(parentFragmentManager, "progress_dialog")
        binding.sendBtn.isEnabled = false
        val channelId = args.channelId
        val serverId = args.serverId
        val server = SessionManager.getServerBySpaceId(args.serverId)
        println("Selected ${server!!.space.name}")
        if (server.space.private) {
            binding.channelName.text = server.space.name
            binding.optionBtn.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_call_start
                )
            )
        } else
            binding.channelName.text =
                SessionManager.getServerBySpaceId(args.serverId)!!.hashChannels[args.channelId]!!.name
        binding.backBtn.setOnClickListener {
            val existing =
                parentFragmentManager.findFragmentByTag(SHEET_TAG) as? AttachmentBottomSheetFragment
            if (existing?.isAdded == true && existing.dialog?.isShowing == true) {
                existing.dismiss()
            } else {
                val navController =
                    (activity as? MainActivity)?.navController ?: return@setOnClickListener
                navController.popBackStack()
            }
        }

        binding.optionBtn.setOnClickListener {
            val navController =
                (activity as? MainActivity)?.navController ?: return@setOnClickListener
            if (server.space.private) {
                navController.navigate(
                    ChatFragmentDirections.actionChatFragmentToVoiceChatFragment(
                        channelId = server.channels[1].id,
                        serverId = server.space.id
                    )
                )
            } else {
                val action = ChatFragmentDirections.actionChatToMembers(
                    channelId = channelId,
                    serverId = serverId
                )
                navController.navigate(action)
            }
        }

        SessionManager.selectedChannel = args.channelId
        val r = SessionManager.unreadedMessages[SessionManager.selectedServer?.space?.id]
        if (r != null) {
            r.remove(args.channelId)
            if (r.isEmpty())
                SessionManager.unreadedMessages.remove(SessionManager.selectedServer!!.space.id)
            else
                SessionManager.unreadedMessages[SessionManager.selectedServer!!.space.id] = r
        }
        if (SessionManager.cachedMessageList.containsKey(channelId)) {
            ml = SessionManager.cachedMessageList[channelId]!!
            onLoadMessage()
        } else {
            lifecycleScope.launch {
                GrpcClient.getMessages(
                    serverId, channelId, 0,
                    onSuccess = {
                        ml = MessageList(serverId, channelId, it)
                        SessionManager.cachedMessageList[channelId] = ml
                        onLoadMessage()
                    }, onError = {

                    })
            }
        }

    }


    private var updateChat = false

    private fun onLoadMessage() {
        if (ml.messageLis.isNotEmpty())
            SecurePrefs.save("last_${args.serverId}_${args.channelId}", ml.messageLis[0].id)

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
                        layoutManager.findFirstVisibleItemPosition()
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
            if (!binding.messageBox.isPresent() && AttachmentStorage.getSelectedFiles().isEmpty())
                return@setOnClickListener
            val message = binding.messageBox.getEndText()
            sendMessageWithAttachemnts(message)
            binding.messageBox.clear()
            AttachmentStorage.clearSelectedFiles()
            updateAttachmentText()
        }

//        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
//            ViewTreeObserver.OnGlobalLayoutListener {
//            override fun onGlobalLayout() {
////                bottomSheetDialog.layoutParams.height =
////                    binding.root.height - binding.actionBar.height + 45
////                binding.bottomSheet.requestLayout()
//
//                bottomSheetDialog.behavior?.peekHeight = (binding.root.height / 1.75).toInt()
//                bottomSheetDialog.binding.bottomSheetItemList.minimumHeight =
//                    bottomSheetDialog.behavior?.peekHeight?.minus(binding.bottomSheetAttachmentType.height)?.minus(45)?:0
//                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
//            }
//        })


        binding.attachmentBtn.setOnClickListener {
            println("Handle attachemtn click")
            binding.messageBox.clearFocus()
            this@ChatFragment.hideKeyboard()
            Handler(Looper.getMainLooper()).postDelayed({
                if (requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        this@ChatFragment.requestPermissions(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.READ_MEDIA_IMAGES
                            ), 123
                        )
                    } else {
                        this@ChatFragment.requestPermissions(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            123
                        )
                    }
                }

                val existing =
                    parentFragmentManager.findFragmentByTag(SHEET_TAG) as? AttachmentBottomSheetFragment
                if (existing?.isAdded == true && existing.dialog?.isShowing == true) {
                    existing.dismiss()
                } else {
                    showAttachmentSheet()
                }
            }, 150)
        }




        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (bottomSheetDialog != null && !bottomSheetDialog!!.isHidden) {
                bottomSheetDialog?.dismiss()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }

        binding.clearFileBtn.setOnClickListener {
            AttachmentStorage.clearSelectedFiles()
            updateAttachmentText()
        }

        job = viewLifecycleOwner.lifecycleScope.launch {
            SessionManager.viewModel.uiState.collect { state ->
                when (state) {
                    is MainViewModel.UiState.NewMessage -> {
                        if (SessionManager.lastMessage != null) {
                            if (SessionManager.lastMessage!!.id == state.message.message.id)
                                return@collect
                        }
                        SessionManager.lastMessage = state.message.message
                        println("New message to channelID = ${state.message.channelId} (${args.channelId}) and spaceId = ${state.message.spaceId} (${args.serverId})")
                        if (state.message.channelId.equals(args.channelId) && state.message.spaceId.equals(
                                args.serverId
                            )
                        ) {
                            ml.addMessage(state.message.message)
                            (binding.listMessages.adapter as MessageListAdapter).updateData(ml)
                            binding.listMessages.post {
                                binding.listMessages.smoothScrollToPosition(ml.stackedMessageList.size)
                            }
                            val r =
                                SessionManager.unreadedMessages.get(SessionManager.selectedServer!!.space.id)
                            if (r != null) {
                                r.remove(args.channelId)
                                if (r.isEmpty())
                                    SessionManager.unreadedMessages.remove(SessionManager.selectedServer!!.space.id)
                                else
                                    SessionManager.unreadedMessages[SessionManager.selectedServer!!.space.id] =
                                        r
                            }

                        }
                    }

                    else -> Unit
                }
            }
        }

    }

    private var job: Job? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) {
            println("REQUEST PERMISSION RESULT")
            showAttachmentSheet()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 321 && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                closeAttachmentFragment()
                AttachmentStorage.selectFile(uri.toString())
                updateAttachmentText()
            }
        }
    }


    private fun closeAttachmentFragment() {
        val existing =
            parentFragmentManager.findFragmentByTag(SHEET_TAG) as? AttachmentBottomSheetFragment
        if (existing?.isAdded == true && existing.dialog?.isShowing == true) {
            existing.dismiss()
        }
    }

    @SuppressLint("SetTextI18n", "UseKtx")
    fun updateAttachmentText() {
        val l = AttachmentStorage.getSelectedFiles()
        if (l.isNotEmpty()) {
            binding.filesText.text = "files: ${l.size}"
            if (binding.filesSelectedLayout.visibility != View.VISIBLE) {
                binding.filesSelectedLayout.apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                    animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setListener(null)
                }
            }
        } else {
            if (binding.filesSelectedLayout.isVisible) {
                binding.filesSelectedLayout.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.filesSelectedLayout.visibility = View.GONE
                    }
            }
        }
    }

    fun isUri(str: String): Boolean {
        return str.startsWith("content://") || str.startsWith("file://")
    }

    fun sendMessageWithAttachemnts(message: String) {
        pd.show(parentFragmentManager, "progress_dialog")
        val files = AttachmentStorage.getSelectedFiles()
        viewLifecycleOwner.lifecycleScope.launch {
            val ids = arrayListOf<String>()
            files.forEach {
                if (!isUri(it)) {
                    ids.add(it)
                    return@forEach
                }
                val file = Utils.uriToFile(requireContext(), it.toUri())
                if (file == null)
                    return@forEach
                val result = GrpcClient.sendFile(file, requireContext())
                println("Grpc file send result id: ${result.id}")
                ids.add(result.id)
                AttachmentStorage.addUploadedFile(result.id, file.name)
            }
            GrpcClient.sendMessage(
                args.serverId,
                args.channelId,
                message,
                ids
            )
            pd.dismiss()
        }

    }

    override fun onDestroy() {
        job?.cancel(CancellationException())
        super.onDestroy()
        val existing =
            parentFragmentManager.findFragmentByTag(SHEET_TAG) as? AttachmentBottomSheetFragment
        existing?.dismissAllowingStateLoss()

        super.onDestroyView()
    }
}