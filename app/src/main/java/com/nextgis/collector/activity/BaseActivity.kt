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

package com.nextgis.collector.activity

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.data.Project
import com.nextgis.collector.util.IntentFor
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.TrackLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplibui.activity.NGActivity
import com.nextgis.maplibui.mapui.MapViewOverlays
import org.json.JSONObject

abstract class BaseActivity : NGActivity() {
    protected lateinit var app: CollectorApplication
    protected lateinit var mapView: MapViewOverlays
    protected lateinit var map: MapDrawable
    protected lateinit var preferences: SharedPreferences
    protected lateinit var project: Project

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        app = application as CollectorApplication
        mapView = MapViewOverlays(this, app.map as MapDrawable)
        mapView.id = R.id.container
        map = mapView.map
        loadProject()
    }

    protected fun loadProject() {
        val json = preferences.getString("project", null)
        project = Project(JSONObject(json ?: "{}"))
    }

    protected fun deleteAll() {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                app.getAccount(layer.accountName)?.let { app.removeAccount(it) }
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
    }

    protected fun hasChanges(): Boolean {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                if (changesCount > 0) {
                    return true
                }
            }
        }
        return false
    }

    protected fun change(project: Project? = null) {
        deleteAll()
        val intent = IntentFor<ProjectListActivity>(this)
        project?.let {
            intent.putExtra("project", it.id)
            intent.putExtra("private", it.private)
        }
        finish()
        startActivity(intent)
    }

}