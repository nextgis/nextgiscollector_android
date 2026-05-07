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
import io.sentry.IPerformanceContinuousCollector
import java.io.File
import java.io.FileNotFoundException
import androidx.core.view.isVisible
import com.nextgis.collector.activity.MapFragment.Companion.CLICKED_FORM_ID
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE_BY_WALK
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.util.Constants
import com.nextgis.maplibui.util.ConstantsUI

class AddFeatureActivity :
    ProjectActivity(),
    View.OnClickListener,
    EditableLayersAdapter.OnItemClickListener {
    companion object {
        const val PERMISSIONS_CODE = 625
        const val IS_MAP_START = "is_map_start"
    }

    lateinit var binding: ActivityAddFeatureBinding
    private var layer: NGWVectorLayerUI? = null
    private val tree = ResourceTree(arrayListOf())
    private val layers = ArrayList<NGWVectorLayerUI>()
    private var history = ArrayList<String>()

    var mapFragment: MapFragment? = null

    var savedFormId : Long = -1L
    var savedAction : String = ""
    var savedLayerId : Int = -1
    var postponedIntent : Intent? = null
        get() {
            val prev = field
            postponedIntent = null
            return prev
        }

    //  should after edit return to list or keep map displayed
    // onetime read def -false
    private var returnToList = false
        get() {
            val prev = field
            returnToList = false
            return prev
        }

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

        mapFragment = MapFragment()
        supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container,mapFragment!!)
                .commit()

        if (intent != null && intent.getBooleanExtra(IS_MAP_START, false))
            showMap(true)
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
                showMap(! (binding.mapFragmentContainer.isVisible))
            } else ->
                mapFragment?.onClick(view)
        }
    }

    private fun layerByPath(id: String): NGWVectorLayerUI? {
        return layers.firstOrNull { it.path.name == id }
    }

    override fun onMapClick(id: String, clickedFormId: Long) {
        this.layer = layerByPath(id)
        requestForPermissions(object : OnPermissionCallback {
            override fun onPermissionGranted() {
                returnToList = true
                startEdit(true, false, clickedFormId)
            }
        }, true)
    }

    override fun onGpsClick(id: String, useMap : Boolean, clickedFormId : Long) {
        this.layer = layerByPath(id)
        requestForPermissions(object : OnPermissionCallback {
            override fun onPermissionGranted() {
                returnToList = true
                startEdit(false, useMap, clickedFormId)
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
            else  ->
                mapFragment?.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startEdit(map: Boolean, useMap : Boolean, clickedFormId: Long) {
        if (layer != null) {
            if (layer?.geometryType == GeoConstants.GTPoint || layer?.geometryType == GeoConstants.GTMultiPoint
                    || layer?.geometryType == GeoConstants.GTLineString || layer?.geometryType == GeoConstants.GTPolygon
                    || layer?.geometryType == GeoConstants.GTMultiLineString || layer?.geometryType == GeoConstants.GTMultiPolygon)
                if (map || useMap) {
                    savedFormId = clickedFormId
                    savedLayerId = layer!!.id
                    savedAction = if (useMap) NEW_FEATURE_BY_WALK else NEW_FEATURE

                    showMap(true)

                    val intent = IntentFor<AddFeatureActivity>(this)
                    intent.putExtra(CLICKED_FORM_ID, clickedFormId)

                    if (useMap)
                        intent.putExtra(NEW_FEATURE_BY_WALK, layer?.id)
                    else
                        intent.putExtra(NEW_FEATURE, layer?.id)

                    if (mapFragment?.isMapReadyToWork == true)
                        mapFragment?.startEditIfNeed(intent)
                    else {
                        postponedIntent = intent
                    }
                } else {
                    layer?.showEditForm(this, -1, null,clickedFormId)
                }
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

    fun getFormId(): Long{
        return savedFormId
    }

    fun showMap(visible : Boolean){
        binding.mapFragmentContainer.visibility = if (visible) View.VISIBLE else View.GONE
        binding.showMap.setImageResource(if (visible) R.drawable.ic_add_white_48dp else R.drawable.ic_map )

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (projectBorders != null && resultCode == RESULT_OK && data!= null){
            if (data.hasExtra(ConstantsUI.KEY_ADDED_POINT)){
                if (returnToList){
                    // hideMap
                    showMap(false)
                } else {
                    val pointArray :DoubleArray? = data.getDoubleArrayExtra(ConstantsUI.KEY_ADDED_POINT);
                    if (pointArray != null && pointArray.size>=2) {
                        val geoPoint = GeoPoint(pointArray[0], pointArray[1])
                        projectBorders.let {
                            if (!projectBorders!!.contains(geoPoint)) {
                                val builder = android.app.AlertDialog.Builder(this@AddFeatureActivity)
                                builder
                                    .setPositiveButton("ok", null)
                                    .setTitle(R.string.out_of_area_header)
                                    .setMessage(R.string.out_of_area_text)
                                val alertDialog = builder.create()
                                alertDialog.show()
                            }
                        }
                    }
                }
            }
        }

        if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
            val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())

            if (id != Constants.NOT_FOUND.toLong()) {
                mapFragment?.overlay!!.setSelectedFeature(id)

                map?.updateEditedId(id)
                if (mapFragment?.mSelectedLayer != null)
                    mapFragment?.mSelectedLayer!!.showFeature(id)

                mapFragment?.setHighlight()
                mapFragment?.overlay?.setHasEdits(false)
                //mapFragment?.setMode(MODE_SELECT_ACTION)

                if (map == null)
                    return;

                map.loadViewFeature(id,mapFragment?.selectedLayer!!.id)
                map.finishCreateNewFeature(id,mapFragment?.selectedLayer!! )
                map.loadViewFeature(id,mapFragment?.selectedLayer!!.id)
                map.reloadFeatureToMaplibre(id, mapFragment?.selectedLayer)
                map.updateSelectedMarker()
                map.hideSelectedDotSource()
                mapFragment?.setUpToolbar()
            }
        } else if  (mapFragment?.overlay!!.selectedFeatureGeometry != null)
            mapFragment?.overlay!!.setHasEdits(true)
    }
}