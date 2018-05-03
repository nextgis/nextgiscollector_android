package com.nextgis.collector.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.nextgis.collector.R
import com.pawegio.kandroid.longToast
import com.pawegio.kandroid.startActivity

class IntroActivity : AppIntro() {
    companion object {
        val INTRO_SHOWN = "intro_shown"
    }

    private val PERMISSIONS_CODE = 17

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
        val permissions = arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.WRITE_SYNC_SETTINGS)
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var granted = requestCode == PERMISSIONS_CODE
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(INTRO_SHOWN, true).apply()
            startActivity<ProjectListActivity>()
        } else
            longToast(R.string.permission_denied)
    }
}