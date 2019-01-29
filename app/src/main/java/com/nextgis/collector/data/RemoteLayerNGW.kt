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

package com.nextgis.collector.data

import android.os.Parcel
import com.nextgis.collector.KParcelable.Companion.readStringFrom
import com.nextgis.collector.parcelableCreator
import com.nextgis.collector.readBoolean
import com.nextgis.collector.writeBoolean
import org.json.JSONObject


class RemoteLayerNGW(title: String, type: String, description: String, url: String, visible: Boolean, minZoom: Float, maxZoom: Float,
                     var login: String, var password: String?, val editable: Boolean, val syncable: Boolean, var style: String = "")
    : RemoteLayer(title, type, description, url, visible, minZoom, maxZoom) {

    private constructor(parcel: Parcel) : this(readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel),
            readStringFrom(parcel), parcel.readBoolean(), parcel.readFloat(), parcel.readFloat(), readStringFrom(parcel),
            readStringFrom(parcel), parcel.readBoolean(), parcel.readBoolean(), readStringFrom(parcel))

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(login)
        dest.writeString(password)
        dest.writeBoolean(editable)
        dest.writeBoolean(syncable)
        dest.writeString(style)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::RemoteLayerNGW)
    }

    val styleable: Boolean
        get() {
            return style.isNotBlank()
        }

    val renderer: JSONObject
        get() {
            val json = JSONObject("{\"name\":\"SimpleFeatureRenderer\"}")
            val style = JSONObject(style)
            json.put("style", style)
            return json
        }
}