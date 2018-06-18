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
import org.json.JSONObject


class ProjectModel {
    companion object {
        fun getResponse(path: String): HttpResponse? {
            try {
                val target = "${CollectorApplication.BASE_URL}/$path"
                return NetworkUtil.get(target, null, null, false)
            } catch (e: Exception) {
            }
            return null
        }

    }

    fun getProjects(onDataReadyCallback: OnDataReadyCallback) {
        runAsync {
            val response = getResponse("public")
            var list = ArrayList<Project>()
            response?.let {
                if (it.isOk)
                    list = parseProjects(it.responseBody)
            }
            onDataReadyCallback.onDataReady(list)
        }
    }

    fun getProject(id: Int, onDataReadyCallback: OnDataReadyCallback) {
        runAsync {
            var project: Project? = null
            val response = getResponse("$id")
            response?.let {
                val json = try {
                    JSONObject(response.responseBody)
                } catch (e: Exception) {
                    JSONObject()
                }
                project = parseProject(json)
            }
            onDataReadyCallback.onProjectReady(project)
        }
    }

    private fun parseProjects(data: String): ArrayList<Project> {
        val list = ArrayList<Project>()
        val json = JSONArray(data)
        for (i in 0 until json.length()) {
            val jsonProject = json.getJSONObject(i)
            val project = parseProject(jsonProject)
            list.add(project)
        }
        return list
    }

    private fun parseProject(jsonProject: JSONObject): Project {
        val title = jsonProject.optString("title")
        val screen = jsonProject.optString("screen")
        val id = jsonProject.optInt("id")
        val version = jsonProject.optInt("version")
        val description = jsonProject.optString("description")

        val jsonLayers = jsonProject.optJSONArray("layers")
        val layers = ArrayList<RemoteLayer>()
        jsonLayers?.let {
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
                        val style = jsonLayer.optJSONObject("style")
                        val json = style?.toString() ?: ""
                        layer = RemoteLayerNGW(layerTitle, type, url, visible, minZoom, maxZoom, login, password, editable, syncable, json)
                    }
                }
                layer?.let { layers.add(it) }
            }
        }

        return Project(id, title, description, screen, version, layers)
    }

    interface OnDataReadyCallback {
        fun onDataReady(data: ArrayList<Project>)
        fun onProjectReady(project: Project?)
    }
}