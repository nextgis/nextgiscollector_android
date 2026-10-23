/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
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

package com.nextgis.collector

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.activity.AddFeatureActivity
import com.nextgis.collector.activity.PreferenceActivity
import com.nextgis.collector.util.Logger
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.map.MapBase
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.NGWUtil
import com.nextgis.maplib.util.NetworkUtil
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.mapui.TrackLayerUI
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.NotificationHelper
import kotlin.system.exitProcess

class CollectorApplication : GISApplication() {

    private  var posponedLayeId = -1;
    private var syncReceiver: SyncReceiver = SyncReceiver()

    var geoEnvelope: GeoEnvelope? = null;
    var defBOrdersWasApply = false

    companion object {
        const val BASE_URL = "https://collector.nextgis.com/api/project"
        const val TREE = "resource.tree"
        var isSyncProgress = false
        const val NOTIFICATION_ID: Int = 517
    }

    protected inner class SyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            HyperLog.v(Constants.TAG, "Got: ${intent.action}")
            if (intent.action == SyncAdapter.SYNC_START) {
                isSyncProgress = true
            } else if (intent.action == SyncAdapter.SYNC_FINISH || intent.action == SyncAdapter.SYNC_CANCELED) {
                isSyncProgress = false
            }
        }
    }

    override fun onCreate() {
//        if (!BuildConfig.DEBUG)
//            Sentry.init(AndroidSentryClientFactory(applicationContext)) // work if disable init
        super.onCreate()
        if (mSharedPreferences.getBoolean("save_log", false)) {
            Logger.initialize(this)
        }

        // set userAgent info
        //        Sentry.captureMessage("NGM2 Sentry is init.", Sentry.SentryEventLevel.DEBUG);

        // set userAgent info
        try {

            //(NGID 542d1f02-8c93-448f-8bb7-ccfb28e6b401; Supported; DID 3F60F6E9; Build 99; Vendor TCL)
            NetworkUtil.setUserAgentPrefix(
                this, "NextGIS-Collector/" + BuildConfig.VERSION_NAME,
                TrackerService.getUid(this), BuildConfig.VERSION_CODE
            )
            NetworkUtil.setUserAgentPostfix(System.getProperty("http.agent") )
        } catch (ex: java.lang.Exception) {
            Log.e(Constants.TAG, ex.message!!)
        }

        checkTracksLayerExistence()
        updateFromPreviousVersion()
        NGWUtil.UUID = TrackerService.getUid(this)
        NGWUtil.NGUA = "ng_collector"

        val intentFilter = IntentFilter()
        intentFilter.addAction(SyncAdapter.SYNC_START)
        intentFilter.addAction(SyncAdapter.SYNC_FINISH)
        intentFilter.addAction(SyncAdapter.SYNC_CANCELED)
        registerReceiver(syncReceiver, intentFilter, RECEIVER_EXPORTED)


    }

    private fun updateFromPreviousVersion() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val savedVersionCode = mSharedPreferences.getInt("last", 0)

//        if (savedVersionCode == 0)
//            mSharedPreferences.edit().putBoolean(KEY_PREF_TRACK_SEND, true).apply()

        if (savedVersionCode < currentVersionCode) {
            mSharedPreferences.edit().putInt("last", currentVersionCode).apply()
        }
    }

    override fun getAuthority(): String {
        return BuildConfig.providerAuth
    }

    override fun showSettings(setting: String?, code: Int, activity: Activity?) {
        val intent = Intent(this, PreferenceActivity::class.java)
        activity?.startActivity(intent)
    }

    override fun sendEvent(category: String?, action: String?, label: String?) {

    }

    override fun sendScreen(name: String?) {

    }

    override fun getAccountsType(): String {
        return BuildConfig.collector_accounts_auth //Constants.NGW_ACCOUNT_TYPE
    }

    override fun isCollectorApplication(): Boolean {
        return true
    }

    private fun checkTracksLayerExistence() {
        val tracks = ArrayList<ILayer>()
        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks)
        if (tracks.isEmpty()) {
            try {
                val trackLayer = TrackLayerUI(applicationContext, mMap.createLayerStorage("tracks"))
                trackLayer.name = getString(R.string.tracks)
                trackLayer.isVisible = true
                mMap.addLayer(trackLayer)
                mMap.save()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.restart_app, Toast.LENGTH_SHORT).show()
                exitProcess(0)
            }
        } else {
            //mMap.moveLayer(, tracks.first())
            mMap.moveLayer(map.layerCount - 1, tracks.first())
            //mMap.moveLayer(0, tracks.first())
            mMap.save()
        }
    }

    override fun getMapBase(): MapBase? {
        return getMap()
    }

    override fun startCreateNGWLayerSync(lpath: String?) {
        // no need here
        // it's method  for mobile app
    }

    override fun checkTracksLayerExist() {
        val LAYER_TRACKS = "tracks"
        val tracks: MutableList<ILayer?> = java.util.ArrayList<ILayer?>()
        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks)
        if (tracks.isEmpty()) {
            val trackLayerName: String? = getString(com.nextgis.maplib.R.string.tracks)
            val trackLayer =
                TrackLayerUI(
                    getApplicationContext(),
                    mMap.createLayerStorage(LAYER_TRACKS)
                )
            trackLayer.setName(trackLayerName)
            trackLayer.setVisible(true)
            mMap.addLayer(trackLayer)
            mMap.save()
        }
    }

    override fun setPostponedLayerId(layerId: Int) {
        this.posponedLayeId = layerId
    }

    override fun getPostponedLayerId(): Int {
        return this.posponedLayeId
    }

    override
    fun setPostponedExtent(geoEnvelopeNew: GeoEnvelope){
        geoEnvelope = geoEnvelopeNew;
    }

    override
    fun getPostponedExtent(): GeoEnvelope?{
        return geoEnvelope
    }

    override
    fun setBordersWasApply(apply: Boolean){
        defBOrdersWasApply = apply;
    }

    override fun getBordersWasApply(): Boolean {
        HyperLog.v("AREA", "get getBordersWasApply = "  +  (if (defBOrdersWasApply) "true" else "false"))
        return defBOrdersWasApply
    }

    override fun getSelfContext(): Context? {
        return this
    }

    override fun sendNotification(
        context: Context?,
        notificationType: String?,
        message: String?
    ) {
        val KEY_PREF_SHOW_SYNC = "show_sync"
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_PREF_SHOW_SYNC, false))
            return

        val notificationIntent: Intent = Intent(context, AddFeatureActivity::class.java)
        notificationIntent.setFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationHelper.createBuilder(context, com.nextgis.maplibui.R.string.sync)
            .setSmallIcon(R.drawable.ic_action_sync)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOngoing(false)

        var largeIcon =
            NotificationHelper.getLargeIcon(R.drawable.ic_action_sync, context!!.getResources())
        when (notificationType) {
            SyncAdapter.SYNC_START -> {
                largeIcon = NotificationHelper.getLargeIcon(
                    com.nextgis.maplibui.R.drawable.ic_next_dark,
                    context.getResources()
                )
                builder.setProgress(0, 0, true)
                    .setTicker(context.getString(com.nextgis.maplib.R.string.sync_started))
                    .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                    .setContentText(context.getString(com.nextgis.maplib.R.string.sync_progress))
            }

            SyncAdapter.SYNC_FINISH -> {
                largeIcon = NotificationHelper.getLargeIcon(
                    com.nextgis.maplibui.R.drawable.ic_action_apply_dark,
                    context.getResources()
                )
                builder.setProgress(0, 0, false)
                    .setTicker(context.getString(com.nextgis.maplib.R.string.sync_finished))
                    .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                    .setContentText(context.getString(com.nextgis.maplib.R.string.sync_finished))
            }

            SyncAdapter.SYNC_CANCELED -> {
                largeIcon = NotificationHelper.getLargeIcon(
                    com.nextgis.maplibui.R.drawable.ic_action_cancel_dark,
                    context.getResources()
                )
                builder.setProgress(0, 0, false)
                    .setTicker(context.getString(com.nextgis.maplib.R.string.sync_canceled))
                    .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                    .setContentText(context.getString(com.nextgis.maplib.R.string.sync_canceled))
            }

            SyncAdapter.SYNC_CHANGES -> {
                largeIcon = NotificationHelper.getLargeIcon(
                    com.nextgis.maplibui.R.drawable.ic_action_warning_dark,
                    context.getResources()
                )
                builder.setProgress(0, 0, false)
                    .setTicker(context.getString(com.nextgis.maplib.R.string.sync_error))
                    .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(message)
                    )
                    .setContentText(message)
            }
        }

        builder.setLargeIcon(largeIcon)
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

//    override fun checkTracksLayerExist() {
//        val tracks: MutableList<ILayer?> = java.util.ArrayList<ILayer?>()
//        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks)
//        if (tracks.isEmpty()) {
//            val trackLayerName: String? = getString(com.nextgis.maplib.R.string.tracks)
//            val trackLayer =
//                TrackLayerUI(
//                    getApplicationContext(),
//                    mMap.createLayerStorage(LAYER_TRACKS)
//                )
//            trackLayer.setName(trackLayerName)
//            trackLayer.setVisible(true)
//            mMap.addLayer(trackLayer)
//            mMap.save()
//        }
//    }


}