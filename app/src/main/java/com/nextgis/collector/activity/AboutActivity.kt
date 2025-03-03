package com.nextgis.collector.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.nextgis.collector.BuildConfig
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ActivityAboutBinding
import com.nextgis.maplibui.util.ControlHelper.highlightText
import java.lang.String
import java.util.Calendar
import java.util.GregorianCalendar

class AboutActivity : BaseActivity(), OnPageChangeListener {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val mViewPager = binding.viewPager
        val adapter: PagerAdapter = AboutActivity.TabsAdapter(supportFragmentManager, this)
        mViewPager.setAdapter(adapter)
        mViewPager.addOnPageChangeListener(this)

        val tabLayout = binding.tabs
        tabLayout.setupWithViewPager(mViewPager)

        val calendar: Calendar = GregorianCalendar()
        val year = calendar[Calendar.YEAR]
        val copyrText = String.format(getString(R.string.copyright), year.toString())

        val txtCopyrightText = findViewById(R.id.copyright) as TextView
        txtCopyrightText.text = Html.fromHtml(copyrText)
        txtCopyrightText.movementMethod = LinkMovementMethod.getInstance()
    }


    class TabsAdapter(fm: FragmentManager?, activity : Activity) : FragmentPagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        val activity : Activity
        init {
            this.activity = activity
        }

        override fun getItem(position: Int): Fragment {
            when (position) {
                0 -> return ContactsFragment()
                1 -> return AboutFragment()
            }
            return Fragment()
        }

        private fun getContextFromFragment(fragment: Fragment): Context {
            return fragment.requireContext()
        }

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence? {

            when (position) {
                0 -> return  activity.getString(R.string.action_support)
                1 -> return activity.getString(R.string.action_about)
            }
            return activity.getString(R.string.action_about)
        }
    }


    class ContactsFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val context = context
            val v = View.inflate(context, R.layout.fragment_contacts, null)

            val telegram = v.findViewById<View>(R.id.telegram) as TextView
            highlightText(telegram)
            telegram.setOnClickListener {
                try {
                    val telegramPart = getString(R.string.telegram_prompt)
                    val telegram = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("tg://resolve?domain=$telegramPart")
                    )
                    startActivity(telegram)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.not_installed, Toast.LENGTH_SHORT).show()
                }
            }

            return v
        }
    }


    class AboutFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle? ): View? {
            val activity: Activity? = activity as Activity?
            val v = View.inflate(activity, R.layout.fragment_about, null)

            val appName = v.findViewById<View>(R.id.app_name) as TextView
            appName.setText(getString(R.string.APP_NAME))

            val txtVersion = v.findViewById<View>(R.id.app_version) as TextView
            txtVersion.text =
                (("v. " + BuildConfig.VERSION_NAME).toString() + " (rev. " + BuildConfig.VERSION_CODE).toString() + ")"

            v.findViewById<View>(R.id.creditsInto).setOnClickListener {
                val builder: AlertDialog =
                    AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.credits_intro)
                        .setMessage(R.string.credits)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                val message = builder.findViewById<View>(android.R.id.message) as TextView?
                if (message != null) {
                    message.movementMethod = LinkMovementMethod.getInstance()
                    message.linksClickable = true
                }
            }

            val contacts = v.findViewById<View>(R.id.contacts) as TextView
            contacts.movementMethod = LinkMovementMethod.getInstance()

//            val createGis = v.findViewById<View>(R.id.create_gis) as TextView
//            val copyrText = String.format(getString(R.string.tracker_details_newngw))
//            createGis.text = Html.fromHtml(copyrText)
//            createGis.movementMethod = LinkMovementMethod.getInstance()

            return v
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
    }

    override fun onPageScrollStateChanged(state: Int) {
    }
}