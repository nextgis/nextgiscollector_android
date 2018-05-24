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

import android.Manifest
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.View
import com.nextgis.collector.R
import com.nextgis.collector.adapter.EditableLayersAdapter
import com.nextgis.collector.databinding.ActivityAddFeatureBinding
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.pawegio.kandroid.longToast
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.toast

class AddFeatureActivity : ProjectActivity(), View.OnClickListener, EditableLayersAdapter.OnItemClickListener {
    companion object {
        const val PERMISSIONS_CODE = 625
    }

    private lateinit var binding: ActivityAddFeatureBinding
    private var layer: NGWVectorLayerUI? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_feature)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        var hasChanges = false
        val layers = ArrayList<NGWVectorLayerUI>()
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer && !hasChanges) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                hasChanges = changesCount > 0
            }

            if (layer is NGWVectorLayerUI && layer.isEditable)
                if (layer.geometryType == GeoConstants.GTPoint || layer.geometryType == GeoConstants.GTMultiPoint) // TODO add line and polygon support
                    layers.add(layer)
        }
        binding.layers.adapter = EditableLayersAdapter(layers, this)
        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration = DividerItemDecoration(this, manager.orientation)
        binding.layers.addItemDecoration(dividerItemDecoration)

        setSubtitle(hasChanges)
        binding.executePendingBindings()
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.show_map -> startActivity<MapActivity>()
        }
    }

    override fun onMapClick(layer: NGWVectorLayerUI) {
        toast("Map clicked")
    }

    override fun onGpsClick(layer: NGWVectorLayerUI) {
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val status = ActivityCompat.checkSelfPermission(this, fine)
        if (status != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(coarse, fine)
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_CODE)
            this.layer = layer
        } else
            layer.showEditForm(this, -1, null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var granted = requestCode == PERMISSIONS_CODE
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted) {
            layer?.showEditForm(this, -1, null)
        } else
            longToast(R.string.permission_denied)
    }
}