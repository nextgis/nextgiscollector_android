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
import com.handicap.surpriseme.util.writeBoolean


class RemoteLayerNGW(title: String, type: String, url: String, val login: String, val password: String, val editable: Boolean, val syncable: Boolean) : RemoteLayer(title, type, url) {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readBoolean(), parcel.readBoolean())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(type)
        dest.writeString(url)
        dest.writeString(login)
        dest.writeString(password)
        dest.writeBoolean(editable)
        dest.writeBoolean(syncable)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::RemoteLayerNGW)
    }
}