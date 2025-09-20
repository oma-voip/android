package me.sweettie.oma.utils

import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import proto.OmaOuterClass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


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

fun OmaOuterClass.Message.getTimeFormat1(): String {
    if (this.time == null) return ""
    val instant = Instant.ofEpochSecond(this.time.seconds, this.time.nanos.toLong())
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yy hh:mm a")
    return zonedDateTime.format(formatter)
}

fun OmaOuterClass.Message.getOnlyDateFormat(): String {
    if (this.time == null) return ""
    val instant = Instant.ofEpochSecond(this.time.seconds, this.time.nanos.toLong())
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yy")
    return zonedDateTime.format(formatter)
}

fun OmaOuterClass.Message.getTimeFormat2(): String {
    if (this.time == null) return ""
    val instant = Instant.ofEpochSecond(this.time.seconds, this.time.nanos.toLong())
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")
    return zonedDateTime.format(formatter)
}

fun List<OmaOuterClass.User>.getById(userId: String): OmaOuterClass.User? {
    this.forEach {
        if (it.id.equals(userId))
            return it
    }
    return null
}

fun OmaOuterClass.Attachment.isImage(): Boolean {
    val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff")
    val filenameLower = this.filename.lowercase()
    return imageExtensions.any { ext -> filenameLower.endsWith(".$ext") }
}

fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
