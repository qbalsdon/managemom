package com.balsdon.managemom

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Swallows the known GMS SecurityException that can occur on emulators or when
 * Play Services doesn't recognize the app ("Unknown calling package name 'com.google.android.gms'").
 * The exception is thrown on a background thread by Firebase/WorkManager, so try/catch in app code
 * doesn't prevent the crash.
 */
class ManageMomApplication : Application() {

    companion object {
        private const val TAG = "ManageMom"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            sendTokenToServer(token)
        }
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isGmsSecurityException(throwable)) {
                Log.w("ManageMom", "Suppressing GMS SecurityException (emulator/Play mismatch): ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun sendTokenToServer(token: String?) {
        if (token != null) FirebaseSync.saveFcmToken(this, token)
    }

    private fun isGmsSecurityException(t: Throwable): Boolean {
        if (t !is SecurityException) return false
        val msg = t.message ?: ""
        return msg.contains("Unknown calling package", ignoreCase = true) ||
            msg.contains("broker", ignoreCase = true)
    }
}
