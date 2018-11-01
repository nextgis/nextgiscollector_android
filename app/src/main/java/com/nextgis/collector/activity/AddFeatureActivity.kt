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

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.MenuItem
import android.view.View
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.adapter.EditableLayersAdapter
import com.nextgis.collector.data.ResourceTree
import com.nextgis.collector.databinding.ActivityAddFeatureBinding
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.pawegio.kandroid.IntentFor
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.toast
import kotlinx.android.synthetic.main.toolbar.*
import java.io.File

class AddFeatureActivity : ProjectActivity(), View.OnClickListener, EditableLayersAdapter.OnItemClickListener, ProjectActivity.OnPermissionCallback {
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
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_feature)
        setup(with = toolbar)

        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration = DividerItemDecoration(this, manager.orientation)
        binding.layers.addItemDecoration(dividerItemDecoration)
        binding.executePendingBindings()
    }

    override fun init() {
        layers.clear()
        history.clear()
        tree.resources.clear()

        val base = getExternalFilesDir(null) ?: filesDir
        val file = File(base, CollectorApplication.TREE)
        val json = FileUtil.readFromFile(file)
        tree.parse(json)

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
        val layers = items.filter {
            val layer = map.getLayerByPathName(it.id)
            (layer as? NGWVectorLayerUI)?.isEditable ?: true
        }
        binding.layers.adapter = EditableLayersAdapter(layers, this)
        supportActionBar?.setDisplayHomeAsUpEnabled(history.size != 0)
        supportActionBar?.setHomeButtonEnabled(history.size != 0)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.show_map -> startActivity<MapActivity>()
        }
    }

    private fun layerByPath(id: String): NGWVectorLayerUI? {
        return layers.firstOrNull { it.path.name == id }
    }

    override fun onMapClick(id: String) {
        val intent = IntentFor<MapActivity>(this)
        layerByPath(id)?.let {
            intent.putExtra(MapActivity.NEW_FEATURE, it.id)
            startActivity(intent)
        }
    }

    override fun onGpsClick(id: String) {
        this.layer = layerByPath(id)
        requestForPermissions(this, true)
    }

    override fun onDirClick(id: String) {
        history.add(id)
        changeAdapter(id)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
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

    override fun onPermissionGranted() {
        if (layer?.geometryType == 1 || layer?.geometryType == 4)
            layer?.showEditForm(this, -1, null)
        else
            toast(R.string.not_implemented)
    }

}