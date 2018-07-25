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

import android.databinding.BaseObservable
import android.os.Parcel
import com.nextgis.collector.KParcelable
import com.nextgis.collector.parcelableCreator


open class Resource(val title: String, val type: String, val id: String, val resources: ArrayList<Resource>) : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), parcel.readString(), readArrayList(parcel))

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(type)
        dest.writeString(id)
        dest.writeArray(resources.toArray())
    }

    companion object {
        @JvmField
        val CREATOR = parcelableCreator(::Resource)

        fun readArrayList(parcel: Parcel): ArrayList<Resource> {
            val array = parcel.readArray(Resource::class.java.classLoader)
            val list = ArrayList<Resource>(array.size)
            array.map { list.add(it as Resource) }
            return list
        }
    }
}