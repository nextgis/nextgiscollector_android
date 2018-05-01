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

package com.nextgis.collector

import com.nextgis.maplib.util.Constants
import com.nextgis.maplibui.GISApplication

class CollectorApplication : GISApplication() {
    override fun getAuthority(): String {
        return BuildConfig.APPLICATION_ID
    }

    override fun showSettings(setting: String?) {

    }

    override fun sendEvent(category: String?, action: String?, label: String?) {

    }

    override fun sendScreen(name: String?) {

    }

    override fun getAccountsType(): String {
        return Constants.NGW_ACCOUNT_TYPE
    }

}