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
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        handleIntent(intent)
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
                // If we are at apiKeyFragment, we might need to wait or navigate to main first
                // but assuming the user has already set it up.
                when (action) {
                    ACTION_QUICK_SCAN -> navController.navigate(R.id.cameraFragment)
                    ACTION_QUICK_BARCODE -> navController.navigate(R.id.barcodeScanFragment)
                    ACTION_QUICK_SEARCH -> navController.navigate(R.id.searchFragment)
                    ACTION_OPEN_LOG -> navController.navigate(R.id.mainFragment, bundleOf("start_tab" to 1))
                }
            } catch (e: Exception) {
                // NavController might not be initialized yet or destination not reachable
            }
        }
    }
}
