package pl.kserocki.scratchticket.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Normalised device orientation relative to gravity.
 *
 * @param roll -1 when the left edge points down, +1 when the right edge points down.
 * @param pitch -1 when the top edge leans towards the user, +1 when it leans away,
 * re-centred around a natural hand-held angle.
 */
@Immutable
data class DeviceTilt(val roll: Float, val pitch: Float) {
    companion object {
        val Flat = DeviceTilt(0f, 0f)
    }
}

private const val NATURAL_HOLD_PITCH = 0.55f
private const val SMOOTHING = 0.18f

/**
 * Streams [DeviceTilt] from the gravity sensor (falling back to the raw accelerometer) while the
 * host lifecycle is resumed. Readings are low-pass filtered so rendering stays calm.
 */
@Composable
fun rememberDeviceTilt(): State<DeviceTilt> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(DeviceTilt.Flat) }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, _) = event.values
                val targetRoll = (-x / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val targetPitch = (y / SensorManager.GRAVITY_EARTH - NATURAL_HOLD_PITCH)
                    .coerceIn(-1f, 1f)
                val current = tilt.value
                tilt.value = DeviceTilt(
                    roll = current.roll + (targetRoll - current.roll) * SMOOTHING,
                    pitch = current.pitch + (targetPitch - current.pitch) * SMOOTHING,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sensor?.let {
                    sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }
    return tilt
}
