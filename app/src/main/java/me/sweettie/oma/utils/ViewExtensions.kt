package me.sweettie.oma.utils

import android.content.Context
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import proto.OmaOuterClass
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID


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

fun Fragment.hideKeyboard() {
    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view?.windowToken?.let { token ->
        imm.hideSoftInputFromWindow(token, 0)
    }
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

fun View.setOposit() {
    this.visibility = if (this.visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

fun UUID.toByteArray(size: Int = 16): ByteArray {
    val buffer = ByteBuffer.allocate(size)
    buffer.putLong(mostSignificantBits)
    buffer.putLong(leastSignificantBits)
    return buffer.array()
}

fun String.HexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
