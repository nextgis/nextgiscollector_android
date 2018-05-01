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

package com.nextgis.collector

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import com.nextgis.collector.adapter.ProjectAdapter
import com.nextgis.collector.data.Project
import com.nextgis.collector.databinding.ActivityProjectListBinding
import com.nextgis.collector.viewmodel.ProjectViewModel
import com.pawegio.kandroid.toast

class ProjectListActivity : AppCompatActivity(), ProjectAdapter.OnItemClickListener {
    private lateinit var binding: ActivityProjectListBinding
    private var projectAdapter = ProjectAdapter(arrayListOf(), this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_project_list)

        val projectModel = ViewModelProviders.of(this).get(ProjectViewModel::class.java)
        binding.projectModel = projectModel
        binding.executePendingBindings()

//        setSupportActionBar(findViewById(R.id.toolbar))
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setHomeButtonEnabled(true)

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
                .setPositiveButton(R.string.yes, { _, _ -> toast(project.description) })
                .show()
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
