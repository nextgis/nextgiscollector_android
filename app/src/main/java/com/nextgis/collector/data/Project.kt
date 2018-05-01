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
import com.handicap.surpriseme.util.KParcelable
import com.handicap.surpriseme.util.parcelableCreator
import com.nextgis.collector.BR


class Project(title: String, description: String, val layers: ArrayList<RemoteLayer>) : BaseObservable(), KParcelable {

    private constructor(parcel: Parcel) : this(parcel.readString(), parcel.readString(), readArrayList(parcel))

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(description)
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
    var title: String = title
        set(value) {
            field = value
            notifyPropertyChanged(BR.title)
        }

    @get:Bindable
    var description: String = description
        set(value) {
            field = value
            notifyPropertyChanged(BR.description)
        }
}