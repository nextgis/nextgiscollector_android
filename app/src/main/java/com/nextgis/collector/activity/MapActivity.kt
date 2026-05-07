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
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.collector.R
import com.nextgis.collector.activity.MapFragment.Companion.CLICKED_FORM_ID
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE_BY_WALK
import com.nextgis.collector.adapter.LayersAdapter
import com.nextgis.collector.databinding.ActivityMapBinding
import com.nextgis.collector.util.locationManager
import com.nextgis.collector.util.runDelayed
import com.nextgis.collector.util.startActivity
import com.nextgis.collector.util.toast
import com.nextgis.maplib.api.GpsEventListener
import com.nextgis.maplib.api.ILayerView
import com.nextgis.maplib.datasource.Feature
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoGeometry
import com.nextgis.maplib.datasource.GeoLineString
import com.nextgis.maplib.datasource.GeoLinearRing
import com.nextgis.maplib.datasource.GeoMultiLineString
import com.nextgis.maplib.datasource.GeoMultiPoint
import com.nextgis.maplib.datasource.GeoMultiPolygon
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.datasource.GeoPolygon
import com.nextgis.maplib.map.Layer
import com.nextgis.maplib.map.MLP.MLGeometryEditClass
import com.nextgis.maplib.map.MPLFeaturesUtils
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.MapDrawable.MODE_EDIT_BY_TOUCH
import com.nextgis.maplib.map.MaplibreMapInteraction
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplib.util.MapUtil
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.api.MapViewEventListener
import com.nextgis.maplibui.mapui.NGWRasterLayerUI
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.nextgis.maplibui.mapui.NGWWebMapLayerUI
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI
import com.nextgis.maplibui.overlay.CurrentLocationOverlay
import com.nextgis.maplibui.overlay.CurrentTrackOverlay
import com.nextgis.maplibui.overlay.EditLayerOverlay
import com.nextgis.maplibui.overlay.EditLayerOverlay.MODE_EDIT
import com.nextgis.maplibui.overlay.EditLayerOverlay.MODE_EDIT_BY_WALK
import com.nextgis.maplibui.overlay.UndoRedoOverlay
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.nextgis.maplibui.util.SettingsConstantsUI.DEFAUL_BORDERS_WAS_APPLY
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.internal.http2.Http2Reader
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.module.http.HttpRequestImpl
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan


// ALL from this activity moved to collector MapFragment
class MapActivity
//    : ProjectActivity(),
//    View.OnClickListener,
//    LayersAdapter.OnItemClickListener,
//    MapViewEventListener,
//    GpsEventListener,
//    ProjectActivity.OnPermissionCallback,
//    OnMapReadyCallback ,
//    MaplibreMapInteraction,
//    MapLibreMap.OnCameraIdleListener

{


//    private lateinit var binding: ActivityMapBinding
//    private lateinit var overlay: EditLayerOverlay
//    private lateinit var historyOverlay: UndoRedoOverlay
//    private lateinit var locationOverlay: CurrentLocationOverlay
//    private lateinit var trackOverlay: CurrentTrackOverlay
//    private var selectedLayer: NGWVectorLayerUI? = null
//    private var selectedFeature: Feature? = null
//    private var needSave = false
//    private var clickedFormId = -1L
//    private var returnToList = false
//        get() {
//            val prev = field
//            returnToList = false
//            return prev
//        }
//    private var currentCenter = GeoPoint()
//    private var newIntent: Intent? = null
//
//
//    // merge to maplibre part
//    //lateinit var mMapRef: WeakReference<MapViewOverlays>
//    protected var mSelectedLayer: VectorLayer? = null
//
////    var mode: Int = 0
////        protected set
//
//    // use edit overlay mode
//
//    protected var mTolerancePX: Float = 0f
//
//    var longClickProcessed = false
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        newIntent = intent
//
//        binding = ActivityMapBinding.inflate(layoutInflater) //DataBindingUtil.setContentView(this, R.layout.activity_main)
//        setContentView(binding.root)
//
//        overlay = EditLayerOverlay(this, mapView)
//        setup(with = binding.toolbar, skipUpdateCheck = newIntent?.hasExtra(NEW_FEATURE))
//
//        binding.apply {
//            val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
//            mapContainer.addView(mapView, FrameLayout.LayoutParams(matchParent, matchParent))
//        }
//
//        val defBOrdersWasApply = preferences.getBoolean(DEFAUL_BORDERS_WAS_APPLY, false);
//        if (defBOrdersWasApply)
//            setCenter(0)
//
//        overlay.setTopToolbar(binding.toolbar)
//        overlay.setBottomToolbar(binding.bottomToolbar)
//        historyOverlay = UndoRedoOverlay(this, mapView)
//        historyOverlay.setTopToolbar(binding.toolbar)
//
//        locationOverlay = CurrentLocationOverlay(this, mapView)
//        locationOverlay.setStandingMarker(R.mipmap.ic_location_standing)
//        locationOverlay.setMovingMarker(R.mipmap.ic_location_moving)
//        locationOverlay.setAutopanningEnabled(true)
//        trackOverlay = CurrentTrackOverlay(this, mapView)
//
//        mapView.addOverlay(overlay)
//        mapView.addOverlay(historyOverlay)
//        mapView.addOverlay(locationOverlay)
//        mapView.addOverlay(trackOverlay)
//
//        val manager = LinearLayoutManager(this)
//        binding.layers.layoutManager = manager
//        val dividerItemDecoration =
//            DividerItemDecoration(
//                this,
//                manager.orientation
//            )
//        binding.layers.addItemDecoration(dividerItemDecoration)
//        binding.executePendingBindings()
//        clickedFormId = intent.getLongExtra(CLICKED_FORM_ID, -1)
//
//
//        if (projectBorders != null && !defBOrdersWasApply){
//            projectBorders.let {
//                preferences.edit().putBoolean(DEFAUL_BORDERS_WAS_APPLY, true).apply()
//                map.zoomToExtent(GeoEnvelope(projectBorders!!.minX, projectBorders!!.maxX,
//                    projectBorders!!.minY, projectBorders!!.maxY))
//            }
//        }
//
//        mTolerancePX = resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP
//
////        mMapRef = WeakReference(
////            MapViewOverlays(this,(applicationContext as GISApplication)!!.map as MapDrawable))
////        mMapRef.get()!!.id = R.id.map_view
//
//        mapView.map!!.maplibreMapView = binding.mapViewMaplibre
//        binding.mapViewMaplibre.onCreate(savedInstanceState)
//        binding.mapViewMaplibre.getMapAsync(this)
//    }
//
//
//    override fun onMapReady(mapboxMap: MapLibreMap) {
//        mapView.map!!.setMapContext(this)
//
//        val  interceptor = (applicationContext as GISApplication).getAuthInterceptor();
//
//        val client = OkHttpClient.Builder()
//            .addInterceptor(interceptor)
//            .connectTimeout(10, TimeUnit.SECONDS)
//            .readTimeout(30, TimeUnit.SECONDS)
//            .dispatcher(getDispatcher())
//            .build()
//
//        // set global http client for raster auth
//        HttpRequestImpl.setOkHttpClient(client)
//
//        val mapboxMaplibre = mapboxMap
//        mapView.map!!.maplibreMap = mapboxMaplibre
//
//        mapboxMaplibre.uiSettings.isRotateGesturesEnabled = false
//        mapboxMaplibre.uiSettings.isCompassEnabled = false
//
//        mapboxMaplibre.addOnCameraIdleListener(this)
//
//        val styleJson = loadJsonFromAssets(this, "ngwstyle.json")
//        val vectorLayers = mapView.getVectorLayersByType(GeoConstants.GTAnyCheck)
//        val layersTrack =  mapView.getLayersByType(Constants.LAYERTYPE_TRACKS)
//        vectorLayers.addAll(layersTrack);
//
//        val allLayers = mapView.getAllLayers()
//
//        mapView.map!!.loadLayersToMaplibreMap(styleJson, allLayers, true, true)
//    }
//
//    private fun getDispatcher(): Dispatcher {
//        val dispatcher = Dispatcher()
//        // Matches core limit set on
//        // https://github.com/mapbox/mapbox-gl-native/blob/master/platform/android/src/http_file_source.cpp#L192
//        dispatcher.maxRequestsPerHost = 20
//        return dispatcher
//    }
//
//    fun loadJsonFromAssets(context: Context, fileName: String): String? {
//        return try {
//            val inputStream = context.assets.open(fileName)
//            val size = inputStream.available()
//            val buffer = ByteArray(size)
//            inputStream.read(buffer)
//            inputStream.close()
//            String(buffer, Charsets.UTF_8)
//        } catch (ex: IOException) {
//            ex.printStackTrace()
//            null
//        }
//    }
//
//    override fun init() {
//        val layers = ArrayList<Layer>()
//        var hasChanges = false
//        for (i in 0 until map.layerCount) {
//            val layer = map.getLayer(map.layerCount -1 - i)
//            if (layer is Layer)
//                layers.add(layer)
//            if (layer is NGWVectorLayer && !hasChanges) {
//                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
//                hasChanges = changesCount > 0
//            }
//        }
//
//        val layersAdapter = LayersAdapter(layers, this)
//        binding.layers.adapter = layersAdapter
//        setUpToolbar(hasChanges)
//    }
//
//    override fun onResume() {
//        super.onResume()
//        overlay.onResume()
////        if ( !(application as CollectorApplication).isTrackInProgress)
////            binding.overlay.visibility = View.GONE;
//    }
//
//    override fun onPause() {
//        super.onPause()
//        overlay.onPause()
//    }
//
//    override fun onStart() {
//        super.onStart()
//        mapView.addListener(this)
//        locationOverlay.startShowingCurrentLocation()
//        app.gpsEventSource.addListener(this)
//        currentCenter.crs = 0
//        //startEditIfNeed()
//    }
//
//    override fun onStop() {
//        super.onStop()
//        val point = map.mapCenter
//        preferences.edit().putFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, map.zoomLevel)
//            .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_X, java.lang.Double.doubleToRawLongBits(point.x))
//            .putLong(SettingsConstantsUI.KEY_PREF_SCROLL_Y, java.lang.Double.doubleToRawLongBits(point.y))
//            .apply()
//
//        app.gpsEventSource.removeListener(this)
//        locationOverlay.stopShowingCurrentLocation()
//        mapView.removeListener(this)
//    }
//
//    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
//        if (overlay.mode == EditLayerOverlay.MODE_EDIT || overlay.mode == EditLayerOverlay.MODE_EDIT_BY_WALK) {
//            menu?.clear()
//            binding.toolbar.inflateMenu(R.menu.edit_geometry)
//        }
//        return super.onPrepareOptionsMenu(menu)
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun lastKnown(): GeoPoint? {
//        var point: GeoPoint? = null
//        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
//            point = GeoPoint(location.longitude, location.latitude)
//            point!!.crs = GeoConstants.CRS_WGS84
//            point!!.project(GeoConstants.CRS_WEB_MERCATOR)
//        }
//
//        return point
//    }
//
//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//        newIntent = intent
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item?.itemId) {
//
//            0 -> {
//                mapView.isLockMap = false
//                setMode(MODE_EDIT)
//                return true
//            }
//
//            R.id.menu_edit_save -> return saveEdits()
//            R.id.menu_edit_undo, R.id.menu_edit_redo -> {
//                val result = historyOverlay.onOptionsItemSelected(item.itemId)
//                if (result) {
//                    val undoRedoFeature = historyOverlay.feature
//                    overlay.selectedFeature?.let { feature ->
//                        feature.geometry = undoRedoFeature.geometry
//                        overlay.fillDrawItems(undoRedoFeature.geometry)
//
//                        val original = selectedLayer?.getGeometryForId(feature.id)
//                        val hasEdits = original != null && undoRedoFeature.geometry == original
//                        overlay.setHasEdits(!hasEdits)
//
//                        mapView.map!!.replaceGeometryFromHistoryChanges(feature.geometry)
//                        mapView.map!!.updateMarkerByEditObject();
//
//                        mapView.buffer()
//                        mapView.postInvalidate()
//                    }
//                }
//
//                return result
//            }
//            else -> super.onOptionsItemSelected(item)
//        }
//        return true
//    }
//
//    private fun setCenter(delay: Int) {
//        val mapZoom = preferences.getFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, map.minZoom)
//        var mapScrollX: Double
//        var mapScrollY: Double
//        try {
//            val x = preferences.getLong(SettingsConstantsUI.KEY_PREF_SCROLL_X, 0)
//            val y = preferences.getLong(SettingsConstantsUI.KEY_PREF_SCROLL_Y, 0)
//            mapScrollX = java.lang.Double.longBitsToDouble(x)
//            mapScrollY = java.lang.Double.longBitsToDouble(y)
//        } catch (e: ClassCastException) {
//            mapScrollX = 0.0
//            mapScrollY = 0.0
//        }
//
//        map.setZoomAndCenter(mapZoom, GeoPoint(mapScrollX, mapScrollY), false,
//            delay)
//    }
//
//    private fun setUpToolbar(hasChanges: Boolean? = null) {
//        title = project.title
//        val toggle = ActionBarDrawerToggle(
//            this,
//            binding.drawer,
//            binding.toolbar,
//            R.string.layers_drawer_open,
//            R.string.layers_drawer_close
//        )
//        binding.apply {
//            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
//            drawer.refreshDrawableState()
//            drawer.addDrawerListener(toggle)
//            editAttributes.visibility = View.GONE
//            editGeometry.visibility = View.GONE
//            addFeature.visibility = View.VISIBLE
//        }
//
//        var changes = hasChanges ?: false
//        if (hasChanges == null) {
//            for (i in 0 until map.layerCount) {
//                val layer = map.getLayer(i)
//                if (layer is NGWVectorLayer) {
//                    val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
//                    if (changesCount > 0) {
//                        changes = true
//                        break
//                    }
//                }
//            }
//        }
//
//        setSubtitle(changes)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setHomeButtonEnabled(true)
//        toggle.syncState()
//
//        clearData()
//        if (returnToList) {
//            if (overlay != null && overlay is EditLayerOverlay)
//
//                (overlay as EditLayerOverlay).stopGeometryByWalk()
//            (overlay as EditLayerOverlay).mode
//            finish ()
//
//        }
//
//    }
//
//    private fun clearData() {
//        binding.bottomToolbar.visibility = View.GONE
//        overlay.mode = EditLayerOverlay.MODE_NONE
//        setMenu()
//        selectedLayer = null
//        selectedFeature = null
//        mapView.map!!.unselectFeatureFromView()
//    }
//
//    private fun setHighlight() {
//        binding.addFeature.visibility = View.VISIBLE
//        binding.editAttributes.visibility = View.VISIBLE
//        binding.editGeometry.visibility = View.VISIBLE
//        overlay.mode = EditLayerOverlay.MODE_HIGHLIGHT
//        binding.bottomToolbar.visibility = View.GONE
//        setMenu()
//        if (returnToList ){
//            mapView.map.unselectFeatureFromEdit(false,false)
//            finish()
//        }
//
//    }
//
//    private fun setMenu() {
//        binding.toolbar.menu.clear()
//        binding.toolbar.inflateMenu(R.menu.main)
//    }
//
//    private fun cancelEdits() {
////        if (mEditLayerOverlay.hasEdits()) TODO prompt dialog
////            return;
//
//        overlay.setHasEdits(false)
//        val featureId = overlay.selectedFeatureId
//        overlay.setSelectedFeature(featureId)
//        if (featureId > -1)
//            setHighlight()
//        else
//            setUpToolbar()
//        mapView.map.unselectFeatureFromEdit(true,false)
//        mapView.map.hideMarker()
//    }
//
//    // run creating new object
//    fun startEditIfNeed(){
//        historyOverlay.defineUndoRedo()
//        newIntent?.let {
//            if (it.hasExtra(NEW_FEATURE) || it.hasExtra(NEW_FEATURE_BY_WALK)) {
//                var extraName : String
//                val id : Int?
//                if (it.hasExtra(NEW_FEATURE)) {
//                    id = it.getIntExtra(NEW_FEATURE, -1)
//                    extraName = NEW_FEATURE
//                } else  if (it.hasExtra(NEW_FEATURE_BY_WALK)) {
//                    id = it.getIntExtra(NEW_FEATURE_BY_WALK, -1)
//                    extraName = NEW_FEATURE_BY_WALK
//                } else {
//                    id = null
//                    extraName = ""
//                }
//
//                var centerPoint: GeoPoint? = null
//                if (currentCenter.crs == 0) {
//                    val gps = Manifest.permission.ACCESS_FINE_LOCATION
//                    if (!PermissionUtil.hasPermission(this, gps)) {
//                        requestForPermissions(object : OnPermissionCallback {
//                            override fun onPermissionGranted() {
//                                lastKnown()?.let { point -> mapView.panTo(point) }
//                            }
//                        }, false)
//                    } else
//                        centerPoint = lastKnown()
//                } else
//                    centerPoint = currentCenter
//                centerPoint?.let { center ->
//                    val zoom = map.zoomLevel
//                    map.setZoomAndCenter(zoom, center, false, 0)
//                }
//
//
//                id?.let {
//                    val selected = map.getLayerById(id) as NGWVectorLayerUI?
//                    selected?.let { layer ->
//                        selectedLayer = layer
//                        setTitle(getString(R.string.new_feature), layer.name)
//                        setToolbar()
//                        overlay.setSelectedLayer(layer)
//
//                        overlay.selectedFeature = Feature()
//
//                        mSelectedLayer = layer
//                        when(extraName == NEW_FEATURE){
//                            true -> {
//
//                                overlay.createNewGeometry()
//
//                                if (mapView.map == null)
//                                    Log.e("NNULL", "mapView.map == null")
//                                mapView.map!!.startFeatureSelectionForEdit(
//                                    mSelectedLayer,
//                                    mSelectedLayer!!.geometryType,
//                                    overlay!!.selectedFeature,
//                                    true,
//                                    mSelectedLayer!!.defaultStyleNoExcept,
//                                    false )
//
//                                // update rudiment code - created geometry on old pre-maplibre code
//                                // on editing it updates on MotionEvent.ACTION_UP actions
//                                updateGeometryFromMaplibre(
//                                    mapView.map!!.editingObject.editingFeature,
//                                    mapView.map!!.originalSelectedFeature,
//                                    mapView.map!!.editingObject   )
//
//                                setMode(MODE_EDIT)
//                            }  else -> {
//                                overlay.newGeometryByWalk()
//                                mapView.map!!.startFeatureSelectionForEdit(
//                                    mSelectedLayer,
//                                    mSelectedLayer!!.geometryType,
//                                    overlay!!.selectedFeature,
//                                    true,
//                                    mSelectedLayer!!.defaultStyleNoExcept,
//                                    true )
//
//                                // update rudiment code - created geometry on old pre-maplibre code
//                                // on editing it updates on MotionEvent.ACTION_UP actions
//                                updateGeometryFromMaplibre(
//                                    mapView.map!!.editingObject.editingFeature,
//                                    mapView.map!!.originalSelectedFeature,
//                                    mapView.map!!.editingObject   )
//                                setMode(MODE_EDIT_BY_WALK)
//                            }
//                        }
//                        overlay.setHasEdits(true)
//                        historyOverlay.saveToHistory(overlay.selectedFeature)
//                    }
//                }
//
//                it.removeExtra(extraName)
//                returnToList = true
//            }
//        }
//    }
//
//    fun setMode(mode: Int){
//        // set editing mode (may be not from draft )
//        if (mode == MODE_EDIT){
//            binding.addFeature.visibility = View.GONE
//            binding.editAttributes.visibility = View.GONE
//            binding.editGeometry.visibility = View.GONE
//            binding.bottomToolbar.visibility = View.VISIBLE
//            binding.toolbar.menu.clear()
//            binding.toolbar.inflateMenu(R.menu.edit_geometry)
//
//            overlay.mode = mode
//            binding.bottomToolbar.setOnMenuItemClickListener {
//
//                var result: Boolean
//                when (it.itemId) {
//                    android.R.id.home -> {
//                        cancelEdits()
//                        true
//                    }
//
//                    0 -> {
//                        mapView.isLockMap = false
//                        setMode(MODE_EDIT)
//                        true
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_undo, com.nextgis.maplibui.R.id.menu_edit_redo -> {
//                        result = historyOverlay!!.onOptionsItemSelected(it.itemId)
//                        if (result) {
//                            val undoRedoFeature = historyOverlay!!.feature
//                            val feature = overlay!!.selectedFeature
//                            feature.geometry = undoRedoFeature.geometry
//                            overlay!!.fillDrawItems(undoRedoFeature.geometry)
//
//                            val original = mSelectedLayer!!.getGeometryForId(feature.id)
//                            val hasEdits = original != null && undoRedoFeature.geometry == original
//                            overlay!!.setHasEdits(!hasEdits)
//
//                            mapView.map!!.replaceGeometryFromHistoryChanges(feature.geometry)
//                            mapView.map!!.updateMarkerByEditObject();
//
//                            mapView.buffer()
//                            mapView.postInvalidate()
//                        }
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_by_touch -> {
//                        setMode(MODE_EDIT_BY_TOUCH)
//                        result = overlay!!.onOptionsItemSelected(it.itemId)
//                        mSelectedLayer!!.isLocked = true
//
//                        //mActivity!!.showEditToolbar()
//                        //editLayerOverlay!!.mode = EditLayerOverlay.MODE_EDIT_BY_TOUCH
////                        toolbar.setOnMenuItemClickListener { menuItem ->
////                            onOptionsItemSelected(menuItem.itemId)
////                        }
//
//                        mapView.map!!.unselectFeatureFromEdit(false, true)
//                        mapView.map!!.hideVertex()
//                        mapView.map!!.hideMarker()
//
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_by_walk -> {
//                        setMode(MODE_EDIT_BY_WALK)
//
//                        result = overlay!!.onOptionsItemSelected(it.itemId)
//                        if (result)
//                            historyOverlay!!.saveToHistory(overlay!!.selectedFeature)
//
//
//                        (mapView.map).updateHistoryByWalkEnd()
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_delete_point  ->{
//                        val result = mapView.map!!.deleteCurrentPoint();
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_delete_line  ->{
//                        val result = mapView.map!!.deleteCurrentLine();
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_add_new_line  ->{
//                        val center = mapView.map!!.maplibreMap.cameraPosition.target
//                        val result = mapView.map!!.addNewLine(center, mapView.map!!.maplibreMap.getProjection());
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_add_new_point  ->{
//                        val center = mapView.map!!.maplibreMap.cameraPosition.target
//                        val result = mapView.map!!.addNewPoint(center);
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_add_new_inner_ring  ->{
//                        val center = mapView.map!!.maplibreMap.cameraPosition.target
//                        val result = mapView.map!!.addHole( center, mapView.map!!.maplibreMap.getProjection());
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_delete_inner_ring  ->{
//                        val result = mapView.map!!.deleteCurrentHole();
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_delete_polygon  ->{
//                        val result = mapView.map!!.deleteCurrentPolygon();
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_add_new_polygon  ->{
//                        val center = mapView.map!!.maplibreMap.cameraPosition.target
//                        val result = mapView.map!!.addNewPolygon(center, mapView.map!!.maplibreMap.getProjection());
//                        result
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_move_point_to_center  ->{
//                        val center = mapView.map!!.maplibreMap.cameraPosition.target
//                        mapView.map!!.moveToPoint(center);
//                    }
//
//                    com.nextgis.maplibui.R.id.menu_edit_move_point_to_current_location  ->{
//                        val latlng = mapView.map!!.maplibreMap.getCameraPosition().target
//                        mapView.map!!.moveToPoint(latlng)
//                        false;
//                    }
//                    else -> {
//                        result = overlay!!.onOptionsItemSelected(it.itemId)
//                        if (result) historyOverlay!!.saveToHistory(overlay!!.selectedFeature)
//                        result
//                    }
//                }
//                false
//
////                val result = overlay.onOptionsItemSelected(it.itemId)
////                if (result)
////                    historyOverlay.saveToHistory(overlay.selectedFeature)
////                else
////                    requestForPermissions(this, true)
////                result
//            }
//
//            historyOverlay.clearHistory()
//            historyOverlay.saveToHistory(selectedFeature)
//            overlay.setHasEdits(false)
//
//            mapView.map!!.showVertex()
//            mapView.map!!.showMarker();
//
//        }
//
//        if (mode == MODE_EDIT_BY_WALK){
//            binding.addFeature.visibility = View.GONE
//            binding.editAttributes.visibility = View.GONE
//            binding.editGeometry.visibility = View.GONE
//            binding.bottomToolbar.visibility = View.VISIBLE
//            binding.toolbar.menu.clear()
//            binding.toolbar.inflateMenu(R.menu.edit_geometry)
//
//            overlay.mode = MODE_EDIT
//            overlay.mode = MODE_EDIT_BY_WALK
//
//            binding.bottomToolbar.setOnMenuItemClickListener {
//                val result = overlay.onOptionsItemSelected(it.itemId)
//                if (result)
//                    historyOverlay.saveToHistory(overlay.selectedFeature)
//                else
//                    requestForPermissions(this, true)
//                result
//            }
//
//            historyOverlay.clearHistory()
//            historyOverlay.saveToHistory(selectedFeature)
//            overlay.setHasEdits(false)
//            mSelectedLayer!!.isLocked = true
//
//            mapView.map!!.unselectFeatureFromEdit(false, true)
//            mapView.map!!.hideVertex()
//            mapView.map!!.hideMarker()
//        }
//        if (mode == MODE_EDIT_BY_TOUCH){
//            overlay.mode = MODE_EDIT_BY_TOUCH
//            mSelectedLayer!!.isLocked = true
//
//            //mActivity!!.showEditToolbar()
//            overlay!!.mode = EditLayerOverlay.MODE_EDIT_BY_TOUCH
//
//            binding.bottomToolbar.setOnMenuItemClickListener { menuItem ->
//                            onOptionsItemSelected(menuItem)
//                        }
//            mapView.map!!.unselectFeatureFromEdit(false, true)
//            mapView.map!!.hideVertex()
//            mapView.map!!.hideMarker()
//        }
//    }
//
//    private fun startEdit(mode:Int) {
//        binding.addFeature.visibility = View.GONE
//        binding.editAttributes.visibility = View.GONE
//        binding.editGeometry.visibility = View.GONE
//        binding.bottomToolbar.visibility = View.VISIBLE
//        binding.toolbar.menu.clear()
//        binding.toolbar.inflateMenu(R.menu.edit_geometry)
//
//        overlay.mode = mode
//        binding.bottomToolbar.setOnMenuItemClickListener {
//            val result = overlay.onOptionsItemSelected(it.itemId)
//            if (result)
//                historyOverlay.saveToHistory(overlay.selectedFeature)
//            else
//                requestForPermissions(this, true)
//            result
//        }
//
//        historyOverlay.clearHistory()
//        historyOverlay.saveToHistory(selectedFeature)
//        overlay.setHasEdits(false)
//
//
//        mapView.map.startFeatureSelectionForEdit(
//
//            mSelectedLayer,
//            mSelectedLayer!!.geometryType,
//            overlay!!.selectedFeature,
//            false,
//            mSelectedLayer!!.defaultStyleNoExcept,
//            false)
//    }
//
//    private fun saveEdits(): Boolean {
//        val feature = overlay.selectedFeature
//        var featureId = -1L
//        var geometry: GeoGeometry? = null
//
//        if (feature != null) {
//            geometry = feature.geometry
//            featureId = feature.id
//        }
//
//
//        if (overlay.mode == EditLayerOverlay.MODE_EDIT_BY_WALK) {
//            overlay.stopGeometryByWalk()
//            overlay.setMode(EditLayerOverlay.MODE_EDIT)
//            historyOverlay.clearHistory()
//            historyOverlay.defineUndoRedo()
//
//            mSelectedLayer!!.isLocked = true
//            mapView.map!!.showVertex()
//            mapView.map!!.showMarker();
//            return true
//        }
//
//        if (mode == MODE_EDIT_BY_TOUCH) {
//            setMode(MODE_EDIT)
//            historyOverlay.clearHistory()
//            historyOverlay.defineUndoRedo()
//            return true
//        }
//
//        if (geometry == null || !geometry.isValid) {
//            toast(R.string.not_enough_points)
//            return false
//        }
//
////        if (MapUtil.isGeometryIntersects(this, geometry))
////            return false
//
//        selectedLayer?.let {
//            if (featureId == -1L) {
//                it.showEditForm(this, featureId, geometry, clickedFormId )
//            } else {
//                var uri = Uri.parse("content://" + app.authority + "/" + it.path.name)
//                uri = ContentUris.withAppendedId(uri, featureId)
//                val values = ContentValues()
//
//                try {
//                    values.put(Constants.FIELD_GEOM, geometry.toBlob())
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                }
//
//                contentResolver.update(uri, values, null, null)
//                setHighlight()
//                overlay.setHasEdits(false)
//
//                mapView.map!!.cancelFeatureEdit(false)
//                // setNewMode(MODE_SELECT_ACTION)
//            }
//        }
//        return true
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (projectBorders != null && resultCode == RESULT_OK && data!= null){
//            if (data.hasExtra(ConstantsUI.KEY_ADDED_POINT)){
//                if (returnToList){ // need pass result to next activity
//                    returnToList = true
//                    val dataNext = Intent()
//                    dataNext.putExtra(ConstantsUI.KEY_ADDED_POINT,data.getDoubleArrayExtra(ConstantsUI.KEY_ADDED_POINT))
//                    setResult(RESULT_OK, dataNext)
//                } else {
//                    val pointArray :DoubleArray? = data.getDoubleArrayExtra(ConstantsUI.KEY_ADDED_POINT);
//                    if (pointArray != null && pointArray.size>=2) {
//                        val geoPoint = GeoPoint(pointArray[0], pointArray[1])
//                        projectBorders.let {
//                            if (!projectBorders!!.contains(geoPoint)) {
//                                val builder = android.app.AlertDialog.Builder(this@MapActivity)
//                                builder
//                                    .setPositiveButton("ok", null)
//                                    .setTitle(R.string.out_of_area_header)
//                                    .setMessage(R.string.out_of_area_text)
//                                val alertDialog = builder.create()
//                                alertDialog.show()
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
//            val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, -1)
//            if (id != -1L) {
//                overlay.setSelectedFeature(id)
//                selectedLayer?.showFeature(id)
//                setHighlight()
//                overlay.setHasEdits(false)
//            }
//        }
//    }
//
//    private fun locateCurrentPosition() {
//        requestForPermissions(this, false)
//    }
//
//    override fun onPermissionGranted() {
//        if (currentCenter.crs != 0)
//            mapView.panTo(currentCenter)
//        else
//            toast(R.string.error_no_location)
//    }
//
//    override fun onLocationChanged(location: Location?) {
//        location?.let {
//            currentCenter.setCoordinates(it.longitude, it.latitude)
//            currentCenter.crs = GeoConstants.CRS_WGS84
//
//            if (!currentCenter.project(GeoConstants.CRS_WEB_MERCATOR))
//                currentCenter.crs = 0
//
//
//            val isStanding =
//                location == null || !location.hasBearing() || !location.hasSpeed() || location.getSpeed() == 0f
//
//            mapView.map!!.updateLocation(
//                Point.fromLngLat(location.longitude, location.latitude),
//                isStanding,
//                location.bearing)
//
////            Log.e("TTRR", "location at" + ": " + location.longitude + " : " + location.latitude)
//
//
//            if (TrackerService.hasUnfinishedTracks(this))
//                mapView.map!!.reloadCurrentTrackToMap()
//
////            Log.e("TTRR", "end olLocChange update---------------" )
//
//            if (mode == EditLayerOverlay.MODE_EDIT_BY_WALK){
//                if (location != null)
//                    mapView.map!!.addPointByWalk(LatLng(location.latitude, location.longitude));
//            }
//
//        }
//    }
//
//    override fun onBestLocationChanged(location: Location) {
//
//    }
//
//    override fun onGpsStatusChanged(event: Int) {
//
//    }
//
//    override fun onClick(view: View?) {
//        when (view?.id) {
//            R.id.zoom_in -> {
//                //if (mapView.canZoomIn()) mapView.zoomIn()
//
//                val currentZoom = mapView.map.maplibreMap.cameraPosition.zoom
//                var newZoom = currentZoom + 1.0
//                if (newZoom > mapView.map.maplibreMap.maxZoomLevel)
//                    newZoom = mapView.map.maplibreMap.maxZoomLevel
//                val cameraUpdate = CameraUpdateFactory.zoomTo(newZoom)
//                mapView.map.maplibreMap.animateCamera(cameraUpdate)
//            }
//            R.id.zoom_out -> {
//                //if (mapView.canZoomOut()) mapView.zoomOut()
//
//                val currentZoom = mapView.map.maplibreMap.cameraPosition.zoom
//                var newZoom = currentZoom - 1.0
//                if (newZoom < mapView.map.maplibreMap.minZoomLevel)
//                    newZoom = mapView.map.maplibreMap.minZoomLevel
//                val cameraUpdate = CameraUpdateFactory.zoomTo(newZoom)
//                mapView.map.maplibreMap.animateCamera(cameraUpdate)
//            }
//            R.id.locate -> locateCurrentPosition()
//            R.id.add_feature -> {
//                finish()
//                startActivity<AddFeatureActivity>()
//            }
//            R.id.edit_geometry -> startEdit(MODE_EDIT)
//            R.id.edit_attributes -> selectedFeature?.let {
//
//                //val defidarray = selectedLayer?.defaultFormId
//                var defid = -1L
////                if (defidarray != null && defidarray.size > 0)
////                    defid = defidarray[0]
//
//                selectedLayer?.showEditForm(this, it.id, it.geometry, defid)
//            }
//        }
//    }
//
//    override fun onItemClick(layer: Layer) {
//
//    }
//
//    override fun onDownloadTilesClick(layer: Layer) {
//        val env: GeoEnvelope = map.currentBounds
//
//        if (layer is RemoteTMSLayerUI) {
//            layer.downloadTiles(this, env)
//        } else if (layer is NGWRasterLayerUI) {
//            layer.downloadTiles(this, env)
//        } else if (layer is NGWWebMapLayerUI) {
//            layer.downloadTiles(this, env)
//        }
//    }
//
//    override fun onLongPress(event: MotionEvent) {
//
//    }
//
//    override fun onLayerAdded(id: Int) {
//
//    }
//
//    override fun onLayerDeleted(id: Int) {
//
//    }
//
//    override fun onLayersReordered() {
//
//    }
//
//    override fun onLayerDrawFinished(id: Int, percent: Float) {
//
//    }
//
//    private fun setToolbar() {
//        binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
//        binding.toolbar.setNavigationIcon(R.drawable.ic_action_cancel_dark)
//        binding.toolbar.setNavigationOnClickListener {
//            if (overlay.mode == EditLayerOverlay.MODE_EDIT)
//                cancelEdits()
//            else
//                setUpToolbar()
//        }
//    }
//
//    private fun setTitle(title: String, subtitle: String?) {
//        this.title = title
//        supportActionBar?.subtitle = subtitle
//    }
//
//    override fun onSingleTapUp(event: MotionEvent) {
//        if (overlay.mode == EditLayerOverlay.MODE_EDIT) {
//            if (overlay.selectGeometryInScreenCoordinates(event.x, event.y))
//                historyOverlay.saveToHistory(overlay.selectedFeature)
//            return
//        }
//
//        val tolerance = resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP.toDouble()
//        val dMinX = event.x - tolerance
//        val dMaxX = event.x + tolerance
//        val dMinY = event.y - tolerance
//        val dMaxY = event.y + tolerance
//        val mapEnv = map.screenToMap(GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)) ?: return
//
//        val types = GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck or
//                GeoConstants.GTLineStringCheck or GeoConstants.GTMultiLineStringCheck or
//                GeoConstants.GTPolygonCheck or GeoConstants.GTMultiPolygonCheck
//        val layers = mapView.getVectorLayersByType(types).filter {
//            it is NGWVectorLayerUI && it.isEditable
//        }
//        var items: List<Long>? = null
//        var intersects = false
//        for (layer in layers) {
//            if (!layer.isValid || layer is ILayerView && !layer.isVisible)
//                continue
//
//            items = (layer as NGWVectorLayerUI).query(mapEnv)
//            if (!items.isEmpty()) {
//                selectedLayer = layer
//                intersects = true
//                break
//            }
//        }
//
//        if (intersects) {
//            overlay.setSelectedLayer(selectedLayer)
//            for (i in items!!.indices) {    // FIXME hack for bad RTree cache
//                val feature = selectedLayer?.getFeature(items[i])
//                if (feature != null && feature.geometry != null)
//                    overlay.setSelectedFeature(feature.id)
//            }
//
//            overlay.selectedFeature?.let {
//                binding.editAttributes.visibility = View.VISIBLE
//                binding.editGeometry.visibility = View.VISIBLE
//
//                setTitle(getString(R.string.feature_n, it.id), selectedLayer?.name)
//                setToolbar()
//
//                selectedFeature = it
//                overlay.mode = EditLayerOverlay.MODE_HIGHLIGHT
//            }
//        } else
//            setUpToolbar()
//
//        mapView.postInvalidate()
//    }
//
//    override fun onLayerChanged(id: Int) {
//
//    }
//
//    override fun onExtentChanged(zoom: Float, center: GeoPoint?) {
//
//    }
//
//    override fun onLayerDrawStarted() {
//
//    }
//
//    override fun panStart(e: MotionEvent?) {
//        if (overlay.mode == EditLayerOverlay.MODE_CHANGE)
//            needSave = true
//    }
//
//    override fun panMoveTo(e: MotionEvent?) {
//
//    }
//
//    override fun panStop() {
//        if (needSave) {
//            needSave = false
//            historyOverlay.saveToHistory(overlay.selectedFeature)
//        }
//    }
//
//    fun isBordersExists(): Boolean{
//        return project.one != -1.0
//    }
//
//    override fun onLayerVisibleChanged(id: Int) {
//        // its from mobile
//    }
//
//    override fun onLayerChangedFeatureId(
//        oldFeatureId: Long,
//        newFeatureId: Long,
//        layerId: Int
//    ) {
//        // its from mobile
//    }
//
//    override fun processMapLongClick(
//        exactEnv: GeoEnvelope?,
//        clickPoint: PointF? ): Boolean {
//        // no long click on maplibre
//        return true;
//    }
//
//    //     }
//
//    override fun processMapClick(x: Float, y: Float): Boolean {
//        onSingleTapUpFromMaplibre(x, y)
//        return true
//    }
//
//    override fun setHasEdit() {
//        overlay!!.setHasEdits(true)
//    }
//
//    override fun updateGeometryFromMaplibre(
//        feature: org.maplibre.geojson.Feature?,
//        originalSelectedFeature: Feature?,
//        editObject: MLGeometryEditClass?    ) {
//        if (feature == null || originalSelectedFeature == null)
//            return
//        originalSelectedFeature.geometry = getGeometryFromMaplibreGeometry(feature)
//        overlay!!.updateGeometryFromMaplibre(originalSelectedFeature.geometry)
//        overlay!!.fillDrawItems(originalSelectedFeature.geometry)
//
//        overlay!!.updateActions(editObject)
//        historyOverlay!!.saveToHistory(originalSelectedFeature)
//
//    }
//
//    override fun getSelectedLayer(): VectorLayer? {
//        return mSelectedLayer
//    }
//
//    override fun updateActions(editObject: MLGeometryEditClass?) {
//        overlay!!.updateActions(editObject)
//    }
//
//    override fun getMode(): Int? {
//        return overlay.mode;
//    }
//
//    override fun loadLayersLite() {
//        val allLayers = mapView.getAllLayers()
//        mapView.map!!.loadLayersToMaplibreMapLite(allLayers, false)
//    }
//
//    override fun getLongLongClickProcesses(): Boolean {
//        return longClickProcessed
//    }
//
//    override fun setLongLongClickProcesses(longLongCLickPrecesses: Boolean) {
//        this.longClickProcessed = longLongCLickPrecesses;
//    }
//
//    override fun getGeometryFromMaplibreGeometry(feature: org.maplibre.geojson.Feature?) : GeoGeometry? {
//
//        if (feature == null)
//            return null;
//
//
//        if (feature.geometry()!= null && feature.geometry() is MultiPolygon){
//            val multipoly = feature.geometry() as MultiPolygon
//            val geomultiPolygon = GeoMultiPolygon()
//            for (poly in multipoly.coordinates()){
//                val geoPolygon = GeoPolygon()
//                geoPolygon.crs = GeoConstants.CRS_WEB_MERCATOR
//
//                var iter = 0
//                for (outer in poly){
//
//                    if (iter == 0){ // outer ring
//                        for (outer2 in outer){
//                            val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
//                            val geopoint = GeoPoint(points[0], points[1])
//                            geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                            geoPolygon.add(geopoint)
//                        }
//                    } else {
//                        // inner
//                        val ring = GeoLinearRing()
//
//                        for (outer2 in outer){
//                            val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
//                            val geopoint = GeoPoint(points[0], points[1])
//                            geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                            ring.add(geopoint)
//                        }
//                        geoPolygon.addInnerRing(ring)
//                    }
//                    iter++
//                }
//
//                geomultiPolygon.add(geoPolygon)
//            }
//            return geomultiPolygon
//        }
//
//
//
//        if (feature.geometry()!= null && feature.geometry() is Polygon){
//            val poly = feature.geometry() as Polygon
//
//            val geoPolygon = GeoPolygon()
//            geoPolygon.crs = GeoConstants.CRS_WEB_MERCATOR
//
//            var iter = 0
//            for (outer in poly.coordinates()){
//
//                if (iter == 0){ // outer ring
//                    for (outer2 in outer){
//                        val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
//                        val geopoint = GeoPoint(points[0], points[1])
//                        geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                        geoPolygon.add(geopoint)
//                    }
//                } else {
//                    // inner
//                    val ring = GeoLinearRing()
//
//                    for (outer2 in outer){
//                        val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
//                        val geopoint = GeoPoint(points[0], points[1])
//                        geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                        ring.add(geopoint)
//                    }
//                    geoPolygon.addInnerRing(ring)
//                }
//                iter++
//            }
//            return geoPolygon
//        }
//        if (feature.geometry()!= null && feature.geometry() is Point){
//            val point = feature.geometry() as Point
//
//            val geoPoint = GeoPoint()
//            geoPoint.crs = GeoConstants.CRS_WEB_MERCATOR
//            val points: DoubleArray = convert4326To3857(point.longitude(), point.latitude())
//            geoPoint.setCoordinates(points[0], points[1])
//            return geoPoint
//        }
//        if (feature.geometry()!= null && feature.geometry() is MultiPoint){
//            val geoPoint = GeoMultiPoint()
//
//            val multiPoint = feature.geometry() as MultiPoint
//            for ( point in multiPoint.coordinates()){
//                val newPoint = GeoPoint()
//
//                val points: DoubleArray = convert4326To3857(point.longitude(), point.latitude())
//                newPoint.setCoordinates(points[0], points[1])
//                newPoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                geoPoint.add(newPoint)
//            }
//            geoPoint.crs = GeoConstants.CRS_WEB_MERCATOR
//            return geoPoint
//        }
//
//
//        if (feature.geometry()!= null && feature.geometry() is LineString){
//
//            val geoLineObj = GeoLineString()
//            geoLineObj.crs = GeoConstants.CRS_WEB_MERCATOR
//
//            val poly = feature.geometry() as LineString
//
//            val geoLine = GeoLineString()
//            geoLine.crs = GeoConstants.CRS_WEB_MERCATOR
//
//            for (outer in poly.coordinates()){
//                val points: DoubleArray = convert4326To3857(outer.longitude(), outer.latitude())
//                val geopoint = GeoPoint(points[0], points[1])
//                geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                geoLineObj.add(geopoint)
//            }
//            return geoLineObj
//        }
//
//        if (feature.geometry()!= null && feature.geometry() is MultiLineString){
//            val geoMultiLineObj = GeoMultiLineString()
//            geoMultiLineObj.crs = GeoConstants.CRS_WEB_MERCATOR
//
//            val geoMultiLine = feature.geometry() as MultiLineString
//            for (line in geoMultiLine.lineStrings()){
//
//                val geoLineObj = GeoLineString()
//                geoLineObj.crs = GeoConstants.CRS_WEB_MERCATOR
//
//                val poly = line as LineString
//
//                val geoLine = GeoLineString()
//                geoLine.crs = GeoConstants.CRS_WEB_MERCATOR
//
//                for (outer in poly.coordinates()){
//                    val points: DoubleArray = convert4326To3857(outer.longitude(), outer.latitude())
//                    val geopoint = GeoPoint(points[0], points[1])
//                    geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
//                    geoLineObj.add(geopoint)
//                }
//                geoMultiLineObj.add(geoLineObj)
//            }
//            return geoMultiLineObj
//        }
//
//        return null;
//    }
//
//    private fun convert4326To3857(lon: Double, lat: Double): DoubleArray {
//        val x = lon * 20037508.34 / 180
//        val y = ln(tan(Math.PI / 4 + Math.toRadians(lat) / 2)) * 20037508.34 / Math.PI
//        return doubleArrayOf(x, y)
//    }
//
//    fun convert3857To4326(x: Double, y: Double): DoubleArray {
//        val lon = x * 180 / 20037508.34
//        val lat = Math.toDegrees(atan(sinh(y * Math.PI / 20037508.34)))
//        return doubleArrayOf(lon, lat)
//    }
//
//    override fun onLengthChanged(length: Double) {
//        // no need for collector
//        //this.title = LocationUtil.formatLength(this, length, 3)
//    }
//
//    override fun onAreaChanged(area: Double) {
//        // no need for collector
//    }
//
//    override fun changeProgress(show: Boolean) {
//        if (show)
//            binding.stylingProgress?.visibility = View.VISIBLE
//        else {
//            binding.stylingProgress?.visibility = View.GONE
//            binding.textStyling ?.text = ""
//        }
//    }
//
//    override fun checkCreateIfNeed() {
//        runDelayed(2000, {
//            startEditIfNeed()
//        })
//    }
//
//    override fun setMapLayersLoaded() {
//        TODO("Not yet implemented")
//    }
//
//    override fun onCameraIdle() {
////        val zoom = getCurrentZoom()
////        setMapLibreZoomInEnabled()
////        setMapLibreZoomOutEnabled()
////        mScaleRulerText!!.text = rulerText
////        if (mZoom != null)
////            mZoom!!.text = zoomText
//    }
//
//    fun onSingleTapUpFromMaplibre(screenx: Float, screeny :Float) {
//
//        Log.e("MMAAPP", "On Create - mMapRef created")
//
//        if (overlay.mode == EditLayerOverlay.MODE_EDIT) {
//            if (overlay.selectGeometryInScreenCoordinates(screenx, screeny))
//                historyOverlay.saveToHistory(overlay.selectedFeature)
//            return
//        }
//        val dMinX = (screenx - mTolerancePX)
//        val dMaxX = (screenx + mTolerancePX)
//        val dMinY = (screeny - mTolerancePX)
//        val dMaxY = (screeny + mTolerancePX)
//
//        val screenPointMin = PointF(dMinX, dMinY)
//        val screenPointMax = PointF(dMaxX, dMaxY)
//
//        val minPoint = mapView.map!!.maplibreMap.getProjection().fromScreenLocation(screenPointMin)
//        val maxPoint = mapView.map!!.maplibreMap.getProjection().fromScreenLocation(screenPointMax)
//
//
//        val pointsMin = convert4326To3857(minPoint.longitude, minPoint.latitude)
//        val pointsMax = convert4326To3857(maxPoint.longitude, maxPoint.latitude);
//
//        var minx =   pointsMin[0];
//        var maxx =   pointsMax[0];
//        var miny =   pointsMin[1];
//        var maxy =   pointsMax[1];
//
//        if (minx > maxx){
//            minx =   pointsMax[0]
//            maxx =   pointsMin[0]
//        }
//        if (miny > maxy){
//            miny =   pointsMax[1]
//            maxy =   pointsMin[1]
//        }
//        val pointClick = PointF(screenx, screeny)
//        val exactEnv: GeoEnvelope = getClickEnelope(pointClick, mapView.map!!.maplibreMap)
//
//        val types = GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck or
//                GeoConstants.GTLineStringCheck or GeoConstants.GTMultiLineStringCheck or
//                GeoConstants.GTPolygonCheck or GeoConstants.GTMultiPolygonCheck
//        val layers = mapView.getVectorLayersByType(types).filter {
//            it is NGWVectorLayerUI && it.isEditable
//        }
//        var items: List<Long>? = null
//        var intersects = false
//        for (layer in layers) {
//            if (!layer.isValid || layer is ILayerView && !layer.isVisible)
//                continue
//
//            items = (layer as NGWVectorLayerUI).query(exactEnv)
//            if (!items.isEmpty()) {
//                selectedLayer = layer
//                intersects = true
//                break
//            }
//        }
//
//        if (intersects) {
//            overlay.setSelectedLayer(selectedLayer)
//            for (i in items!!.indices) {    // FIXME hack for bad RTree cache
//                val feature = selectedLayer?.getFeature(items[i])
//                if (feature != null && feature.geometry != null)
//                    overlay.setSelectedFeature(feature.id)
//            }
//
//            overlay.selectedFeature?.let {
//                binding.editAttributes.visibility = View.VISIBLE
//                binding.editGeometry.visibility = View.VISIBLE
//
//                setTitle(getString(R.string.feature_n, it.id), selectedLayer?.name)
//                setToolbar()
//
//                selectedFeature = it
//                overlay.mode = EditLayerOverlay.MODE_HIGHLIGHT
//
//                mapView.map.startFeatureSelectionForView(selectedLayer,selectedFeature)
//                mSelectedLayer = selectedLayer
//            }
//        } else {
//            // need unselect feature
//            mapView.map.unselectFeatureFromView()
//            mSelectedLayer = null
//            setUpToolbar()
//        }
//    }
//
//    fun getClickEnelope(clickPoint: PointF, maplibreMap:MapLibreMap): GeoEnvelope {
//        val TOLERANCE_DP = 20
//        val mTolerancePX = getResources().getDisplayMetrics().density * TOLERANCE_DP
//
//        val minP = PointF(clickPoint.x - mTolerancePX, clickPoint.y - mTolerancePX)
//        val maxP = PointF(clickPoint.x + mTolerancePX, clickPoint.y + mTolerancePX)
//
//        val minL: LatLng = maplibreMap.getProjection().fromScreenLocation(minP)
//        val maxL: LatLng = maplibreMap.getProjection().fromScreenLocation(maxP)
//
//        val minPoints = MPLFeaturesUtils.convert4326To3857(minL.longitude, minL.latitude)
//        val maxPoints = MPLFeaturesUtils.convert4326To3857(maxL.longitude, maxL.latitude)
//
//        var minx = minPoints[0]
//        var maxx = maxPoints[0]
//        var miny = minPoints[1]
//        var maxy = maxPoints[1]
//
//        if (minx > maxx) {
//            minx = maxPoints[0]
//            maxx = minPoints[0]
//        }
//        if (miny > maxy) {
//            miny = maxPoints[1]
//            maxy = minPoints[1]
//        }
//
//        //val exactEnv: GeoEnvelope = GeoEnvelope(pointsMin[0],  pointsMax[0], pointsMin[1], pointsMax[1])
//        val exactEnv = GeoEnvelope(minx, maxx, miny, maxy)
//        return exactEnv
//    }

}