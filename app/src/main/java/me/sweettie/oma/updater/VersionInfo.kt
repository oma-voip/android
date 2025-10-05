package me.sweettie.oma.updater

data class VersionInfo(
    val versionStr: String,
    val version: Int,
    val title: String,
    val changeLog: List<String>,
    val updateLink: String
)