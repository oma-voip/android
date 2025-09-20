package me.sweettie.oma.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.*

object SecurePrefs {

    private const val PREFS_NAME = "oma_secure_prefs"

    lateinit var sf: SharedPreferences

    fun init(context: Context) {
        sf = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t = sf.getString("uuid", null)
        if (t == null) {
            val uuid = UUID.randomUUID()
            save("uuid", uuid.toString())
            SessionManager.uuid = uuid
        } else {
            SessionManager.uuid = UUID.fromString(t)
        }

    }

    inline fun <reified T> save(key: String, value: T) {
        val editor = sf.edit()
        when (T::class) {
            String::class -> editor.putString(key, value as String)
            Int::class -> editor.putInt(key, value as Int)
            Boolean::class -> editor.putBoolean(key, value as Boolean)
            Long::class -> editor.putLong(key, value as Long)
            Float::class -> editor.putFloat(key, value as Float)
            Nothing::class -> editor.remove(key)
            else -> throw IllegalArgumentException("Unsupported type")
        }
        editor.apply()
    }

}