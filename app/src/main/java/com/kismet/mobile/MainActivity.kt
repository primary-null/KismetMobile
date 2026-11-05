package com.kismet.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kismet.mobile.databinding.ActivityMainBinding
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: KismetViewModel by viewModels { androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application) }
    val windowLayoutViewModel: WindowLayoutViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Do not crash if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            com.kismet.mobile.ui.devices.OuiLookupManager.init(applicationContext)
        }
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        viewModel.connectionState.observe(this) { state ->
            val isConnected = state is ConnectionState.Connected
            binding.bottomNav.visibility = if (isConnected)
                android.view.View.VISIBLE else android.view.View.GONE
            if (state is ConnectionState.Disconnected &&
                navController.currentDestination?.id != R.id.connectFragment) {
                navController.navigate(
                    R.id.connectFragment,
                    null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .setLaunchSingleTop(true)
                        .build()
                )
            }
        }

        lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@MainActivity)
                .windowLayoutInfo(this@MainActivity)
                .collect { layoutInfo ->
                    val fold = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                    windowLayoutViewModel.update(fold)
                }
        }
    }
}
