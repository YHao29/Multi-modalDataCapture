package com.lannooo.audiocenter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.lannooo.audiocenter.client.ClientService
import com.lannooo.audiocenter.ui.theme.AudioCenterTheme

class MainActivity : ComponentActivity() {
    private lateinit var myViewModel: AudioCenterViewModel
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. start and bind the client foreground service here
                Log.i(TAG, "Permission granted in MainActivity")

                startClientService()
                bindClientService()
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                Log.e(TAG, "Permission denied")
                // show a toast or snackbar to inform the user
            }
        }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume Called")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart Called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause Called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy Called")
        if (myViewModel.binder != null) {
            Log.i(TAG, "Unbinding service")
            unbindService(myViewModel.connection)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop Called")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart Called")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate Called")

        enableEdgeToEdge()

        val viewModel: AudioCenterViewModel by viewModels()
        myViewModel = viewModel

        setContent {
            AudioCenterTheme(darkTheme = true) {
                AudioCenterApp()
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // start and bind a foreground service,
            // only does this once in the lifecycle of application
            startClientService()
            bindClientService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun bindClientService() {
        Log.i(TAG, "Binding service")
        bindService(
            Intent(this, ClientService::class.java),
            myViewModel.connection, BIND_AUTO_CREATE
        )
    }

    private fun startClientService() {
        Log.i(TAG, "Starting foreground service")
        startForegroundService(Intent(this, ClientService::class.java))
    }

    private fun printBasicInformation(mainActivity: MainActivity) {
        Log.i(TAG, "Device: ${Build.DEVICE}")
        Log.i(TAG, "Model: ${Build.MODEL}")
        Log.i(TAG, "Manufacturer: ${Build.MANUFACTURER}")
        Log.i(TAG, "Brand: ${Build.BRAND}")
        Log.i(TAG, "Display: ${Build.DISPLAY}")
        Log.i(TAG, "SDK: ${Build.VERSION.SDK_INT}")
        Log.i(TAG, "Version Code: ${Build.VERSION.SDK_INT}")
        val androidId = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        Log.i(TAG, "Android ID: $androidId")
    }


    companion object {
        // define a constant TAG string for logging
        const val TAG = "Main"
    }
}


