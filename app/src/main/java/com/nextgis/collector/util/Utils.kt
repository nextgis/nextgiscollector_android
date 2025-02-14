package com.nextgis.collector.util

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes

class Utils {
}

fun Context.shortToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.shortToast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.longToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}


inline val Context.inputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?

inline val Context.locationManager
    get() = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
inline val Context.accountManager
    get() = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager?

inline fun <reified T : Any> IntentFor(context: Context): Intent = Intent(context, T::class.java)

inline fun <reified T : Any> Activity.startActivityForResult(requestCode: Int, options: Bundle? = null, action: String? = null) =
    startActivityForResult(IntentFor<T>(this).setAction(action), requestCode, options)

inline fun <reified T : Any> Context.startActivity() = startActivity(IntentFor<T>(this))

inline fun Context.toast(text: CharSequence): Toast = Toast.makeText(this, text, Toast.LENGTH_SHORT).apply { show() }

inline fun Context.longToast(text: CharSequence): Toast = Toast.makeText(this, text, Toast.LENGTH_LONG).apply { show() }

inline fun Context.toast(@StringRes resId: Int): Toast = Toast.makeText(this, resId, Toast.LENGTH_SHORT).apply { show() }

inline fun Context.longToast(@StringRes resId: Int): Toast = Toast.makeText(this, resId, Toast.LENGTH_LONG).apply { show() }

fun runDelayed(delayMillis: Long, action: () -> Unit) = Handler().postDelayed(Runnable(action), delayMillis)

fun runDelayedOnUiThread(delayMillis: Long, action: () -> Unit) = Handler(Looper.getMainLooper()).postDelayed(Runnable(action), delayMillis)

private fun isMainLooperAlive() = Looper.myLooper() == Looper.getMainLooper()