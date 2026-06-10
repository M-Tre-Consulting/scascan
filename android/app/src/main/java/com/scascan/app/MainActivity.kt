package com.scascan.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.os.bundleOf
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.android.material.color.DynamicColors
import com.scascan.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_QUICK_SCAN = "com.scascan.app.ACTION_QUICK_SCAN"
        const val ACTION_QUICK_BARCODE = "com.scascan.app.ACTION_QUICK_BARCODE"
        const val ACTION_QUICK_SEARCH = "com.scascan.app.ACTION_QUICK_SEARCH"
        const val ACTION_OPEN_LOG = "com.scascan.app.ACTION_OPEN_LOG"
        const val ACTION_SHOW_ANALYSIS = "com.scascan.app.ACTION_SHOW_ANALYSIS"
        const val EXTRA_FACTS_JSON = "extra_facts_json"
    }

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(permission)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        
        // Wait for NavController to be ready if needed, 
        // but typically we can post to the root view
        binding.root.post {
            try {
                val navController = findNavController(R.id.nav_host_fragment)
                
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()

                when (action) {
                    ACTION_QUICK_SCAN -> navController.navigate(R.id.cameraFragment)
                    ACTION_QUICK_BARCODE -> navController.navigate(R.id.barcodeScanFragment)
                    ACTION_QUICK_SEARCH -> navController.navigate(R.id.searchFragment)
                    ACTION_OPEN_LOG -> navController.navigate(R.id.mainFragment, bundleOf("start_tab" to 1), navOptions)
                    ACTION_SHOW_ANALYSIS -> {
                        val json = intent.getStringExtra(EXTRA_FACTS_JSON)
                        if (json != null) {
                            val facts = com.google.gson.Gson().fromJson(json, com.scascan.app.data.model.NutritionFacts::class.java)
                            navController.navigate(R.id.mainFragment, bundleOf("pending_facts" to facts), navOptions)
                        }
                    }
                }
            } catch (e: Exception) {
                // NavController might not be initialized yet or destination not reachable
            }
        }
    }
}
