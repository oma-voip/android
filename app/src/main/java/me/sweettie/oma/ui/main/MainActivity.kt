package me.sweettie.oma.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import me.sweettie.oma.R
import me.sweettie.oma.ui.home.HomeFragment

class MainActivity : FragmentActivity() {

    private lateinit var navigator: FragmentNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigator = FragmentNavigator(supportFragmentManager, R.id.fragment_container)


    }
}