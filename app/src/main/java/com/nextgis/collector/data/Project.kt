/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018-2021 NextGIS, info@nextgis.com
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

import android.os.Build
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import android.os.Parcel
import com.nextgis.collector.*
import com.nextgis.collector.KParcelable.Companion.readArrayList
import com.nextgis.collector.KParcelable.Companion.readStringFrom
import org.json.JSONObject
import java.util.*


class Project(val id: Int, val ngwId: Int, val title: String, val description: String, val screen: String, version: Int,
              val layers: ArrayList<RemoteLayer>, val tree: String, val private: Boolean, val url: String, val user: String, val hash: String,
    val one :Double, val two :Double,val three :Double,val four :Double)
    : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt(), readStringFrom(parcel),
        readStringFrom(parcel), readStringFrom(parcel), parcel.readInt(), readArrayList(parcel), readStringFrom(parcel),
        parcel.readBoolean(),
        readStringFrom(parcel), readStringFrom(parcel), readStringFrom(parcel),
        parcel.readDouble(), parcel.readDouble(),parcel.readDouble(),parcel.readDouble()
        )

    constructor(json: JSONObject) : this(json.optInt("id"), json.optInt("ngwId"), json.optString("title"),
        json.optString("description", ""),
            json.optString("screen"), json.optInt("version"), ArrayList(), "",
            json.optBoolean("private"), json.optString("url"), "", "",
        json.optDouble("one"), json.optDouble("two"),json.optDouble("three"),json.optDouble("four")

        )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id)
        dest.writeInt(ngwId)
        dest.writeString(title)
        dest.writeString(description)
        dest.writeString(screen)
        dest.writeInt(version)
        dest.writeArray(layers.toArray())
        dest.writeString(tree)
        try {
            dest.writeBoolean(private)
        } catch (ex : Exception){

        }
        dest.writeString(url)
        dest.writeString(user)
        dest.writeString(hash)

        dest.writeDouble(one)
        dest.writeDouble(two)
        dest.writeDouble(three)
        dest.writeDouble(four)
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
            json.put("ngwId", ngwId)
            json.put("screen", screen)
            json.put("private", private)
            json.put("version", version)
            json.put("description", description)
            json.put("url", url)
            json.put("one", one)
            json.put("two", two)
            json.put("three", three)
            json.put("four", four)
            json.put("layers", layers)


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