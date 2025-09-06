package me.sweettie.oma.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat


fun EditText.onTextUpdate(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            if (editable.isNullOrBlank() || editable.isEmpty()) {
                afterTextChanged.invoke("")
            } else {
                afterTextChanged.invoke(editable.toString())
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

fun EditText.clear() {
    this.text.clear()
}

fun EditText.isPresent(): Boolean {
    return this.text.isPresent()
}

fun EditText.getEndText(): String {
    return this.text.toString()
}

fun Editable?.isPresent(): Boolean {
    return !(this.isNullOrBlank() || this.isEmpty())
}

fun TextView.setStartDrawable(@DrawableRes drawableRes: Int, @ColorInt tintColor: Int) {
    val drawable = ContextCompat.getDrawable(context, drawableRes)?.mutate()
    drawable?.setTint(tintColor)
    setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
}