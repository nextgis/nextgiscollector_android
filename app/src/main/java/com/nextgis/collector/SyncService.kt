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
import android.util.Log
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.service.NGWSyncService
import com.nextgis.maplib.util.Constants

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

    inner class SyncReceiver : NGWSyncService.SyncReceiver() {
        override fun onReceive(
                context: Context,
                intent: Intent) {
            val action = intent.action
            when (action) {
                SyncAdapter.SYNC_START -> {
                    mIsSyncStarted = true
//                    Sentry.capture("Sync started")
                }
                SyncAdapter.SYNC_FINISH -> {
                    mIsSyncStarted = false
//                    Sentry.capture("Sync error: ${intent.getStringExtra(SyncAdapter.EXCEPTION)}")
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