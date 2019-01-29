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

import android.databinding.BaseObservable
import android.databinding.Bindable
import android.os.Parcel
import com.nextgis.collector.BR
import com.nextgis.collector.KParcelable
import com.nextgis.collector.KParcelable.Companion.readArrayList
import com.nextgis.collector.KParcelable.Companion.readStringFrom
import com.nextgis.collector.parcelableCreator
import org.json.JSONObject
import java.util.*


class Project(val id: Int, val title: String, val description: String, val screen: String, version: Int,
              val layers: ArrayList<RemoteLayer>, val tree: String,
              val url: String, val user: String, val hash: String) : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(parcel.readInt(), readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel),
            parcel.readInt(), readArrayList(parcel), readStringFrom(parcel),
            readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel))

    constructor(json: JSONObject) : this(json.optInt("id"), json.optString("title"), "", json.optString("screen"), json.optInt("version"), ArrayList(), "", "", "", "")

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id)
        dest.writeString(title)
        dest.writeString(description)
        dest.writeString(screen)
        dest.writeInt(version)
        dest.writeArray(layers.toArray())
        dest.writeString(tree)
        dest.writeString(url)
        dest.writeString(user)
        dest.writeString(hash)
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::Project)
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
            json.put("id", id)
            json.put("screen", screen)
            json.put("version", version)
            return json.toString()
        }

    val isMapMain: Boolean
        get() {
            return screen != "list"
        }

    val password: String
        get() {
            val array = arrayListOf<Int>()
            array.addAll(hash.chunked(4).map { it.toInt(16) - version })
            for (i in 0 until version)
                array.add(0, array.removeAt(array.size - 1))
            val length = array.removeAt(0)
            return array.dropLast(array.size - length).map { it.toChar() }.joinToString("")
        }
}