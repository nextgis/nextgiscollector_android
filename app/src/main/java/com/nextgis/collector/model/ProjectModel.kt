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

package com.nextgis.collector.model

import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.data.Project
import com.nextgis.collector.data.RemoteLayer
import com.nextgis.collector.data.RemoteLayerNGW
import com.nextgis.collector.data.RemoteLayerTMS
import com.nextgis.maplib.util.HttpResponse
import com.nextgis.maplib.util.NetworkUtil
import com.pawegio.kandroid.runAsync
import org.json.JSONArray


class ProjectModel {
    fun getProjects(onDataReadyCallback: OnDataReadyCallback) {
        runAsync {
            val response = loadList()
            var list = ArrayList<Project>()
            response?.let {
                if (it.isOk)
                    list = parseProjects(it.responseBody)
            }
            onDataReadyCallback.onDataReady(list)
        }
    }

    private fun loadList(): HttpResponse? {
        try {
            val target = "${CollectorApplication.BASE_URL}/public"
            return NetworkUtil.get(target, null, null, false)
        } catch (e: Exception) {
        }
        return null
    }

    private fun parseProjects(data: String): ArrayList<Project> {
        val list = ArrayList<Project>()
        val json = JSONArray(data)
        for (i in 0 until json.length()) {
            val jsonProject = json.getJSONObject(i)
            val title = jsonProject.optString("title")
            val screen = jsonProject.optString("screen")
            val slug = jsonProject.optString("slug")
            val version = jsonProject.optInt("version")
            val description = jsonProject.optString("description")
            val jsonLayers = jsonProject.getJSONArray("layers")
            val layers = ArrayList<RemoteLayer>()
            for (j in 0 until jsonLayers.length()) {
                val jsonLayer = jsonLayers.getJSONObject(j)
                var layer: RemoteLayer? = null
                val type = jsonLayer.optString("type")
                val layerTitle = jsonLayer.optString("title")
                val url = jsonLayer.optString("url")
                val visible = jsonLayer.optBoolean("visible")
                val minZoom = jsonLayer.optDouble("min_zoom").toFloat()
                val maxZoom = jsonLayer.optDouble("max_zoom").toFloat()
                when (type) {
                    "tms", "ngrc" -> {
                        val lifetime = jsonLayer.optLong("lifetime")
                        val tmsType = jsonLayer.optInt("tms_type")
                        layer = RemoteLayerTMS(layerTitle, type, url, visible, minZoom, maxZoom, lifetime, tmsType)
                    }
                    "ngw", "ngfp" -> {
                        val login = jsonLayer.optString("login")
                        val password = if (jsonLayer.isNull("password")) null else jsonLayer.optString("password")
                        val editable = jsonLayer.optBoolean("editable")
                        val syncable = jsonLayer.optBoolean("syncable")
                        layer = RemoteLayerNGW(layerTitle, type, url, visible, minZoom, maxZoom, login, password, editable, syncable)
                    }
                }
                layer?.let { layers.add(it) }
            }
            list.add(Project(title, description, slug, screen, version, layers))
        }
        return list
    }

    interface OnDataReadyCallback {
        fun onDataReady(data: ArrayList<Project>)
    }
}