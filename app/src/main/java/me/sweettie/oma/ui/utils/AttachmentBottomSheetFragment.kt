package me.sweettie.oma.ui.utils

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import me.sweettie.oma.R
import me.sweettie.oma.databinding.AttachmentBottomSheetBinding
import me.sweettie.oma.ui.chat.AttachmentOptionAdapter
import me.sweettie.oma.ui.chat.FileAdapter
import me.sweettie.oma.ui.chat.ImagesAdapter
import me.sweettie.oma.utils.AttachmentStorage
import me.sweettie.oma.utils.Utils

class AttachmentBottomSheetFragment(
    val onSlide: (offset: Float) -> Unit,
    val update: () -> Unit,
    val peekHeight: Int
) : BottomSheetDialogFragment() {
    private var _binding: AttachmentBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun getTheme() = R.style.AppBottomSheetDialogTheme


    private lateinit var behavior: BottomSheetBehavior<FrameLayout>
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_HIDDEN -> {
                    dismiss()
                }

                BottomSheetBehavior.STATE_EXPANDED -> { /*...*/
                }

                BottomSheetBehavior.STATE_COLLAPSED -> { /*...*/
                    println("New state!!! Collapesed")
                }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            this@AttachmentBottomSheetFragment.onSlide(slideOffset)
            println("Slide on: $slideOffset")
            if (slideOffset <= 0.0f) {
                val height = binding.bottomSheetAttachmentType.height.toFloat()
                val r1 = peekHeight - height
                binding.bottomSheetAttachmentType.translationY = r1
                binding.bottomSheetAttachmentType.post {
                    binding.bottomSheetAttachmentType.visibility = View.VISIBLE
                }

            } else {
                binding.bottomSheetAttachmentType.translationX =
                    -binding.bottomSheetAttachmentType.width * slideOffset
                binding.bottomSheetAttachmentType.alpha = 1 - slideOffset
                if (slideOffset >= 0.8f)
                    binding.bottomSheetAttachmentType.visibility = View.GONE
                else
                    binding.bottomSheetAttachmentType.visibility = View.VISIBLE
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AttachmentBottomSheetBinding.bind(
            inflater.inflate(
                R.layout.attachment_bottom_sheet,
                container
            )
        )
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val density = requireContext().resources.displayMetrics.density
        dialog?.let {
            val bottomSheet =
                it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
            behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.peekHeight = peekHeight

            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            val items = listOf(
                AttachmentOptionAdapter.AttachmentItem.Photo("Gallery"),
                AttachmentOptionAdapter.AttachmentItem.File("File")
            )
            val adapter = AttachmentOptionAdapter(
                items,
                onPhotoClicked = { photo ->
                    changeType(0)
                },
                onFileClicked = { file ->
                    changeType(1)

                }
            )
            binding.bottomSheetAttachmentType.adapter = adapter
            binding.bottomSheetAttachmentType.layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL, false
            )



            behavior.addBottomSheetCallback(bottomSheetCallback)
            binding.bottomSheetAttachmentType.post {
                val height = binding.bottomSheetAttachmentType.height.toFloat()
                val r1 = peekHeight - height
                binding.bottomSheetAttachmentType.translationY = r1
                binding.bottomSheetAttachmentType.post {
                    binding.bottomSheetAttachmentType.visibility = View.VISIBLE
                }
            }

        }

    }

    private var selectedAttachment = 0
    fun changeType(type: Int) {
        selectedAttachment = type
        reCreate()
    }

    fun reCreate() {
        binding.bottomSheetItemList.removeAllViews()
        if (selectedAttachment == 0) {
            binding.bottomSheetItemList.layoutManager = GridLayoutManager(context, 3)

            val imageUris = Utils.loadGalleryImages(requireContext())
            binding.bottomSheetItemList.adapter = ImagesAdapter(imageUris) { selectedUri ->
                println("Selected image: ${selectedUri}")
                dismiss()
                AttachmentStorage.selectFile(selectedUri.toString())
                update()
            }
        } else if (selectedAttachment == 1) {

            binding.bottomSheetItemList.layoutManager = LinearLayoutManager(context)
            val files = AttachmentStorage.getUploadedFiles()
            binding.bottomSheetItemList.adapter = FileAdapter(
                files, onClicked = { selectedUri ->
                    println("Selected image: ${selectedUri}")
                    dismiss()
                    AttachmentStorage.selectFile(selectedUri)
                    update()
                },
                onHeaderClicked = {
                    dismiss()

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*" // "image/*" - для изображений
                    }

                    startActivityForResult(intent, 321)
                })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        behavior.removeBottomSheetCallback(bottomSheetCallback)
    }
}