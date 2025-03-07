package com.ruble.jmanga

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ruble.jmanga.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment.newInstance())
                    true
                }
                R.id.navigation_explore -> {
                    loadFragment(ExploreFragment.newInstance())
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment.newInstance())
                    true
                }
                else -> false
            }
        }

        // 默认显示首页
        if (savedInstanceState == null) {
            loadFragment(HomeFragment.newInstance())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
} 