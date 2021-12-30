package com.robertogianassi.pennydrop

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeId = when (prefs.getString("theme", "AppTheme")) {
            "Crew" -> R.style.Crew
            "FTD" -> R.style.FTD
            "GPG" -> R.style.GPG
            "Hazel" -> R.style.Hazel
            "Kotlin" -> R.style.Kotlin
            else -> R.style.AppTheme
        }
        setTheme(themeId) // must be called before setContentView
        val nightMode = when (prefs.getString("themeMode", "")) {
            "Light" -> AppCompatDelegate.MODE_NIGHT_NO
            "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode) // must be called before setContentView
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.containerFragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        val appBarConfiguration = AppBarConfiguration(bottomNav.menu)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (this::navController.isInitialized) {
            item.onNavDestinationSelected(navController) ||
                    super.onOptionsItemSelected(item)
        } else false

    override fun onSupportNavigateUp(): Boolean =
        (this::navController.isInitialized && navController.navigateUp()) || super.onSupportNavigateUp()
}
