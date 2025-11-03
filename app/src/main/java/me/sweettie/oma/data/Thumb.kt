package me.sweettie.oma.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import proto.OmaOuterClass

class Thumb(thumbnail: OmaOuterClass.Thumbnail) {
    var bitmap: Bitmap? = null

    init {
        val bytes = thumbnail.data.toByteArray()
        if (bytes != null)
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}