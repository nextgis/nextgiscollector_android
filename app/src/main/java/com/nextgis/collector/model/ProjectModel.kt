/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018-2020 NextGIS, info@nextgis.com
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

import android.content.Context
import android.text.Html
import android.util.Log
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.data.*
import com.nextgis.collector.data.ResourceTree.Companion.parseResources
import com.nextgis.collector.util.NetworkUtil
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.HttpResponse
import com.nextgis.maplib.util.NGWUtil
import com.nextgis.maplibui.util.NGIDUtils.COLLECTOR_PROJECTS_URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture.runAsync


class ProjectModel {
    companion object {
        fun getBaseUrl(base: String, private: Boolean): String {
            return if (private) {
                base + COLLECTOR_PROJECTS_URL
            } else {
                CollectorApplication.BASE_URL
            }
        }

        fun getResponse(path: String, email: String): HttpResponse? {
            try {
                val hash = NetworkUtil.hash(email)
                val connection = NetworkUtil.getHttpConnection("GET", path, hash)
                return NetworkUtil.getHttpResponse(connection, false)
//                return NetworkUtil.get(target, null, null, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

    }

    fun getProjects(url: String, private: Boolean, onDataReadyCallback: OnDataReadyCallback, email: String) {
        runAsync {
            val namespace = "?namespace=" + if (private) "private" else "public"
            val path = getBaseUrl(url, private) + "/$namespace"
            //com.nextgis.maplib.util.NetworkUtil.configureSSLdefault()
            val response = getResponse(path, email)
            var list = ArrayList<Project>()
            response?.let {
                if (it.isOk)
                    list = parseProjects(it.responseBody, private)
            }
            onDataReadyCallback.onDataReady(list)
        }
    }

    fun getProject(url: String, id: Int, onDataReadyCallback: OnDataReadyCallback, email: String, private: Boolean,
                   context : Context, onResetLoadProjectCallback: OnResetLoadProjectCallback) {
        runAsync {
            var project: Project? = null
            val path = getBaseUrl(url, private) + "/$id"
            //com.nextgis.maplib.util.NetworkUtil.configureSSLdefault()
            val response = getResponse(path, email)
            response?.let { 
                val json = try {
                    JSONObject(response.responseBody)
                } catch (e: Exception) {
                    JSONObject()
                    onDataReadyCallback.onProjectGetError("Error on get project: " + e.message )
                    return@runAsync
                }


                try {
                    // change single form to layer (with default form notation)
                    if (json != null && json.has("items")) {
                        val layers = json.getJSONArray("items")
                        for (i in 0 until layers.length()) {
                            val layer = layers[i]
                            if (layer is JSONObject && (layer as JSONObject)
                                    .getString("resource_cls")
                                    .equals("formbuilder_form")
                            ) {
                                val version = json.getInt("version")
                                val user = json.getString("username")
                                val hash = json.getString("password")
                                val ngwUrl = json.getString("ngw_url")

                                val array = arrayListOf<Int>()
                                array.addAll(hash.chunked(4).map { it.toInt(16) - version })
                                for (i in 0 until version)
                                    array.add(0, array.removeAt(array.size - 1))
                                val length = array.removeAt(0)
                                val pass = array.dropLast(array.size - length).map { it.toChar() }
                                    .joinToString("")

                                //getDefaultFormId
                                val sURL = NGWUtil.getResourceUrl(ngwUrl, layer.getLong("resource_id"))
                                val response = com.nextgis.maplib.util.NetworkUtil.get(sURL, user, pass, false)

                                HyperLog.v(Constants.TAG, "response on get " + sURL +" is " + response.toString())
                                HyperLog.v(Constants.TAG, "response code is " + response.responseCode)

                                val children = JSONObject(response.responseBody)
                                val JSONResource = children.getJSONObject("resource")
                                val parent = JSONResource.getJSONObject("parent")
                                val parentId = parent.getLong("id")

                                // get layer description
                                val response2 = com.nextgis.maplib.util.NetworkUtil.get(
                                    NGWUtil.getResourceUrl(ngwUrl,parentId), user, pass, false)

                                HyperLog.v(Constants.TAG, "response on get " + ngwUrl +" is " + response2.toString())
                                HyperLog.v(Constants.TAG, "response code is " + response2.responseCode)

                                if (!response2.isOk) {
                                    break
                                }
                                val parentLayer =
                                    JSONObject(response.responseBody).getJSONObject("resource")

                                val defaultFormId = layer.getInt("resource_id")
                                val newItem = JSONObject()
                                newItem.put("id", parentLayer.getInt("id"))
                                newItem.put("item_type", "item")
                                newItem.put("resource_id", parentId)
                                newItem.put("resource_cls", "vector_layer")
                                newItem.put("display_name", layer.getString("display_name"))
                                newItem.put("editable", true)
                                newItem.put("visible", layer.getBoolean("visible"))
                                newItem.put("syncable", layer.getBoolean("syncable"))
                                newItem.put("lifetime", layer.getLong("lifetime"))
                                newItem.put("min_zoom", layer.getInt("min_zoom"))
                                newItem.put("max_zoom", layer.getInt("max_zoom"))
                                newItem.put("default_form_id", defaultFormId)
                                newItem.put("form", true)
                                layers.put(i, newItem)
                            }
                        }


//                        val retrievedValue = mutableMapOf<Long, MutableList<Long>>()
//
//
//                        for (i in 0 until layers.length()) {
//                            val layer = layers[i]
//                            if (layer is JSONObject && (layer as JSONObject)
//                                    .getString("resource_cls")
//                                    .equals("vector_layer")) {
//
//                                if(layer.has("default_form_id")){
//                                    val defFormId = layer.getLong("default_form_id")
//                                    val resource_id = layer.getLong("resource_id")
//
//                                    val listForms = retrievedValue.getOrPut(resource_id) { mutableListOf() }
//                                    listForms.add(defFormId)
//                                }
//
//                            }
//                        }
//                        if(retrievedValue.size > 0 ){
//                            for (i in 0 until layers.length()) {
//                                val layer = layers[i]
//                                if (layer is JSONObject && (layer as JSONObject)
//                                        .getString("resource_cls")
//                                        .equals("vector_layer")) {
//
//                                    if (!layer.has("default_form_id")){
//                                        val resource_id = layer.getLong("resource_id")
//                                        if(retrievedValue.containsKey(resource_id)){
//                                            val listForms = retrievedValue[resource_id]
//                                            if (listForms != null) {
//
//
//                                                val jsonArray = JSONArray()
//                                                listForms.forEach { jsonArray.put(it) }
//                                                layer.put("default_set_forms", jsonArray)
//                                            }
//                                        }
//                                }
//                            }
//
//                        }
                        //}

                    }
                } catch (ex:Exception){
                    HyperLog.v(Constants.TAG, "exception is  " + ex.message)
                    onDataReadyCallback.onProjectGetError(context.getString(R.string.project_get_error) )
                    onResetLoadProjectCallback.onReset()
                    return@runAsync
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
        val ngwId = jsonProject.optInt("ngw_project_id")
        val version = jsonProject.optInt("version")
        val description =   Html.fromHtml(if (jsonProject.isNull("description")) "" else jsonProject.optString("description")).toString()

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

        var one = -1.0
        var two = -1.0
        var three = -1.0
        var four = -1.0

        var borders : DoubleArray = DoubleArray(0)
        try {
            if (jsonProject.has("initial_extent") && !jsonProject.isNull("initial_extent") ){
                val jsonBorders = jsonProject.getJSONArray("initial_extent")
                borders = DoubleArray(jsonBorders.length()) { i -> jsonBorders.getDouble(i) }
                one = borders[0]
                two = borders[1]
                three = borders[2]
                four = borders[3]
            }
        } catch (exeption:Exception){
            Log.e("project", "exception "  + exeption.message)
        }


        return Project(id, ngwId, title, description, screen, version, layers, tree.json, private, url, user, hash, one, two, three, four)
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
                val resourceId = jsonLayer.optInt("resource_id")

                val minZoom = jsonLayer.optDouble("min_zoom").toFloat()
                val maxZoom = jsonLayer.optDouble("max_zoom").toFloat()
                val defaultFormId =  jsonLayer.optLong("default_form_id", -1).toLong()
                when (type) {
                    "qgis_vector_style", "mapserver_style" -> {
                        type = "tms"
                        jsonLayer.put("type", type)
                        url = NGWUtil.getTMSUrl(base, arrayOf(jsonLayer.getLong("resource_id")))
                        jsonLayer.put("url", url)
                        layer = tmsLayer(jsonLayer, layerTitle, type, description, url, visible, minZoom, maxZoom, defaultFormId, resourceId)
                    }
                    "tms", "ngrc", "basemap_layer" -> {
                        type = "tms"
                        jsonLayer.put("type", type)
                        layer = tmsLayer(jsonLayer, layerTitle, type, description, url, visible, minZoom, maxZoom, defaultFormId, resourceId)
                    }
                    "ngw", "ngfp", "vector_layer" , "postgis_layer",  "formbuilder_form"-> {
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
                        layer = RemoteLayerNGW(layerTitle, type, description, url, visible, minZoom, maxZoom, defaultFormId, login, password, editable, syncable, jsonStyle, resourceId)
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
                         url: String, visible: Boolean, minZoom: Float, maxZoom: Float, defaultFormId : Long , resourceId:Int): RemoteLayerTMS {
        val lifetime = jsonLayer.optLong("lifetime")
        val tmsType = jsonLayer.optInt("tms_type")
        return RemoteLayerTMS(layerTitle, type, description, url, visible, minZoom, maxZoom, defaultFormId, lifetime, tmsType, resourceId)
    }

    interface OnDataReadyCallback {
        fun onDataReady(data: ArrayList<Project>)
        fun onProjectReady(project: Project?)
        fun onProjectGetError(errorText: String)
    }


    interface OnResetLoadProjectCallback {
        fun onReset()
    }

    }