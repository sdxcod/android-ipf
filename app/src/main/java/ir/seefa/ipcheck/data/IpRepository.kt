package ir.seefa.ipcheck.data

import ir.seefa.ipcheck.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class IpInfo(
    val ip: String,
    val type: String,
    val continent: String,
    val continentCode: String,
    val country: String,
    val region: String,
    val city: String,
    val timezoneId: String,
    val timezoneAbbr: String,
    val org: String,
    val isp: String,
    val latitude: Double?,
    val longitude: Double?
)

class IpRepository {
    fun fetchWithFallback(): IpInfo {
        return runCatching { fetchFromIpWho() }
            .getOrElse { primaryError ->
                fetchFromIpifyOrNull() ?: throw primaryError
            }
    }

    private fun fetchFromIpWho(): IpInfo {
        val connection = openConnection(BuildConfig.IPWHO_URL)
        try {
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            if (!json.optBoolean("success", true)) {
                val reason = json.optString("message").ifBlank { "Unknown error" }
                throw IllegalStateException("ipwho.is error: $reason")
            }
            return IpInfo(
                ip = json.optString("ip"),
                type = json.optString("type"),
                continent = json.optString("continent"),
                continentCode = json.optString("continent_code"),
                country = json.optString("country"),
                region = json.optString("region"),
                city = json.optString("city"),
                timezoneId = json.optJSONObject("timezone")?.optString("id").orEmpty(),
                timezoneAbbr = json.optJSONObject("timezone")?.optString("abbr").orEmpty(),
                org = json.optString("org"),
                isp = json.optString("isp"),
                latitude = json.optDoubleOrNull("latitude"),
                longitude = json.optDoubleOrNull("longitude")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFromIpifyOrNull(): IpInfo? {
        return runCatching {
            val connection = openConnection(BuildConfig.IPIFY_URL)
            try {
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(payload)
                val ip = json.optString("ip")
                if (ip.isBlank()) null else IpInfo(
                    ip = ip,
                    type = "",
                    continent = "",
                    continentCode = "",
                    country = "",
                    region = "",
                    city = "",
                    timezoneId = "",
                    timezoneAbbr = "",
                    org = "",
                    isp = "",
                    latitude = null,
                    longitude = null
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }.also { connection ->
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("Unexpected response: $responseCode")
            }
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    return if (has(key) && !isNull(key)) optDouble(key) else null
}
