package me.sweettie.oma.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import me.sweettie.oma.R

class MainActivity : FragmentActivity() {

    private lateinit var navigator: FragmentNavigator


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigator = FragmentNavigator(supportFragmentManager, R.id.fragment_container)

    }

}

/*

    val response = GrpcClient.login("foma", "Ff1779008")
    SecurePrefs.save("token",response.token)
    Toast.makeText(applicationContext, "Token: " + response.token, Toast.LENGTH_LONG).show()
 */