package com.digihappy.sfapi

import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "App launched — triggering wipe.")

        resolveRestrictionsAndWipe()

        // Close immediately so user knows nothing is meant to be here
        moveTaskToBack(true)
        finish()
    }

    private fun resolveRestrictionsAndWipe() {
        val restrictionsManager = getSystemService(RESTRICTIONS_SERVICE) as? RestrictionsManager

        if (restrictionsManager == null) {
            Log.e(TAG, "RestrictionsManager unavailable.")
            return
        }

        val mdmId = restrictionsManager.applicationRestrictions
            .getString(KEY_MDM_DEVICE_ID)

        Log.d(TAG, "mdm_device_id: $mdmId")

        AwsClient(applicationContext).executeWipe()
    }

    companion object {
        private const val TAG = "SFAPI"
        private const val KEY_MDM_DEVICE_ID = "mdm_device_id"
    }
}