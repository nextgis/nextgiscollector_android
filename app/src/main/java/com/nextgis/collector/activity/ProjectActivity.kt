/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
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

import android.content.*
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.nextgis.collector.R
import com.nextgis.maplib.map.NGWVectorLayer
import com.pawegio.kandroid.startActivity
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.pawegio.kandroid.toast
import android.accounts.Account
import android.os.Bundle
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.api.INGWLayer
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplibui.fragment.NGWSettingsFragment
import com.pawegio.kandroid.accountManager


abstract class ProjectActivity : BaseActivity() {
    private var syncReceiver: SyncReceiver = SyncReceiver()
    private var total: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter()
        intentFilter.addAction(SyncAdapter.SYNC_START)
        intentFilter.addAction(SyncAdapter.SYNC_FINISH)
        intentFilter.addAction(SyncAdapter.SYNC_CANCELED)
        registerReceiver(syncReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(syncReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_sync -> sync()
            R.id.menu_change_project -> ask()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    protected fun updateSubtitle() {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val changesCount = FeatureChanges.getChangeCount(layer.changeTableName)
                if (changesCount > 0) {
                    setSubtitle(true)
                    break
                }
            }
        }
    }

    protected fun setSubtitle(hasChanges: Boolean) {
        supportActionBar?.setSubtitle(if (hasChanges) R.string.not_synced else R.string.all_synced)
    }

    protected fun sync() {
        val accounts = ArrayList<Account>()
        val layers = ArrayList<INGWLayer>()

        accountManager?.let {
            for (account in it.getAccountsByType(app.accountsType)) {
                layers.clear()
                MapContentProviderHelper.getLayersByAccount(map, account.name, layers)
                val syncEnabled = NGWSettingsFragment.isAccountSyncEnabled(account, app.authority)
                if (layers.size > 0 && syncEnabled)
                    accounts.add(account)
            }

            for (account in accounts) {
                val settings = Bundle()
                settings.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                settings.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                ContentResolver.requestSync(account, app.authority, settings)
            }
        }

        total = accounts.size
    }

    private fun change() {
        for (i in 0 until map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                val account = app.getAccount(layer.accountName)
                app.removeAccount(account)
            }
        }

        map.delete()
        startActivity<ProjectListActivity>()
        preferences.edit().remove("project").apply()
    }

    private fun ask() {
        AlertDialog.Builder(this).setTitle(R.string.change_project)
                .setMessage(R.string.change_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, { _, _ -> change() })
                .show()
    }

    protected inner class SyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SyncAdapter.SYNC_START) {
                findViewById<FrameLayout>(R.id.overlay).visibility = View.VISIBLE
            } else if (intent.action == SyncAdapter.SYNC_FINISH || intent.action == SyncAdapter.SYNC_CANCELED) {
                if (intent.hasExtra(SyncAdapter.EXCEPTION))
                    toast(intent.getStringExtra(SyncAdapter.EXCEPTION))

                total--
                if (total <= 0) {
                    updateSubtitle()
                    findViewById<FrameLayout>(R.id.overlay).visibility = View.GONE
                }
            }
        }
    }
}