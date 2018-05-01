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
        val data = "[{\"title\":\"Project 1\",\"description\":\"Explore the history of the classic Lorem Ipsum passage and generate your own text using any number of characters, words, sentences or paragraphs. Commonly used as placeholder text in the graphic and print industries, Lorem Ipsum's origins extend far back to a scrambled Latin passage from Cicero in the middle ages.\",\"layers\":[{\"title\":\"Layer default OSM\",\"type\":\"tms\",\"url\":\"http://{a,b,c}.tile.openstreetmap.org/{z}/{x}/{y}.png\",\"lifetime\":4233600000},{\"title\":\"Layer TMS cached\",\"type\":\"ngrc\",\"url\":\"http://4ert.com/nextgis/layer.ngrc\"},{\"title\":\"Layer vector\",\"type\":\"ngw\",\"url\":\"http://source.nextgis.com/resource/45\",\"editable\":true,\"syncable\":true,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector NS NE\",\"type\":\"ngw\",\"url\":\"http://source.nextgis.com/resource/45\",\"editable\":false,\"syncable\":false,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector not editable\",\"type\":\"ngw\",\"url\":\"http://source.nextgis.com/resource/45\",\"editable\":false,\"syncable\":true,\"login\":\"administrator\",\"password\":\"admin\"},{\"title\":\"Layer vector not editable\",\"type\":\"ngw\",\"url\":\"http://source.nextgis.com/resource/45\",\"editable\":true,\"syncable\":false,\"login\":\"administrator\",\"password\":\"admin\"}]}]"
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
                val layerTitle = jsonLayer.optString("type")
                val url = jsonLayer.optString("type")
                when (type) {
                    "tms" -> {
                        val lifetime = jsonLayer.optLong("lifetime")
                        layer = RemoteLayerTMS(layerTitle, type, url, lifetime)
                    }
                    "ngrc" -> {

                    }
                    "ngw" -> {
                        val login = jsonProject.optString("login")
                        val password = jsonLayer.optString("password")
                        val editable= jsonLayer.optBoolean("editable")
                        val syncable = jsonLayer.optBoolean("syncable")
                        layer = RemoteLayerNGW(layerTitle, type, url, login, password, editable, syncable)
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