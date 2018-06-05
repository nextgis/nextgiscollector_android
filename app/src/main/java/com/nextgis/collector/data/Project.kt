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
import android.databinding.Bindable
import android.os.Parcel
import com.nextgis.collector.BR
import com.nextgis.collector.KParcelable
import com.nextgis.collector.parcelableCreator
import org.json.JSONObject


class Project(val title: String, val description: String, val slug: String, val screen: String, version: Int, val layers: ArrayList<RemoteLayer>) : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(), parcel.readInt(), readArrayList(parcel))

    constructor(json: JSONObject) : this(json.optString("title"), "", json.optString("slug"), json.optString("screen"), json.optInt("version"), ArrayList())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(description)
        dest.writeString(slug)
        dest.writeString(screen)
        dest.writeInt(version)
        dest.writeArray(layers.toArray())
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::Project)

        fun readArrayList(parcel: Parcel): ArrayList<RemoteLayer> {
            val array = parcel.readArray(RemoteLayer::class.java.classLoader)
            val list = ArrayList<RemoteLayer>(array.size)
            array.map { list.add(it as RemoteLayer) }
            return list
        }
    }

    @get:Bindable
    var version: Int = version
        set(value) {
            field = value
            notifyPropertyChanged(BR.version)
        }

    val json: String?
        get() {
            val json = JSONObject()
            json.put("title", title)
            json.put("slug", slug)
            json.put("screen", screen)
            json.put("version", version)
            return json.toString()
        }
}