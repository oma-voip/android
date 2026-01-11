package me.sweettie.oma.ui.photoview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import com.davemorrissey.labs.subscaleview.ImageSource
import me.sweettie.oma.R
import me.sweettie.oma.databinding.ActivityFullScreenBinding
import me.sweettie.oma.network.GrpcClient.downloadChunks
import me.sweettie.oma.utils.Utils
import java.io.File


@UnstableApi
class FullScreenActivity : AppCompatActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    private lateinit var binding: ActivityFullScreenBinding
    private var attachVideoId: String? = null
    private var fileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenBinding.inflate(layoutInflater)
        val view = binding.root
        var fileUri: Uri? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (intent.hasExtra("photoUri"))
                fileUri = intent.getParcelableExtra("photoUri", Uri::class.java)
            if (intent.hasExtra("videoId"))
                attachVideoId = intent.getStringExtra("videoId")
        } else {
            if (intent.hasExtra("photoUri"))
                fileUri = intent.getParcelableExtra("photoUri")
            if (intent.hasExtra("videoId"))
                attachVideoId = intent.getStringExtra("videoId")
        }

        if (fileUri != null) {
            binding.fullPhoto.setImage(ImageSource.uri(fileUri))

            binding.channelName.text = intent.getStringExtra("actionBarName")
            binding.backBtn.setOnClickListener { finish() }
            binding.optionBtn.setOnClickListener {
                val popup = PopupMenu(this@FullScreenActivity, it)
                popup.menuInflater.inflate(R.menu.photo_viewer_option_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_save -> {
                            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
                            Utils.saveImageToGallery(fileUri, this@FullScreenActivity)
                            return@setOnMenuItemClickListener true
                        }

                        R.id.action_share -> {
                            val contentUri = FileProvider.getUriForFile(
                                this,
                                "${applicationContext.packageName}.fileprovider",
                                File(fileUri.path!!)
                            )

                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                            }
                            startActivity(Intent.createChooser(share, "Share Image"))
                            return@setOnMenuItemClickListener true
                        }

//                    R.id.action_delete -> {
//                        val result = SessionManager.cacheManager.deletePhoto(fileUri)
//                        if(result)
//                            finishActivity(101)
//                        else{
//                            println("Can't delete image... :(")
//                        }
//                        return@setOnMenuItemClickListener true
//                    }

                        R.id.action_copy -> {
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val contentUri = FileProvider.getUriForFile(
                                this,
                                "${applicationContext.packageName}.fileprovider",
                                File(fileUri.path!!)
                            )

                            val clip = ClipData.newUri(contentResolver, "Image", contentUri)

                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                this,
                                "Image success copied to clipboard!",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                            return@setOnMenuItemClickListener true
                        }

                        else -> return@setOnMenuItemClickListener false
                    }
                }
                popup.show()
            }
            setContentView(view)
        } else if (attachVideoId != null) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            setContentView(view)
            binding.actionBar.visibility = View.GONE
            binding.fullPhoto.visibility = View.GONE
            binding.videoFrame.visibility = View.VISIBLE


//            val dataSourceFactory =
//                AttachmentDataSourceFactory(attachId = videoId, downloadFunc = ::downloadChunks)
//
//            val mediaItem = MediaItem.fromUri("attachment:$videoId")
//            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(mediaItem)

        }


    }

    override fun onStart() {
        super.onStart()
        // attach player to view
        if (attachVideoId != null) {
            val player = playerViewModel.getOrCreatePlayer()
            binding.playerView.player = player
            binding.playerView.player!!.playWhenReady = true
            playerViewModel.prepareIfNeeded(attachVideoId!!, ::downloadChunks)
        }
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.saveStateFromPlayer()
        binding.playerView.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}