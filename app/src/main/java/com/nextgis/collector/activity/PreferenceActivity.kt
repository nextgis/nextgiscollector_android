/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2018-2019, 2021 NextGIS, info@nextgis.com
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

import android.app.Activity
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import com.nextgis.collector.BuildConfig
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ActivityPreferenceBinding
import com.nextgis.collector.util.Logger
import com.nextgis.collector.viewmodel.SettingsViewModel
import com.nextgis.maplib.util.AccountUtil
import com.nextgis.maplib.util.NetworkUtil
import com.nextgis.maplib.util.SettingsConstants
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.NGIDUtils
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_SYNC
import com.pawegio.kandroid.runDelayedOnUiThread
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.Permission
import java.text.DateFormat
import java.util.*

class PreferenceActivity : BaseActivity() {
    private lateinit var binding: ActivityPreferenceBinding

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        for (fragment in supportFragmentManager.fragments) {
            if (fragment is PreferencesFragment) (fragment as PreferencesFragment).processPermission(
                requestCode, resultCode, data  )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is PreferencesFragment) (fragment as PreferencesFragment).processPermission(
                requestCode, permissions, grantResults  )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferenceBinding.inflate(layoutInflater) // DataBindingUtil.setContentView(this, R.layout.activity_preference)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        val settingsModel = ViewModelProviders.of(this).get(SettingsViewModel::class.java)
        settingsModel.init(this)
        binding.settings = settingsModel
        binding.executePendingBindings()

        supportFragmentManager.beginTransaction().replace(R.id.container, PreferencesFragment()).commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (preferences.getBoolean("save_log", false)) {
            Logger.initialize(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.about, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
        val REQUEST_NOTIFICATION_PERMISSION = 741

        override fun onCreatePreferences(savedInstanceState: Bundle?, screen: String?) {
            addPreferencesFromResource(R.xml.preferences)
            val signOut = findPreference("sign_out") as Preference?
            signOut?.setOnPreferenceClickListener {
                (activity as? PreferenceActivity)?.let { activity -> ask(activity) }
                true
            }
            val uid = findPreference(SettingsConstants.KEY_PREF_TRACK_SEND) as CheckBoxPreference?
            initializeUid(uid)

            val notify = findPreference<Preference>(SettingsConstantsUI.KEY_PREF_SHOW_SYNC)
            if (activity != null)
                initializeNotification(requireActivity(), notify)


        }

        fun initializeNotification( activity: Activity,
            preference: Preference?
        ) {
            if (null != preference) {
                preference.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { preference ->
                        if ((preference as CheckBoxPreference).isChecked) {
                            // check notify perm
                            processNotifyPerm(activity,
                                preference
                            )
                        }
                        false
                    }
            }
        }

        fun processNotifyPerm(activity: Activity, preference: Preference): Boolean {
            if (ContextCompat.checkSelfPermission(activity, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                return true
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, "android.permission.POST_NOTIFICATIONS" )) {
                val confirm = AlertDialog.Builder(activity)
                confirm.setTitle(R.string.push_perm_title)
                    .setMessage(R.string.push_perm_why_text)
                    .setNegativeButton(
                        android.R.string.cancel,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                .putBoolean(KEY_PREF_SHOW_SYNC, false)
                                .commit()
                            (preference as CheckBoxPreference).isChecked = false
                        })
                    .setPositiveButton(android.R.string.ok,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            if (which == -1) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    activity?.requestPermissions(
                                        arrayOf<String>("android.permission.POST_NOTIFICATIONS"),
                                        REQUEST_NOTIFICATION_PERMISSION
                                    )
                                } else {
                                }
                            }
                        })
                    .show()
            } else {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.requestPermissions(
                    arrayOf<String>("android.permission.POST_NOTIFICATIONS"),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
            return true
        }

        public fun processPermission(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray  ) {

            if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // nothing
                } else if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    val notify = findPreference<Preference>(SettingsConstantsUI.KEY_PREF_SHOW_SYNC)
                    (notify as CheckBoxPreference?)!!.isChecked = false
                    PreferenceManager.getDefaultSharedPreferences(this.context).edit()
                        .putBoolean(KEY_PREF_SHOW_SYNC, false)
                        .commit()


                    // alert perm off
                    val confirm = AlertDialog.Builder(requireActivity())
                    confirm.setTitle(R.string.push_perm_title)
                        .setMessage(R.string.push_perm_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }


        public fun processPermission(
            requestCode: Int, resultCode: Int, data: Intent?  ) {

            if (requestCode == REQUEST_NOTIFICATION_PERMISSION &&
                 resultCode == RESULT_OK) {
                // nothing
            } else {
                val notify = findPreference<Preference>(SettingsConstantsUI.KEY_PREF_SHOW_SYNC)
                (notify as CheckBoxPreference?)!!.isChecked = false
                PreferenceManager.getDefaultSharedPreferences(this.context).edit()
                    .putBoolean(KEY_PREF_SHOW_SYNC, false)
                    .commit()


                // alert perm off
                val confirm = AlertDialog.Builder(requireActivity())
                confirm.setTitle(R.string.push_perm_title)
                    .setMessage(R.string.push_perm_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
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
            val dialog = ProgressDialog(activity)
            dialog.setTitle(R.string.sign_out_ngid)
            dialog.setMessage(getString(R.string.waiting))
            dialog.setCanceledOnTouchOutside(false)
            dialog.setCancelable(false)
            dialog.show()

            NGIDUtils.signOut(activity.preferences, activity)
            activity.deleteAll()
            runDelayedOnUiThread(2500) { rebirth(activity) }
        }

        // https://stackoverflow.com/a/46848226/2088273
        private fun rebirth(activity: Activity) {
            val packageManager = activity.packageManager
            val intent = packageManager?.getLaunchIntentForPackage(activity.packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
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

        override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
            return super.onCreateAdapter(preferenceScreen)
        }

//        override fun onCreateAdapter(preferenceScreen: PreferenceScreen?): RecyclerView.Adapter<*> =
//                object : PreferenceGroupAdapter(preferenceScreen) {
//                    @SuppressLint("RestrictedApi")
//                    override fun onPreferenceHierarchyChange(preference: Preference?) {
//                        if (preference != null)
//                            setAllPreferencesToAvoidHavingExtraSpace(preference)
//                        super.onPreferenceHierarchyChange(preference)
//                    }
//                }

        fun initializeUid(preference: CheckBoxPreference?) {
            // async check
            // registered = true - enabled = true; keep state
            // registered = false - enabled = false; checked = false
            // no network - enabled = false; keep state; no network info
            val context = preference?.context
            val uid = TrackerService.getUid(context)
            //preference.summary = context.getString(R.string.track_uid, uid)
            CheckRegistration(
                preference,
                AccountUtil.isProUser(preference?.context)
            ).execute()
        }

        public class CheckRegistration constructor(
            private val mPreference: CheckBoxPreference?,
            private val isProFinal: Boolean) :   AsyncTask<Void?, Void?, Boolean?>() {

            override fun doInBackground(vararg params: Void?): Boolean? {
                return try {
                    //if (isProFinal) {
                        val base = mPreference?.sharedPreferences?.getString(
                            "tracker_hub_url",
                            TrackerService.HOST)
                        val url = String.format(
                            "%s/%s/registered",
                            base + TrackerService.URL,
                            TrackerService.getUid(mPreference?.context))
                        val response = NetworkUtil.get(url, null, null, false)
                        val body = response.responseBody
                        val json = JSONObject(body ?: "")
                        json.optBoolean("registered")
                    //} else false
                } catch (e: IOException) {
                    null
                } catch (e: JSONException) {
                    null
                }
            }

            override fun onPostExecute(result: Boolean?) {
                super.onPostExecute(result)

                if (result == null) {
                    mPreference?.setSummary(R.string.error_connect_failed)
                    return
                }

                //val uid = TrackerService.getUid(context)
                //mPreference.summary = context.getString(R.string.track_uid, uid)
                // save String name = getPackageName() + "_preferences";

                if (result) {
                    mPreference?.isEnabled = true
                } else {
                    val context = mPreference?.context
                    val name: String = context?.getPackageName() + "_preferences"
                    val mSharedPreferences: SharedPreferences = context!!.getSharedPreferences(name, MODE_MULTI_PROCESS)
                    val syncen = mSharedPreferences.getBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false)

                    mPreference?.isEnabled = false

                    if(syncen){
                        // off send and warning about it
                        val builder = android.app.AlertDialog.Builder(context)
                            .setTitle(R.string.alert_no_trackerid_onserver_title)
                            .setMessage(R.string.alert_no_trackerid_onserver)
                            .setPositiveButton(R.string.ok, null)
                            .create()
                        builder.show()
                    }
                    mPreference?.isChecked = false
                }
            }
        }
    }
}