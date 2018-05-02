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

import android.os.Parcel
import com.handicap.surpriseme.util.parcelableCreator
import com.handicap.surpriseme.util.readBoolean

class RemoteLayerTMS(title: String, type: String, url: String, visible: Boolean, minZoom: Float, maxZoom: Float, val lifetime: Long, val tmsType: Int) : RemoteLayer(title, type, url, visible, minZoom, maxZoom) {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString(), parcel.readBoolean(), parcel.readFloat(), parcel.readFloat(), parcel.readLong(), parcel.readInt())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeLong(lifetime)
        dest.writeInt(tmsType)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::RemoteLayerTMS)
    }
}