package com.balsdon.managemom

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.balsdon.managemom.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var adapter: AppListAdapter? = null
    /** (sourceLabel, apps) in tab order. Updated in loadApps(). */
    private var appsBySource: List<Pair<String, List<AppInfo>>> = emptyList()
    /** Packages we've already triggered uninstall for this session (avoids repeat dialogs on resume). */
    private val uninstallTriggeredThisSession = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isDeletePermissionGranted()) {
            setContentView(R.layout.activity_permission)
            val messageView = findViewById<android.widget.TextView>(R.id.permission_message)
            if (!PackageHelper.canRequestUninstall(this)) {
                messageView.text = getString(R.string.permission_not_supported)
            }
            findViewById<android.view.View>(R.id.permission_continue).setOnClickListener {
                grantDeletePermission()
                setupMainContent()
            }
            return
        }
        setupMainContent()
    }

    private fun isDeletePermissionGranted(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DELETE_GRANTED, false)
    }

    private fun grantDeletePermission() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DELETE_GRANTED, true).apply()
    }

    private fun setupMainContent() {
        val mainBinding = ActivityMainBinding.inflate(layoutInflater)
        binding = mainBinding
        setContentView(mainBinding.root)

        try {
            scheduleDailySync()
        } catch (e: SecurityException) {
        } catch (e: Exception) { }
        mainBinding.syncButton.setOnClickListener { syncToFirebase() }
        mainBinding.enableAccessibility.setOnClickListener { openAccessibilitySettings() }
        updateAccessibilityButtonVisibility()

        val listAdapter = AppListAdapter(
            onDeleteClick = { appInfo -> PackageHelper.requestUninstall(this, appInfo.packageName) },
            onBugClick = { appInfo ->
                BugPackages.toggle(this, appInfo.packageName)
                loadApps()
            }
        )
        adapter = listAdapter
        mainBinding.recycler.layoutManager = LinearLayoutManager(this)
        mainBinding.recycler.adapter = listAdapter

        mainBinding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { showTab(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadApps()
    }

    companion object {
        private const val PREFS_NAME = "managemom_permission"
        private const val KEY_DELETE_GRANTED = "delete_permission_granted"
    }

    override fun onResume() {
        super.onResume()
        if (binding == null) return
        PendingDeleteSnackbar.onNewSignal = { appNames ->
            showDeleteSignalSnackbar(appNames)
            loadApps()
        }
        loadApps()
        PendingDeleteSnackbar.getAndClear()?.let { showDeleteSignalSnackbar(it); loadApps() }
        updateAccessibilityButtonVisibility()
        triggerUninstallForBlocklistedInstalled()

        // Always attempt Firebase sync on resume
        syncToFirebase()

        try {
            FirebaseSync.processPendingUninstalls(this) { appNames ->
                runOnUiThread {
                    showDeleteSignalSnackbar(appNames)
                    loadApps()
                }
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) { }
    }

    override fun onPause() {
        PendingDeleteSnackbar.onNewSignal = null
        super.onPause()
    }

    private fun showDeleteSignalSnackbar(appNames: List<String>) {
        if (appNames.isEmpty()) return
        val b = binding ?: return
        val message = appNames.joinToString(", ")
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun loadApps() {
        val ad = adapter ?: return
        val b = binding ?: return
        val apps = PackageHelper.getInstalledApps(this)
        appsBySource = PackageHelper.groupAppsBySource(this, apps)

        val prevTabPos = b.tabs.selectedTabPosition.coerceIn(0, Int.MAX_VALUE)
        b.tabs.removeAllTabs()
        appsBySource.forEach { (sourceLabel, _) ->
            b.tabs.addTab(b.tabs.newTab().setText(sourceLabel))
        }

        if (appsBySource.isEmpty()) {
            ad.submitList(emptyList())
        } else {
            val tabPos = prevTabPos.coerceIn(0, appsBySource.size - 1)
            b.tabs.selectTab(b.tabs.getTabAt(tabPos))
            showTab(tabPos)
        }
    }

    private fun showTab(position: Int) {
        val ad = adapter ?: return
        if (position !in appsBySource.indices) return
        val apps = appsBySource[position].second
        val items = apps.map { AppListItem.App(it) }
        ad.submitList(items)
    }

    /**
     * When the app is in the foreground, uninstall intents are allowed. When we're in the
     * background (e.g. FCM), the system blocks starting the uninstall activity. So we trigger
     * uninstall here for any blocklisted package that is still installed when the user opens the app.
     */
    private fun triggerUninstallForBlocklistedInstalled() {
        val blocklisted = UninstallBlocklist.getPackages(this)
        val pm = packageManager
        for (pkg in blocklisted) {
            if (pkg in uninstallTriggeredThisSession) continue
            try {
                pm.getPackageInfo(pkg, 0)
                uninstallTriggeredThisSession.add(pkg)
                PackageHelper.requestUninstall(this, pkg)
                break
            } catch (_: PackageManager.NameNotFoundException) { }
        }
    }

    private fun syncToFirebase() {
        val ad = adapter ?: return
        FirebaseSync.syncToFirebase(
            this,
            onSuccess = {
                runOnUiThread {
                    val apps = PackageHelper.getInstalledApps(this).map { it.copy(isSynced = true) }
                    appsBySource = PackageHelper.groupAppsBySource(this, apps)
                    binding?.tabs?.let { tabs ->
                        val prevPos = tabs.selectedTabPosition.coerceIn(0, Int.MAX_VALUE)
                        tabs.removeAllTabs()
                        appsBySource.forEach { (sourceLabel, _) ->
                            tabs.addTab(tabs.newTab().setText(sourceLabel))
                        }
                        if (appsBySource.isNotEmpty()) {
                            val pos = prevPos.coerceIn(0, appsBySource.size - 1)
                            tabs.selectTab(tabs.getTabAt(pos))
                            val items = appsBySource[pos].second.map { AppListItem.App(it) }
                            ad.submitList(items)
                        }
                    }
                }
            }
        )
    }

    private fun scheduleDailySync() {
        try {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            // WorkManager may fail with SecurityException if GMS is unavailable (e.g. emulator)
        }
    }

    private fun updateAccessibilityButtonVisibility() {
        binding?.enableAccessibility?.visibility =
            if (ManageMomAccessibilityService.isEnabled(this)) View.GONE else View.VISIBLE
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
