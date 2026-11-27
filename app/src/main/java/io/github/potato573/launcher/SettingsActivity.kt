package io.github.potato573.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<androidx.preference.Preference>("set_default")?.setOnPreferenceClickListener {
                openHomeSettings()
                true
            }
            findPreference<androidx.preference.Preference>("reset_layout")?.setOnPreferenceClickListener {
                Prefs(requireContext()).resetLayout()
                Toast.makeText(requireContext(), R.string.reset_done, Toast.LENGTH_SHORT).show()
                true
            }
        }

        private fun openHomeSettings() {
            val intents = listOf(
                Intent(Settings.ACTION_HOME_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            for (i in intents) {
                try {
                    startActivity(i)
                    return
                } catch (_: Exception) {
                }
            }
        }
    }
}
