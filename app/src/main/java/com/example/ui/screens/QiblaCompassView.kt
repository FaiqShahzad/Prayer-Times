package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QiblaCompassView(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }
    var sensorAccuracy by remember { mutableStateOf(0) }

    // Mecca coordinates (Kaaba)
    val meccaLat = 21.4225
    val meccaLng = 39.8262

    // Calculate Qibla direction bearing (True North is 0)
    val qiblaBearing = remember(userLatitude, userLongitude) {
        val latRad = Math.toRadians(userLatitude)
        val lngRad = Math.toRadians(userLongitude)
        val meccaLatRad = Math.toRadians(meccaLat)
        val meccaLngRad = Math.toRadians(meccaLng)

        val dLng = meccaLngRad - lngRad

        val y = sin(dLng)
        val x = cos(latRad) * sin(meccaLatRad) - sin(latRad) * cos(meccaLatRad) * cos(dLng)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        ((bearingDeg + 360.0) % 360.0).toFloat()
    }

    // Register sensor listener
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val orientationVals = FloatArray(3)

        val sensorListener = object : SensorEventListener {
            // Fallback parameters if TYPE_ROTATION_VECTOR is not available
            private val lastAccelerometer = FloatArray(3)
            private val lastMagnetometer = FloatArray(3)
            private var lastAccelerometerSet = false
            private var lastMagnetometerSet = false

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationVals)
                    val rawAzimuth = Math.toDegrees(orientationVals[0].toDouble()).toFloat()
                    azimuth = (rawAzimuth + 360f) % 360f
                } else {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
                        lastAccelerometerSet = true
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
                        lastMagnetometerSet = true
                    }

                    if (lastAccelerometerSet && lastMagnetometerSet) {
                        SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
                        SensorManager.getOrientation(rotationMatrix, orientationVals)
                        val rawAzimuth = Math.toDegrees(orientationVals[0].toDouble()).toFloat()
                        azimuth = (rawAzimuth + 360f) % 360f
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                sensorAccuracy = accuracy
            }
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(sensorListener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // Relative Qibla angle: Mecca bearing minus device direction
    val relativeQiblaAngle = (qiblaBearing - azimuth + 360f) % 360f
    val animatedRelativeAngle by animateFloatAsState(targetValue = relativeQiblaAngle, label = "QiblaAnimation")

    // Check if device is aligned with Qibla (within 3 degrees)
    val isAligned = abs(relativeQiblaAngle) < 3f || abs(relativeQiblaAngle - 360f) < 3f

    // Subtle vibration feedback on perfect alignment
    LaunchedEffect(isAligned) {
        if (isAligned) {
            triggerVibration(context)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Qibla Finder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Align the needle with the golden marker to locate Kaaba",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isAligned) {
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isAligned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 1. Static Outer ring (Mecca pointer / Alignment Ring)
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2

                // Center point
                drawCircle(
                    color = if (isAligned) Color(0xFFD4AF37) else primaryColor,
                    radius = 8.dp.toPx(),
                    center = center
                )
            }

            // 2. Rotating Compass Face (oriented with device's heading / azimuth)
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .rotate(-azimuth), // Rotate opposite to azimuth to point North UP
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    // Draw Cardinal points (N, E, S, W) on compass dial
                    val paint = android.graphics.Paint().apply {
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 14.sp.toPx()
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // North
                    paint.color = android.graphics.Color.RED
                    drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - radius + 22.dp.toPx(), paint)

                    // Other cardinal points
                    paint.color = android.graphics.Color.GRAY
                    drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + radius - 10.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("E", center.x + radius - 14.dp.toPx(), center.y + 5.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("W", center.x - radius + 14.dp.toPx(), center.y + 5.dp.toPx(), paint)

                    // Tick marks
                    for (i in 0 until 360 step 30) {
                        if (i % 90 == 0) continue
                        val angleRad = Math.toRadians(i.toDouble())
                        val startX = center.x + (radius - 12.dp.toPx()) * sin(angleRad).toFloat()
                        val startY = center.y - (radius - 12.dp.toPx()) * cos(angleRad).toFloat()
                        val endX = center.x + (radius - 4.dp.toPx()) * sin(angleRad).toFloat()
                        val endY = center.y - (radius - 4.dp.toPx()) * cos(angleRad).toFloat()
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }

            // 3. Rotating Mecca Needle (points to Mecca relative to device rotation)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .rotate(animatedRelativeAngle),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    // Draw golden qibla pointer arrow
                    val path = Path().apply {
                        moveTo(center.x, center.y - radius + 12.dp.toPx()) // Tip
                        lineTo(center.x - 12.dp.toPx(), center.y - radius + 36.dp.toPx()) // Left base
                        lineTo(center.x, center.y - radius + 28.dp.toPx()) // Inner notch
                        lineTo(center.x + 12.dp.toPx(), center.y - radius + 36.dp.toPx()) // Right base
                        close()
                    }

                    drawPath(
                        path = path,
                        color = if (isAligned) Color(0xFFD4AF37) else primaryColor
                    )

                    // Draw thin line from center to needle tip
                    drawLine(
                        color = if (isAligned) Color(0xFFD4AF37) else primaryColor.copy(alpha = 0.6f),
                        start = center,
                        end = Offset(center.x, center.y - radius + 28.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Aligned text or details
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAligned) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isAligned) "Aligned with Qibla!" else "Looking for Qibla...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isAligned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bearing to Kaaba", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(String.format(Locale.US, "%.1f°", qiblaBearing), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Device Heading", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(String.format(Locale.US, "%.1f°", azimuth), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Angle Diff", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(String.format(Locale.US, "%.1f°", relativeQiblaAngle), fontWeight = FontWeight.Bold, color = if (isAligned) Color(0xFFD4AF37) else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun triggerVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(100)
        }
    } catch (e: Exception) {
        // Safe to ignore if vibrate fails or lacks permission
    }
}
