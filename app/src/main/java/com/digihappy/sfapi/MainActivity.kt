package com.digihappy.sfapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var androidIdValue: TextView
    private lateinit var deviceIdValue: TextView
    private lateinit var retryButton: Button
    private lateinit var wipeButton: Button
    private lateinit var debugLog: TextView

    private val debugLines = mutableListOf<String>()
    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        androidIdValue = findViewById(R.id.androidIdValue)
        deviceIdValue = findViewById(R.id.deviceIdValue)
        retryButton = findViewById(R.id.retryButton)
        wipeButton = findViewById(R.id.wipeButton)
        debugLog = findViewById(R.id.debugLog)

        retryButton.setOnClickListener { resolveRestrictions() }
        wipeButton.setOnClickListener { triggerWipe() }
    }

    override fun onResume() {
        super.onResume()
        resolveRestrictions()
        registerRestrictionsReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterRestrictionsReceiver()
    }

    private fun registerRestrictionsReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        registerReceiver(restrictionsReceiver, filter)
        receiverRegistered = true
        logDebug("Registered restrictions receiver.")
    }

    private fun unregisterRestrictionsReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(restrictionsReceiver)
            logDebug("Unregistered restrictions receiver.")
        } catch (e: IllegalArgumentException) {
            // Receiver not registered; ignore
        } finally {
            receiverRegistered = false
        }
    }

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            logDebug("Restrictions changed broadcast received; reloading.")
            resolveRestrictions()
        }
    }

    private fun resolveRestrictions() {
        logDebug("Reading managed configuration...")
        val restrictionsManager = getSystemService(RESTRICTIONS_SERVICE) as? RestrictionsManager
        if (restrictionsManager == null) {
            updateStatus(getString(R.string.restrictions_not_available))
            logDebug("RestrictionsManager not available on this device.")
            deviceIdValue.text = "--"
            androidIdValue.text = "--"
            return
        }

        val bundle = restrictionsManager.applicationRestrictions
        val keys = bundle.keySet()
        logDebug("Managed config keys: ${if (keys.isEmpty()) "<none>" else keys.joinToString()}")
        val mdmId = bundle.getString(KEY_MDM_DEVICE_ID)
        if (mdmId.isNullOrEmpty()) {
            updateStatus(getString(R.string.restriction_missing))
            logDebug("Key '$KEY_MDM_DEVICE_ID' missing or empty in restrictions. Bundle contents: ${bundle.toString()}")
            deviceIdValue.text = "--"
            androidIdValue.text = "--"
        } else {
            updateStatus(getString(R.string.restriction_found, mdmId))
            androidIdValue.text = mdmId
            deviceIdValue.text = mdmId
            logDebug("Managed config mdm_device_id: $mdmId")
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        logDebug(message)
    }

    private fun logDebug(message: String) {
        debugLines.add(message)
        debugLog.text = debugLines.joinToString("\n")
    }

    private fun triggerWipe() {
        logDebug("Sending wipe request via AWS helper...")
        AwsClient(applicationContext).executeWipe()
    }

    companion object {
        private const val KEY_MDM_DEVICE_ID = "mdm_device_id"
    }
}
