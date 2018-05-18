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

package com.nextgis.collector.activity

import android.content.SyncResult
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import com.nextgis.collector.R
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.NGWUtil
import com.pawegio.kandroid.startActivity
import kotlin.concurrent.thread

abstract class ProjectActivity : BaseActivity() {

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_sync -> sync()
            R.id.menu_change_project -> ask()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    protected fun sync() {
        thread {
            for (i in 0 until map.layerCount) {
                val layer = map.getLayer(i)
                if (layer is NGWVectorLayer) {
                    val ver = NGWUtil.getNgwVersion(this@ProjectActivity, layer.accountName)
                    layer.sync(app.authority, ver, SyncResult())
                }
            }
        }.start()
    }

    private fun change() {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val account = app.getAccount(layer.accountName)
                app.removeAccount(account)
            }
        }

        map.delete()
        startActivity<ProjectListActivity>()
        preferences.edit().remove("project").apply()
    }

    private fun ask() {
        AlertDialog.Builder(this).setTitle(R.string.change_project)
                .setMessage(R.string.change_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, { _, _ -> change() })
                .show()
    }
}