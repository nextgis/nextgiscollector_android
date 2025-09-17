/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
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

package com.nextgis.collector.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.nextgis.collector.R
import com.nextgis.collector.util.longToast
import com.nextgis.collector.util.startActivity
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.maplibui.util.SettingsConstantsUI

class IntroActivity : AppIntro() {


    override fun getLayoutId(): Int {
        return  R.layout.appintro_intro_layout_copy
    }
    // override val layoutId = R.layout.appintro_intro_layout_copy

    companion object {
        const val INTRO_SHOWN = "intro_shown"
        const val PERMISSIONS_CODE = 17
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title1 = getString(R.string.intro_title1)
        val title2 = getString(R.string.intro_title2)
        val description1 = getString(R.string.intro_description1)
        val description2 = getString(R.string.intro_description2)
        val color1 = ContextCompat.getColor(this, R.color.actionBarColor)
        val color2 = ContextCompat.getColor(this, R.color.primary_dark)
        addSlide(AppIntroFragment.newInstance(title1, description1, R.drawable.intro1, color1))
        addSlide(AppIntroFragment.newInstance(title2, description2, R.drawable.intro2, color2))

        showSkipButton(false)
    }

    override fun onDonePressed(currentFragment: Fragment) {
        super.onDonePressed(currentFragment)
        if (!PermissionUtil.hasPermission(this, Manifest.permission.WRITE_SYNC_SETTINGS)
                || !PermissionUtil.hasPermission(this, Manifest.permission.GET_ACCOUNTS)) {
            var permissions = arrayOf(Manifest.permission.GET_ACCOUNTS,
                Manifest.permission.WRITE_SYNC_SETTINGS)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
                permissions = permissions.plus(Manifest.permission.POST_NOTIFICATIONS)
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_CODE)
        } else
            openProjectList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)


        for (i in permissions.indices) {
            if (permissions[i] == Manifest.permission.POST_NOTIFICATIONS && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                    .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_SYNC, true)
                    .apply()
            }
        }
        var granted = requestCode == PERMISSIONS_CODE
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted)
            openProjectList()
        else
            longToast(R.string.permission_denied)
    }

    private fun openProjectList() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(INTRO_SHOWN, true).apply()
        startActivity<ProjectListActivity>()
        finish()
    }
}