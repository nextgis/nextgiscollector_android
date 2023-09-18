package com.nextgis.collector.service

import android.accounts.Account
import android.accounts.AccountManager
import android.app.IntentService
import android.content.Intent
import android.content.Context
import android.content.SyncResult
import com.nextgis.collector.BuildConfig
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.api.INGWLayer
import com.nextgis.maplib.datasource.ngw.SyncAdapter
import com.nextgis.maplib.map.MapContentProviderHelper


private const val ACTION_OFFSYNC = "com.nextgis.collector.service.action.ACTION_OFFSYNC"

class OfflineIntentService : IntentService("OfflineIntentService") {

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OFFSYNC -> {
                handleActionFoo()
            }
        }
    }

    private fun handleActionFoo() {
        val mAccounts: MutableList<Account> = ArrayList()
        val accountManager = AccountManager.get(applicationContext)
        val application = application as IGISApplication
        val layers: MutableList<INGWLayer> = ArrayList()
        for (account in accountManager.getAccountsByType(application.accountsType)) {
            layers.clear()
            MapContentProviderHelper.getLayersByAccount(application.map, account.name, layers)
            if (layers.size > 0) mAccounts.add(account)
        }
        val syncResult = SyncResult()
        val syncAdapter = SyncAdapter(applicationContext, true)
        for (account in mAccounts) {
            syncAdapter.onPerformSync(
                account, null, BuildConfig.providerAuth,
                null, syncResult
            )
        }
    }

    companion object {
        @JvmStatic
        fun startActionSync(context: Context) {
            val intent = Intent(context, OfflineIntentService::class.java).apply {
                action = ACTION_OFFSYNC
            }
            context.startService(intent)
        }
    }
}