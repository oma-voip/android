package me.sweettie.oma.network.services.voip

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

fun registerPhoneAccount(context: Context): PhoneAccountHandle {
    val componentName = ComponentName(context, VoipConnectionService::class.java)
    val phoneAccountHandle = PhoneAccountHandle(componentName, "voip_account")
    val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "VoIP")
        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
        .build()

    val telecomManager = context.getSystemService(TelecomManager::class.java)
    telecomManager.registerPhoneAccount(phoneAccount)

    return phoneAccountHandle
}

fun startOutgoingCall(
    context: Context,
    phoneAccountHandle: PhoneAccountHandle,
    calleeNumber: String
) {
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    val uri = Uri.fromParts("tel", calleeNumber, null)
    val extras = android.os.Bundle()
    extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)

    telecomManager.placeCall(uri, extras)
}