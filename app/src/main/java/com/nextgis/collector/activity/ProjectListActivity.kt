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
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.adapter.ProjectAdapter
import com.nextgis.collector.data.Project
import com.nextgis.collector.data.RemoteLayer
import com.nextgis.collector.data.RemoteLayerNGW
import com.nextgis.collector.data.RemoteLayerTMS
import com.nextgis.collector.databinding.ActivityProjectListBinding
import com.nextgis.collector.util.NetworkUtil
import com.nextgis.collector.viewmodel.ProjectViewModel
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.datasource.ngw.LayerWithStyles
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplib.util.NGWUtil
import com.nextgis.maplibui.activity.NGIDLoginActivity
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI
import com.nextgis.maplibui.service.LayerFillService
import com.nextgis.maplibui.util.NGIDUtils.*
import com.pawegio.kandroid.*
import java.io.File

class ProjectListActivity : BaseActivity(), View.OnClickListener, ProjectAdapter.OnItemClickListener {
    private lateinit var binding: ActivityProjectListBinding
    private var projectAdapter = ProjectAdapter(arrayListOf(), this)
    private lateinit var receiver: BroadcastReceiver
    @Volatile
    private var total = 0
    private val queue = arrayListOf<Intent>()
    private val layers = arrayListOf<NGWVectorLayer>()

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
        projectModel.email = NetworkUtil.getEmailOrUsername(preferences)
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
                    prepare(it)
                }
            }
        })

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                HyperLog.v(Constants.TAG, "BroadcastReceiver got action from LayerFillService: ${intent.action}")
                if (intent.action?.equals(LayerFillService.ACTION_STOP) == true) {
                    toast(R.string.canceled)
                    reset()
                    return
                }
                val serviceStatus = intent.getShortExtra(LayerFillService.KEY_STATUS, 0)
                HyperLog.v(Constants.TAG, "BroadcastReceiver got status from LayerFillService: $serviceStatus")
                when (serviceStatus) {
                    LayerFillService.STATUS_STOP -> {
                        val canceled = intent.getBooleanExtra(LayerFillService.KEY_CANCELLED, false)
                        val success = intent.getBooleanExtra(LayerFillService.KEY_RESULT, false)
                        HyperLog.v(Constants.TAG, "BroadcastReceiver stop with success: $success (canceled: $canceled)")
                        if (!success) {
                            if (canceled)
                                toast(R.string.canceled)
                            else if (intent.hasExtra(LayerFillService.KEY_MESSAGE))
                                longToast(intent.getStringExtra(LayerFillService.KEY_MESSAGE) ?: getString(R.string.sync_error))
                            error()
                            return
                        }
                        if (!intent.hasExtra(LayerFillService.KEY_MESSAGE))
                            return

                        val isNgw = intent.getBooleanExtra(LayerFillService.KEY_SYNC, false)
                        HyperLog.v(Constants.TAG, "BroadcastReceiver no error message found, isNgw: $isNgw")
                        if (success && !canceled && isNgw) {
                            val id = intent.getIntExtra(LayerFillService.KEY_REMOTE_ID, -1)
                            val ngwLayer = map.getLayerById(id) as? NGWVectorLayer
                            if (ngwLayer == null) {
                                longToast(R.string.error_file_create)
                                error()
                                return
                            }

                            val account = app.getAccount(ngwLayer.accountName)
                            val project = binding.projectModel?.selectedProject?.get()
                            val remote = project?.layers?.firstOrNull { it.path == ngwLayer.path.name }
                            if (remote is RemoteLayerNGW) {
                                ngwLayer.setIsEditable(remote.editable && remote.syncable)
                                if (remote.syncable) {
                                    NGWSettingsFragment.setAccountSyncEnabled(account, app.authority, true)
                                    ngwLayer.syncType = Constants.SYNC_ALL
                                }

                                layers.add(ngwLayer)
                            }
                        }

                        total--
                        check()
                    }
                    LayerFillService.STATUS_START -> {
                        binding.message.text = ""
                        intent.getStringExtra(LayerFillService.KEY_TITLE)?.let { binding.status.text = it }
                    }
                    LayerFillService.STATUS_UPDATE -> {
                        intent.getStringExtra(LayerFillService.KEY_MESSAGE)?.let { binding.message.text = it }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(LayerFillService.ACTION_UPDATE)
        intentFilter.addAction(LayerFillService.ACTION_STOP)
        registerReceiver(receiver, intentFilter)

        val extras = intent.extras
        val id = extras?.getInt("project", -1) ?: -1
        val private = extras?.getBoolean("private", true) ?: true
        if (id >= 0) {
            load(id, private)
            setTitle()
        } else {
            loadProjects()
        }
    }

    private fun error() {
        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_STOP
        ContextCompat.startForegroundService(this, intent)
        reset()
    }

    private fun reset() {
        runDelayed(2000) { deleteAll() }
        binding.apply {
            projectModel?.let {
                it.info.set(true)
                it.error.set(true)
                it.isLoading.set(false)
                it.isLoaded.set(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (preferences.contains("project")) {
            open()
            finish()
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.create -> write()
            R.id.retry -> retry()
            R.id.cancel -> cancel()
        }
    }

    private fun loadProjects() {
        binding.apply {
            setTitle()
            val url = preferences.getString("collector_hub_url", COLLECTOR_HUB_URL)
            projectModel?.load(private = true, base = url ?: COLLECTOR_HUB_URL)
        }
    }

    private fun retry() {
        binding.apply {
            projectModel?.let {
                it.info.set(false)
                it.error.set(false)
                it.isLoading.set(true)
                it.isLoaded.set(false)
                it.selectedProject.get()?.let { project -> prepare(project) }
            }
            message.text = ""
            status.text = ""
        }
    }

    private fun cancel() {
        binding.apply {
            projectModel?.let {
                it.info.set(false)
                it.error.set(false)
                it.isLoading.set(false)
                it.isLoaded.set(true)
            }
            status.text = ""
            message.text = ""
        }
    }

    private fun write() {
        val builder = AlertDialog.Builder(this).setTitle(R.string.create_new)
                .setMessage(R.string.write_us)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.details) { _, _ -> details() }
                .create()

        builder.show()
        val message = builder.findViewById<TextView>(android.R.id.message)
        if (message != null) {
            message.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun details() {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.premium_url)))
        startActivity(browser)
    }

    override fun onItemClick(project: Project) {
        AlertDialog.Builder(this).setTitle(R.string.join_project)
                .setMessage(getString(R.string.join_message, project.title))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> load(project.id, true) }
                .show()
    }

    /**
     * This was used to determine title for public or private project
     *
     */
    private @Deprecated("Useless", replaceWith = ReplaceWith("supportActionBar?.title")) fun setTitle() {
        supportActionBar?.title = getString(R.string.app_name_private)
    }

    private fun load(id: Int, private: Boolean) {
        val url = preferences.getString("collector_hub_url", COLLECTOR_HUB_URL)
        binding.projectModel?.load(id, private, url ?: COLLECTOR_HUB_URL)
    }

    private fun open() {
        if (!project.isMapMain)
            startActivity<AddFeatureActivity>()
        else
            startActivity<MapActivity>()
    }

    private fun check() {
        HyperLog.v(Constants.TAG, "Check completeness, total: $total")
        if (total <= 0) {
            binding.projectModel?.selectedProject?.get()?.let { project ->
                val paths = project.layers.map { it.path }.reversed()
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

                    if (layer is VectorLayer && id >= 0 && id < project.layers.size) {
                        val vector = project.layers.reversed()[id] as RemoteLayerNGW
                        if (vector.styleable) {
                            layer.setRenderer(vector.renderer)
                            vector.style = ""
                        }

                        val ngw = layers.firstOrNull { it.id == layer.id }
                        ngw?.save()
                    }
                }
                this.project = project
                preferences.edit().putString("project", project.json).apply()
                open()
            }
        }
    }

    private fun getFullUrl(project: Project): String {
        if (project.private)
            return project.url

        val uri = Uri.parse(Uri.decode(project.url))
        val scheme = uri.scheme ?: "http"
        return scheme + "://" + uri.authority
    }

    private fun needAccount(project: Project): Boolean {
        return project.user.isNotBlank() && project.url.isNotBlank()
    }

    private fun prepare(project: Project) {
        if (project.layers.size == 0) {
            runOnUiThread { toast(R.string.error_empty_dataset) }
            return
        }

        binding.projectModel?.isLoading?.set(true)
        binding.projectModel?.isLoaded?.set(false)
        runOnUiThread { account(project) }
    }

    private fun account(project: Project) {
        if (needAccount(project)) {
            val fullUrl = getFullUrl(project)
            val authority = fullUrl.split("://")[1]
            app.getAccount(authority)?.let { app.removeAccount(it) }
            val success = app.addAccount(authority, fullUrl, project.user, project.password, "ngw")
            if (!success) {
                toast(R.string.error_auth)
                reset()
                return
            }
        }

        runAsync { create(project) }
    }

    private fun create(project: Project) {
        val base = getExternalFilesDir(null) ?: filesDir
        val file = File(base, CollectorApplication.TREE)
        FileUtil.writeToFile(file, project.tree)

        val needAccount = needAccount(project)
        val fullUrl = getFullUrl(project)
        val authority = fullUrl.split("://")[1]

        total = project.layers.size
        HyperLog.v(Constants.TAG, "Create layers, total: $total")
        for (layer in project.layers) {
            var mapLayer: ILayer? = null
            when (layer.type) {
                "tms" -> {
                    mapLayer = createTMS(layer as RemoteLayerTMS)
                    total--
                    HyperLog.v(Constants.TAG, "TMS layer found: ${layer.title}")
                }
                "ngrc" -> {
                    addRaster(layer)
                    HyperLog.v(Constants.TAG, "NGRC layer found: ${layer.title}")
                }
                "ngw", "ngfp" -> {
                    val resource = layer as RemoteLayerNGW
                    if (needAccount) {
                        resource.login = project.user
                        resource.password = project.password
                    }
                    addVector(resource, authority, fullUrl, project.user, project.password)
                    HyperLog.v(Constants.TAG, "Vector layer found: ${layer.title}")
                }
            }
            mapLayer?.let { map.addLayer(mapLayer) }
        }
        map.save()
        HyperLog.v(Constants.TAG, "Save to map and start filling with features: $total")
        check()
        start()
    }

    private fun queue(intent: Intent, layer: RemoteLayer, formUrl: String = "") {
        var type = LayerFillService.NGW_LAYER
        var url = layer.url
        when (layer.type) {
            "ngrc" -> type = LayerFillService.TMS_LAYER
            "ngfp" -> {
                type = LayerFillService.VECTOR_LAYER_WITH_FORM
                url = formUrl
            }
        }
        intent.putExtra(LayerFillService.KEY_NAME, layer.title)
        intent.putExtra(LayerFillService.KEY_LAYER_PATH, map.createLayerStorage(layer.path))
        intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, map.id)
        intent.putExtra(LayerFillService.KEY_INPUT_TYPE, type)
        intent.putExtra(LayerFillService.KEY_VISIBLE, layer.visible)
        intent.putExtra(LayerFillService.KEY_MIN_ZOOM, layer.minZoom)
        intent.putExtra(LayerFillService.KEY_MAX_ZOOM, layer.maxZoom)
        intent.putExtra(LayerFillService.KEY_URI, Uri.parse(url))
        queue.add(intent)
    }

    private fun start() {
        for (intent in queue)
            ContextCompat.startForegroundService(this, intent)
        queue.clear()
    }

    private fun addRaster(layer: RemoteLayer) {
        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_ADD_TASK
        queue(intent, layer)
    }

    private fun addVector(layer: RemoteLayerNGW, accountName: String, url: String, user: String, pass: String) {
        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_ADD_TASK

        val uri = Uri.parse(Uri.decode(layer.url))
        val id = uri.lastPathSegment?.toLongOrNull()
        intent.putExtra(LayerFillService.KEY_REMOTE_ID, id)
        intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName)
        intent.putExtra(LayerFillService.KEY_SYNC, layer.syncable)
        var formUrl = layer.url
        if (layer.type == "ngfp")
            id?.let {
                val forms = arrayListOf<Long>()
                LayerWithStyles.fillStyles(url, user, pass, it, null, forms)
                forms.firstOrNull()?.let { form -> formUrl = NGWUtil.getFormUrl(url, form) }
            }
        queue(intent, layer, formUrl)
    }

    private fun createTMS(layer: RemoteLayerTMS): ILayer {
        val tmsLayer = RemoteTMSLayerUI(this, map.createLayerStorage(layer.path))
        tmsLayer.isVisible = layer.visible
        tmsLayer.minZoom = layer.minZoom
        tmsLayer.maxZoom = layer.maxZoom
        tmsLayer.name = layer.title
        tmsLayer.url = layer.url
        tmsLayer.tileMaxAge = layer.lifetime * 60 * 1000
        val type = if (layer.tmsType == 0) GeoConstants.TMSTYPE_OSM else layer.tmsType
        tmsLayer.tmsType = type
        return tmsLayer
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> startActivity<PreferenceActivity>()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

}
