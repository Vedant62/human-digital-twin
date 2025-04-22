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
import androidx.health.services.client.data.SampleDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import com.google.common.util.concurrent.ListenableFuture

class HealthServicesTest(private val context: Context) {
    private val TAG = "HealthServicesTest"
    private val healthClient = HealthServices.getClient(context)
    private val measureClient = healthClient.measureClient
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Check if heart rate measurement is supported on this device
    suspend fun checkHeartRateSupport(): Boolean {
        val capabilities = measureClient.getCapabilitiesAsync().await()
        val supportsHeartRate = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
        Log.d(TAG, "Heart rate support: $supportsHeartRate")
        return supportsHeartRate
    }

    // Register for heart rate updates
    fun registerForHeartRateData(onHeartRateReceived: (Double, String) -> Unit) {
        val heartRateCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                if (availability is DataTypeAvailability) {
                    Log.d(TAG, "Availability changed for $dataType: $availability")
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                // Process the heart rate data
                data.getData(DataType.HEART_RATE_BPM).let { heartRateData ->
                    for (heartRate in heartRateData) {
                        if (heartRate is SampleDataPoint) {
                            val bpm = heartRate.value
                            val accuracyString = heartRate.accuracy?.toString() ?: "UNKNOWN"
                            
                            Log.d(TAG, "Heart rate received: $bpm BPM, accuracy: $accuracyString")
                            onHeartRateReceived(bpm, accuracyString)
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            try {
                // Register the callback
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)
                Log.d(TAG, "Successfully registered for heart rate updates")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering for heart rate data", e)
            }
        }
    }

    // Unregister the heart rate callback when done
    fun unregisterHeartRateCallback(callback: MeasureCallback) {
        coroutineScope.launch {
            try {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback).await()
                Log.d(TAG, "Successfully unregistered heart rate callback")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering heart rate callback", e)
            }
        }
    }
} 