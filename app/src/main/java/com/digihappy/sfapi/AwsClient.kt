package com.digihappy.sfapi

import android.content.Context
import android.content.RestrictionsManager
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class AwsClient(private val context: Context) {

    private val restrictionsManager: RestrictionsManager? by lazy {
        context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
    }

    private val requestQueue by lazy {
        Volley.newRequestQueue(context.applicationContext)
    }

    // Helper to read any managed config key
    private fun getConfig(key: String): String {
        val bundle = restrictionsManager?.applicationRestrictions
        return bundle?.getString(key).orEmpty()
    }

    fun executeWipe() {
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

        val request = object : JsonObjectRequest(
            Method.POST,
            awsUrl,
            jsonBody,
            { Log.d(TAG, "Wipe request succeeded") },
            { error -> Log.e(TAG, "Wipe request failed", error) }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["x-api-key"] = awsKey
                return headers
            }

            override fun getMethod(): Int = Request.Method.POST
        }

        requestQueue.add(request)
    }

    companion object {
        private const val TAG = "AWS"
        private const val KEY_AWS_ENDPOINT = "aws_endpoint_url"
        private const val KEY_AWS_API_KEY = "aws_api_key"
        private const val KEY_MDM_DEVICE_ID = "mdm_device_id"
    }
}
