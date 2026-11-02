package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AttendanceViewModel

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var viewModelInstance: AttendanceViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        setContent {
            MyApplicationTheme {
                val viewModel: AttendanceViewModel = viewModel()
                viewModelInstance = viewModel
                LaunchedEffect(Unit) {
                    handleIncomingIntent(intent, viewModel)
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.let { handleIncomingIntent(intent, it) }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?, viewModel: AttendanceViewModel) {
        if (incomingIntent == null) return
        val action = incomingIntent.action ?: return
        if (Intent.ACTION_VIEW != action && Intent.ACTION_SEND != action) return

        val uri: Uri? = incomingIntent.data
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            })
            ?: incomingIntent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        val extraText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)

        // Clear action so subsequent screen rotations or configuration changes don't re-trigger
        incomingIntent.action = null

        lifecycleScope.launch {
            var handled = false
            if (uri != null) {
                val content = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText().trim().removePrefix("\uFEFF")
                        }
                    } catch (e: Exception) {
                        if (uri.scheme == "file" && uri.path != null) {
                            try {
                                val f = java.io.File(uri.path!!)
                                if (f.exists()) f.readText(Charsets.UTF_8).trim().removePrefix("\uFEFF") else null
                            } catch (_: Exception) { null }
                        } else null
                    }
                }

                if (!content.isNullOrBlank()) {
                    val result = withContext(Dispatchers.IO) {
                        viewModel.importConfigFromJson(content)
                    }
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        result.message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    handled = true
                }
            }

            if (!handled && !extraText.isNullOrBlank()) {
                val result = withContext(Dispatchers.IO) {
                    viewModel.importConfigFromJson(extraText.trim())
                }
                android.widget.Toast.makeText(
                    this@MainActivity,
                    result.message,
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Otomatis simpan data saat meninggalkan aplikasi
        viewModelInstance?.autoSaveData()
    }

    override fun onStop() {
        super.onStop()
        // Otomatis simpan data saat aplikasi berhenti / ke background
        viewModelInstance?.autoSaveData()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notifikasi KIKAV 2/JRTR"
            val descriptionText = "Peringatan Siaga, Alarm Militer, dan Perintah Komando"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("kikav_military_alerts", name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
