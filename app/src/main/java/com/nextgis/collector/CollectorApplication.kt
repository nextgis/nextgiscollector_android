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

import android.widget.Toast
import com.nextgis.collector.util.Logger
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.SettingsConstants.KEY_PREF_TRACK_SEND
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.mapui.TrackLayerUI
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import kotlin.system.exitProcess

class CollectorApplication : GISApplication() {
    companion object {
        const val BASE_URL = "http://collector.nextgis.com/api/project"
        const val TREE = "resource.tree"
    }

    override fun onCreate() {
        if (!BuildConfig.DEBUG)
            Sentry.init(AndroidSentryClientFactory(applicationContext))
        super.onCreate()
        if (mSharedPreferences.getBoolean("save_log", false)) {
            Logger.initialize(this)
        }
        checkTracksLayerExistence()
        updateFromPreviousVersion()
    }

    private fun updateFromPreviousVersion() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val savedVersionCode = mSharedPreferences.getInt("last", 0)

        if (savedVersionCode == 0)
            mSharedPreferences.edit().putBoolean(KEY_PREF_TRACK_SEND, true).apply()

        if (savedVersionCode < currentVersionCode) {
            mSharedPreferences.edit().putInt("last", currentVersionCode).apply()
        }
    }

    override fun getAuthority(): String {
        return BuildConfig.APPLICATION_ID
    }

    override fun showSettings(setting: String?) {

    }

    override fun sendEvent(category: String?, action: String?, label: String?) {

    }

    override fun sendScreen(name: String?) {

    }

    override fun getAccountsType(): String {
        return "com.nextgiscollector.account"//Constants.NGW_ACCOUNT_TYPE
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
        } else
            mMap.moveLayer(map.layerCount - 1, tracks.first())
    }

}