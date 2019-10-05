/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018-2019 NextGIS, info@nextgis.com
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
import com.nextgis.collector.data.*
import com.nextgis.collector.data.ResourceTree.Companion.parseResources
import com.nextgis.collector.util.NetworkUtil
import com.nextgis.maplib.util.HttpResponse
import com.nextgis.maplib.util.NGWUtil
import com.pawegio.kandroid.runAsync
import org.json.JSONArray
import org.json.JSONObject


class ProjectModel {
    companion object {
        private fun base(private: Boolean): String = if (private) CollectorApplication.BASE_NGW_URL else CollectorApplication.BASE_URL

        fun getResponse(path: String, email: String, private: Boolean): HttpResponse? {
            try {
                val hash = NetworkUtil.hash(email)
                val base = base(private)
                val target = "$base/$path"
                val connection = NetworkUtil.getHttpConnection("GET", target, hash)
                return NetworkUtil.getHttpResponse(connection, false)
//                return NetworkUtil.get(target, null, null, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

    }

    fun getProjects(private: Boolean, onDataReadyCallback: OnDataReadyCallback, email: String) {
        runAsync {
            val namespace = "?namespace=" + if (private) "private" else "public"
            val response = getResponse(namespace, email, private)
            var list = ArrayList<Project>()
            response?.let {
                if (it.isOk)
                    list = parseProjects(it.responseBody, private)
            }
            onDataReadyCallback.onDataReady(list)
        }
    }

    fun getProject(id: Int, onDataReadyCallback: OnDataReadyCallback, email: String, private: Boolean) {
        runAsync {
            var project: Project? = null
            val response = getResponse("$id", email, private)
            response?.let {
                val json = try {
                    JSONObject(response.responseBody)
                } catch (e: Exception) {
                    JSONObject()
                }
                project = parseProject(json, private)
            }
            onDataReadyCallback.onProjectReady(project)
        }
    }

    private fun parseProjects(data: String, private: Boolean): ArrayList<Project> {
        val list = ArrayList<Project>()
        try {
            val json = JSONArray(data)
            for (i in 0 until json.length()) {
                val jsonProject = json.getJSONObject(i)
                val project = parseProject(jsonProject, private)
                list.add(project)
            }
        } catch (ignored: Exception) {}
        return list
    }

    private fun parseProject(jsonProject: JSONObject, private: Boolean): Project {
        val title = jsonProject.optString("title")
        val screen = jsonProject.optString("screen")
        val id = jsonProject.optInt("id")
        val version = jsonProject.optInt("version")
        val description = jsonProject.optString("description")

        var url = jsonProject.optString("url")
        var user = jsonProject.optString("username")
        var hash = jsonProject.optString("hash")
        if (private) {
            url = jsonProject.optString("ngw_url")
            user = jsonProject.optString("username")
            hash = jsonProject.optString("password")
        }

        var jsonLayers = jsonProject.optJSONArray(if (private) "items" else "layers")
        jsonLayers?.let {
            var str = jsonLayers.toString().replace("\"display_name\"", "\"title\"")
            str = str.replace("\"resource_cls\"", "\"type\"")
//            str = str.replace("\"item_type\"", "\"type\"")
            str = str.replace("\"children\"", "\"layers\"")
//        str = str.replace("\"resource_id\"", "\"id\"")
            jsonLayers = JSONArray(str)
        }

        val layers = parseLayers(jsonLayers, url)
        val tree = parseTree(jsonLayers)

        return Project(id, title, description, screen, version, layers, tree.json, private, url, user, hash)
    }

    private fun parseTree(json: JSONArray?): ResourceTree {
        val tree = ResourceTree(arrayListOf())
        tree.resources.addAll(parseResources(json, true))
        return tree
    }

    private fun parseLayers(json: JSONArray?, base: String): ArrayList<RemoteLayer> {
        val jsonLayers = ArrayList<RemoteLayer>()
        json?.let { data ->
            for (j in 0 until data.length()) {
                val jsonLayer = data.getJSONObject(j)
                var layer: RemoteLayer? = null
                var type = jsonLayer.optString("type")
                val layerTitle = jsonLayer.optString("title")
                val description = jsonLayer.optString("description")
                var url = jsonLayer.optString("url")
                val visible = jsonLayer.optBoolean("visible")
                val minZoom = jsonLayer.optDouble("min_zoom").toFloat()
                val maxZoom = jsonLayer.optDouble("max_zoom").toFloat()
                when (type) {
                    "qgis_vector_style", "mapserver_vector_style" -> {
                        type = "tms"
                        jsonLayer.put("type", type)
                        url = NGWUtil.getTMSUrl(base, arrayOf(jsonLayer.getLong("resource_id")))
                        jsonLayer.put("url", url)
                        layer = tmsLayer(jsonLayer, layerTitle, type, description, url, visible, minZoom, maxZoom)
                    }
                    "tms", "ngrc", "basemap_layer" -> {
                        type = "tms"
                        jsonLayer.put("type", type)
                        layer = tmsLayer(jsonLayer, layerTitle, type, description, url, visible, minZoom, maxZoom)
                    }
                    "ngw", "ngfp", "vector_layer" -> {
                        if (jsonLayer.has("item_type") || type == "vector_layer") {
                            type = if (jsonLayer.optBoolean("form")) "ngfp" else "ngw"
                            jsonLayer.put("type", type)
                            url = NGWUtil.getResourceUrl(base, jsonLayer.getLong("resource_id"))
                            jsonLayer.put("url", url)
                        }
                        val login = jsonLayer.optString("login")
                        val password = if (jsonLayer.isNull("password")) null else jsonLayer.optString("password")
                        val editable = jsonLayer.optBoolean("editable")
                        val syncable = jsonLayer.optBoolean("syncable")
                        val style = jsonLayer.optJSONObject("style")
                        val jsonStyle = style?.toString() ?: ""
                        layer = RemoteLayerNGW(layerTitle, type, description, url, visible, minZoom, maxZoom, login, password, editable, syncable, jsonStyle)
                    }
                    else -> { // "dir", "group"
                        jsonLayer.put("type", "dir")
                        val childLayers = jsonLayer.optJSONArray("layers")
                        val parsed = parseLayers(childLayers, base)
                        jsonLayers.addAll(parsed)
                    }
                }
                layer?.let {
                    jsonLayers.add(it)
                }
            }
        }
        return jsonLayers
    }

    private fun tmsLayer(jsonLayer: JSONObject, layerTitle: String, type: String, description: String,
                         url: String, visible: Boolean, minZoom: Float, maxZoom: Float): RemoteLayerTMS {
        val lifetime = jsonLayer.optLong("lifetime")
        val tmsType = jsonLayer.optInt("tms_type")
        return RemoteLayerTMS(layerTitle, type, description, url, visible, minZoom, maxZoom, lifetime, tmsType)
    }

    interface OnDataReadyCallback {
        fun onDataReady(data: ArrayList<Project>)
        fun onProjectReady(project: Project?)
    }
}