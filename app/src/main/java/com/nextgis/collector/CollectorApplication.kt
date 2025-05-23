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
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.activity.ProjectActivity
import com.nextgis.collector.util.Logger
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.NGWUtil
import com.nextgis.maplib.util.NetworkUtil
import com.nextgis.maplib.util.SettingsConstants.KEY_PREF_TRACK_SEND
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.mapui.TrackLayerUI
import com.nextgis.maplibui.service.TrackerService
import io.sentry.Sentry
import kotlin.system.exitProcess

class CollectorApplication : GISApplication() {

    private var syncReceiver: SyncReceiver = SyncReceiver()
    companion object {
        const val BASE_URL = "https://collector.nextgis.com/api/project"
        const val TREE = "resource.tree"
        public var isSyncProgress = false
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



}