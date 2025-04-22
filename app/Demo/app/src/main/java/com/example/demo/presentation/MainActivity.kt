/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.demo.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.demo.HealthSender
import com.example.demo.HealthServicesTest
import com.example.demo.presentation.theme.DemoTheme
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var healthServicesTest: HealthServicesTest
    private lateinit var healthSender: HealthSender
    
    // Health metrics states
    private var heartRate = mutableStateOf("--")
    private var heartRateAccuracy = mutableStateOf("--")
    private var steps = mutableStateOf("--")
    private var calories = mutableStateOf("--")
    private var sleepState = mutableStateOf("--")
    
    // Monitoring states
    private var isMonitoring = mutableStateOf(false)
    private var isMeasuring = mutableStateOf(false)
    private var statusMessage = mutableStateOf("")

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            checkHealthSupport()
        } else {
            Log.d(TAG, "Some permissions denied")
            statusMessage.value = "Body sensors permission required"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize Health Services
        healthServicesTest = HealthServicesTest(this)
        healthSender = HealthSender(this)

        // Check and request permissions
        requestPermissions()

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp(
                heartRate = heartRate.value,
                heartRateAccuracy = heartRateAccuracy.value,
                steps = steps.value,
                calories = calories.value,
                sleepState = sleepState.value,
                isMonitoring = isMonitoring.value,
                isMeasuring = isMeasuring.value,
                statusMessage = statusMessage.value,
                onStartStopClick = { toggleHealthMonitoring() },
                onMeasureClick = { takeSingleMeasurement() }
            )
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND,
            Manifest.permission.INTERNET,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        val permissionsToRequest = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            // All permissions are already granted
            checkHealthSupport()
        }
    }

    private fun checkHealthSupport() {
        lifecycleScope.launch {
            val supportedFeatures = healthSender.checkHealthSupport()
            
            if (supportedFeatures["heartRate"] == true) {
                statusMessage.value = "Ready to measure health data"
            } else {
                statusMessage.value = "Heart rate not supported on this device"
            }
            
            // Log which features are supported
            Log.d(TAG, "Supported features: $supportedFeatures")
        }
    }

    private fun toggleHealthMonitoring() {
        if (isMonitoring.value) {
            // Stop monitoring
            isMonitoring.value = false
            healthSender.stopAllMonitoring()
            statusMessage.value = "Monitoring stopped"
        } else {
            // Start monitoring
            isMonitoring.value = true
            statusMessage.value = "Monitoring health data..."
            
            // Set up callbacks to update UI with real sensor data
            setupHealthDataCallbacks()
            
            // Start all health metrics monitoring
            healthSender.startAllMonitoring()
        }
    }
    
    // Set up callbacks to receive real sensor data from HealthSender
    private fun setupHealthDataCallbacks() {
        // Heart rate callback
        healthSender.setHeartRateCallback { bpm, accuracy ->
            heartRate.value = String.format("%.1f", bpm)
            heartRateAccuracy.value = accuracy
        }
        
        // Steps callback
        healthSender.setStepsCallback { steps ->
            this.steps.value = steps.toString()
        }
        
        // Calories callback
        healthSender.setCaloriesCallback { calories ->
            this.calories.value = String.format("%.1f", calories)
        }
        
        // Sleep callback
        healthSender.setSleepCallback { sleepState ->
            this.sleepState.value = sleepState
        }
    }
    
    private fun takeSingleMeasurement() {
        if (isMeasuring.value) return
        
        isMeasuring.value = true
        heartRate.value = "--"
        heartRateAccuracy.value = "--"
        statusMessage.value = "Taking measurement..."
        
        healthSender.takeHeartRateMeasurement { payload ->
            isMeasuring.value = false
            
            if (payload != null) {
                heartRate.value = String.format("%.1f", payload.heartRate)
                heartRateAccuracy.value = payload.accuracy
                statusMessage.value = "Measurement complete and sent"
            } else {
                statusMessage.value = "Failed to get measurement"
            }
        }
    }
}

@Composable
fun WearApp(
    heartRate: String,
    heartRateAccuracy: String,
    steps: String,
    calories: String,
    sleepState: String,
    isMonitoring: Boolean,
    isMeasuring: Boolean,
    statusMessage: String,
    onStartStopClick: () -> Unit,
    onMeasureClick: () -> Unit
) {
    DemoTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Heart Rate Section
                MetricCard(
                    title = "Heart Rate",
                    value = heartRate,
                    unit = "BPM",
                    subtitle = "Accuracy: $heartRateAccuracy"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Steps Section
                MetricCard(
                    title = "Steps",
                    value = steps,
                    unit = "",
                    subtitle = "Today"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Calories Section
                MetricCard(
                    title = "Calories",
                    value = calories,
                    unit = "kcal",
                    subtitle = "Burned today"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Sleep Section
                MetricCard(
                    title = "Sleep",
                    value = sleepState,
                    unit = "",
                    subtitle = "Current state"
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = statusMessage,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.secondary,
                    style = MaterialTheme.typography.caption1,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Button(
                    onClick = onMeasureClick,
                    enabled = !isMeasuring && !isMonitoring,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Single HR Measure",
                        fontSize = 12.sp
                    )
                }
                
                Chip(
                    onClick = onStartStopClick,
                    enabled = !isMeasuring,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (isMonitoring) 
                            MaterialTheme.colors.secondary 
                        else 
                            MaterialTheme.colors.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    label = { 
                        Text(
                            text = if (isMonitoring) "Stop Monitoring" else "Start Health Monitoring",
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String
) {
    Card(
        onClick = { /* No action */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2,
                fontSize = 12.sp
            )
            
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = value,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.title1,
                    fontSize = 18.sp
                )
                
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
            
            Text(
                text = subtitle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.secondary,
                style = MaterialTheme.typography.caption2,
                fontSize = 9.sp
            )
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(
        heartRate = "72.5",
        heartRateAccuracy = "MEDIUM",
        steps = "3452",
        calories = "245.3",
        sleepState = "DEEP_SLEEP",
        isMonitoring = false,
        isMeasuring = false,
        statusMessage = "Ready to measure",
        onStartStopClick = {},
        onMeasureClick = {}
    )
}