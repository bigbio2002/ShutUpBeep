package com.example.shutupbeep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shutupbeep.ui.theme.ShutUpBeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShutUpBeepTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val vm: VadViewModel = viewModel()
                    val lifecycleOwner = LocalLifecycleOwner.current

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) vm.onPermissionGranted()
                        else vm.onPermissionDenied()
                    }

                    // Auto-start listening if permission was already granted
                    LaunchedEffect(Unit) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            vm.onPermissionGranted()
                        }
                    }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) {
                                vm.stopListening()
                            } else if (event == Lifecycle.Event.ON_START) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission && (vm.status.value == AppStatus.IDLE)) {
                                    vm.onPermissionGranted()
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    VadScreen(
                        onRequestPermission = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                vm.onPermissionGranted()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        viewModel = vm,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
