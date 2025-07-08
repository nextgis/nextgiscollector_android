/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
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

package com.nextgis.collector.data

import org.json.JSONArray
import org.json.JSONObject


open class ResourceTree(val resources: ArrayList<Resource>) {
    companion object {
        fun parseResources(json: JSONArray?, skipId: Boolean = false): ArrayList<Resource> {
            val resources = ArrayList<Resource>()
            json?.let {
                for (j in 0 until json.length()) {
                    val jsonResource = json.getJSONObject(j)
                    val type = jsonResource.optString("type")
                    val title = jsonResource.optString("title")
                    val description = jsonResource.optString("description")
                    val resourseId = jsonResource.optInt("resource_id")
                    val defaultFormId = jsonResource.optLong("default_form_id", -1)

                    //val defaultFormsArray = jsonResource.optJSONArray("default_set_forms")

//                    val defaultForms: List<Long> =
//                        (0 until (defaultFormsArray?.length() ?: 0)).map { i ->
//                            defaultFormsArray!!.getLong(i)
//                        }



                    val url = jsonResource.optString("url", System.currentTimeMillis().toString())
                    val layer = RemoteLayer(title, type, description, url, true, 0f, 0f, -1, resourseId)
                    val id = if (skipId) layer.path else jsonResource.optString("id")
                    val resource = Resource(title, type, description, id, arrayListOf(), defaultFormId)
                    when (type) {
                        "dir" -> {
                            val childLayers = jsonResource.optJSONArray("layers")
                            resource.resources.addAll(parseResources(childLayers, skipId))
                        }
                    }
                    resources.add(resource)
                }
            }
            return resources
        }
    }

    fun getLevel(id: String): ArrayList<Resource> {
        if (id.isBlank())
            return resources

        return findById(id, resources)
    }

    private fun findById(id: String, array: ArrayList<Resource>): ArrayList<Resource> {
        for (resource in array) {
            if (resource.id == id)
                return resource.resources
            if (resource.type == "dir") {
                val children = findById(id, resource.resources)
                if (children.isNotEmpty())
                    return children
            }
        }
        return arrayListOf()
    }

    private fun iterateOver(id: String, array: ArrayList<Resource>) {
        for (resource in array)
            if (resource.type == "dir")
                findById(id, resource.resources)
    }

    fun parse(string: String) {
        val json = JSONArray(string)
        resources.addAll(parseResources(json))
    }

    private fun getChild(resource: Resource): JSONObject {
        val json = JSONObject()
        json.put("id", resource.id)
        json.put("type", resource.type)
        json.put("title", resource.title)
        json.put("description", resource.description)
        json.put("default_form_id", resource.defaultFormId)
        when (resource.type) {
            "dir" -> {
                val children = JSONArray()
                for (child in resource.resources)
                    children.put(getChild(child))
                json.put("layers", children)
            }
        }
        return json
    }

    val json: String
        get() {
            val json = JSONArray()
            for (resource in resources)
                json.put(getChild(resource))
            return json.toString()
        }
}