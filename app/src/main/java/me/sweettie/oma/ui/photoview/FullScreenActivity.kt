package me.sweettie.oma.ui.photoview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import com.davemorrissey.labs.subscaleview.ImageSource
import me.sweettie.oma.R
import me.sweettie.oma.databinding.ActivityFullScreenBinding
import me.sweettie.oma.utils.Utils
import java.io.File


class FullScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenBinding.inflate(layoutInflater)
        val view = binding.root
        val fileUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra("photoUri", Uri::class.java)!!
        else
            intent.getParcelableExtra("photoUri")!!
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


//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }


    }
}