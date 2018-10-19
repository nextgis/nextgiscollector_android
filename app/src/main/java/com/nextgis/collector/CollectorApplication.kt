/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
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

package com.nextgis.collector

import com.nextgis.maplib.map.MapBase
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.mapui.TrackLayerUI
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.util.Constants
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory

class CollectorApplication : GISApplication() {
    companion object {
        const val BASE_URL = "http://collector.nextgis.com/api/project"
        const val TREE = "resource.tree"
    }

    override fun onCreate() {
        super.onCreate()
        Sentry.init(AndroidSentryClientFactory(applicationContext))
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

    override fun getMap(): MapBase {
        val map = super.getMap()
        checkTracksLayerExistence()
        return map
    }

    private fun checkTracksLayerExistence() {
        val tracks = ArrayList<ILayer>()
        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks)
        if (tracks.isEmpty()) {
            val trackLayerName = getString(R.string.tracks)
            val trackLayer = TrackLayerUI(applicationContext, mMap.createLayerStorage("tracks"))
            trackLayer.name = trackLayerName
            trackLayer.isVisible = true
            mMap.addLayer(trackLayer)
            mMap.save()
        }
    }

}