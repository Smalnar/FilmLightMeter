package com.filmlightmeter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    if (hasCameraPermission) {
                        LightMeterApp()
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Для работы экспонометра требуется доступ к камере", color = Color.White)
                                Button(onClick = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Разрешить доступ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMeterApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FilmLightMeterPrefs", Context.MODE_PRIVATE) }

    // Double.NaN на старте позволяет мгновенно захватить первый кадр без долгой сходимости от 10.0 EV
    var smoothedEv by remember { mutableDoubleStateOf(Double.NaN) }
    var isHeld by remember { mutableStateOf(false) }

    var selectedFilm by remember {
        val savedName = prefs.getString("film", FilmCatalog.profiles.first().name)
        mutableStateOf(FilmCatalog.profiles.find { it.name == savedName } ?: FilmCatalog.profiles.first())
    }

    var currentIso by remember { mutableIntStateOf(prefs.getInt("iso", selectedFilm.defaultIso)) }
    var currentAperture by remember { mutableDoubleStateOf(prefs.getFloat("aperture", 2.8f).toDouble()) }
    var evCalibration by remember { mutableFloatStateOf(prefs.getFloat("calibration", 0.0f)) }
    var filterCompensationEv by remember { mutableFloatStateOf(prefs.getFloat("filter_ev", 0.0f)) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(selectedFilm, currentIso, currentAperture, evCalibration, filterCompensationEv) {
        prefs.edit()
            .putString("film", selectedFilm.name)
            .putInt("iso", currentIso)
            .putFloat("aperture", currentAperture.toFloat())
            .putFloat("calibration", evCalibration)
            .putFloat("filter_ev", filterCompensationEv)
            .apply()
    }

    // Округление до 0.1 EV внутри derivedStateOf отсекает микроколебания сенсора (UI обновляется только при реальном сдвиге света)
    val effectiveEv by remember {
        derivedStateOf {
            val base = if (smoothedEv.isNaN()) 10.0 else smoothedEv
            val total = base + evCalibration - filterCompensationEv
            (total * 10.0).roundToInt() / 10.0
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1.0f)) {
            SpotCameraPreview(
                isPaused = isHeld,
                zoomLevel = zoomLevel,
                onEvMeasured = { measuredEv ->
                    if (!isHeld) {
                        smoothedEv = if (smoothedEv.isNaN()) {
                            measuredEv
                        } else {
                            (0.2 * measuredEv) + (0.8 * smoothedEv)
                        }
                    }
                }
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .border(2.dp, if (isHeld) Color.Red else Color.Green, CircleShape)
            )

            if (isHeld) {
                Surface(
                    color = Color.Red,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                ) {
                    Text(
                        "HOLD (ЗАФИКСИРОВАНО)",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f)
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Передаём чтение effectiveEv внутрь дочернего Composable, чтобы не вызывать рекомпозицию кнопок и списков
            ExposureReadout(
                effectiveEvProvider = { effectiveEv },
                iso = currentIso,
                aperture = currentAperture,
                selectedFilm = selectedFilm
            )

            Button(
                onClick = { isHeld = !isHeld },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHeld) Color.Red else Color(0xFF3F51B5)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isHeld) "РАЗБЛОКИРОВАТЬ ЗАМЕР" else "ЗАФИКСИРОВАТЬ ЗАМЕР (HOLD)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("УГОЛ ЗАМЕРА (ЗУМ):", color = Color.White, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 2f, 4f).forEach { z ->
                        FilterChip(
                            selected = zoomLevel == z,
                            onClick = { zoomLevel = z },
                            label = { Text("${z.toInt()}x") }
                        )
                    }
                }
            }

            Text("ПЛЁНКА: ${selectedFilm.manufacturer} ${selectedFilm.name}", color = Color.White, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilmCatalog.profiles, key = { it.name }) { film ->
                    FilterChip(
                        selected = selectedFilm == film,
                        onClick = {
                            selectedFilm = film
                            currentIso = film.defaultIso
                        },
                        label = { Text(film.name) }
                    )
                }
            }

            val pushPullStops = log2(currentIso.toDouble() / selectedFilm.defaultIso.toDouble())
            val pushPullLabel = when {
                currentIso == selectedFilm.defaultIso -> "Номинал"
                pushPullStops > 0 -> String.format(Locale.US, "Push %+.1f EV", pushPullStops)
                else -> String.format(Locale.US, "Pull %+.1f EV", pushPullStops)
            }

            Text("ISO: $currentIso ($pushPullLabel)", color = Color.White, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExposureUtils.STANDARD_ISOS, key = { it }) { iso ->
                    FilterChip(
                        selected = currentIso == iso,
                        onClick = { currentIso = iso },
                        label = { Text("$iso") }
                    )
                }
            }

            Text("ДИАФРАГМА: f/$currentAperture", color = Color.White, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExposureUtils.STANDARD_APERTURES, key = { it }) { f ->
                    FilterChip(
                        selected = currentAperture == f,
                        onClick = { currentAperture = f },
                        label = { Text("f/$f") }
                    )
                }
            }

            Text(
                "СВЕТОФИЛЬТР (ND / ЦВЕТНОЙ): +${
                    if (filterCompensationEv % 1f == 0f) filterCompensationEv.toInt().toString()
                    else filterCompensationEv.toString()
                } EV",
                color = Color.White,
                fontSize = 13.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExposureUtils.FILTER_COMPENSATIONS, key = { it }) { comp ->
                    val labelText = if (comp % 1f == 0f) "+${comp.toInt()}" else "+$comp"
                    FilterChip(
                        selected = filterCompensationEv == comp,
                        onClick = { filterCompensationEv = comp },
                        label = { Text(labelText) }
                    )
                }
            }

            Column {
                Text(
                    "Калибровка датчика: ${String.format(Locale.US, "%+.1f", evCalibration)} EV",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Slider(
                    value = evCalibration,
                    onValueChange = { evCalibration = (it * 10).roundToInt() / 10.0f },
                    valueRange = -3.0f..3.0f,
                    steps = 59
                )
            }
        }
    }
}

@Composable
fun ExposureReadout(
    effectiveEvProvider: () -> Double,
    iso: Int,
    aperture: Double,
    selectedFilm: FilmProfile
) {
    val ev = effectiveEvProvider()
    val denom = 2.0.pow(ev) * (iso / 100.0)
    // Сначала вычисляем точную математическую выдержку
    val rawShutter = if (denom > 0.0) (aperture * aperture) / denom else 0.0
    // Для механического затвора квантуем до стандартного ряда, а Шварцшильда считаем от точного значения
    val standardShutter = ExposureUtils.quantizeShutter(rawShutter)
    val reciprocityShutter = selectedFilm.correctShutter(rawShutter)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("EV100", color = Color.Gray, fontSize = 12.sp)
            Text(String.format(Locale.US, "%.1f", ev), color = Color.Yellow, fontSize = 28.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ВЫДЕРЖКА", color = Color.Gray, fontSize = 12.sp)
            Text(ExposureUtils.formatShutter(standardShutter), color = Color.Green, fontSize = 28.sp)
        }
        if (rawShutter >= selectedFilm.reciprocityThreshold && selectedFilm.reciprocityP > 1.0) {
            Column(horizontalAlignment = Alignment.End) {
                Text("ШВАРЦШИЛЬД", color = Color.Cyan, fontSize = 11.sp)
                Text(ExposureUtils.formatShutter(reciprocityShutter), color = Color.Cyan, fontSize = 22.sp)
            }
        }
    }
}

private fun applyCenterSpotMetering(control: CameraControl) {
    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
    val centerPoint = factory.createPoint(0.5f, 0.5f, 0.08f)
    val action = FocusMeteringAction.Builder(
        centerPoint,
        FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF
    ).disableAutoCancel().build()
    control.startFocusAndMetering(action)
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun SpotCameraPreview(
    isPaused: Boolean,
    zoomLevel: Float,
    onEvMeasured: (Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentOnEvMeasured by rememberUpdatedState(onEvMeasured)

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // При изменении зума ждём завершения перестроения CROP_REGION и повторно фиксируем точечный замер в центре
    LaunchedEffect(zoomLevel, cameraControl) {
        val control = cameraControl ?: return@LaunchedEffect
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val zoomFuture = control.setZoomRatio(zoomLevel)
        zoomFuture.addListener({
            applyCenterSpotMetering(control)
        }, mainExecutor)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)

            providerFuture.addListener({
                val provider = providerFuture.get()
                val previewBuilder = Preview.Builder()

                // Считываем данные сенсора напрямую из Preview без лишнего ImageAnalysis
                Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            if (currentIsPaused) return
                            val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
                            val rawIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
                            val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
                            val aperture = result.get(CaptureResult.LENS_APERTURE) ?: 1.8f

                            val effectiveSensorIso = rawIso * (boost / 100.0)
                            if (expNs > 0L && effectiveSensorIso > 0.0) {
                                val ev100 = log2((aperture * aperture) / (expNs / 1e9)) - log2(effectiveSensorIso / 100.0)
                                previewView.post {
                                    if (!currentIsPaused) {
                                        currentOnEvMeasured(ev100)
                                    }
                                }
                            }
                        }
                    }
                )

                val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                    cameraControl = camera.cameraControl
                    applyCenterSpotMetering(camera.cameraControl)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, mainExecutor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
