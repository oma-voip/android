package me.sweettie.oma.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import proto.OmaOuterClass

class Thumb(thumbnail: OmaOuterClass.Thumbnail) {
    val bitmap: Bitmap

    init {
        val bytes = thumbnail.data.toByteArray()
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}