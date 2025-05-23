/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2018-2021 NextGIS, info@nextgis.com
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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.adapter.EditableLayersAdapter
import com.nextgis.collector.data.ResourceTree
import com.nextgis.collector.databinding.ActivityAddFeatureBinding
import com.nextgis.collector.util.IntentFor
import com.nextgis.collector.util.startActivity
import com.nextgis.collector.util.toast
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.nextgis.maplibui.service.TrackerService
import java.io.File
import java.io.FileNotFoundException

class AddFeatureActivity : ProjectActivity(), View.OnClickListener, EditableLayersAdapter.OnItemClickListener {
    companion object {
        const val PERMISSIONS_CODE = 625
    }

    private lateinit var binding: ActivityAddFeatureBinding
    private var layer: NGWVectorLayerUI? = null
    private val tree = ResourceTree(arrayListOf())
    private val layers = ArrayList<NGWVectorLayerUI>()
    private var history = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =  ActivityAddFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setup(with = binding.toolbar)

        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration =
            DividerItemDecoration(
                this,
                manager.orientation
            )
        binding.layers.addItemDecoration(dividerItemDecoration)
        binding.executePendingBindings()
    }

    override fun init() {
        layers.clear()
        history.clear()
        tree.resources.clear()

        val base = getExternalFilesDir(null) ?: filesDir
        val file = File(base, CollectorApplication.TREE)
        try {
            val json = FileUtil.readFromFile(file)
            tree.parse(json)
        } catch (e: FileNotFoundException) {
            toast(R.string.error_form_create)
            return
        }

        var hasChanges = false
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer && !hasChanges) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                hasChanges = changesCount > 0
            }

            if (layer is NGWVectorLayerUI && layer.isEditable)
                if (layer.geometryType in 1..6)
                    layers.add(layer)
        }
        changeAdapter()
        setSubtitle(hasChanges)
    }

    private fun changeAdapter(dirId: String = "") {
        val level = tree.getLevel(dirId)
        val items = level.filter { it.type != "tms" && it.type != "ngrc" }
        val layersResources = items.filter {
            val layer = map.getLayerByPathName(it.id)
            (layer as? NGWVectorLayerUI)?.isEditable ?: true
        }
        binding.layers.adapter = EditableLayersAdapter(layersResources, this, layers)
        supportActionBar?.setDisplayHomeAsUpEnabled(history.size != 0)
        supportActionBar?.setHomeButtonEnabled(history.size != 0)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.show_map ->  {
                finish()
                startActivity<MapActivity>()
            }

        }
    }

    private fun layerByPath(id: String): NGWVectorLayerUI? {
        return layers.firstOrNull { it.path.name == id }
    }

    override fun onMapClick(id: String) {
        this.layer = layerByPath(id)
        requestForPermissions(object : OnPermissionCallback {
            override fun onPermissionGranted() {
                startEdit(true, false)
            }
        }, true)
    }

    override fun onGpsClick(id: String, useMap : Boolean) {
        this.layer = layerByPath(id)
        requestForPermissions(object : OnPermissionCallback {
            override fun onPermissionGranted() {
                startEdit(false, useMap)
            }
        }, true)
    }

    override fun onDirClick(id: String) {
        history.add(id)
        changeAdapter(id)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val trackInProgress = TrackerService.hasUnfinishedTracks(this) && TrackerService.isTrackerServiceRunning(this)
        val itemName = getString(if (trackInProgress) R.string.tracks_stop else R.string.start)
        trackItem?.setTitle(itemName)




        when (item.itemId) {
            android.R.id.home -> {
                if (history.size > 0) {
                    history.removeAt(history.size - 1)
                    val last = if (history.size > 0) history[history.size - 1] else ""
                    changeAdapter(last)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startEdit(map: Boolean, useMap : Boolean) {
        if (layer != null) {
            if (layer?.geometryType == GeoConstants.GTPoint || layer?.geometryType == GeoConstants.GTMultiPoint
                    || layer?.geometryType == GeoConstants.GTLineString || layer?.geometryType == GeoConstants.GTPolygon
                    || layer?.geometryType == GeoConstants.GTMultiLineString || layer?.geometryType == GeoConstants.GTMultiPolygon)
                if (map || useMap) {
                    val intent = IntentFor<MapActivity>(this)
                    if (useMap)
                        intent.putExtra(MapActivity.NEW_FEATURE_BY_WALK, layer?.id)
                    else
                        intent.putExtra(MapActivity.NEW_FEATURE, layer?.id)
                    startActivityForResult(intent,IVectorLayerUI.MODIFY_REQUEST )
                } else
                    layer?.showEditForm(this, -1, null)
            else
                toast(R.string.not_implemented)
        } else
            toast(R.string.error_layer_not_inited)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        //Toast.makeText(this,"ON_RESUME", -1)
//        if ( !(application as CollectorApplication).isSyncProgress)
//            binding.overlay.visibility = View.GONE;
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (resultCode == RESULT_OK && )
//
//
//    }


}