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

package com.nextgis.collector.data

import android.databinding.BaseObservable
import android.os.Parcel
import com.nextgis.collector.KParcelable
import com.nextgis.collector.KParcelable.Companion.readStringFrom
import com.nextgis.collector.parcelableCreator
import com.nextgis.collector.readBoolean
import com.nextgis.collector.writeBoolean
import com.nextgis.maplib.util.Constants


open class RemoteLayer(val title: String, val type: String, val description: String, val url: String, val visible: Boolean, val minZoom: Float, val maxZoom:
Float) : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel), parcel.readBoolean(),
            parcel.readFloat(), parcel.readFloat())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(type)
        dest.writeString(description)
        dest.writeString(url)
        dest.writeBoolean(visible)
        dest.writeFloat(minZoom)
        dest.writeFloat(maxZoom)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::RemoteLayer)
    }

    val path: String
        get() {
            return Constants.LAYER_PREFIX + String.format("%X", (title + type + url).hashCode()).toLowerCase()
        }
}