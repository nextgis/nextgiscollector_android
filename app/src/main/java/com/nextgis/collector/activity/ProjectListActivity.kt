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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.adapter.ProjectAdapter
import com.nextgis.collector.data.Project
import com.nextgis.collector.data.RemoteLayer
import com.nextgis.collector.data.RemoteLayerNGW
import com.nextgis.collector.data.RemoteLayerTMS
import com.nextgis.collector.databinding.ActivityProjectListBinding
import com.nextgis.collector.viewmodel.ProjectViewModel
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplibui.activity.NGIDLoginActivity
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI
import com.nextgis.maplibui.service.LayerFillService
import com.nextgis.maplibui.util.NGIDUtils.isLoggedIn
import com.pawegio.kandroid.longToast
import com.pawegio.kandroid.startActivity
import com.pawegio.kandroid.toast
import java.io.File

class ProjectListActivity : BaseActivity(), ProjectAdapter.OnItemClickListener {
    private lateinit var binding: ActivityProjectListBinding
    private var projectAdapter = ProjectAdapter(arrayListOf(), this)
    private lateinit var receiver: BroadcastReceiver
    @Volatile
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!preferences.getBoolean(IntroActivity.INTRO_SHOWN, false)) {
            startActivity<IntroActivity>()
            finish()
            return
        }

        if (!isLoggedIn(preferences)) {
            val intent = Intent(this, NGIDLoginActivity::class.java)
            intent.putExtra(NGIDLoginActivity.EXTRA_NEXT, this::class.java)
            startActivity(intent)
            finish()
            return
        }

        if (preferences.contains("project")) {
            open()
            finish()
            return
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_project_list)
        val projectModel = ViewModelProviders.of(this).get(ProjectViewModel::class.java)
        binding.projectModel = projectModel
        binding.executePendingBindings()

        binding.projects.adapter = projectAdapter
        binding.projects.layoutManager = LinearLayoutManager(this)
        projectModel.projects.observe(this, Observer { projects ->
            projects?.let { projectAdapter.replaceData(it) }
        })

        projectModel.selectedProject.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                projectModel.selectedProject.get()?.let {
                    if (it.layers.size > 0)
                        create(it)
                    else
                        runOnUiThread { toast(R.string.error_download_data) }
                }
            }
        })

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val serviceStatus = intent.getShortExtra(LayerFillService.KEY_STATUS, 0)
                when (serviceStatus) {
                    LayerFillService.STATUS_STOP -> {
                        val canceled = intent.getBooleanExtra(LayerFillService.KEY_CANCELLED, false)
                        val success = intent.getBooleanExtra(LayerFillService.KEY_RESULT, false)
                        if (!success) {
                            if (canceled)
                                toast(R.string.canceled)
                            else if (intent.hasExtra(LayerFillService.KEY_MESSAGE))
                                longToast(intent.getStringExtra(LayerFillService.KEY_MESSAGE))
                        }
                        if (!intent.hasExtra(LayerFillService.KEY_MESSAGE))
                            return

                        val isNgw = intent.getBooleanExtra(LayerFillService.KEY_SYNC, false)
                        if (success && !canceled && isNgw) {
                            val id = intent.getIntExtra(LayerFillService.KEY_REMOTE_ID, -1)
                            val ngwLayer = map.getLayerById(id) as NGWVectorLayer
                            val account = app.getAccount(ngwLayer.accountName)

                            val project = binding.projectModel?.selectedProject?.get()
                            val remote = project?.layers?.first { it.path == ngwLayer.path.name }

                            if (remote is RemoteLayerNGW) {
                                ngwLayer.setIsEditable(remote.editable && remote.syncable)
                                if (remote.syncable) {
                                    NGWSettingsFragment.setAccountSyncEnabled(account, app.authority, true)
                                    ngwLayer.syncType = Constants.SYNC_ALL
                                }

                                ngwLayer.save()
                            }
                        }

                        total--
                        check()
                    }
                }
            }
        }

        val extras = intent.extras
        val id = extras?.getInt("project", -1) ?: -1
        if (id >= 0)
            load(id)
        else
            projectModel.load()

//         Example of a call to a native method
//        sample_text.text = stringFromJNI()
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(LayerFillService.ACTION_UPDATE)
        registerReceiver(receiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
    }

    override fun onItemClick(project: Project) {
        AlertDialog.Builder(this).setTitle(R.string.join_project)
                .setMessage(getString(R.string.join_message, project.title))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> load(project.id) }
                .show()
    }

    private fun load(id: Int) {
        binding.projectModel?.load(id)
    }

    private fun open() {
        if (project.screen == "list")
            startActivity<AddFeatureActivity>()
        else
            startActivity<MapActivity>()
    }

    private fun check() {
        if (total <= 0) {
            binding.projectModel?.selectedProject?.get()?.let { project ->
                val paths = project.layers.map { it.path }
                var i = 0
                while (i < map.layerCount) {
                    val layer = map.getLayer(i)
                    val name = layer.path.name
                    val id = paths.indexOf(name)
                    if (id != i && id.coerceIn(0 until map.layerCount) == id) {
                        map.moveLayer(id, layer)
                        i = 0
                    } else
                        i++

                    if (layer is VectorLayer) {
                        val vector = project.layers[id] as RemoteLayerNGW
                        if (vector.styleable) {
                            layer.setRenderer(vector.renderer)
                            vector.style = ""
                        }
                    }
                }
                this.project = project
                open()
            }
        }
    }

    private fun create(project: Project) {
        binding.projectModel?.isLoading?.set(true)
        preferences.edit().putString("project", project.json).apply()
        val base = getExternalFilesDir(null) ?: filesDir
        val file = File(base, CollectorApplication.TREE)
        FileUtil.writeToFile(file, project.tree)
        total = project.layers.size
        for (layer in project.layers) {
            var mapLayer: ILayer? = null
            when (layer.type) {
                "tms" -> {
                    mapLayer = createTMS(layer as RemoteLayerTMS)
                    total--
                }
                "ngrc" -> {
                    addRaster(layer)
                }
                "ngw", "ngfp" -> {
                    addVector(layer as RemoteLayerNGW)
                }
            }
            mapLayer?.let { map.addLayer(mapLayer) }
        }
        map.save()
        check()
    }

    private fun start(intent: Intent, layer: RemoteLayer) {
        var type = LayerFillService.NGW_LAYER
        when (layer.type) {
            "ngrc" -> type = LayerFillService.TMS_LAYER
            "ngfp" -> type = LayerFillService.VECTOR_LAYER_WITH_FORM
        }
        intent.putExtra(LayerFillService.KEY_NAME, layer.title)
        intent.putExtra(LayerFillService.KEY_LAYER_PATH, map.createLayerStorage(layer.path))
        intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, map.id)
        intent.putExtra(LayerFillService.KEY_INPUT_TYPE, type)
        intent.putExtra(LayerFillService.KEY_VISIBLE, layer.visible)
        intent.putExtra(LayerFillService.KEY_MIN_ZOOM, layer.minZoom)
        intent.putExtra(LayerFillService.KEY_MAX_ZOOM, layer.maxZoom)
        intent.putExtra(LayerFillService.KEY_URI, Uri.parse(layer.url))
        startService(intent)
    }

    private fun addRaster(layer: RemoteLayer) {
        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_ADD_TASK
        start(intent, layer)
    }

    private fun addVector(layer: RemoteLayerNGW) {
        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_ADD_TASK

        if (layer.type == "ngw") {
            val uri = Uri.parse(layer.url)
            val fullUrl = uri.scheme + "://" + uri.authority
            val accountName = "Collector " + System.currentTimeMillis()
            app.addAccount(accountName, fullUrl, layer.login, layer.password, "ngw")
            val id = uri.lastPathSegment.toLongOrNull()
            intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName)
            intent.putExtra(LayerFillService.KEY_REMOTE_ID, id)
        }

        intent.putExtra(LayerFillService.KEY_SYNC, layer.syncable)
        start(intent, layer)
    }

    private fun createTMS(layer: RemoteLayerTMS): ILayer {
        val tmsLayer = RemoteTMSLayerUI(this, map.createLayerStorage(layer.path))
        tmsLayer.isVisible = layer.visible
        tmsLayer.minZoom = layer.minZoom
        tmsLayer.maxZoom = layer.maxZoom
        tmsLayer.name = layer.title
        tmsLayer.url = layer.url
        tmsLayer.tileMaxAge = layer.lifetime
        tmsLayer.tmsType = layer.tmsType
        return tmsLayer
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String
//
//    companion object {
//
//         Used to load the 'native-lib' library on application startup.
//        init {
//            System.loadLibrary("native-lib")
//        }
//    }
}
