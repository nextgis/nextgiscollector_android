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

package com.nextgis.collector

import android.os.Parcel
import android.os.Parcelable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

// https://medium.com/@BladeCoder/reducing-parcelable-boilerplate-code-using-kotlin-741c3124a49a
interface KParcelable : Parcelable {
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int)

    companion object {
        fun readStringFrom(parcel: Parcel): String {
            parcel.readString()?.let { return it }
            return ""
        }

        inline fun <reified T> readArrayList(parcel: Parcel): ArrayList<T> {
            val data = parcel.readArray(T::class.java.classLoader)
            data?.let { array ->
                val list = ArrayList<T>(array.size)
                array.map { list.add(it as T) }
                return list
            }
            return arrayListOf()
        }
    }
}

// Creator factory functions

inline fun <reified T> parcelableCreator(
        crossinline create: (Parcel) -> T) =
        object : Parcelable.Creator<T> {
            override fun createFromParcel(source: Parcel) = create(source)
            override fun newArray(size: Int) = arrayOfNulls<T>(size)
        }

inline fun <reified T> parcelableClassLoaderCreator(
        crossinline create: (Parcel, ClassLoader) -> T) =
        object : Parcelable.ClassLoaderCreator<T> {
            override fun createFromParcel(source: Parcel, loader: ClassLoader) =
                    create(source, loader)

            override fun createFromParcel(source: Parcel) =
                    createFromParcel(source, T::class.java.classLoader)

            override fun newArray(size: Int) = arrayOfNulls<T>(size)
        }

// Parcel extensions

fun Parcel.readBoolean() = readInt() != 0

fun Parcel.writeBoolean(value: Boolean) = writeInt(if (value) 1 else 0)

inline fun <reified T : Enum<T>> Parcel.readEnum() =
        readInt().let { if (it >= 0) enumValues<T>()[it] else null }

fun <T : Enum<T>> Parcel.writeEnum(value: T?) =
        writeInt(value?.ordinal ?: -1)

inline fun <T> Parcel.readNullable(reader: () -> T) =
        if (readInt() != 0) reader() else null

inline fun <T> Parcel.writeNullable(value: T?, writer: (T) -> Unit) {
    if (value != null) {
        writeInt(1)
        writer(value)
    } else {
        writeInt(0)
    }
}

fun Parcel.readDate() =
        readNullable { Date(readLong()) }

fun Parcel.writeDate(value: Date?) =
        writeNullable(value) { writeLong(it.time) }

fun Parcel.readBigInteger() =
        readNullable { BigInteger(createByteArray()) }

fun Parcel.writeBigInteger(value: BigInteger?) =
        writeNullable(value) { writeByteArray(it.toByteArray()) }

fun Parcel.readBigDecimal() =
        readNullable { BigDecimal(BigInteger(createByteArray()), readInt()) }

fun Parcel.writeBigDecimal(value: BigDecimal?) = writeNullable(value) {
    writeByteArray(it.unscaledValue().toByteArray())
    writeInt(it.scale())
}

fun <T : Parcelable> Parcel.readTypedObjectCompat(c: Parcelable.Creator<T>) =
        readNullable { c.createFromParcel(this) }

fun <T : Parcelable> Parcel.writeTypedObjectCompat(value: T?, parcelableFlags: Int) =
        writeNullable(value) { it.writeToParcel(this, parcelableFlags) }