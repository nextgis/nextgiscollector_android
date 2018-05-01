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

package com.nextgis.collector.activity

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ActivityMainBinding
import com.pawegio.kandroid.startActivity
import kotlinx.android.synthetic.main.activity_main.*
import com.nextgis.maplib.datasource.GeoPoint



class MapActivity : BaseActivity(), View.OnClickListener {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.executePendingBindings()
        title = preferences.getString("project", getString(R.string.app_name))

        binding.apply {
            map.setZoomAndCenter(map.minZoom, GeoPoint(0.0, 0.0))
            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
            container.addView(map, FrameLayout.LayoutParams(matchParent, matchParent))
            zoomIn.setOnClickListener(this@MapActivity)
            zoomOut.setOnClickListener(this@MapActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_change_project -> ask()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.zoom_in -> if (map.canZoomIn()) map.zoomIn()
            R.id.zoom_out -> if (map.canZoomOut()) map.zoomOut()
        }
    }

    private fun change() {
        map.map.delete()
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