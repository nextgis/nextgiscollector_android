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

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.collector.CollectorApplication
import com.nextgis.collector.R
import com.nextgis.collector.data.Project
import com.nextgis.collector.util.IntentFor
import com.nextgis.collector.util.toast
import com.nextgis.maplib.datasource.Geo
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.TrackLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.MapUtil
import com.nextgis.maplibui.activity.NGActivity
import com.nextgis.maplibui.mapui.MapViewOverlays
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.UiUtil
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

abstract class BaseActivity : NGActivity() {
    protected lateinit var app: CollectorApplication
    protected lateinit var mapView: MapViewOverlays
    protected lateinit var map: MapDrawable
    protected lateinit var preferences: SharedPreferences
    protected lateinit var project: Project
    var projectBorders : GeoEnvelope? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        app = application as CollectorApplication
        mapView = MapViewOverlays(this, app.map as MapDrawable)
        mapView.id = R.id.container
        map = mapView.map
        loadProject()
    }

    protected fun loadProject() {
        val json = preferences.getString("project", null)
        project = Project(JSONObject(json ?: "{}"))

        if (project.one != -1.0 ) {
            // need zoom to projects area
            val xMin = Geo.wgs84ToMercatorSphereX(project.one)
            val yMin = Geo.wgs84ToMercatorSphereY(project.two )
            val xMax = Geo.wgs84ToMercatorSphereX(project.three)
            val yMax = Geo.wgs84ToMercatorSphereY(project.four)
            val hasInfinity = xMin.isInfinite() || xMax.isInfinite() || yMin.isInfinite() || yMax.isInfinite()
            if (hasInfinity)
                return
            else
                projectBorders = GeoEnvelope(xMin, xMax, yMin, yMax)
        }
    }

    protected fun deleteAll(keepTrack : Boolean) {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                app.getAccount(layer.accountName)?.let { app.removeAccount(it) }
            }
        }

        if (!keepTrack) {
            val tracks = mapView.getLayersByType(Constants.LAYERTYPE_TRACKS)
            if (tracks.size > 0) {
                map.removeLayer(tracks[0])
                val uri = Uri.parse("content://" + app.authority + "/" + TrackLayer.TABLE_TRACKS)
                contentResolver.delete(uri, null, null)
            }
        }

        map.delete(keepTrack)
        preferences.edit().remove("project").apply()
    }

    protected fun hasChanges(): Boolean {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                if (changesCount > 0) {
                    return true
                }
            }
        }
        return false
    }

    protected fun change(project: Project? = null) {
        deleteAll(true)
        val intent = IntentFor<ProjectListActivity>(this)
        project?.let {
            intent.putExtra("project", it.id)
            intent.putExtra("private", it.private)
        }
        finish()
        startActivity(intent)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (projectBorders != null && resultCode == RESULT_OK && data!= null){
            if (data.hasExtra(ConstantsUI.KEY_ADDED_POINT)){
                val pointArray :DoubleArray? = data.getDoubleArrayExtra(ConstantsUI.KEY_ADDED_POINT);
                if (pointArray != null && pointArray.size>=2){
                    val geoPoint = GeoPoint(pointArray[0],  pointArray[1])
                    projectBorders.let {
                        if (!projectBorders!!.contains(geoPoint)){
                            val builder = android.app.AlertDialog.Builder(this@BaseActivity)
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


    public fun shareLog() {
        HyperLog.getDeviceLogsInFile(this)
        val dir = File(getExternalFilesDir(null), "LogFiles")
        val size = FileUtil.getDirectorySize(dir)
        if (size == 0L) {
            toast(R.string.error_empty_dataset)
            return
        }

        val files = zipLogs(dir)
        val type = "text/plain"
        UiUtil.share(files, type, this, false)
    }

    private fun zipLogs(dir: File): File? {
        var temp = MapUtil.prepareTempDir(this, "shared_layers", false)
        val outdated = arrayListOf<File>()
        try {
            val fileName = "ng-logs.zip"
            if (temp == null) {
                toast(R.string.error_file_create)
            }

            temp = File(temp, fileName)
            temp.createNewFile()
            val fos = FileOutputStream(temp, false)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            val buffer = ByteArray(1024)
            var length: Int

            for (file in dir.listFiles()) {
                if (System.currentTimeMillis() - file.lastModified() > 60 * 60 * 1000)
                    outdated.add(file)
                try {
                    val fis = FileInputStream(file)
                    zos.putNextEntry(ZipEntry(file.name))
                    while (fis.read(buffer).also { length = it } > 0) zos.write(buffer, 0, length)
                    zos.closeEntry()
                    fis.close()
                } catch (ex: Exception) {
                }
            }

            zos.close()
            fos.close()
        } catch (e: IOException) {
            temp = null
        }
        for (file in outdated) {
            file.delete()
        }
        return temp
    }

}