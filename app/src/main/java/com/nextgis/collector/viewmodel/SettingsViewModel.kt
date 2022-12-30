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

package com.nextgis.collector.viewmodel

import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.databinding.ObservableField
import com.nextgis.collector.model.SettingsModel

class SettingsViewModel : ViewModel() {
    private var settingsModel: SettingsModel = SettingsModel()
    var uuid = ObservableField("0055AAFF")

    fun init(context: Context) {
        uuid.set(settingsModel.getUid(context))
    }
}