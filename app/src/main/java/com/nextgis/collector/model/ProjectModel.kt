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

import com.nextgis.collector.data.Project
import com.nextgis.collector.data.RemoteLayer
import com.nextgis.collector.data.RemoteLayerNGW
import com.nextgis.collector.data.RemoteLayerTMS
import org.json.JSONArray


class ProjectModel {
    fun getProjects(onDataReadyCallback: OnDataReadyCallback) {
        val data = "[{\"title\":\"Project 1\",\"description\":\"Explore the history of the classic Lorem Ipsum passage and generate your own text using any number of characters, words, sentences or paragraphs. Commonly used as placeholder text in the graphic and print industries, Lorem Ipsum's origins extend far back to a scrambled Latin passage from Cicero in the middle ages.\",\"layers\":[{\"title\":\"Layer default OSM\",\"type\":\"tms\",\"visible\":true,\"min_zoom\":0,\"max_zoom\":25,\"tms_type\":2,\"url\":\"http://{a,b,c}.tile.openstreetmap.org/{z}/{x}/{y}.png\",\"lifetime\":4233600000},{\"title\":\"Layer TMS cached\",\"type\":\"ngrc\",\"min_zoom\":0,\"max_zoom\":25,\"visible\":true,\"url\":\"http://4ert.com/data/march.ngrc\"},{\"title\":\"Layer vector\",\"type\":\"ngw\",\"min_zoom\":0,\"max_zoom\":25,\"url\":\"http://source.nextgis.com/resource/148\",\"editable\":true,\"syncable\":true,\"visible\":true,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector NS NE\",\"type\":\"ngw\",\"min_zoom\":5,\"max_zoom\":10,\"url\":\"http://source.nextgis.com/resource/207\",\"editable\":false,\"visible\":true,\"syncable\":false,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector not editable\",\"type\":\"ngw\",\"min_zoom\":10,\"max_zoom\":15,\"visible\":true,\"url\":\"http://source.nextgis.com/resource/207\",\"editable\":false,\"syncable\":true,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector not syncable\",\"type\":\"ngw\",\"min_zoom\":15,\"max_zoom\":25,\"url\":\"http://source.nextgis.com/resource/207\",\"editable\":true,\"visible\":false,\"syncable\":false,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer NGFP Local\",\"editable\":true,\"syncable\":false,\"type\":\"ngfp\",\"min_zoom\":0,\"max_zoom\":25,\"visible\":true,\"url\":\"http://4ert.com/data/165.ngfp\"},{\"title\":\"Layer NGFP NGW\",\"editable\":true,\"syncable\":true,\"type\":\"ngfp\",\"min_zoom\":0,\"max_zoom\":25,\"visible\":true,\"url\":\"http://4ert.com/data/old_trees.ngfp\"}]}]"
        val json = JSONArray(data)
        val list = ArrayList<Project>()
        for (i in 0 until json.length()) {
            val jsonProject = json.getJSONObject(i)
            val title = jsonProject.optString("title")
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
            list.add(Project(title, description, layers))
        }
        onDataReadyCallback.onDataReady(list)
    }

    interface OnDataReadyCallback {
        fun onDataReady(data: ArrayList<Project>)
    }
}