package me.sweettie.oma.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


class VersionChecker {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun fetchVersionInfo(): VersionInfo = withContext(Dispatchers.IO) {
        val url = URL("https://sovamor.co/oma/android_version")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
        }

        try {
            connection.inputStream.bufferedReader().use { reader ->
                val responseStr = reader.readText()
                val json = JSONObject(responseStr)

                val versionStr = json.getString("version_str")
                val version = json.getInt("version")
                val title = json.getString("title")

                val changeLogJsonArray = json.getJSONArray("change_log")
                val changeLog = mutableListOf<String>()
                for (i in 0 until changeLogJsonArray.length()) {
                    changeLog.add(changeLogJsonArray.getString(i))
                }

                val updateLink = json.getString("update_link")

                VersionInfo(
                    versionStr = versionStr,
                    version = version,
                    title = title,
                    changeLog = changeLog,
                    updateLink = updateLink
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}