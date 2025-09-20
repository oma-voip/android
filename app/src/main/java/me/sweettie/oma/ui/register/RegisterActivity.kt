package me.sweettie.oma.ui.register

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.databinding.ActivityRegisterBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.login.LoginActivity
import me.sweettie.oma.utils.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        SessionManager.saveState(SessionState.SIGNIN)
        setContentView(binding.root)

        binding.username.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.password.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.email.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.passwordConfirm.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.login.setOnClickListener {
            validateData()
        }

        binding.loginPrompt.setOnClickListener {
            Utils.openActivity(this@RegisterActivity, LoginActivity::class.java)
        }
    }

    private fun isEditablePresent(): Boolean {
        val b1 =
            binding.email.isPresent() && binding.username.isPresent() && binding.password.isPresent() && binding.passwordConfirm.isPresent()
        val b2 = binding.password.getEndText() == binding.passwordConfirm.getEndText()
        if (b1 && b2)
            return true
        if (!b2 && (binding.password.getEndText().isEmpty() || binding.password.getEndText()
                .isBlank())
        ) {
            binding.password.error = this@RegisterActivity.getString(R.string.passwords_not_correct)
            return false
        }
        return true
    }

    private fun validateData() {
        val t1 = binding.username.text.toString()
        val t2 = binding.password.text.toString()
        val t3 = binding.email.text.toString()

        if (t1.isEmpty()) {
            binding.username.error = resources.getString(R.string.invalid_username)
            return
        }
        if (t2.isEmpty()) {
            binding.username.error = resources.getString(R.string.invalid_password)
            return
        }
        lifecycleScope.launch {
            GrpcClient.register(t1, t2, t3,
                onSuccess = {
                    SecurePrefs.save("email", t3)
                    Utils.openActivity(this@RegisterActivity, ConfirmEmailActivity::class.java)
                }
            ) {
                Utils.showConfirmationDialog(this@RegisterActivity,
                    getString(R.string.register_account_issue),
                    getString(R.string.confirm_email),
                    getString(R.string.close),
                    {
                        lifecycleScope.launch {
                            GrpcClient.resendCode(t3) {
                                Utils.openActivity(
                                    this@RegisterActivity,
                                    ConfirmEmailActivity::class.java
                                )

                            }
                        }

                    },
                    {
                        binding.email.clear()
                        binding.password.clear()
                        binding.email.clear()
                        SecurePrefs.save("email", Nothing::class.java)
                    })
                it.printStackTrace()
                println(it.message)
            }
        }
    }
}

