package com.kismet.mobile

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kismet.mobile.databinding.ActivityMainBinding
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: KismetViewModel by viewModels { androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application) }
    val windowLayoutViewModel: WindowLayoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
            if (state is ConnectionState.Disconnected) {
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
