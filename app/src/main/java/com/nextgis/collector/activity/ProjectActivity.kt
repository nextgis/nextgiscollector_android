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
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.snackbar.Snackbar
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.R
import com.nextgis.collector.model.ProjectModel
import com.nextgis.collector.service.OfflineIntentService
import com.nextgis.collector.util.NetworkUtil
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.api.INGWLayer
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.TrackLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.MapUtil
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.maplibui.activity.TracksActivity
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.TrackerService.*
import com.nextgis.maplibui.util.*
import com.nextgis.maplibui.util.NGIDUtils.COLLECTOR_HUB_URL
import com.nextgis.maplibui.util.NGIDUtils.get
import com.pawegio.kandroid.*
import org.json.JSONObject
import java.io.*
import java.security.AccessController.getContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


abstract class ProjectActivity : BaseActivity() {

    companion object {
        const val LOCATION_REQUEST = 703
    }

    @Volatile
    private var update = false
    private var syncReceiver: SyncReceiver = SyncReceiver()
    private var onPermissionCallback: OnPermissionCallback? = null
    private val isSyncActive: Boolean
        get() {
            return findViewById<FrameLayout>(R.id.overlay).visibility == View.VISIBLE
        }
    protected var trackItem: MenuItem? = null

    protected var mMessageReceiver: MessageReceiver? = null

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
        mMessageReceiver = MessageReceiver()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        loadProject()
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
        setTracksTitle(menu?.findItem(R.id.menu_track))
        //updateTracksMenuItems(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        //updateTracksMenuItems(menu)
        setTracksTitle(menu?.findItem(R.id.menu_track))
        menu?.findItem(R.id.menu_share_log)?.isVisible = preferences.getBoolean("save_log", false)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sync -> sync()
            R.id.menu_change_project -> ask()
            R.id.menu_backup -> {
                HyperLog.v(Constants.TAG, "Backup button pressed")
                backup()
            }
            R.id.menu_share_log -> shareLog()
            R.id.menu_about -> about()
            R.id.menu_track -> {
                trackItem = item
                mMessageReceiver?.updateTrackItem(item)
                checkForBackgroundPermission(item)
            }
            R.id.menu_track_list -> startActivity<TracksActivity>()
            R.id.menu_settings -> startActivity<PreferenceActivity>()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun shareLog() {
        HyperLog.getDeviceLogsInFile(this)
        val dir = File(getExternalFilesDir(null), "LogFiles")
        val size = FileUtil.getDirectorySize(dir)
        if (size == 0L) {
            toast(R.string.error_empty_dataset)
            return
        }

        val files = zipLogs(dir)
        val type = "text/plain"
        UiUtil.share(files, type, this, false)
    }

    private fun zipLogs(dir: File): File? {
        var temp = MapUtil.prepareTempDir(this, "shared_layers", false)
        val outdated = arrayListOf<File>()
        try {
            val fileName = "ng-logs.zip"
            if (temp == null) {
                toast(R.string.error_file_create)
            }

            temp = File(temp, fileName)
            temp.createNewFile()
            val fos = FileOutputStream(temp, false)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            val buffer = ByteArray(1024)
            var length: Int

            for (file in dir.listFiles()) {
                if (System.currentTimeMillis() - file.lastModified() > 60 * 60 * 1000)
                    outdated.add(file)
                try {
                    val fis = FileInputStream(file)
                    zos.putNextEntry(ZipEntry(file.name))
                    while (fis.read(buffer).also { length = it } > 0) zos.write(buffer, 0, length)
                    zos.closeEntry()
                    fis.close()
                } catch (ex: Exception) {
                }
            }

            zos.close()
            fos.close()
        } catch (e: IOException) {
            temp = null
        }
        for (file in outdated) {
            file.delete()
        }
        return temp
    }

    private fun about() {
        var info = ""
        if (project.description.isNotBlank())
            info += project.description + "\n\n"
        info += getString(R.string.project_version, project.version)
        if (project.url.isNotBlank()) {
            val url = "${project.url}/resource/${project.ngwId}"
            info += "\n" + getString(R.string.project_instance, url, url)
        }
        val ss = SpannableString(info); // msg should have url to enable clicking
        Linkify.addLinks(ss, Linkify.ALL);
        val builder = AlertDialog.Builder(this).setTitle(project.title)
                .setMessage(ss)
                .setPositiveButton(R.string.ok, null)
                .create()

        builder.show()
        val message = builder.findViewById<TextView>(android.R.id.message)
        if (message != null) {
            message.movementMethod = LinkMovementMethod.getInstance()
            message.linksClickable = true
        }
    }

    private fun backup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !PermissionUtil.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            HyperLog.v(Constants.TAG, "No write permission granted, request it")
            requestForPermissions(object : OnPermissionCallback {
                override fun onPermissionGranted() {
                    HyperLog.v(Constants.TAG, "Write permission granted")
                    backup()
                }
            }, true, geo = false)
            return
        }
        HyperLog.v(Constants.TAG, "Backup started, total ${map.layerCount} layers")
        val layers = arrayListOf<VectorLayer>()
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            HyperLog.v(Constants.TAG, "Processing layer '${layer.name}'")
            if (layer is NGWVectorLayer) {
                if (!layer.isEditable) {
                    HyperLog.v(Constants.TAG, "It is NGWVectorLayer, but not editable")
                    continue
                }
                HyperLog.v(Constants.TAG, "It is NGWVectorLayer, append")
                layers.add(layer)
            }
        }

        // add tracksinfo
        val tracksList = ArrayList<android.util.Pair<Int,String >>()

        val application = application as IGISApplication
        val authority = application.authority
        val projection = null //arrayOf(TrackLayer.FIELD_ID, TrackLayer.FIELD_NAME, TrackLayer.FIELD_VISIBLE)
        val selection = null // TrackLayer.FIELD_ID + " = ?"
        val mContentUriTracks = Uri.parse("content://" + authority + "/" + TrackLayer.TABLE_TRACKS)

        val tracksCursor : Cursor? = getContentResolver().query(
                mContentUriTracks,
                projection,
                selection,
                null,
                null)
        tracksCursor?.let {
            while (it.moveToNext()){
                val trackId = tracksCursor.getInt(tracksCursor.getColumnIndex(TrackLayer.FIELD_ID))
                val trackName = tracksCursor.getString(tracksCursor.getColumnIndex(TrackLayer.FIELD_NAME))
                var endTrack : Int? = tracksCursor.getInt(tracksCursor.getColumnIndex(TrackLayer.FIELD_END))
                if (endTrack != null)
                    tracksList.add(android.util.Pair(trackId,trackName))
                }
        }

        HyperLog.v(Constants.TAG, "Create dialog to choose layers for backup")
        var checked = BooleanArray(layers.size)
        for (i in 0 until layers.size) {
            checked[i] = true
        }
        var names = layers.map { it.name }
        val tracksName = getString(R.string.tracks_to_archive)
        if (tracksList.size > 0){
            names = names + tracksName
            checked = checked + true
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.choose_layers)
                .setMultiChoiceItems(names.toTypedArray(), checked) { _, id, selected -> checked[id] = selected }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    HyperLog.v(Constants.TAG, "Filter selected layers only")
                    val list = layers.filterIndexed { index, _ -> checked[index] }
                    if (list.isEmpty() &&
                        (tracksList.size > 0 &&
                                !checked[checked.size -1]) ) {
                        toast(R.string.error_empty_dataset)
                        return@setPositiveButton
                    }
                    if (tracksList.size > 0 && !checked[checked.size -1])
                        tracksList.clear()
                    // choose one vector layer and it is not tracks
                    if (list.size == 1 &&  tracksList.size == 0 ) {
                        HyperLog.v(Constants.TAG, "Execute ExportGeoJSONTask")
                        val exportTask = ExportGeoJSONTask(this, list.first(), true,
                            false, false, null, false)
                        exportTask.execute()
                        return@setPositiveButton
                    }

                    // choose more than one layers
                    HyperLog.v(Constants.TAG, "Execute ExportGeoJSONBatchTask")
                    val exportTask = ExportGeoJSONBatchTask(this, list, true,
                            project.title, tracksList)
                    exportTask.execute()
                }
        HyperLog.v(Constants.TAG, "Show dialog to choose layers for backup")
        builder.show()
    }

    private fun checkForBackgroundPermission(item: MenuItem) {
        if (!PermissionUtil.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestForPermissions(object : OnPermissionCallback {
                override fun onPermissionGranted() {
                    checkForBackgroundPermission(item)
                }
            }, false)
            return
        }

        showBackgroundDialog(this, object : BackgroundPermissionCallback {
            override fun beforeAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    trackItem = item
                    mMessageReceiver?.updateTrackItem(item)
                    val permission = Manifest.permission.ACCESS_FINE_LOCATION
                    requestPermissions(R.string.no_permission_granted, R.string.requested_permissions, LOCATION_REQUEST, permission)
                } else {
                    controlTrack(item)
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.Q)
            override fun onAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    trackItem = item
                    mMessageReceiver?.updateTrackItem(item)
                    requestPermissions()
                } else {
                    controlTrack(item)
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.Q)
            override fun afterAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    trackItem = item
                    mMessageReceiver?.updateTrackItem(item)
                    requestPermissions()
                } else {
                    controlTrack(item)
                }
            }
        })
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, LOCATION_REQUEST)
    }

    private fun controlTrack(item: MenuItem?) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(trackerService)
        } else {
            ContextCompat.startForegroundService(this, trackerService)
        }
//        runDelayedOnUiThread(2500) {
//            Log.e("TRACCKK", "runDelayedOnUiThread(2500)")
//            setTracksTitle(item)
//        }

        runDelayedOnUiThread(300) {
            setTracksTitle(item)
        }
    }

    private fun setTracksTitle(item: MenuItem?): Boolean {

        val unfinished = hasUnfinishedTracks(this)
        val caption = getString(if (unfinished) R.string.tracks_stop else R.string.start)
        item?.setTitle(caption)
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                permissions.add(photo)
        if (permissions.size > 0)
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), AddFeatureActivity.PERMISSIONS_CODE)
        else
            onPermissionCallback.onPermissionGranted()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var granted = true
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted) {
            if (requestCode == AddFeatureActivity.PERMISSIONS_CODE)
                onPermissionCallback?.onPermissionGranted()
            if (requestCode ==  LOCATION_REQUEST) {
                controlTrack(trackItem)
                trackItem = null
                mMessageReceiver?.clearTrackItem()
            }
        } else
            longToast(R.string.permission_denied)
    }

    private fun updateSubtitle() {
        setSubtitle(hasChanges())
    }

    protected fun setSubtitle(hasChanges: Boolean) {
        supportActionBar?.setSubtitle(if (hasChanges) R.string.not_synced else R.string.all_synced)
    }

    private fun checkAccountForSync(context: Context, account: Account) {
        val isYourAccountSyncEnabled =
            ContentResolver.getSyncAutomatically(account, getString(R.string.provider_auth))
        if (!isYourAccountSyncEnabled) {
            val onClickListener =
                DialogInterface.OnClickListener { dialog, which ->
                    ContentResolver.setSyncAutomatically(
                        account,
                        getString(R.string.provider_auth),
                        true
                    )
                }
            AlertDialog.Builder(context).setTitle(R.string.alert_sync_title)
                .setMessage(R.string.alert_sync_turned_off)
                .setPositiveButton(R.string.yes, onClickListener)
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show()
        }
    }

    protected fun sync() {
        val accounts = ArrayList<Account>()
        val layers = ArrayList<INGWLayer>()

        accountManager?.let {
            for (account in it.getAccountsByType(app.accountsType)) {
                checkAccountForSync(this, account)
                layers.clear()
                MapContentProviderHelper.getLayersByAccount(map, account.name, layers)
                val syncEnabled = NGWSettingsFragment.isAccountSyncEnabled(account, app.authority)
                if (layers.size > 0 && syncEnabled)
                    accounts.add(account)
            }

//            for (account in accounts) {
            accounts.firstOrNull()?.let {

                val mPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val base = mPreferences.getString("ngid_url", NGIDUtils.NGID_MY)

                if (!NGIDUtils.NGID_MY.equals(base)) {
                    OfflineIntentService.startActionSync(this)
                } else {
                    val settings = Bundle()
                    settings.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    settings.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    ContentResolver.requestSync(
                        accounts.first(),
                        app.authority,
                        settings
                    )
                }
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
            HyperLog.v(Constants.TAG, "Got: ${intent.action}")
            if (intent.action == SyncAdapter.SYNC_START) {
                findViewById<FrameLayout>(R.id.overlay).visibility = View.VISIBLE
            } else if (intent.action == SyncAdapter.SYNC_FINISH || intent.action == SyncAdapter.SYNC_CANCELED) {
                if (intent.hasExtra(SyncAdapter.EXCEPTION))
                    toast(intent.getStringExtra(SyncAdapter.EXCEPTION) ?: getString(R.string.sync_error))

                findViewById<FrameLayout>(R.id.overlay).visibility = View.GONE
                if (update)
                    update()
                else
                    updateSubtitle()
            }
        }
    }

    protected class MessageReceiver() : BroadcastReceiver() {

        var trackItem : MenuItem? = null;

        fun updateTrackItem(item : MenuItem){
            trackItem = item
        }

        fun clearTrackItem(){
            trackItem = null
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ConstantsUI.MESSAGE_INTENT_TRACK) {
                val trackAction = intent.getBooleanExtra(ConstantsUI.KEY_MESSAGE_TRACK, false)

                val caption = context.getString(if (trackAction) R.string.tracks_stop else R.string.start)

                trackItem?.setTitle(caption)

            }

        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConstantsUI.MESSAGE_INTENT_TRACK)
        registerReceiver(mMessageReceiver, intentFilter)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    override fun onPause() {
        super.onPause()
        unregisterReceiver(mMessageReceiver)

    }


}