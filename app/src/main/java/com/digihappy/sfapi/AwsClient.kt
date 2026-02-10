package com.digihappy.sfapi

import android.content.Context
import android.content.RestrictionsManager
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

class AwsClient(private val context: Context) {

    private val restrictionsManager: RestrictionsManager? by lazy {
        context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
    }

    // Explicit HurlStack with connection-level timeouts for more predictable behavior.
    private val requestQueue by lazy {
        val stack = object : HurlStack() {
            override fun createConnection(url: URL): HttpURLConnection {
                return (super.createConnection(url) as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                }
            }
        }
        Volley.newRequestQueue(context.applicationContext, stack)
    }

    // Helper to read any managed config key
    private fun getConfig(key: String): String {
        val bundle = restrictionsManager?.applicationRestrictions
        return bundle?.getString(key).orEmpty()
    }

    /**
     * Public entry point. Probes HTTPS connectivity first so emulator/network issues are obvious.
     */
    fun executeWipe() {
        probeInternet { ok ->
            if (!ok) {
                Log.e(TAG, "No working HTTPS connectivity on this device/emulator. Not calling Lambda.")
                return@probeInternet
            }
            executeWipeInternal()
        }
    }

    private fun executeWipeInternal() {
        val awsUrl = getConfig(KEY_AWS_ENDPOINT)
        val awsKey = getConfig(KEY_AWS_API_KEY)
        val deviceId = getConfig(KEY_MDM_DEVICE_ID)

        if (awsUrl.isEmpty() || awsKey.isEmpty()) {
            Log.e(TAG, "Configuration missing: aws_endpoint_url or aws_api_key.")
            return
        }

        val jsonBody = JSONObject()
            .put("action", "WIPE_CHROME")
            .put("device_id", deviceId)

        val bodyString = jsonBody.toString()
        Log.d(TAG, "Sending body: $bodyString")

        val request = object : StringRequest(
            Method.POST,
            awsUrl,
            { resp ->
                Log.d(TAG, "Wipe request succeeded. Raw response: $resp")
            },
            { error ->
                Log.e(TAG, "Wipe request failed. ${formatVolleyError(error)}", error)
                error.cause?.let { cause ->
                    Log.e(TAG, "Root cause: ${cause::class.java.name}: ${cause.message}")
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "x-api-key" to awsKey,
                    "Accept" to "application/json",
                    "Content-Type" to "application/json; charset=utf-8",
                )
            }

            override fun getBodyContentType(): String = "application/json; charset=utf-8"

            override fun getBody(): ByteArray = bodyString.toByteArray(Charsets.UTF_8)

            override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
                // ✅ headers can be null in some Volley versions
                val headers: Map<String, String> = response.headers ?: emptyMap()

                val contentType = headers["Content-Type"].orEmpty()
                val charsetName = HttpHeaderParser.parseCharset(headers, "utf-8")

                val respBody = try {
                    String(response.data, Charset.forName(charsetName))
                } catch (_: Exception) {
                    String(response.data, Charsets.UTF_8)
                }

                Log.d(TAG, "HTTP ${response.statusCode} Content-Type=$contentType Body=$respBody")

                return try {
                    Response.success(respBody, HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: Exception) {
                    Response.error(ParseError(e))
                }
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            45_000, // timeout
            0,      // retries
            1.0f
        )

        requestQueue.add(request)
    }

    /**
     * Quick HTTPS connectivity probe (helps diagnose emulator/managed network/TLS problems).
     */
    private fun probeInternet(onDone: (Boolean) -> Unit) {
        val req = object : StringRequest(
            Request.Method.GET,
            "https://www.google.com/generate_204",
            {
                Log.d(TAG, "Internet probe OK")
                onDone(true)
            },
            { err ->
                Log.e(TAG, "Internet probe failed: ${formatVolleyError(err)}", err)
                err.cause?.let { cause ->
                    Log.e(TAG, "Probe root cause: ${cause::class.java.name}: ${cause.message}")
                }
                onDone(false)
            }
        ) {
            override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
                Log.d(TAG, "Probe HTTP ${response.statusCode}")
                return super.parseNetworkResponse(response)
            }
        }

        req.retryPolicy = DefaultRetryPolicy(10_000, 0, 1.0f)
        requestQueue.add(req)
    }

    private fun formatVolleyError(error: VolleyError): String {
        val nr = error.networkResponse ?: return "No networkResponse (timeout/no connection?)"
        val status = nr.statusCode

        // ✅ headers can be null in some Volley versions
        val headers: Map<String, String> = nr.headers ?: emptyMap()

        val charset = try {
            val ct = headers["Content-Type"].orEmpty()
            Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(ct)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { Charset.forName(it.trim()) }
        } catch (_: Exception) {
            null
        } ?: Charsets.UTF_8

        val body = try {
            nr.data?.toString(charset).orEmpty()
        } catch (_: Exception) {
            ""
        }

        return "HTTP $status Body: $body"
    }

    companion object {
        private const val TAG = "AWS"
        private const val KEY_AWS_ENDPOINT = "aws_endpoint_url"
        private const val KEY_AWS_API_KEY = "aws_api_key"
        private const val KEY_MDM_DEVICE_ID = "mdm_device_id"
    }
}