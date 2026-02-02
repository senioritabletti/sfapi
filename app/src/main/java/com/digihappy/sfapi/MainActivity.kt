package com.digihappy.sfapi

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.digihappy.sfapi.BuildConfig.SCALEFUSION_API_TOKEN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var androidIdValue: TextView
    private lateinit var deviceIdValue: TextView
    private lateinit var retryButton: Button
    private lateinit var progressBar: ProgressBar

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        androidIdValue = findViewById(R.id.androidIdValue)
        deviceIdValue = findViewById(R.id.deviceIdValue)
        retryButton = findViewById(R.id.retryButton)
        progressBar = findViewById(R.id.progressBar)

        retryButton.setOnClickListener { ensureDeviceId(forceRefresh = true) }

        ensureDeviceId()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun ensureDeviceId(forceRefresh: Boolean = false) {
        val storedId = prefs.getString(KEY_DEVICE_ID, null)
        if (!forceRefresh && !storedId.isNullOrEmpty()) {
            deviceIdValue.text = storedId
            updateStatus(getString(R.string.device_id_already_set))
            return
        }
        fetchDeviceId()
    }

    private fun fetchDeviceId() {
        val token = SCALEFUSION_API_TOKEN
        if (token.isBlank()) {
            updateStatus(getString(R.string.missing_token_hint))
            return
        }

        val androidId = obtainAndroidId()
        if (androidId.isNullOrEmpty()) {
            updateStatus(getString(R.string.android_id_unavailable))
            return
        }

        androidIdValue.text = androidId
        setLoading(true)
        updateStatus(getString(R.string.fetching_from_api))

        uiScope.launch {
            val result = withContext(Dispatchers.IO) { lookupDeviceId(token, androidId) }
            when (result) {
                is DeviceLookup.Success -> {
                    prefs.edit().putString(KEY_DEVICE_ID, result.deviceId).apply()
                    deviceIdValue.text = result.deviceId
                    updateStatus(getString(R.string.device_id_retrieved))
                }

                is DeviceLookup.Failure -> updateStatus(result.message)
            }
            setLoading(false)
        }
    }

    private fun obtainAndroidId(): String? {
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }

    private fun lookupDeviceId(token: String, androidId: String): DeviceLookup {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Token $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                return DeviceLookup.Failure(
                    getString(R.string.api_error_with_code, responseCode)
                )
            }

            val deviceId = extractDeviceId(body, androidId)
            if (deviceId.isNullOrEmpty()) {
                DeviceLookup.Failure(getString(R.string.no_match_for_android_id))
            } else {
                DeviceLookup.Success(deviceId)
            }
        } catch (e: Exception) {
            DeviceLookup.Failure(e.message ?: getString(R.string.unexpected_error))
        } finally {
            connection.disconnect()
        }
    }

    private fun extractDeviceId(body: String, androidId: String): String? {
        val normalizedId = androidId.lowercase().filter { it.isLetterOrDigit() }

        fun matches(target: String?): Boolean {
            val cleaned = target?.lowercase()?.filter { it.isLetterOrDigit() }
            return !cleaned.isNullOrEmpty() && cleaned == normalizedId
        }

        fun idFromDevice(device: JSONObject): String? {
            return device.optString("device_id")
                .takeIf { it.isNotBlank() }
                ?: device.optString("id").takeIf { it.isNotBlank() }
        }

        fun unwrapDevice(container: JSONObject): JSONObject {
            return container.optJSONObject("device") ?: container
        }

        fun findInArray(array: JSONArray): String? {
            for (index in 0 until array.length()) {
                val container = array.optJSONObject(index) ?: continue
                val device = unwrapDevice(container)
                val idField = listOf(
                    device.optString("android_id"),
                    device.optString("androidId"),
                    device.optString("androidID")
                ).firstOrNull { matches(it) }
                if (idField != null) {
                    return idFromDevice(device)
                }
            }
            return null
        }

        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        return when {
            trimmed.startsWith("[") -> findInArray(JSONArray(trimmed))
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("devices")?.let { findInArray(it) }
                    ?: obj.optJSONArray("data")?.let { findInArray(it) }
                    ?: run {
                        val device = unwrapDevice(obj)
                        if (matches(device.optString("android_id"))) idFromDevice(device) else null
                    }
            }

            else -> null
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        retryButton.isEnabled = !isLoading
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    sealed class DeviceLookup {
        data class Success(val deviceId: String) : DeviceLookup()
        data class Failure(val message: String) : DeviceLookup()
    }

    companion object {
        private const val API_URL = "https://api.scalefusion.com/api/v3/devices.json"
        private const val PREF_NAME = "scalefusion_prefs"
        private const val KEY_DEVICE_ID = "scalefusion_device_id"
    }
}
