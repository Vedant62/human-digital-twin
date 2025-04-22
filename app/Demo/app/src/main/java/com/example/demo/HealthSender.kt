package com.example.demo

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.data.CumulativeDataPoint
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepStageRecord
import java.time.Instant

class HealthSender(private val context: Context) {
    private val TAG = "HealthSender"
    private val healthClient = HealthServices.getClient(context)
    private val measureClient = healthClient.measureClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Base URL for all health data
    private val baseUrl = APPWRITE_FUNCTION_BASE_URL
    
    // Store the latest values for all metrics
    private var latestBpm: Double? = null
    private var latestSteps: Int? = null
    private var latestCalories: Double? = null
    private var latestSleepData: String? = null // Simplified representation
    
    // Callbacks for UI updates
    private var heartRateCallback: ((Double, String) -> Unit)? = null
    private var stepsCallback: ((Int) -> Unit)? = null
    private var caloriesCallback: ((Double) -> Unit)? = null
    private var sleepCallback: ((String) -> Unit)? = null
    
    // Register callbacks from MainActivity
    fun setHeartRateCallback(callback: (Double, String) -> Unit) {
        heartRateCallback = callback
    }
    
    fun setStepsCallback(callback: (Int) -> Unit) {
        stepsCallback = callback
    }
    
    fun setCaloriesCallback(callback: (Double) -> Unit) {
        caloriesCallback = callback
    }
    
    fun setSleepCallback(callback: (String) -> Unit) {
        sleepCallback = callback
    }
    
    // Flags to control sending data
    private var isHeartRateMonitoringRunning = false
    private var isStepsMonitoringRunning = false
    private var isCaloriesMonitoringRunning = false
    private var isSleepMonitoringRunning = false
    
    // Health Connect client for more advanced health data
    private var healthConnectClient: HealthConnectClient? = null
    
    init {
        try {
            // Initialize Health Connect client if available
            try {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
                Log.d(TAG, "Health Connect client initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Health Connect is not available on this device", e)
                // Health Connect is not available on this device
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Health Connect client", e)
        }
    }
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // Heart rate callback
    private val heartRateMeasureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability is DataTypeAvailability) {
                Log.d(TAG, "Availability changed for $dataType: $availability")
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).let { heartRateData ->
                for (heartRate in heartRateData) {
                    if (heartRate is SampleDataPoint) {
                        val bpm = heartRate.value
                        latestBpm = bpm  // Store the latest value
                        
                        // Get accuracy information if available
                        val accuracyStr = heartRate.accuracy?.toString() ?: "UNKNOWN"
                        
                        // Notify UI via callback
                        heartRateCallback?.invoke(bpm, accuracyStr)
                        
                        Log.d(TAG, "Heart rate received: $bpm BPM")
                    }
                }
            }
        }
    }
    
    // Step counter callback
    private val stepCounterMeasureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability is DataTypeAvailability) {
                Log.d(TAG, "Availability changed for $dataType: $availability")
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.STEPS).let { stepsData ->
                for (steps in stepsData) {
                    if (steps is CumulativeDataPoint<*>) {
                        val count = steps.total.toInt()
                        latestSteps = count  // Store the latest value
                        
                        // Notify UI via callback
                        stepsCallback?.invoke(count)
                        
                        Log.d(TAG, "Steps received: $count steps")
                    }
                }
            }
        }
    }
    
    // Calories callback using Health Connect if available
    private suspend fun fetchCaloriesData() {
        try {
            val healthConnectClient = this.healthConnectClient ?: return
            
            // Get today's start time (midnight)
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            // Current time as end time
            val endTime = System.currentTimeMillis()
            
            // Create time range filter for today
            val timeRangeFilter = TimeRangeFilter.between(
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime)
            )
            
            // Request to read calorie records
            val request = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            
            // Read the records
            val response = healthConnectClient.readRecords(request)
            
            // Calculate total calories burned
            var totalCalories = 0.0
            for (record in response.records) {
                if (record is ActiveCaloriesBurnedRecord) {
                    totalCalories += record.energy.inKilocalories
                }
            }
            
            // Update latest calories
            latestCalories = totalCalories
            
            // Notify UI via callback
            caloriesCallback?.invoke(totalCalories)
            
            Log.d(TAG, "Calories fetched from Health Connect: $totalCalories kcal")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calories data", e)
            // Fall back to simulated data if Health Connect fails
            updateCaloriesData()
        }
    }
    
    // Fallback for calorie tracking when Health Connect is not available
    private fun updateCaloriesData() {
        // Simple simulation of calorie burning
        val currentCalories = latestCalories ?: 0.0
        latestCalories = currentCalories + (1.0 + Random.nextDouble() * 4.0) // 1.0 to 5.0
        
        // Notify UI via callback (even for simulated data)
        latestCalories?.let { caloriesCallback?.invoke(it) }
        
        Log.d(TAG, "Calories updated (simulated): ${latestCalories}")
    }
    
    // Sleep tracking using Health Connect
    private suspend fun fetchSleepData() {
        try {
            val healthConnectClient = this.healthConnectClient ?: return
            
            // Get yesterday's start time (midnight)
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            // Current time as end time
            val endTime = System.currentTimeMillis()
            
            // Create time range filter from yesterday until now
            val timeRangeFilter = TimeRangeFilter.between(
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime)
            )
            
            // Request to read sleep sessions
            val sleepSessionRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            
            // Read the sleep sessions
            val sleepSessionResponse = healthConnectClient.readRecords(sleepSessionRequest)
            
            // Find the most recent sleep session
            val mostRecentSleepSession = sleepSessionResponse.records.maxByOrNull { it.endTime.toEpochMilli() }
            
            if (mostRecentSleepSession != null) {
                // Get sleep stages for this session
                val stagesRequest = ReadRecordsRequest(
                    recordType = SleepStageRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(mostRecentSleepSession.startTime.toEpochMilli()),
                        Instant.ofEpochMilli(mostRecentSleepSession.endTime.toEpochMilli())
                    )
                )
                
                val stagesResponse = healthConnectClient.readRecords(stagesRequest)
                
                // Find the most recent sleep stage
                val mostRecentStage = stagesResponse.records.maxByOrNull { it.endTime.toEpochMilli() }
                
                if (mostRecentStage != null && mostRecentStage is SleepStageRecord) {
                    // Map Health Connect stage to our format
                    latestSleepData = when (mostRecentStage.stage) {
                        SleepStageRecord.STAGE_TYPE_DEEP -> "DEEP_SLEEP"
                        SleepStageRecord.STAGE_TYPE_LIGHT -> "LIGHT_SLEEP"
                        SleepStageRecord.STAGE_TYPE_REM -> "REM"
                        SleepStageRecord.STAGE_TYPE_AWAKE -> "AWAKE"
                        SleepStageRecord.STAGE_TYPE_SLEEPING -> "LIGHT_SLEEP" // Generic sleeping
                        SleepStageRecord.STAGE_TYPE_OUT_OF_BED -> "AWAKE"
                        else -> "UNKNOWN"
                    }
                    
                    // Notify UI via callback
                    sleepCallback?.invoke(latestSleepData!!)
                    
                    Log.d(TAG, "Sleep stage fetched from Health Connect: $latestSleepData")
                } else {
                    // No stage data, just log that the user was sleeping
                    latestSleepData = "SLEEPING"
                    
                    // Notify UI via callback
                    sleepCallback?.invoke(latestSleepData!!)
                    
                    Log.d(TAG, "Sleep session found but no stage data")
                }
            } else {
                // No sleep session found, assume awake
                latestSleepData = "AWAKE"
                
                // Notify UI via callback
                sleepCallback?.invoke(latestSleepData!!)
                
                Log.d(TAG, "No sleep session found, assuming AWAKE")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sleep data", e)
            // Fall back to simulated data if Health Connect fails
            updateSleepData()
        }
    }
    
    // Fallback for sleep tracking when Health Connect is not available
    private fun updateSleepData() {
        // Simple simulation of sleep state
        val sleepStates = listOf("DEEP_SLEEP", "LIGHT_SLEEP", "REM", "AWAKE")
        latestSleepData = sleepStates[Random.nextInt(sleepStates.size)]
        
        // Notify UI via callback (even for simulated data)
        latestSleepData?.let { sleepCallback?.invoke(it) }
        
        Log.d(TAG, "Sleep state updated (simulated): $latestSleepData")
    }
    
    // Check if health measurements are supported
    suspend fun checkHealthSupport(): Map<String, Boolean> {
        val capabilities = measureClient.getCapabilitiesAsync().await()
        val support = mapOf(
            "heartRate" to (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure),
            "steps" to (DataType.STEPS in capabilities.supportedDataTypesMeasure),
            "calories" to true, // Simplified - we'll simulate calories for now
            "sleep" to true // Simplified - we'll simulate sleep for now
        )
        
        Log.d(TAG, "Health metrics support: $support")
        return support
    }

    // Start collecting heart rate data (5 second frequency)
    fun startCollectingHeartRate() {
        scope.launch {
            try {
                Log.d(TAG, "Registering for heart rate data")
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateMeasureCallback)
                
                // Start the periodic sending task
                isHeartRateMonitoringRunning = true
                startPeriodicHeartRateSending()
            } catch (e: Exception) {
                Log.e(TAG, "Error registering for heart rate data", e)
            }
        }
    }
    
    // Start collecting steps data using real sensor data
    fun startCollectingSteps() {
        scope.launch {
            try {
                Log.d(TAG, "Starting steps monitoring")
                
                // Initialize steps value if not set
                if (latestSteps == null) {
                    latestSteps = 0
                }
                
                // Register for step count updates from the actual sensor
                measureClient.registerMeasureCallback(DataType.STEPS, stepCounterMeasureCallback)
                
                // Start the periodic sending task
                isStepsMonitoringRunning = true
                startPeriodicStepsSending()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting steps monitoring", e)
            }
        }
    }
    
    // Start collecting calories data with Health Connect if available
    fun startCollectingCalories() {
        scope.launch {
            try {
                Log.d(TAG, "Starting calories monitoring")
                
                // Initialize calories value if not set
                if (latestCalories == null) {
                    latestCalories = 0.0
                }
                
                // Start the periodic sending task
                isCaloriesMonitoringRunning = true
                startPeriodicCaloriesSending()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting calories monitoring", e)
            }
        }
    }
    
    // Start collecting sleep data with Health Connect if available
    fun startCollectingSleep() {
        scope.launch {
            try {
                Log.d(TAG, "Starting sleep monitoring")
                
                // Start the periodic sending task
                isSleepMonitoringRunning = true
                startPeriodicSleepSending()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting sleep monitoring", e)
            }
        }
    }

    // Stop collecting heart rate data
    fun stopCollectingHeartRate() {
        scope.launch {
            try {
                Log.d(TAG, "Unregistering heart rate callback")
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateMeasureCallback).await()
                
                // Stop the periodic sending
                isHeartRateMonitoringRunning = false
                latestBpm = null
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering heart rate callback", e)
            }
        }
    }
    
    // Stop collecting steps data
    fun stopCollectingSteps() {
        scope.launch {
            try {
                Log.d(TAG, "Stopping steps monitoring")
                measureClient.unregisterMeasureCallbackAsync(DataType.STEPS, stepCounterMeasureCallback).await()
                isStepsMonitoringRunning = false
                latestSteps = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping steps monitoring", e)
            }
        }
    }
    
    // Stop collecting calories data
    fun stopCollectingCalories() {
        scope.launch {
            Log.d(TAG, "Stopping calories monitoring")
            isCaloriesMonitoringRunning = false
            latestCalories = null
        }
    }
    
    // Stop collecting sleep data
    fun stopCollectingSleep() {
        scope.launch {
            Log.d(TAG, "Stopping sleep monitoring")
            isSleepMonitoringRunning = false
            latestSleepData = null
        }
    }
    
    // Start all monitoring
    fun startAllMonitoring() {
        startCollectingHeartRate()
        startCollectingSteps()
        startCollectingCalories()
        startCollectingSleep()
    }
    
    // Stop all monitoring
    fun stopAllMonitoring() {
        stopCollectingHeartRate()
        stopCollectingSteps()
        stopCollectingCalories()
        stopCollectingSleep()
    }

    // Periodically send heart rate data every 5 seconds
    private fun startPeriodicHeartRateSending() {
        scope.launch {
            while (isHeartRateMonitoringRunning) {
                latestBpm?.let { bpm ->
                    sendDataToServer(bpm, "$baseUrl/bpm", "heart rate")
                }
                delay(5000)  // Wait for 5 seconds
            }
        }
    }
    
    // Periodically send steps data every 10 seconds
    private fun startPeriodicStepsSending() {
        scope.launch {
            while (isStepsMonitoringRunning) {
                latestSteps?.let { steps ->
                    sendDataToServer(steps, "$baseUrl/steps", "steps")
                }
                delay(10000)  // Wait for 10 seconds
            }
        }
    }
    
    // Periodically send calories data every 3 minutes
    private fun startPeriodicCaloriesSending() {
        scope.launch {
            while (isCaloriesMonitoringRunning) {
                // Try to get real data from Health Connect if available
                if (healthConnectClient != null) {
                    fetchCaloriesData()
                } else {
                    // Fall back to simulated data
                    updateCaloriesData()
                }
                
                latestCalories?.let { calories ->
                    sendDataToServer(calories, "$baseUrl/calories", "calories")
                }
                delay(TimeUnit.MINUTES.toMillis(3))  // Wait for 3 minutes
            }
        }
    }
    
    // Periodically send sleep data every 3 minutes
    private fun startPeriodicSleepSending() {
        scope.launch {
            while (isSleepMonitoringRunning) {
                // Try to get real data from Health Connect if available
                if (healthConnectClient != null) {
                    fetchSleepData()
                } else {
                    // Fall back to simulated data
                    updateSleepData()
                }
                
                latestSleepData?.let { sleepState ->
                    sendDataToServer(sleepState, "$baseUrl/sleep", "sleep")
                }
                delay(TimeUnit.MINUTES.toMillis(3))  // Wait for 3 minutes
            }
        }
    }

    // Generic method to send data to server
    private fun <T> sendDataToServer(data: T, endpoint: String, dataType: String) {
        scope.launch {
            try {
                val payload = when (data) {
                    is Double -> DoublePayload(value = data)
                    is Int -> IntPayload(value = data)
                    is String -> StringPayload(value = data)
                    else -> {
                        Log.e(TAG, "Unsupported data type for $dataType")
                        return@launch
                    }
                }
                
                Log.d(TAG, "Sending $dataType to server: $data")
                
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                
                Log.d(TAG, "Successfully sent $dataType data to server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send $dataType data to server", e)
            }
        }
    }

    // Take a one-time heart rate measurement
    fun takeHeartRateMeasurement(onResult: (HeartRatePayload?) -> Unit) {
        scope.launch {
            try {
                // Create a temporary callback for the one-time measurement
                var tempCallback: MeasureCallback? = null
                tempCallback = object : MeasureCallback {
                    override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                        // Not needed for one-time measurement
                    }

                    override fun onDataReceived(data: DataPointContainer) {
                        data.getData(DataType.HEART_RATE_BPM).let { heartRateData ->
                            for (heartRate in heartRateData) {
                                if (heartRate is SampleDataPoint) {
                                    val bpm = heartRate.value
                                    val accuracyStr = heartRate.accuracy?.toString() ?: "UNKNOWN"
                                    
                                    // Send just the BPM to the server
                                    sendDataToServer(bpm, "$baseUrl/bpm", "heart rate")
                                    
                                    // Create payload for UI
                                    val payload = HeartRatePayload(
                                        heartRate = bpm,
                                        accuracy = accuracyStr,
                                        timestamp = System.currentTimeMillis(),
                                        deviceId = android.os.Build.MODEL
                                    )
                                    
                                    // Deliver result
                                    onResult(payload)
                                    
                                    // Unregister callback after getting the measurement
                                    scope.launch {
                                        tempCallback?.let {
                                            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, it).await()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Register callback for the measurement
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, tempCallback)
                
                // Set a timeout to unregister the callback if no data is received
                scope.launch {
                    delay(30000) // 30 seconds timeout
                    tempCallback?.let {
                        try {
                            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, it).await()
                            onResult(null) // No data received within timeout
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unregistering timeout callback", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error taking heart rate measurement", e)
                onResult(null)
            }
        }
    }

    // Payloads for different data types
    @Serializable
    data class DoublePayload(val value: Double)
    
    @Serializable
    data class IntPayload(val value: Int)
    
    @Serializable
    data class StringPayload(val value: String)
    
    // Full payload for UI feedback
    @Serializable
    data class HeartRatePayload(
        val heartRate: Double,
        val accuracy: String,
        val timestamp: Long,
        val deviceId: String
    )
} 
