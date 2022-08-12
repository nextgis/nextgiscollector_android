/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
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

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.databinding.DataBindingUtil
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.collector.adapter.LayersAdapter
import com.nextgis.collector.databinding.ActivityMainBinding
import com.nextgis.maplib.api.GpsEventListener
import com.nextgis.maplib.api.ILayerView
import com.nextgis.maplib.datasource.Feature
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoGeometry
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.Layer
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.*
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.api.MapViewEventListener
import com.nextgis.maplibui.mapui.NGWRasterLayerUI
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.nextgis.maplibui.mapui.NGWWebMapLayerUI
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI
import com.nextgis.maplibui.overlay.CurrentLocationOverlay
import com.nextgis.maplibui.overlay.CurrentTrackOverlay
import com.nextgis.maplibui.overlay.EditLayerOverlay
import com.nextgis.maplibui.overlay.UndoRedoOverlay
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.pawegio.kandroid.locationManager
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar.*
import java.io.IOException


class MapActivity : ProjectActivity(), View.OnClickListener, LayersAdapter.OnItemClickListener, MapViewEventListener, GpsEventListener, ProjectActivity.OnPermissionCallback {
    companion object {
        const val NEW_FEATURE = "new_feature"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var overlay: EditLayerOverlay
    private lateinit var historyOverlay: UndoRedoOverlay
    private lateinit var locationOverlay: CurrentLocationOverlay
    private lateinit var trackOverlay: CurrentTrackOverlay
    private var selectedLayer: NGWVectorLayerUI? = null
    private var selectedFeature: Feature? = null
    private var needSave = false
    private var returnToList = false
    get() {
        val prev = field
        returnToList = false
        return prev
    }
    private var currentCenter = GeoPoint()
    private var newIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newIntent = intent

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        overlay = EditLayerOverlay(this, mapView)
        setup(with = toolbar, skipUpdateCheck = newIntent?.hasExtra(NEW_FEATURE))

        binding.apply {
            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
            mapContainer.addView(mapView, FrameLayout.LayoutParams(matchParent, matchParent))
        }
        setCenter()

        overlay.setTopToolbar(toolbar)
        overlay.setBottomToolbar(binding.bottomToolbar)
        historyOverlay = UndoRedoOverlay(this, mapView)
        historyOverlay.setTopToolbar(toolbar)

        locationOverlay = CurrentLocationOverlay(this, mapView)
        locationOverlay.setStandingMarker(R.mipmap.ic_location_standing)
        locationOverlay.setMovingMarker(R.mipmap.ic_location_moving)
        locationOverlay.setAutopanningEnabled(true)
        trackOverlay = CurrentTrackOverlay(this, mapView)

        mapView.addOverlay(overlay)
        mapView.addOverlay(historyOverlay)
        mapView.addOverlay(locationOverlay)
        mapView.addOverlay(trackOverlay)

        val manager = LinearLayoutManager(this)
        binding.layers.layoutManager = manager
        val dividerItemDecoration = DividerItemDecoration(this, manager.orientation)
        binding.layers.addItemDecoration(dividerItemDecoration)
        binding.executePendingBindings()
    }

    override fun init() {
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

        val layersAdapter = LayersAdapter(layers.reversed(), this)
        binding.layers.adapter = layersAdapter
        setUpToolbar(hasChanges)
    }

    override fun onResume() {
        super.onResume()
        overlay.onResume()
    }

    override fun onPause() {
        super.onPause()
        overlay.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView.addListener(this)
        locationOverlay.startShowingCurrentLocation()
        app.gpsEventSource.addListener(this)
        currentCenter.crs = 0
    }

    override fun onStop() {
        super.onStop()
        val point = map.mapCenter
        preferences.edit().putFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, map.zoomLevel)
                .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_X, java.lang.Double.doubleToRawLongBits(point.x))
                .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_Y, java.lang.Double.doubleToRawLongBits(point.y))
                .apply()

        app.gpsEventSource.removeListener(this)
        locationOverlay.stopShowingCurrentLocation()
        mapView.removeListener(this)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (overlay.mode == EditLayerOverlay.MODE_EDIT) {
            menu?.clear()
            toolbar.inflateMenu(R.menu.edit_geometry)
        }
        historyOverlay.defineUndoRedo()

        newIntent?.let {
            if (it.hasExtra(NEW_FEATURE)) {
                var centerPoint: GeoPoint? = null
                if (currentCenter.crs == 0) {
                    val gps = Manifest.permission.ACCESS_FINE_LOCATION
                    if (!PermissionUtil.hasPermission(this, gps)) {
                        requestForPermissions(object : OnPermissionCallback {
                            override fun onPermissionGranted() {
                                lastKnown()?.let { point -> mapView.panTo(point) }
                            }
                        }, false)
                    } else
                        centerPoint = lastKnown()
                } else
                    centerPoint = currentCenter
                centerPoint?.let { center ->
                    val zoom = map.zoomLevel
                    map.setZoomAndCenter(zoom, center)
                }
                val id = it.getIntExtra(NEW_FEATURE, -1)
                val selected = map.getLayerById(id) as NGWVectorLayerUI?
                selected?.let { layer ->
                    selectedLayer = layer
                    setTitle(getString(R.string.new_feature), layer.name)
                    setToolbar()
                    overlay.setSelectedLayer(layer)
                    overlay.selectedFeature = Feature()
                    startEdit()
                    overlay.createNewGeometry()
                    overlay.setHasEdits(true)
                    historyOverlay.saveToHistory(overlay.selectedFeature)
                }
                it.removeExtra(NEW_FEATURE)
                returnToList = true
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): GeoPoint? {
        var point: GeoPoint? = null
        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
            point = GeoPoint(location.longitude, location.latitude)
            point!!.crs = GeoConstants.CRS_WGS84
            point!!.project(GeoConstants.CRS_WEB_MERCATOR)
        }

        return point
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        newIntent = intent
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            R.id.menu_edit_save -> return saveEdits()
            R.id.menu_edit_undo, R.id.menu_edit_redo -> {
                val result = historyOverlay.onOptionsItemSelected(item.itemId)
                if (result) {
                    val undoRedoFeature = historyOverlay.feature
                    overlay.selectedFeature?.let { feature ->
                        feature.geometry = undoRedoFeature.geometry
                        overlay.fillDrawItems(undoRedoFeature.geometry)

                        val original = selectedLayer?.getGeometryForId(feature.id)
                        val hasEdits = original != null && undoRedoFeature.geometry == original
                        overlay.setHasEdits(!hasEdits)

                        mapView.buffer()
                        mapView.postInvalidate()
                    }
                }

                return result
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setCenter() {
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
    }

    private fun setUpToolbar(hasChanges: Boolean? = null) {
        title = project.title
        val toggle = ActionBarDrawerToggle(this, binding.drawer, toolbar, R.string.layers_drawer_open, R.string.layers_drawer_close)
        binding.apply {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawer.addDrawerListener(toggle)
            editAttributes.visibility = View.GONE
            editGeometry.visibility = View.GONE
            addFeature.visibility = View.VISIBLE
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

        clearData()
        if (returnToList)
            finish()
    }

    private fun clearData() {
        bottom_toolbar.visibility = View.GONE
        overlay.mode = EditLayerOverlay.MODE_NONE
        setMenu()
        selectedLayer = null
        selectedFeature = null
    }

    private fun setHighlight() {
        add_feature.visibility = View.VISIBLE
        edit_attributes.visibility = View.VISIBLE
        edit_geometry.visibility = View.VISIBLE
        overlay.mode = EditLayerOverlay.MODE_HIGHLIGHT
        bottom_toolbar.visibility = View.GONE
        setMenu()
        if (returnToList)
            finish()
    }

    private fun setMenu() {
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.main)
    }

    private fun cancelEdits() {
//        if (mEditLayerOverlay.hasEdits()) TODO prompt dialog
//            return;

        overlay.setHasEdits(false)
        val featureId = overlay.selectedFeatureId
        overlay.setSelectedFeature(featureId)
        if (featureId > -1)
            setHighlight()
        else
            setUpToolbar()
    }

    private fun saveEdits(): Boolean {
        val feature = overlay.selectedFeature
        var featureId = -1L
        var geometry: GeoGeometry? = null

        if (feature != null) {
            geometry = feature.geometry
            featureId = feature.id
        }

        if (geometry == null || !geometry.isValid) {
            toast(R.string.not_enough_points)
            return false
        }

        if (MapUtil.isGeometryIntersects(this, geometry))
            return false

        selectedLayer?.let {
            if (featureId == -1L) {
                it.showEditForm(this, featureId, geometry)
            } else {
                var uri = Uri.parse("content://" + app.authority + "/" + it.path.name)
                uri = ContentUris.withAppendedId(uri, featureId)
                val values = ContentValues()

                try {
                    values.put(Constants.FIELD_GEOM, geometry.toBlob())
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                contentResolver.update(uri, values, null, null)
                setHighlight()
                overlay.setHasEdits(false)
            }
        }

        return true
    }

    private fun startEdit() {
        add_feature.visibility = View.GONE
        edit_attributes.visibility = View.GONE
        edit_geometry.visibility = View.GONE
        bottom_toolbar.visibility = View.VISIBLE
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.edit_geometry)

        overlay.mode = EditLayerOverlay.MODE_EDIT
        bottom_toolbar.setOnMenuItemClickListener {
            val result = overlay.onOptionsItemSelected(it.itemId)
            if (result)
                historyOverlay.saveToHistory(overlay.selectedFeature)
            else
                requestForPermissions(this, true)
            result
        }

        historyOverlay.clearHistory()
        historyOverlay.saveToHistory(selectedFeature)
        overlay.setHasEdits(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
            val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, -1)
            if (id != -1L) {
                overlay.setSelectedFeature(id)
                selectedLayer?.showFeature(id)
                setHighlight()
                overlay.setHasEdits(false)
            }
        }
    }

    private fun locateCurrentPosition() {
        requestForPermissions(this, false)
    }

    override fun onPermissionGranted() {
        if (currentCenter.crs != 0)
            mapView.panTo(currentCenter)
        else
            toast(R.string.error_no_location)
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            currentCenter.setCoordinates(it.longitude, it.latitude)
            currentCenter.crs = GeoConstants.CRS_WGS84

            if (!currentCenter.project(GeoConstants.CRS_WEB_MERCATOR))
                currentCenter.crs = 0
        }
    }

    override fun onBestLocationChanged(location: Location) {

    }

    override fun onGpsStatusChanged(event: Int) {

    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.zoom_in -> if (mapView.canZoomIn()) mapView.zoomIn()
            R.id.zoom_out -> if (mapView.canZoomOut()) mapView.zoomOut()
            R.id.locate -> locateCurrentPosition()
            R.id.add_feature -> {
                finish()
                startActivity<AddFeatureActivity>()
            }
            R.id.edit_geometry -> startEdit()
            R.id.edit_attributes -> selectedFeature?.let { selectedLayer?.showEditForm(this, it.id, it.geometry) }
        }
    }

    override fun onItemClick(layer: Layer) {

    }

    override fun onDownloadTilesClick(layer: Layer) {
        val env: GeoEnvelope = map.currentBounds

        if (layer is RemoteTMSLayerUI) {
            layer.downloadTiles(this, env)
        } else if (layer is NGWRasterLayerUI) {
            layer.downloadTiles(this, env)
        } else if (layer is NGWWebMapLayerUI) {
            layer.downloadTiles(this, env)
        }
    }

    override fun onLongPress(event: MotionEvent) {

    }

    override fun onLayerAdded(id: Int) {

    }

    override fun onLayerDeleted(id: Int) {

    }

    override fun onLayersReordered() {

    }

    override fun onLayerDrawFinished(id: Int, percent: Float) {

    }

    private fun setToolbar() {
        binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        toolbar.setNavigationIcon(R.drawable.ic_action_cancel_dark)
        toolbar.setNavigationOnClickListener {
            if (overlay.mode == EditLayerOverlay.MODE_EDIT)
                cancelEdits()
            else
                setUpToolbar()
        }
    }

    private fun setTitle(title: String, subtitle: String?) {
        this.title = title
        supportActionBar?.subtitle = subtitle
    }

    override fun onSingleTapUp(event: MotionEvent) {
        if (overlay.mode == EditLayerOverlay.MODE_EDIT) {
            if (overlay.selectGeometryInScreenCoordinates(event.x, event.y))
                historyOverlay.saveToHistory(overlay.selectedFeature)
            return
        }

        val tolerance = resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP.toDouble()
        val dMinX = event.x - tolerance
        val dMaxX = event.x + tolerance
        val dMinY = event.y - tolerance
        val dMaxY = event.y + tolerance
        val mapEnv = map.screenToMap(GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)) ?: return

        val types = GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck or
                GeoConstants.GTLineStringCheck or GeoConstants.GTMultiLineStringCheck or
                GeoConstants.GTPolygonCheck or GeoConstants.GTMultiPolygonCheck
        val layers = mapView.getVectorLayersByType(types).filter {
            it is NGWVectorLayerUI && it.isEditable
        }
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
            overlay.setSelectedLayer(selectedLayer)
            for (i in items!!.indices) {    // FIXME hack for bad RTree cache
                val feature = selectedLayer?.getFeature(items[i])
                if (feature != null && feature.geometry != null)
                    overlay.setSelectedFeature(feature.id)
            }

            overlay.selectedFeature?.let {
                edit_attributes.visibility = View.VISIBLE
                edit_geometry.visibility = View.VISIBLE

                setTitle(getString(R.string.feature_n, it.id), selectedLayer?.name)
                setToolbar()

                selectedFeature = it
                overlay.mode = EditLayerOverlay.MODE_HIGHLIGHT
            }
        } else
            setUpToolbar()

        mapView.postInvalidate()
    }

    override fun onLayerChanged(id: Int) {

    }

    override fun onExtentChanged(zoom: Float, center: GeoPoint?) {

    }

    override fun onLayerDrawStarted() {

    }

    override fun panStart(e: MotionEvent?) {
        if (overlay.mode == EditLayerOverlay.MODE_CHANGE)
            needSave = true
    }

    override fun panMoveTo(e: MotionEvent?) {

    }

    override fun panStop() {
        if (needSave) {
            needSave = false
            historyOverlay.saveToHistory(overlay.selectedFeature)
        }
    }

}