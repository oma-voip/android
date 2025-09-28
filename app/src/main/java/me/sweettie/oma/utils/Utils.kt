package me.sweettie.oma.utils

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import proto.OmaOuterClass

object Utils {
    fun getVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            pInfo.versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            -1L
        } as Int
    }

    fun openActivity(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    fun showConfirmationDialog(
        context: Context,
        message: String,
        positiveText: String,
        negativeText: String,
        onPositiveClick: (DialogInterface) -> Unit,
        onNegativeClick: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context, R.style.Theme_Oma_Dialog)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                onPositiveClick.invoke(dialog)
                dialog.dismiss()
            }
            .setNegativeButton(negativeText) { dialog, _ ->
                onNegativeClick?.invoke()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private val cachedUsers: HashMap<String, OmaOuterClass.User> = HashMap()
    fun getCacheUser(userId: String, onGet: (OmaOuterClass.User?) -> Unit) {
        if (cachedUsers.containsKey(userId))
            onGet(cachedUsers[userId]!!)
        else {
            CoroutineScope(Dispatchers.IO).launch {
                val r2 = GrpcClient.getUser(userId)
                if (r2 == null) {
                    withContext(Dispatchers.Main) {
                        onGet(null)
                    }
                    return@launch

                }
                withContext(Dispatchers.Main) {
                    cachedUsers.put(userId, r2)
                    onGet(cachedUsers[userId]!!)
                }

            }
        }
    }
}