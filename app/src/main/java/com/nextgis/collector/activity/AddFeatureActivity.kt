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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.activity.MapFragment.Companion.CLICKED_FORM_ID
import com.nextgis.collector.activity.MapFragment.Companion.MOVE_MAP
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE
import com.nextgis.collector.activity.MapFragment.Companion.NEW_FEATURE_BY_WALK
import com.nextgis.collector.adapter.EditableLayersAdapter
import com.nextgis.collector.data.ResourceTree
import com.nextgis.collector.databinding.ActivityAddFeatureBinding
import com.nextgis.collector.util.IntentFor
import com.nextgis.collector.util.longToast
import com.nextgis.collector.util.toast
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.MapDrawable.MODE_EDIT_BY_WALK
import com.nextgis.maplib.map.MapDrawable.MODE_HIGHLIGHT
import com.nextgis.maplib.map.MapDrawable.MODE_NONE
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.Constants.MESSAGE_INTENT_RELOAD
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.mapui.NGWVectorLayerUI
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.WalkEditService
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.ConstantsUI.KEY_BATTERY
import com.nextgis.maplibui.util.ConstantsUI.KEY_TRACK_ACTION
import com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_START
import java.io.File
import java.io.FileNotFoundException

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

    private var receiverRegistered = false
    private var mMessageReload: MessageReloadLayer? = null

    var startMap = false;

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
    public var returnToList = false
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

        if (intent != null && intent.getBooleanExtra(IS_MAP_START, false)) {
            startMap = true
            showMap(true)

        }
        else if (WalkEditService.isServiceRunning(this))
            showMap(true)

        mMessageReload = MessageReloadLayer()
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(mMessageReload)
            receiverRegistered = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        var isEditMode = true
        if (mapFragment != null && mapFragment!!.mode != MODE_NONE && mapFragment!!.mode != MODE_HIGHLIGHT)
            isEditMode = false

        menuInflater.inflate( if (isEditMode) R.menu.main else R.menu.edit_geometry, menu)

        menu?.findItem(R.id.menu_track).let {
            trackItem = menu?.findItem(R.id.menu_track)
        }
        setTracksTitle(menu?.findItem(R.id.menu_track))
        //updateTracksMenuItems(menu)
        return super.onCreateOptionsMenu(menu)
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
                startEdit(true, false, clickedFormId, false)
            }

            override fun onPermissionDenied() {
                val builder = AlertDialog.Builder(this@AddFeatureActivity)
                    .setTitle(R.string.permissions)
                    .setMessage(R.string.location_permissions_ext)
                    .setPositiveButton(
                        com.nextgis.maplibui.R.string.ok, null)
                    .create()
                builder.setCanceledOnTouchOutside(false)
                builder.show()
                returnToList = true
                startEdit(true, false, clickedFormId, false)
            }
        }, true)
    }

    override fun onGpsClick(id: String, useMap : Boolean, clickedFormId : Long) {
        this.layer = layerByPath(id)
        requestForPermissions(object : OnPermissionCallback {
            override fun onPermissionGranted() {
                returnToList = true
                mapFragment?.mSelectedLayer = layer
                mapFragment?.overlay?.setSelectedLayer(layer)
                mapFragment?.createPointFromOverlay(false)
                startEdit(false, useMap, clickedFormId, false)
                checkBatteryOptimize()
            }

            override fun onPermissionDenied() {
                val builder = AlertDialog.Builder(this@AddFeatureActivity)
                    .setTitle(R.string.permissions)
                    .setMessage(R.string.location_permissions_ext)
                    .setPositiveButton(
                        com.nextgis.maplibui.R.string.ok, null)
                    .create()
                builder.setCanceledOnTouchOutside(false)
                builder.show()
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
        Log.d("TRACCK", "onOptionsItemSelected trackInProgress " + trackInProgress)
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

    private fun startEdit(map: Boolean, useMap : Boolean, clickedFormId: Long, skipVisibleCheck: Boolean){
        if (layer != null) {
            mapFragment?.mSelectedLayer = layer
            if (!skipVisibleCheck &&  layer?.isVisible == false){
//
                mapView.map.reloadLayerByID(layer!!.id,
                    Runnable(){
                        startEdit(map, useMap , clickedFormId, true)
                })
                return
            }
            if (layer?.geometryType == GeoConstants.GTPoint || layer?.geometryType == GeoConstants.GTMultiPoint
                    || layer?.geometryType == GeoConstants.GTLineString || layer?.geometryType == GeoConstants.GTPolygon
                    || layer?.geometryType == GeoConstants.GTMultiLineString || layer?.geometryType == GeoConstants.GTMultiPolygon)
                if (map || useMap) {
                    savedFormId = clickedFormId
                    savedLayerId = layer!!.id
                    savedAction = if (useMap) NEW_FEATURE_BY_WALK else NEW_FEATURE

                    returnToList = true
                    showMap(true)

                    val intent = IntentFor<AddFeatureActivity>(this)
                    intent.putExtra(CLICKED_FORM_ID, clickedFormId)

                    if (useMap)
                        intent.putExtra(NEW_FEATURE_BY_WALK, layer?.id)
                    else
                        intent.putExtra(NEW_FEATURE, layer?.id)

                    if (map)
                        intent.putExtra(MOVE_MAP, false)

                    Log.e("MMAPPEE", "ready to work = " + mapFragment?.isMapReadyToWork)
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

        Log.d("WWALK", "AddFeatureActivity onResume")
        //Toast.makeText(this,"ON_RESUME", -1)
//        if ( !(application as CollectorApplication).isSyncProgress)
//            binding.overlay.visibility = View.GONE;
        if (WalkEditService.isServiceRunning(this) && mapFragment!= null && mapFragment?.mode == MODE_EDIT_BY_WALK) {
            Log.d("WWALK", "AddFeatureActivity onResume WalkEditService.isServiceRunning(this) && mapFragment!= null && mapFragment?.mode == MODE_EDIT_BY_WALK")
            // need getFeature from old overlay and update in maplibre logic
            if (mapFragment!!.isMapReadyToWork)
                mapView.map!!.updateWalkingFeature(mapFragment!!.overlay!!.selectedFeature)
            else
                toast(R.string.not_implemented)
        }

        val intentFilterReload = IntentFilter()
        intentFilterReload.addAction( MESSAGE_INTENT_RELOAD)

        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mMessageReload, intentFilterReload, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(mMessageReload, intentFilterReload)
            }
            receiverRegistered = true
        }
    }

    fun getFormId(): Long{
        return savedFormId
    }

    fun getMapVisible(): Boolean{
        if (binding.mapFragmentContainer.visibility == View.VISIBLE)
            return  true
        else
            return false
    }

    fun showMap(visible : Boolean){
        Log.d("WWALK", "AddFeatureActivity showMap " + visible)

        binding.mapFragmentContainer.visibility = if (visible) View.VISIBLE else View.GONE
        binding.showMap.setImageResource(if (visible) R.drawable.ic_add_white_48dp else R.drawable.ic_map )

        if (visible){
            //supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_minus)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)

            if (mapFragment?.isMapReadyToWork == true)
                mapFragment?.setUpToolbar(false)

        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setHomeButtonEnabled(false)
        }

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

        if (requestCode ==TRACKS_REQUEST ){
            //refresh Tracks
            if (mapFragment != null)
                mapFragment?.reloadTracksToMap()

        }
        else if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
            if (mapFragment!= null && ! mapFragment!!.isMapReadyToWork) // case
                return

            val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())
            if (id != Constants.NOT_FOUND.toLong()) {
                mapFragment?.overlay!!.setSelectedFeature(id)

                map?.updateEditedId(id)
                if (mapFragment?.mSelectedLayer != null)
                    mapFragment?.mSelectedLayer!!.showFeature(id)

                mapFragment?.setHighlight()
                mapFragment?.overlay?.setHasEdits(false)
                //mapFragment?.setModпзe(MODE_SELECT_ACTION)

                if (map == null )
                    return;
                if ((mapFragment?.isMapReadyToWork == false)) // skip if map wasnt open
                    return

                map.loadViewFeature(id,mapFragment?.selectedLayer!!.id)
                map.originalSelectedFeature = mapFragment?.overlay?.selectedFeature // MPLFeaturesUtils.getFeatureFromNGFeature( map.viewedFeature)
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

    override fun reloadAllTracks() {
        if (mapFragment != null) {
            mapFragment?.reloadTracksToMap()
            mapFragment?.reloadCurrentTrackToMap()
        }
    }

    private inner class MessageReloadLayer : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent ){
            if (intent.action == MESSAGE_INTENT_RELOAD) {
                val layerid = intent.getIntExtra(ConstantsUI.KEY_LAYER_ID, -1);
                (context.applicationContext  as IGISApplication).removeLayerToRefresh(layerid)
                if (map != null && layerid != -1){

                    if (map!!.getLayerVisible(layerid) == true) {
                        Handler().postDelayed({
                            map!!.refreshLayerVisibility(layerid, false)
                        }, 300)

                        Handler().postDelayed({
                            map!!.refreshLayerVisibility(layerid, true)
                        }, 600)
                    }
                }
            }
        }
    }

    public fun checkBatteryOptimize(){
        val batteryOK = TrackerService.checkIsBatteryPermOK(this)
        if (!batteryOK) {
            Handler().postDelayed(Runnable () {
                val msg = Intent(ConstantsUI.MESSAGE_INTENT_TRACK)
                msg.setPackage(this.getPackageName())
                msg.putExtra(ConstantsUI.KEY_MESSAGE_TRACK, true)
                msg.putExtra(KEY_BATTERY, false)
                msg.putExtra(KEY_TRACK_ACTION, VALUE_TRACK_START)
                msg.setPackage(getPackageName())
                sendBroadcast(msg)
            }, 1000)
        }
    }
}