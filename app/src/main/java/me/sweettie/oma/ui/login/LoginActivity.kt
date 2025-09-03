package me.sweettie.oma.ui.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.databinding.ActivityLoginBinding
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.onTextUpdate

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.username.onTextUpdate {
            binding.login.isEnabled =
                binding.username.text.toString().isNotEmpty() && it.isNotEmpty()
        }

        binding.password.onTextUpdate {
            println(binding.username.text.toString().isNotEmpty() && it.isNotEmpty())
            binding.login.isEnabled =
                binding.username.text.toString().isNotEmpty() && it.isNotEmpty()
        }

        binding.login.setOnClickListener {
            validateData()
        }
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
                        SessionManager.currentUser = GrpcClient.getUser("me")
                        Utils.openActivity(this@LoginActivity, MainActivity::class.java)
                    }
                },
                onError = {
                    it.printStackTrace()
                    println(it.message)
                }
            )
        }
    }
}

