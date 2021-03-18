/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2021 NextGIS, info@nextgis.com
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

package com.nextgis.collector.util

import android.content.Context
import com.hypertrack.hyperlog.LogFormat
import com.nextgis.collector.BuildConfig


internal class CustomLogMessageFormat(context: Context) : LogFormat(context) {

    override fun getFormattedLogMessage(logLevelName: String, tag: String, message: String, timeStamp: String,
                                        senderName: String, osVersion: String, deviceUUID: String?): String? {
        var uuid = deviceUUID
        if (uuid == null) {
            uuid = "DeviceUUID"
        }
        val appTag = BuildConfig.VERSION_NAME
        return "$timeStamp | $senderName : $osVersion | $uuid | [$logLevelName/$appTag]: $message"
    }

}
