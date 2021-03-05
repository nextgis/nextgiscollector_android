/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2018-2021 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.collector.activity

import android.Manifest
import android.accounts.Account
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.nextgis.collector.R
import com.nextgis.collector.model.ProjectModel
import com.nextgis.collector.util.NetworkUtil
import com.nextgis.maplib.api.INGWLayer
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.maplibui.activity.TracksActivity
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.TrackerService.hasUnfinishedTracks
import com.nextgis.maplibui.service.TrackerService.isTrackerServiceRunning
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.ControlHelper
import com.nextgis.maplibui.util.ExportGeoJSONBatchTask
import com.nextgis.maplibui.util.NGIDUtils.COLLECTOR_HUB_URL
import com.nextgis.maplibui.util.NGIDUtils.get
import com.pawegio.kandroid.*
import org.json.JSONObject
import java.io.File
import java.io.IOException


abstract class ProjectActivity : BaseActivity() {
    @Volatile
    private var update = false
    private var syncReceiver: SyncReceiver = SyncReceiver()
    private var onPermissionCallback: OnPermissionCallback? = null
    private val isSyncActive: Boolean
        get() {
            return findViewById<FrameLayout>(R.id.overlay).visibility == View.VISIBLE
        }

    interface OnPermissionCallback {
        fun onPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter()
        intentFilter.addAction(SyncAdapter.SYNC_START)
        intentFilter.addAction(SyncAdapter.SYNC_FINISH)
        intentFilter.addAction(SyncAdapter.SYNC_CANCELED)
        registerReceiver(syncReceiver, intentFilter)

        get(this) { response ->
            if (response.isOk) {
                var support = getExternalFilesDir(null)
                support = if (support == null) File(filesDir, Constants.SUPPORT) else File(support, Constants.SUPPORT)
                try {
                    FileUtil.writeToFile(support, response.responseBody)
                } catch (ignored: IOException) {
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        init()
    }

    protected abstract fun init()

    protected fun setup(with: Toolbar, skipUpdateCheck: Boolean? = false) {
        setSupportActionBar(with)
        if (skipUpdateCheck != true)
            checkUpdates()
        init()
    }

    override fun onStart() {
        super.onStart()
        invalidateOptionsMenu()
    }

    private fun showUpdateDialog(version: Int) {
        if (!window.decorView.rootView.isShown || isSyncActive)
            return

        AlertDialog.Builder(this).setTitle(R.string.update_available)
                .setMessage(getString(R.string.update_for, project.title, project.version, version))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> update() }
                .show()
    }

    private fun update(withDialog: Boolean = true) {
        if (isSyncActive) {
            if (withDialog) {
                val progressBar = ProgressBar(this)
                val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = ControlHelper.dpToPx(16, resources)
                progressBar.layoutParams = lp
                AlertDialog.Builder(this)
                        .setTitle(R.string.wait_sync_stopping)
                        .setView(progressBar)
                        .setCancelable(false)
                        .show()
            }
            runDelayedOnUiThread(5000) { update(withDialog = false) }
            return
        }
        val hasChanges = hasChanges()
        setSubtitle(hasChanges)
        if (hasChanges) {
            update = true
            sync()
        } else {
            update = false
            change(project)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(syncReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        setTracksTitle(menu?.findItem(R.id.menu_track))
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_sync -> sync()
            R.id.menu_change_project -> ask()
            R.id.menu_backup -> {
                Log.d(Constants.TAG, "Backup button pressed")
                backup()
            }
            R.id.menu_about -> about()
            R.id.menu_track -> controlTrack(item)
            R.id.menu_track_list -> startActivity<TracksActivity>()
            R.id.menu_settings -> startActivity<PreferenceActivity>()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun about() {
        var info = ""
        if (project.description.isNotBlank())
            info += project.description + "\n\n"
        info += getString(R.string.project_version, project.version)
//        TODO add instance name and project id
//        if (project.instance.isNotBlank())
//            info += "\n" + getString(R.string.project_instance, project.url)
        val builder = AlertDialog.Builder(this).setTitle(project.title)
                .setMessage(info)
                .setPositiveButton(R.string.ok, null)
                .create()

        builder.show()
        val message = builder.findViewById<TextView>(android.R.id.message)
        if (message != null) {
            message.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun backup() {
        if (!PermissionUtil.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d(Constants.TAG, "No write permission granted, request it")
            requestForPermissions(object : OnPermissionCallback {
                override fun onPermissionGranted() {
                    Log.d(Constants.TAG, "Write permission granted")
                    backup()
                }
            }, true, geo = false)
            return
        }
        Log.d(Constants.TAG, "Backup started, total ${map.layerCount} layers")
        val layers = arrayListOf<VectorLayer>()
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            Log.d(Constants.TAG, "Processing layer '${layer.name}'")
            if (layer is NGWVectorLayer) {
                Log.d(Constants.TAG, "It is NGWVectorLayer, append")
                layers.add(layer)
            }
        }
        val saveLog = preferences.getBoolean("save_log", false)
        Log.d(Constants.TAG, "Execute ExportGeoJSONBatchTask")
        val exportTask = ExportGeoJSONBatchTask(this, layers, true, project.title, saveLog)
        exportTask.execute()
    }

    private fun controlTrack(item: MenuItem) {
        if (!PermissionUtil.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestForPermissions(object : OnPermissionCallback {
                override fun onPermissionGranted() {
                    controlTrack(item)
                }
            }, false)
            return
        }

        val trackerService = IntentFor<TrackerService>(this)
        trackerService.putExtra(ConstantsUI.TARGET_CLASS, this.javaClass.name)
        val unfinished = setTracksTitle(item)

        if (isTrackerServiceRunning(this)) {
            trackerService.action = TrackerService.ACTION_STOP
        } else if (unfinished) {
            trackerService.action = TrackerService.ACTION_STOP
            ContextCompat.startForegroundService(this, trackerService)
            trackerService.action = null
        }

        ContextCompat.startForegroundService(this, trackerService)
        runDelayedOnUiThread(2500) { setTracksTitle(item) }
    }

    private fun setTracksTitle(item: MenuItem?): Boolean {
        val unfinished = hasUnfinishedTracks(this)
        item?.setTitle(if (unfinished) R.string.tracks_stop else R.string.start)
        return unfinished
    }

    protected fun requestForPermissions(onPermissionCallback: OnPermissionCallback, memory: Boolean, geo: Boolean = true) {
        this.onPermissionCallback = onPermissionCallback
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val photo = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val geoStatus = ActivityCompat.checkSelfPermission(this, fine)
        val memStatus = ActivityCompat.checkSelfPermission(this, photo)
        val geoAllowed = geoStatus == PackageManager.PERMISSION_GRANTED
        val memAllowed = memStatus == PackageManager.PERMISSION_GRANTED
        val permissions = arrayListOf<String>()
        if (!geoAllowed && geo) {
            permissions.add(coarse)
            permissions.add(fine)
        }
        if (!memAllowed && memory)
            permissions.add(photo)
        if (permissions.size > 0)
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), AddFeatureActivity.PERMISSIONS_CODE)
        else
            onPermissionCallback.onPermissionGranted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var granted = requestCode == AddFeatureActivity.PERMISSIONS_CODE
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted) {
            onPermissionCallback?.onPermissionGranted()
        } else
            longToast(R.string.permission_denied)
    }

    private fun updateSubtitle() {
        setSubtitle(hasChanges())
    }

    protected fun setSubtitle(hasChanges: Boolean) {
        supportActionBar?.setSubtitle(if (hasChanges) R.string.not_synced else R.string.all_synced)
    }

    protected fun sync() {
        val accounts = ArrayList<Account>()
        val layers = ArrayList<INGWLayer>()

        accountManager?.let {
            for (account in it.getAccountsByType(app.accountsType)) {
                layers.clear()
                MapContentProviderHelper.getLayersByAccount(map, account.name, layers)
                val syncEnabled = NGWSettingsFragment.isAccountSyncEnabled(account, app.authority)
                if (layers.size > 0 && syncEnabled)
                    accounts.add(account)
            }

//            for (account in accounts) {
            accounts.firstOrNull()?.let {
                val settings = Bundle()
                settings.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                settings.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                ContentResolver.requestSync(accounts.first(), app.authority, settings)
            }
//            }
        }
    }

    private fun ask() {
        AlertDialog.Builder(this).setTitle(R.string.change_project)
                .setMessage(R.string.change_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> change() }
                .show()
    }

    private fun showSnackbar(info: Int) {
        val snackbar = Snackbar.make(findViewById(R.id.coordinator), info, Snackbar.LENGTH_SHORT)
        snackbar.show()
    }

    private fun checkUpdates() {
        showSnackbar(R.string.check_update)
        runAsync {
            var json = JSONObject()
            val id = project.id
            val private = project.private
            val base = preferences.getString("collector_hub_url", COLLECTOR_HUB_URL)
            val url = ProjectModel.getBaseUrl(base ?: COLLECTOR_HUB_URL, private)
            val email = NetworkUtil.getEmailOrUsername(preferences)
            val response = ProjectModel.getResponse("$url/$id", email)
            response?.let {
                try {
                    json = JSONObject(response.responseBody)
                } catch (e: Exception) {
                }
            }

            runOnUiThread {
                val version = json.optInt("version", -1)
                when {
                    version > project.version -> showUpdateDialog(version)
                    version == -1 -> toast(R.string.error_network_unavailable)
                    else -> toast(R.string.no_updates)
                }
            }
        }
    }

    protected inner class SyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(Constants.TAG, "Got: ${intent.action}")
            if (intent.action == SyncAdapter.SYNC_START) {
                findViewById<FrameLayout>(R.id.overlay).visibility = View.VISIBLE
            } else if (intent.action == SyncAdapter.SYNC_FINISH || intent.action == SyncAdapter.SYNC_CANCELED) {
                if (intent.hasExtra(SyncAdapter.EXCEPTION))
                    toast(intent.getStringExtra(SyncAdapter.EXCEPTION))

                findViewById<FrameLayout>(R.id.overlay).visibility = View.GONE
                if (update)
                    update()
                else
                    updateSubtitle()
            }
        }
    }
}