package me.sweettie.oma.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

object SecurePrefs {

    private const val PREFS_NAME = "oma_secure_prefs"

    var sf: SharedPreferences? = null

    fun init(context: Context) {
        sf = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t = sf!!.getString("uuid", null)
        if (t == null) {
            val uuid = UUID.randomUUID()
            save("uuid", uuid.toString())
            SessionManager.uuid = uuid
        } else {
            SessionManager.uuid = UUID.fromString(t)
        }

    }

    inline fun <reified T> save(key: String, value: T) {
        sf?.edit {
            when (T::class) {
                String::class -> putString(key, value as String)
                Int::class -> putInt(key, value as Int)
                Boolean::class -> putBoolean(key, value as Boolean)
                Long::class -> putLong(key, value as Long)
                Float::class -> putFloat(key, value as Float)
                Nothing::class -> remove(key)
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
    }

}