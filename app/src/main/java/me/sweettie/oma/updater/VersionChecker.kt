package me.sweettie.oma.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
                jsonParser.decodeFromString<VersionInfo>(responseStr)
            }
        } finally {
            connection.disconnect()
        }
    }
}