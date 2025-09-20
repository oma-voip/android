package me.sweettie.oma.ui.login

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.databinding.ActivityLoginBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.StartUp
import me.sweettie.oma.ui.register.RegisterActivity
import me.sweettie.oma.utils.*

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        SessionManager.saveState(SessionState.LOGIN)
        setContentView(binding.root)

        binding.username.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.password.onTextUpdate {
            binding.login.isEnabled = isEditablePresent()
        }

        binding.login.setOnClickListener {
            validateData()
        }

        binding.registerPrompt.setOnClickListener {
            Utils.openActivity(this@LoginActivity, RegisterActivity::class.java)
        }
    }

    private fun isEditablePresent(): Boolean {
        return binding.username.isPresent() && binding.password.isPresent()
    }

    private fun validateData() {
        val t1 = binding.username.text.toString()
        val t2 = binding.password.text.toString()

        if (t1.isEmpty()) {
            binding.username.error = resources.getString(R.string.invalid_username)
            return
        }
        if (t2.isEmpty()) {
            binding.username.error = resources.getString(R.string.invalid_password)
            return
        }
        lifecycleScope.launch {
            GrpcClient.login(t1, t2,
                onSuccess = {
                    SessionManager.token = it.token
                    SecurePrefs.save("token", SessionManager.token)
                    lifecycleScope.launch {
                        Utils.openActivity(this@LoginActivity, StartUp::class.java)
                    }
                },
                onError = {
                    if (it.message.equals("UNAVAILABLE"))
                        Toast.makeText(
                            this@LoginActivity,
                            "Server busy...try later",
                            Toast.LENGTH_LONG
                        ).show()
                    it.printStackTrace()
                }
            )
        }
    }
}

