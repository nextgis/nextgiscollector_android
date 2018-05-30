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
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.collector.adapter.LayersAdapter
import com.nextgis.collector.databinding.ActivityMainBinding
import com.nextgis.maplib.api.ILayerView
import com.nextgis.maplib.datasource.Feature
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.Layer
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.api.MapViewEventListener
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.toast


class MapActivity : ProjectActivity(), View.OnClickListener, LayersAdapter.OnItemClickListener, MapViewEventListener {
    private lateinit var binding: ActivityMainBinding
    //    private lateinit var overlay: EditLayerOverlay
    private var selectedLayer: NGWVectorLayerUI? = null
    private var selectedFeature: Feature? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.executePendingBindings()

        binding.apply {
            map.setZoomAndCenter(map.minZoom, GeoPoint(0.0, 0.0))
            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
            container.addView(mapView, FrameLayout.LayoutParams(matchParent, matchParent))
        }

        val layers = ArrayList<Layer>()
        var hasChanges = false
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is Layer)
                layers.add(layer)
            if (layer is NGWVectorLayer && !hasChanges) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                hasChanges = changesCount > 0
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setUpToolbar(hasChanges)

        val layersAdapter = LayersAdapter(layers.reversed(), this)
        binding.layers.adapter = layersAdapter
        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration = DividerItemDecoration(this, manager.orientation)
        binding.layers.addItemDecoration(dividerItemDecoration)

        val mapZoom = preferences.getFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, map.minZoom)
        var mapScrollX: Double
        var mapScrollY: Double
        try {
            val x = preferences.getLong(SettingsConstantsUI.KEY_PREF_SCROLL_X, 0)
            val y = preferences.getLong(SettingsConstantsUI.KEY_PREF_SCROLL_Y, 0)
            mapScrollX = java.lang.Double.longBitsToDouble(x)
            mapScrollY = java.lang.Double.longBitsToDouble(y)
        } catch (e: ClassCastException) {
            mapScrollX = 0.0
            mapScrollY = 0.0
        }

        map.setZoomAndCenter(mapZoom, GeoPoint(mapScrollX, mapScrollY))
//        overlay = EditLayerOverlay(this, mapView)
        mapView.addListener(this)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.zoom_in -> if (mapView.canZoomIn()) mapView.zoomIn()
            R.id.zoom_out -> if (mapView.canZoomOut()) mapView.zoomOut()
            R.id.add_feature -> startActivity<AddFeatureActivity>()
            R.id.edit_geometry -> toast("Edit geometry")
            R.id.edit_attributes -> selectedFeature?.let { selectedLayer?.showEditForm(this, it.id, it.geometry) }
        }
    }

    override fun onItemClick(layer: Layer) {

    }

    override fun onStop() {
        super.onStop()
        val point = map.mapCenter
        preferences.edit().putFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, map.zoomLevel)
                .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_X, java.lang.Double.doubleToRawLongBits(point.x))
                .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_Y, java.lang.Double.doubleToRawLongBits(point.y))
                .apply()
    }

    private fun setUpToolbar(hasChanges: Boolean? = null) {
        title = preferences.getString("project", getString(R.string.app_name))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawer, toolbar, R.string.layers_drawer_open, R.string.layers_drawer_close)
        binding.apply {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawer.addDrawerListener(toggle)
            editAttributes.visibility = View.GONE
            editGeometry.visibility = View.GONE
        }

        var changes = hasChanges ?: false
        if (hasChanges == null) {
            for (i in 0 until map.layerCount) {
                val layer = map.getLayer(i)
                if (layer is NGWVectorLayer) {
                    val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                    if (changesCount > 0) {
                        changes = true
                        break
                    }
                }
            }
        }

        setSubtitle(changes)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toggle.syncState()

        selectedLayer = null
        selectedFeature = null
    }

    override fun onLongPress(event: MotionEvent) {
        val tolerance = resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP.toDouble()
        val dMinX = event.x - tolerance
        val dMaxX = event.x + tolerance
        val dMinY = event.y - tolerance
        val dMaxY = event.y + tolerance
        val mapEnv = map.screenToMap(GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)) ?: return

        val types = GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck
        val layers = mapView.getVectorLayersByType(types).filter {
            it is NGWVectorLayerUI && it.isEditable
        } // TODO all geometries
        var items: List<Long>? = null
        var intersects = false
        for (layer in layers) {
            if (!layer.isValid || layer is ILayerView && !layer.isVisible)
                continue

            items = (layer as NGWVectorLayerUI).query(mapEnv)
            if (!items.isEmpty()) {
                selectedLayer = layer
                intersects = true
                break
            }
        }

        if (intersects) {
            selectedFeature = selectedLayer?.getFeature(items!!.first())
            binding.editAttributes.visibility = View.VISIBLE
            binding.editGeometry.visibility = View.VISIBLE

            title = getString(R.string.feature_n, selectedFeature?.id)
            supportActionBar?.subtitle = selectedLayer?.name
            binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            toolbar.setNavigationIcon(R.drawable.ic_action_cancel_dark)
            toolbar.setNavigationOnClickListener { setUpToolbar() }

//            mEditLayerOverlay.setSelectedLayer(selectedLayer)
//            for (i in items!!.indices) {    // FIXME hack for bad RTree cache
//                val featureId = items[i]
//                val geometry = mSelectedLayer.getGeometryForId(featureId)
//                if (geometry != null)
//                    mEditLayerOverlay.setSelectedFeature(featureId)
//            }
//            setMode(MODE_SELECT_ACTION)
        } else {
            selectedLayer = null
        }

        //set select action mode
//        mMap.postInvalidate()
    }

    override fun onLayerAdded(id: Int) {

    }

    override fun onLayerDeleted(id: Int) {

    }

    override fun onLayersReordered() {

    }

    override fun onLayerDrawFinished(id: Int, percent: Float) {

    }

    override fun onSingleTapUp(event: MotionEvent?) {

    }

    override fun onLayerChanged(id: Int) {

    }

    override fun onExtentChanged(zoom: Float, center: GeoPoint?) {

    }

    override fun onLayerDrawStarted() {

    }

    override fun panStart(e: MotionEvent?) {

    }

    override fun panMoveTo(e: MotionEvent?) {

    }

    override fun panStop() {

    }

}