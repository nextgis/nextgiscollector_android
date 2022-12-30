/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018-2019 NextGIS, info@nextgis.com
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

package com.nextgis.collector.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.databinding.ObservableField
import com.nextgis.collector.data.Project
import com.nextgis.collector.model.ProjectModel


class ProjectViewModel : ViewModel() {
    private var projectModel: ProjectModel = ProjectModel()
    var projects = MutableLiveData<ArrayList<Project>>()
    val selectedProject: ObservableField<Project> = ObservableField()
    val isLoading = ObservableField(true)
    val isLoaded = ObservableField(false)
    val isEmpty = ObservableField(false)
    val error = ObservableField(false)
    val info = ObservableField(false)
    var email = ""

    private val onDataReadyCallback = object : ProjectModel.OnDataReadyCallback {
        override fun onProjectReady(project: Project?) {
            info.set(false)
            error.set(false)
            isEmpty.set(false)
            isLoading.set(false)
            isLoaded.set(true)
            selectedProject.set(project)
        }

        override fun onDataReady(data: ArrayList<Project>) {
            isEmpty.set(data.size == 0)
            info.set(isEmpty.get())
            error.set(false)
            isLoading.set(false)
            isLoaded.set(true)
            projects.postValue(data)
        }
    }

    fun load(private: Boolean, base: String) {
        info.set(false)
        error.set(false)
        isEmpty.set(false)
        isLoading.set(true)
        isLoaded.set(false)
        projectModel.getProjects(base, private, onDataReadyCallback, email)
    }

    fun load(id: Int, private: Boolean, base: String) {
        info.set(false)
        error.set(false)
        isEmpty.set(false)
        isLoading.set(true)
        isLoaded.set(false)
        projectModel.getProject(base, id, onDataReadyCallback, email, private)
    }
}