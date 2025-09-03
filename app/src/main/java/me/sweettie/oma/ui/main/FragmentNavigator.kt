package me.sweettie.oma.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class FragmentNavigator(private val fragmentManager: FragmentManager, private val containerId: Int) {
    fun replace(fragment: Fragment, addToBackStack: Boolean = true, tag: String? = null) {
        val transaction = fragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
        if (addToBackStack) {
            transaction.addToBackStack(tag)
        }
        transaction.commit()
    }

    fun add(fragment: Fragment, addToBackStack: Boolean = true, tag: String? = null) {
        val transaction = fragmentManager.beginTransaction()
            .add(containerId, fragment, tag)
        if (addToBackStack) {
            transaction.addToBackStack(tag)
        }
        transaction.commit()
    }

    fun popBackStack() {
        fragmentManager.popBackStack()

    }

    fun popBackStackTo(tag: String, inclusive: Boolean = false) {
        fragmentManager.popBackStack(tag, if (inclusive) FragmentManager.POP_BACK_STACK_INCLUSIVE else 0)
    }

    fun getCurrentFragment(): Fragment? {
        return fragmentManager.findFragmentById(containerId)
    }
}