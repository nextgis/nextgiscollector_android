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

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.*
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import com.nextgis.collector.BuildConfig
import com.nextgis.maplibui.util.NGIDUtils
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ActivityPreferenceBinding
import com.nextgis.collector.viewmodel.SettingsViewModel
import com.nextgis.maplibui.activity.NGIDLoginActivity
import com.pawegio.kandroid.IntentFor
import kotlinx.android.synthetic.main.toolbar.*
import java.text.DateFormat
import java.util.*

class PreferenceActivity : BaseActivity() {
    private lateinit var binding: ActivityPreferenceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_preference)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        val settingsModel = ViewModelProviders.of(this).get(SettingsViewModel::class.java)
        settingsModel.init(this)
        binding.settings = settingsModel
        binding.executePendingBindings()

        supportFragmentManager.beginTransaction().replace(R.id.container, PreferencesFragment()).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.about, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> finish()
            R.id.menu_about -> about()
        }
        return true
    }

    private fun about() {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(BuildConfig.BUILD_TIME))
        val builder = AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, date))
                .setPositiveButton(R.string.ok, null)
        builder.show()
    }

    class PreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, screen: String?) {
            addPreferencesFromResource(R.xml.preferences)
            val signOut = findPreference("sign_out")
            signOut.setOnPreferenceClickListener {
                (activity as? PreferenceActivity)?.let { activity -> ask(activity) }
                true
            }
        }

        private fun ask(activity: PreferenceActivity) {
            AlertDialog.Builder(activity).setTitle(R.string.sign_out)
                    .setMessage(R.string.sign_out_ngid_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes) { _, _ -> signOut(activity) }
                    .show()
        }

        private fun signOut(activity: PreferenceActivity) {
            NGIDUtils.signOut(activity.preferences, activity)
            val intent = IntentFor<NGIDLoginActivity>(activity)
            intent.putExtra(NGIDLoginActivity.EXTRA_NEXT, ProjectListActivity::class.java)
            startActivity(intent)
            activity.finish()
        }

        private fun setAllPreferencesToAvoidHavingExtraSpace(preference: Preference) {
            preference.isIconSpaceReserved = false
            if (preference is PreferenceGroup)
                for (i in 0 until preference.preferenceCount)
                    setAllPreferencesToAvoidHavingExtraSpace(preference.getPreference(i))
        }

        override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
            if (preferenceScreen != null)
                setAllPreferencesToAvoidHavingExtraSpace(preferenceScreen)
            super.setPreferenceScreen(preferenceScreen)
        }

        override fun onCreateAdapter(preferenceScreen: PreferenceScreen?): RecyclerView.Adapter<*> =
                object : PreferenceGroupAdapter(preferenceScreen) {
                    @SuppressLint("RestrictedApi")
                    override fun onPreferenceHierarchyChange(preference: Preference?) {
                        if (preference != null)
                            setAllPreferencesToAvoidHavingExtraSpace(preference)
                        super.onPreferenceHierarchyChange(preference)
                    }
                }
    }
}