package com.balsdon.managemom

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Provides a stable device identifier for Firebase. Prefers Android ID (stable per app install),
 * falls back to a generated UUID persisted in SharedPreferences.
 */
object DeviceIdHelper {

    private const val PREFS_NAME = "managemom_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return androidId
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
    }
}
