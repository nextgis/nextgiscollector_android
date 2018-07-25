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

    private fun parseJson(json: JSONArray) {
        resources.addAll(parseResources(json))
    }

    private fun parseResources(json: JSONArray?): ArrayList<Resource> {
        val resources = ArrayList<Resource>()
        json?.let {
            for (j in 0 until json.length()) {
                val jsonResource = json.getJSONObject(j)
                val type = jsonResource.optString("type")
                val title = jsonResource.optString("title")
                val id = jsonResource.optString("id")
                val resource = Resource(title, type, id, arrayListOf())
                when (type) {
                    "dir" -> {
                        val childLayers = jsonResource.optJSONArray("layers")
                        resource.resources.addAll(parseResources(childLayers))
                    }
                }
                resources.add(resource)
            }
        }
        return resources
    }

    private fun getChild(resource: Resource): JSONObject {
        val json = JSONObject()
        json.put("type", resource.type)
        when (resource.type) {
            "tms", "ngrc", "ngw", "ngfp" -> {
                json.put("id", resource.id)
            }
            "dir" -> {
                json.put("title", resource.title)
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