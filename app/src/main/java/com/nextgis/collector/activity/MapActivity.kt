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
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ActivityMainBinding
import com.pawegio.kandroid.startActivity
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.NGWVectorLayer
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import com.nextgis.collector.adapter.LayersAdapter
import com.nextgis.maplib.map.Layer


class MapActivity : BaseActivity(), View.OnClickListener, LayersAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.executePendingBindings()
        title = preferences.getString("project", getString(R.string.app_name))

        binding.apply {
            map.setZoomAndCenter(map.minZoom, GeoPoint(0.0, 0.0))
            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
            container.addView(mapView, FrameLayout.LayoutParams(matchParent, matchParent))
            zoomIn.setOnClickListener(this@MapActivity)
            zoomOut.setOnClickListener(this@MapActivity)
        }

        val layers = ArrayList<Layer>()
        for (i in 0 until map.layerCount)
            if (map.getLayer(i) is Layer)
                layers.add(map.getLayer(i) as Layer)

        val layersAdapter = LayersAdapter(layers.reversed(), this)
        binding.layers.adapter = layersAdapter
        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration = DividerItemDecoration(binding.layers.context, manager.orientation)
        binding.layers.addItemDecoration(dividerItemDecoration)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawer, toolbar, R.string.layers_drawer_open, R.string.layers_drawer_close)
        binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        binding.drawer.addDrawerListener(toggle)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toggle.syncState()
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
            R.id.zoom_in -> if (mapView.canZoomIn()) mapView.zoomIn()
            R.id.zoom_out -> if (mapView.canZoomOut()) mapView.zoomOut()
        }
    }

    override fun onItemClick(layer: Layer) {

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