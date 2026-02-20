package com.balsdon.managemom

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Accessibility service for Manage Mom.
 * Reports device/app data to Firebase and can respond to remote delete signals.
 * UI automation for uninstall dialogs can be added here when needed.
 */
class ManageMomAccessibilityService : AccessibilityService() {

    companion object {
        /** True if this accessibility service is enabled in system settings. */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val ourComponent = ComponentName(context, ManageMomAccessibilityService::class.java)
            @Suppress("DEPRECATION")
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return enabled.any {
                it.resolveInfo?.serviceInfo?.let { si ->
                    ComponentName(si.packageName, si.name) == ourComponent
                } ?: false
            }
        }
    }

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
                val markedForDeletion = snapshot?.get(FirebaseSync.FIELD_MARKED_FOR_DELETION) as? List<*>
                if (markedForDeletion != null) {
                    val set = markedForDeletion.filterIsInstance<String>().filter { it.isNotBlank() }.toSet()
                    UninstallBlocklist.setPackages(this, set)
                    PackageHelper.requestUninstallForFirstBlocklistedInstalled(this)
                }
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
                        break
                    } catch (_: PackageManager.NameNotFoundException) { }
                }
                snapshot.reference.update(FirebaseSync.FIELD_PENDING_UNINSTALLS, emptyList<String>())
            }
        } catch (e: SecurityException) {
            // GMS/Firebase unavailable
        } catch (e: Exception) { }
    }
}
