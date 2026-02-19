package com.balsdon.managemom

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Accessibility service for Manage Mom.
 * Reports device/app data to Firebase and can respond to remote delete signals.
 * UI automation for uninstall dialogs can be added here when needed.
 */
class ManageMomAccessibilityService : AccessibilityService() {

    private var deleteListenerRegistration: ListenerRegistration? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reserved for future use: e.g. automating "Uninstall" / "OK" in system dialogs
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            FirebaseSync.syncToFirebase(this)
            attachDeleteSignalListener()
        } catch (e: SecurityException) {
            // GMS/Firebase unavailable (e.g. emulator without Google Play)
        } catch (e: Exception) { }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        deleteListenerRegistration?.remove()
        deleteListenerRegistration = null
        return super.onUnbind(intent)
    }

    private fun attachDeleteSignalListener() {
        try {
            val deviceId = DeviceIdHelper.getDeviceId(this)
            deleteListenerRegistration = FirebaseFirestore.getInstance()
            .collection("devices")
            .document(deviceId)
            .addSnapshotListener { snapshot, _ ->
                val pending = snapshot?.get(FirebaseSync.FIELD_PENDING_UNINSTALLS) as? List<*>
                    ?: return@addSnapshotListener
                val packageNames = pending.filterIsInstance<String>().filter { it.isNotBlank() }
                if (packageNames.isEmpty()) return@addSnapshotListener
                val appNames = packageNames.map { PackageHelper.getAppLabel(this, it) }
                PendingDeleteSnackbar.set(appNames)
                UninstallBlocklist.addPackages(this, packageNames)
                val pm = packageManager
                for (pkg in packageNames) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        PackageHelper.requestUninstall(this, pkg)
                    } catch (_: PackageManager.NameNotFoundException) { }
                }
                snapshot.reference.update(FirebaseSync.FIELD_PENDING_UNINSTALLS, emptyList<String>())
            }
        } catch (e: SecurityException) {
            // GMS/Firebase unavailable
        } catch (e: Exception) { }
    }
}
