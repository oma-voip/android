package me.sweettie.oma.updater

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class VersionInfo(
    @SerialName("version_str") val versionStr: String,
    val version: Int,
    val title: String,
    @SerialName("change_log") val changeLog: List<String>,
    @SerialName("update_link") val updateLink: String,
)
