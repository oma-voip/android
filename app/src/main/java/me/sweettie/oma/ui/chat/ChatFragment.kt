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
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.databinding.FragmentChatBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.ui.progress.ProgressDialogFragment
import me.sweettie.oma.utils.AttachmentStorage
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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AttachmentStorage.clearSelectedFiles()
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN


        pd = ProgressDialogFragment()
        pd.show(parentFragmentManager, "progress_dialog")
        binding.sendBtn.isEnabled = false
        val channelId = args.channelId
        val serverId = args.serverId

        binding.channelName.text =
            SessionManager.getServerBySpaceId(args.serverId)!!.hashChannels[args.channelId]!!.name
        binding.backBtn.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                val navController =
                    (activity as? MainActivity)?.navController ?: return@setOnClickListener
                navController.popBackStack()
            }
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

    private lateinit var viewModel: MainViewModel

    private var updateChat = false

    private var selectedAttachment = 0
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
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.bottomSheet.layoutParams.height =
                    binding.root.height - binding.actionBar.height + 45
                binding.bottomSheet.requestLayout()
                bottomSheetBehavior.peekHeight = (binding.root.height / 1.75).toInt()
                binding.bottomSheetItemList.minimumHeight =
                    bottomSheetBehavior.peekHeight - binding.bottomSheetAttachmentType.height - 45
                println(bottomSheetBehavior.peekHeight)
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })


        val items = listOf(
            AttachmentOptionAdapter.AttachmentItem.Photo("Gallery"),
            AttachmentOptionAdapter.AttachmentItem.File("File")
        )
        val adapter = AttachmentOptionAdapter(
            items,
            onPhotoClicked = { photo ->
                selectedAttachment = 0
                createAdapterForPhoto()
            },
            onFileClicked = { file ->
                selectedAttachment = 1
                createAdapterForPhoto()
            }
        )
        binding.bottomSheetAttachmentType.adapter = adapter
        binding.bottomSheetAttachmentType.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL, false
        )



        binding.attachmentBtn.setOnClickListener {
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
                createAdapterForPhoto()

                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }, 150)
        }


        viewModel = ViewModelProvider(this)[MainViewModel::class.java]


        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            println(bottomSheetBehavior.state)
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset <= 0.0f) {
                    binding.bottomSheetAttachmentType.visibility = View.VISIBLE
                    val height = binding.bottomSheetAttachmentType.height.toFloat()
                    val r1 = height * (1 - slideOffset)
                    binding.bottomSheetAttachmentType.translationY = -(height - r1)
                } else {
                    binding.bottomSheetAttachmentType.alpha = 1 - slideOffset
                    if (slideOffset >= 0.8f)
                        binding.bottomSheetAttachmentType.visibility = View.GONE
                    else
                        binding.bottomSheetAttachmentType.visibility = View.VISIBLE
                }
            }

        })

        binding.clearFileBtn.setOnClickListener {
            AttachmentStorage.clearSelectedFiles()
            updateAttachmentText()
        }

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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) {
            println("REQUEST PERMISSION RESULT")
            createAdapterForPhoto()
        }
    }

    private var oldSelectedAttahcmentType = -1
    private fun createAdapterForPhoto() {
        if (oldSelectedAttahcmentType == selectedAttachment)
            return
        binding.bottomSheetItemList.removeAllViews()
        if (selectedAttachment == 0) {
            binding.bottomSheetItemList.layoutManager = GridLayoutManager(context, 3)

            val imageUris = Utils.loadGalleryImages(requireContext())
            binding.bottomSheetItemList.adapter = ImagesAdapter(imageUris) { selectedUri ->
                println("Selected image: ${selectedUri}")
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                AttachmentStorage.selectFile(selectedUri.toString())
                updateAttachmentText()
            }
        } else if (selectedAttachment == 1) {

            binding.bottomSheetItemList.layoutManager = LinearLayoutManager(context)
            val files = AttachmentStorage.getUploadedFiles()
            binding.bottomSheetItemList.adapter = FileAdapter(
                files, onClicked = { selectedUri ->
                    println("Selected image: ${selectedUri}")
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    AttachmentStorage.selectFile(selectedUri)
                    updateAttachmentText()
                },
                onHeaderClicked = {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*" // "image/*" - для изображений
                    }

                    startActivityForResult(intent, 321)
                })


        }

        oldSelectedAttahcmentType = selectedAttachment
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 321 && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                AttachmentStorage.selectFile(uri.toString())
                updateAttachmentText()
            }
        }
    }

    @SuppressLint("SetTextI18n")
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
            if (binding.filesSelectedLayout.visibility == View.VISIBLE) {
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

}