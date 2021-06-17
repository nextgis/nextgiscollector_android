/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2020 NextGIS, info@nextgis.com
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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.ContextCompat
import android.util.Log
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.MapBase
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.service.NGWSyncService
import com.nextgis.maplib.util.Constants
import com.nextgis.maplibui.service.RebuildCacheService
import com.nextgis.maplibui.util.ConstantsUI

class SyncService: NGWSyncService() {

    /*
     * Instantiate the sync adapter object.
     */
    override fun onCreate() {
        // For service debug
//        android.os.Debug.waitForDebugger();

        /*
         * Create the sync adapter as a singleton.
         * Set the sync adapter as syncable
         * Disallow parallel syncs
         */
        synchronized(mSyncAdapterLock) {
            if (mSyncAdapter == null) {
                mSyncAdapter = createSyncAdapter(applicationContext, true)
            }
        }
        mIsSyncStarted = false
        mSyncReceiver = SyncReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(SyncAdapter.SYNC_START)
        intentFilter.addAction(SyncAdapter.SYNC_FINISH)
        intentFilter.addAction(SyncAdapter.SYNC_CANCELED)
        intentFilter.addAction(SyncAdapter.SYNC_CHANGES)
        registerReceiver(mSyncReceiver, intentFilter)
    }

    private fun rebuildLayersCaches(context: Context) {
        val mapContentProviderHelper = MapBase.getInstance() as MapContentProviderHelper
        for (i in 0 until mapContentProviderHelper.layerCount) {
            (mapContentProviderHelper.getLayer(i) as? VectorLayer)?.let {
                val intent = Intent(context, RebuildCacheService::class.java)
                intent.putExtra(ConstantsUI.KEY_LAYER_ID, it.id)
                intent.action = RebuildCacheService.ACTION_ADD_TASK
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    inner class SyncReceiver : NGWSyncService.SyncReceiver() {
        override fun onReceive(
                context: Context,
                intent: Intent) {
            when (intent.action) {
                SyncAdapter.SYNC_START -> {
                    mIsSyncStarted = true
//                    Sentry.capture("Sync started")
                }
                SyncAdapter.SYNC_FINISH -> {
                    mIsSyncStarted = false
//                    Sentry.capture("Sync error: ${intent.getStringExtra(SyncAdapter.EXCEPTION)}")
                    rebuildLayersCaches(context)
                }
                SyncAdapter.SYNC_CANCELED -> {
                    Log.d(Constants.TAG, "SyncAdapter - SYNC_CANCELED is received")
                    mIsSyncStarted = false
//                    Sentry.capture("Sync cancelled")
                }
                SyncAdapter.SYNC_CHANGES -> {
                }
            }
        }
    }
}