/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2018 NextGIS, info@nextgis.com
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
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.collector.model.ProjectModel
import com.nextgis.maplib.api.INGWLayer
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.TrackLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplibui.activity.TracksActivity
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.TrackerService.hasUnfinishedTracks
import com.nextgis.maplibui.service.TrackerService.isTrackerServiceRunning
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.NGIDUtils.PREF_EMAIL
import com.pawegio.kandroid.*
import org.json.JSONObject


abstract class ProjectActivity : BaseActivity() {
    @Volatile
    private var total: Int = 0
    private var update = false
    private var syncReceiver: SyncReceiver = SyncReceiver()
    private var onPermissionCallback: OnPermissionCallback? = null

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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        init()
    }

    protected abstract fun init()

    protected fun setup(with: Toolbar) {
        setSupportActionBar(with)
        checkUpdates()
        init()
    }

    override fun onStart() {
        super.onStart()
        invalidateOptionsMenu()
    }

    private fun showUpdateDialog(version: Int) {
        AlertDialog.Builder(this).setTitle(R.string.update_available)
                .setMessage(getString(R.string.update_for, project.title, project.version, version))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> update() }
                .show()
    }

    private fun update() {
        if (updateSubtitle()) {
            update = true
            sync()
        } else {
            update = false
            change(project.id)
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
            R.id.menu_track -> controlTrack(item)
            R.id.menu_track_list -> startActivity<TracksActivity>()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun controlTrack(item: MenuItem) {
        val trackerService = IntentFor<TrackerService>(this)
        trackerService.putExtra(ConstantsUI.TARGET_CLASS, this.javaClass.name)
        val unfinished = setTracksTitle(item)

        if (isTrackerServiceRunning(this)) {
            trackerService.action = TrackerService.ACTION_STOP
        } else if (unfinished) {
            trackerService.action = TrackerService.ACTION_STOP
            startService(trackerService)
            trackerService.action = null
        }

        startService(trackerService)
        setTracksTitle(item)
    }

    private fun setTracksTitle(item: MenuItem?): Boolean {
        val unfinished = hasUnfinishedTracks(this)
        item?.setTitle(if (unfinished) R.string.tracks_stop else R.string.start)
        return unfinished
    }

    protected fun requestForPermissions(onPermissionCallback: OnPermissionCallback, memory: Boolean) {
        this.onPermissionCallback = onPermissionCallback
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val photo = Manifest.permission.READ_EXTERNAL_STORAGE
        val geoStatus = ActivityCompat.checkSelfPermission(this, fine)
        val memStatus = ActivityCompat.checkSelfPermission(this, photo)
        if (geoStatus != PackageManager.PERMISSION_GRANTED || memStatus != PackageManager.PERMISSION_GRANTED && memory) {
            val permissions: Array<String> = if (memory)
                arrayOf(coarse, fine, photo)
            else
                arrayOf(coarse, fine)
            ActivityCompat.requestPermissions(this, permissions, AddFeatureActivity.PERMISSIONS_CODE)
        } else
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

    private fun updateSubtitle(): Boolean {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                if (changesCount > 0) {
                    setSubtitle(true)
                    return true
                }
            }
        }
        return false
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
            val settings = Bundle()
            settings.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            settings.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            ContentResolver.requestSync(accounts.first(), app.authority, settings)
//            }
        }

        total = accounts.size
    }

    private fun change(id: Int = -1) {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val account = app.getAccount(layer.accountName)
                app.removeAccount(account)
            }
        }

        val tracks = mapView.getLayersByType(Constants.LAYERTYPE_TRACKS)
        if (tracks.size > 0) {
            map.removeLayer(tracks[0])
            val uri = Uri.parse("content://" + app.authority + "/" + TrackLayer.TABLE_TRACKS)
            contentResolver.delete(uri, null, null)
        }
        map.delete()
        preferences.edit().remove("project").apply()
        val intent = IntentFor<ProjectListActivity>(this)
        intent.putExtra("project", id)
        startActivity(intent)
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
            val id = project.id
            val email = preferences.getString(PREF_EMAIL, "")
            val response = ProjectModel.getResponse("$id", email)
            var json = JSONObject()
            response?.let {
                try {
                    json = JSONObject(response.responseBody)
                } catch (e: Exception) {
                }
            }

            runOnUiThread {
                val version = json.optInt("version")
                when {
                    version > project.version -> showUpdateDialog(version)
                    version == 0 -> toast(R.string.error_network_unavailable)
                    else -> toast(R.string.no_updates)
                }
            }
        }
    }

    protected inner class SyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(Constants.TAG, "Got: ${intent.action}")
            if (intent.action == SyncAdapter.SYNC_START) {
                if (total > 0)
                    findViewById<FrameLayout>(R.id.overlay).visibility = View.VISIBLE
                else
                    showSnackbar(info = R.string.sync_changes)
            } else if (intent.action == SyncAdapter.SYNC_FINISH || intent.action == SyncAdapter.SYNC_CANCELED) {
                if (intent.hasExtra(SyncAdapter.EXCEPTION))
                    toast(intent.getStringExtra(SyncAdapter.EXCEPTION))

                total = 0//--
//                if (total <= 0) {
                findViewById<FrameLayout>(R.id.overlay).visibility = View.GONE
                if (update)
                    update()
                else
                    updateSubtitle()
//                }
            }
        }
    }
}