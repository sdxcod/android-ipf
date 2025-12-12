package ir.seefa.ipcheck

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ir.seefa.ipcheck.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.provider.ContactsContract
import android.webkit.WebSettings
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("ipcheck_prefs", Context.MODE_PRIVATE) }
    private val phoneKey = "phone_number"
    private var lastInfo: IpInfo? = null
    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchContactPicker()
        } else {
            android.widget.Toast.makeText(
                this,
                getString(R.string.contacts_permission_denied),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            handlePickedContact(uri)
        } else {
            android.widget.Toast.makeText(
                this,
                getString(R.string.contact_pick_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.checkButton.setOnClickListener { fetchAndDisplay() }
        binding.copyIpButton.setOnClickListener { copyIpToClipboard() }
        binding.saveNumberButton.setOnClickListener { savePhoneNumber() }
        binding.sendIpButton.setOnClickListener { sendIpToPhone() }
        binding.pickContactButton.setOnClickListener { pickContact() }

        binding.phoneInput.setText(prefs.getString(phoneKey, "") ?: "")

        // Keep map clicks inside the app.
        binding.mapView.webViewClient = WebViewClient()
        binding.mapView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }

        fetchAndDisplay()
    }

    private fun fetchAndDisplay() {
        binding.statusText.text = getString(R.string.status_loading)
        binding.checkButton.isEnabled = false

        lifecycleScope.launch {
            val result = runCatching { fetchIpInfoWithFallback() }
            result.onSuccess { info ->
                binding.statusText.text = getString(R.string.status_success)
                binding.ipValue.text = info.ip.ifBlank { "—" }
                binding.ipTypeValue.text = info.type.ifBlank { "—" }
                binding.locationValue.text = buildLocationText(info)
                binding.continentValue.text = buildContinentText(info)
                binding.timezoneValue.text = buildTimezoneText(info)
                binding.orgValue.text = info.org.ifBlank { "—" }
                binding.ispValue.text = info.isp.ifBlank { "—" }
                binding.coordinatesValue.text = buildCoordinatesText(info)
                loadMap(info)
                lastInfo = info
            }.onFailure {
                binding.statusText.text = getString(R.string.status_error)
                binding.ipValue.text = "—"
                binding.ipTypeValue.text = "—"
                binding.locationValue.text = "—"
                binding.continentValue.text = "—"
                binding.timezoneValue.text = "—"
                binding.orgValue.text = "—"
                binding.ispValue.text = "—"
                binding.coordinatesValue.text = "—"
                binding.mapView.loadUrl("about:blank")
                lastInfo = null
            }
            binding.checkButton.isEnabled = true
        }
    }

    private suspend fun fetchIpInfoWithFallback(): IpInfo = withContext(Dispatchers.IO) {
        // Primary: ipwho.is (full dataset)
        runCatching { fetchFromIpWho() }.getOrElse { primaryError ->
            // Fallback: api.ipify.org (IP only)
            val ipOnly = fetchFromIpifyOrNull() ?: throw primaryError
            ipOnly
        }
    }

    private fun buildLocationText(info: IpInfo): String {
        val parts = listOf(info.country, info.region, info.city)
            .filter { it.isNotBlank() }
        return parts.joinToString(" / ").ifBlank { "—" }
    }

    private fun buildContinentText(info: IpInfo): String {
        val code = info.continentCode
        return when {
            info.continent.isBlank() && code.isBlank() -> "—"
            info.continent.isNotBlank() && code.isNotBlank() -> "${info.continent} ($code)"
            else -> info.continent.ifBlank { code }
        }
    }

    private fun buildTimezoneText(info: IpInfo): String {
        val id = info.timezoneId
        val abbr = info.timezoneAbbr
        return when {
            id.isBlank() && abbr.isBlank() -> "—"
            id.isNotBlank() && abbr.isNotBlank() -> "$id ($abbr)"
            else -> id.ifBlank { abbr }
        }
    }

    private fun buildCoordinatesText(info: IpInfo): String {
        val lat = info.latitude
        val lon = info.longitude
        return if (lat != null && lon != null && !lat.isNaN() && !lon.isNaN()) {
            String.format(Locale.US, "%.4f, %.4f", lat, lon)
        } else {
            "—"
        }
    }

    private fun copyIpToClipboard() {
        val ipText = binding.ipValue.text?.toString().orEmpty()
        if (ipText.isBlank() || ipText == "—") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboard?.setPrimaryClip(ClipData.newPlainText("IP", ipText))
        android.widget.Toast.makeText(this, getString(R.string.ip_copied), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key)) optDouble(key) else null
    }

    private fun savePhoneNumber() {
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        prefs.edit().putString(phoneKey, phone).apply()
        android.widget.Toast.makeText(this, getString(R.string.number_saved), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun sendIpToPhone() {
        val ipText = binding.ipValue.text?.toString().orEmpty()
        if (ipText.isBlank() || ipText == "—") {
            android.widget.Toast.makeText(this, getString(R.string.missing_ip), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        if (phone.isBlank()) {
            android.widget.Toast.makeText(this, getString(R.string.missing_number), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val body = "$ipText"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("smsto", phone, null)
            putExtra("sms_body", body)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            android.widget.Toast.makeText(this, "No SMS app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickContact() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launchContactPicker()
        } else {
            requestContactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        pickContactLauncher.launch(null)
    }

    private fun handlePickedContact(uri: Uri) {
        val contactId = try {
            ContactsContract.Contacts.getLookupUri(contentResolver, uri)?.lastPathSegment?.toLongOrNull()
                ?: uri.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }

        val number = contactId?.let { id ->
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
            val args = arrayOf(id.toString())
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }

        if (!number.isNullOrBlank()) {
            binding.phoneInput.setText(number)
            savePhoneNumber()
        } else {
            android.widget.Toast.makeText(
                this,
                getString(R.string.contact_pick_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadMap(info: IpInfo) {
        val lat = info.latitude
        val lon = info.longitude
        if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) {
            binding.mapView.loadUrl("about:blank")
            return
        }
        // Use OpenStreetMap embed (no API key).
        val delta = 0.05
        val west = lon - delta
        val south = lat - delta
        val east = lon + delta
        val north = lat + delta
        val url = "https://www.openstreetmap.org/export/embed.html?bbox=$west,$south,$east,$north&layer=mapnik&marker=$lat,$lon"
        binding.mapView.loadUrl(url)
    }

    private fun fetchFromIpWho(): IpInfo {
        val url = URL("https://ipwho.is")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Unexpected response: $responseCode")
            }
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
            val url = URL("https://api.ipify.org?format=json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Unexpected response: $responseCode")
                }
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
}
