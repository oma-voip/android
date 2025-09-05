package me.sweettie.oma.ui.register

import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.databinding.ActivityConfirmEmailBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.login.LoginActivity
import me.sweettie.oma.utils.*

class ConfirmEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmEmailBinding
    lateinit var email: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmEmailBinding.inflate(layoutInflater)
        val temail = SecurePrefs.sf.getString("email", null)
        if (temail == null) {
            Utils.openActivity(this, RegisterActivity::class.java)
            finishAffinity()
            return
        }
        email = temail
        SessionManager.saveState(SessionState.CEMAIL)
        setContentView(binding.root)


        binding.pinView.post {
            // костыль, так-как библиотека не поддерживает динамическую ширину ячеек внутри EditText
            val totalWidth = binding.pinView.width
            val spacingDp = 5
            val metrics = resources.displayMetrics
            val itemCount = binding.pinView.itemCount
            val spacingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                spacingDp.toFloat() * (itemCount - 1),
                metrics
            )
            val availableWidth = totalWidth - spacingPx
            val itemWidth = (availableWidth / itemCount).toInt()
            binding.pinView.itemWidth = itemWidth
        }

        val check: (String) -> Unit = {
            binding.btnSubmit.isEnabled = isEditablePresent()
        }
        binding.pinView.onTextUpdate(check)

        binding.btnSubmit.setOnClickListener {
            validateData()
        }
    }

    private fun isEditablePresent(): Boolean {
        return binding.pinView.isPresent()
    }

    private fun validateData() {
        val t1 = binding.pinView.getEndText()
        println("Try to confirm email by code $t1")
        lifecycleScope.launch {
            GrpcClient.confirmCode(t1, email,
                onSuccess = {
                    Utils.openActivity(this@ConfirmEmailActivity, LoginActivity::class.java)
                },
                onError = {
                    if (it.message.equals("INTERNAL: internal error")) {

                        Utils.openActivity(this@ConfirmEmailActivity, RegisterActivity::class.java)
                        return@confirmCode
                    }
                    it.printStackTrace()
                    println(it.message)
                }
            )
        }
    }
}

