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
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import com.nextgis.collector.R
import com.nextgis.collector.adapter.ProjectAdapter
import com.nextgis.collector.data.Project
import com.nextgis.collector.data.RemoteLayerTMS
import com.nextgis.collector.databinding.ActivityProjectListBinding
import com.nextgis.collector.viewmodel.ProjectViewModel
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplib.util.GeoConstants.TMSTYPE_OSM
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI
import com.pawegio.kandroid.startActivity

class ProjectListActivity : BaseActivity(), ProjectAdapter.OnItemClickListener {
    private lateinit var binding: ActivityProjectListBinding
    private var projectAdapter = ProjectAdapter(arrayListOf(), this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (preferences.contains("project")) {
            startActivity<MapActivity>()
            finish()
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_project_list)
        val projectModel = ViewModelProviders.of(this).get(ProjectViewModel::class.java)
        binding.projectModel = projectModel
        binding.executePendingBindings()

        binding.projects.adapter = projectAdapter
        binding.projects.layoutManager = LinearLayoutManager(this)
        projectModel.projects.observe(this, Observer {
            it?.let { projectAdapter.replaceData(it) }
        })
        projectModel.load()

//         Example of a call to a native method
//        sample_text.text = stringFromJNI()
    }

    override fun onItemClick(project: Project) {
        AlertDialog.Builder(this).setTitle(R.string.join_project)
                .setMessage(getString(R.string.join_message, project.title))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, { _, _ -> create(project) })
                .show()
    }

    private fun create(project: Project) {
        preferences.edit().putString("project", project.title).apply()
        val map = map.map
        for (layer in project.layers) {
            var mapLayer: ILayer? = null
            when (layer.type) {
                "tms" -> {
                    val tmsLayer = RemoteTMSLayerUI(this, map.createLayerStorage())
                    tmsLayer.isVisible = true
                    tmsLayer.name = layer.title
                    tmsLayer.url = layer.url
                    tmsLayer.tileMaxAge = (layer as RemoteLayerTMS).lifetime
                    tmsLayer.maxZoom = GeoConstants.DEFAULT_MAX_ZOOM.toFloat()
                    tmsLayer.tmsType = TMSTYPE_OSM
                    mapLayer = tmsLayer
                }
            }
            mapLayer?.let { map.addLayer(mapLayer) }
        }
        map.save()
        startActivity<MapActivity>()
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
