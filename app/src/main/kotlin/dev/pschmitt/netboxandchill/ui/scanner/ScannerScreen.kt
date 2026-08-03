@file:Suppress("UnsafeOptInUsageError")

package dev.pschmitt.netboxandchill.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.data.repository.ScannerRearLens
import dev.pschmitt.netboxandchill.scanner.BarcodeAnalyzer
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import dev.pschmitt.netboxandchill.ui.common.NetBoxResponsiveScaffold
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onTargetFound: (NetBoxTarget) -> Unit,
    onBack: () -> Unit,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    showBottomBar: Boolean = true,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scannerLens by viewModel.scannerLens.collectAsStateWithLifecycle()
    val scannerRearLens by viewModel.scannerRearLens.collectAsStateWithLifecycle()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var availableCameras by remember { mutableStateOf<List<ScannerCameraOption>>(emptyList()) }
    var selectedRearCameraId by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val rearCameras = availableCameras.filter { it.lens == ScannerLens.Back }
    val canSwitchFacing =
        availableCameras.any { it.lens == ScannerLens.Back } &&
            availableCameras.any { it.lens == ScannerLens.Front }
    val selectedRearCamera =
        rearCameras.firstOrNull { it.id == selectedRearCameraId } ?: rearCameras.firstOrNull()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        val found = state as? ScanResultState.Found ?: return@LaunchedEffect
        onTargetFound(found.target)
    }

    NetBoxResponsiveScaffold(
        // The camera preview is an immersive surface. On tablets it must cover the area normally
        // occupied by the navigation rail, otherwise the rail remains visible behind the preview.
        fullScreenOnRail = true,
        bottomBar = {
            if (showBottomBar) {
                NetBoxBottomBar(
                    selected = BottomTab.Scan,
                    onDashboardClick = onDashboardClick,
                    onSearchClick = onSearchClick,
                    onScanClick = {},
                    onAddClick = onAddClick,
                    onSettingsClick = onSettingsClick,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Scan device sticker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (hasCameraPermission) {
                CameraPreview(
                    desiredLens = scannerLens,
                    selectedCameraId = selectedRearCameraId,
                    onCodeScanned = viewModel::onCodeScanned,
                    onAvailableCameras = { options ->
                        availableCameras = options
                        if (
                            selectedRearCameraId !in
                                options.filter { it.lens == ScannerLens.Back }.map { it.id }
                        ) {
                            selectedRearCameraId =
                                defaultRearCamera(
                                        options.filter { it.lens == ScannerLens.Back },
                                        scannerRearLens,
                                    )
                                    ?.id
                        }
                    },
                    onCameraReady = { camera = it },
                    zoomRatio = zoomRatio,
                    onZoomRatioChanged = { zoomRatio = it },
                )
                ScannerViewfinder(modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    "Camera permission is required to scan device stickers",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                if (scannerLens == ScannerLens.Back && rearCameras.size > 1) {
                    RearLensSelector(
                        cameras = rearCameras,
                        selectedCameraId = selectedRearCamera?.id,
                        onCameraSelected = { option ->
                            selectedRearCameraId = option.id
                            viewModel.setScannerLens(ScannerLens.Back)
                            camera?.cameraControl?.enableTorch(false)
                            torchOn = false
                        },
                    )
                }
                if (zoomRatio > 1.01f) {
                    Surface(
                        modifier = Modifier.zIndex(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = "%.1f×".format(zoomRatio),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                ScannerControls(
                    modifier = Modifier.zIndex(1f),
                    showTorch = camera?.cameraInfo?.hasFlashUnit() == true,
                    torchOn = torchOn,
                    onTorchClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    showFacingSwitch = canSwitchFacing,
                    showingFront = scannerLens == ScannerLens.Front,
                    onFacingSwitchClick = {
                        val nextLens =
                            if (scannerLens == ScannerLens.Front) ScannerLens.Back
                            else ScannerLens.Front
                        viewModel.setScannerLens(nextLens)
                        camera?.cameraControl?.enableTorch(false)
                        torchOn = false
                    },
                )
            }

            when (val current = state) {
                is ScanResultState.Resolving -> ScanOverlay { CircularProgressIndicator() }
                is ScanResultState.NotRecognized -> {
                    ScanOverlay {
                        Text(
                            "That doesn't look like a NetBox device link",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    LaunchedEffect(current) {
                        delay(1500)
                        viewModel.reset()
                    }
                }
                is ScanResultState.NotFound -> {
                    ScanOverlay {
                        Text(
                            "No device found for asset tag ${current.assetTag}",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    LaunchedEffect(current) {
                        delay(1500)
                        viewModel.reset()
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun RearLensSelector(
    cameras: List<ScannerCameraOption>,
    selectedCameraId: String?,
    onCameraSelected: (ScannerCameraOption) -> Unit,
) {
    Surface(
        modifier = Modifier.zIndex(1f),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            cameras.forEach { camera ->
                val selected = camera.id == selectedCameraId
                FilterChip(
                    selected = selected,
                    onClick = { onCameraSelected(camera) },
                    label = {
                        Text(if (selected) camera.label else camera.label.removeSuffix("×"))
                    },
                )
            }
        }
    }
}

@Composable
private fun ScannerControls(
    modifier: Modifier = Modifier,
    showTorch: Boolean,
    torchOn: Boolean,
    onTorchClick: () -> Unit,
    showFacingSwitch: Boolean,
    showingFront: Boolean,
    onFacingSwitchClick: () -> Unit,
) {
    Surface(
        modifier = modifier.zIndex(1f),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTorch) {
                IconButton(onClick = onTorchClick) {
                    Icon(
                        if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription =
                            if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                    )
                }
            }
            if (showFacingSwitch) {
                IconButton(onClick = onFacingSwitchClick) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription =
                            if (showingFront) "Use rear camera" else "Use front camera",
                    )
                }
            }
        }
    }
}

/**
 * A dimmed frame around a centered square cutout, like most QR scanner apps - purely cosmetic, the
 * analyzer scans the whole camera frame regardless of what's inside the square.
 */
@Composable
private fun ScannerViewfinder(modifier: Modifier = Modifier) {
    val dim = Color.Black.copy(alpha = 0.55f)
    Canvas(modifier = modifier) {
        val squareSize = size.minDimension * 0.65f
        val left = (size.width - squareSize) / 2f
        val top = (size.height - squareSize) / 2f
        val right = left + squareSize
        val bottom = top + squareSize

        drawRect(color = dim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(
            color = dim,
            topLeft = Offset(0f, bottom),
            size = Size(size.width, size.height - bottom),
        )
        drawRect(color = dim, topLeft = Offset(0f, top), size = Size(left, squareSize))
        drawRect(
            color = dim,
            topLeft = Offset(right, top),
            size = Size(size.width - right, squareSize),
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(squareSize, squareSize),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun ScanOverlay(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CameraPreview(
    desiredLens: ScannerLens,
    selectedCameraId: String?,
    onCodeScanned: (String) -> Unit,
    onAvailableCameras: (List<ScannerCameraOption>) -> Unit,
    onCameraReady: (Camera?) -> Unit,
    zoomRatio: Float,
    onZoomRatioChanged: (Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val boundCamera = remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    LaunchedEffect(cameraProviderFuture) {
        cameraProvider = cameraProviderFuture.get()
    }

    LaunchedEffect(boundCamera.value, zoomRatio) {
        val camera = boundCamera.value ?: return@LaunchedEffect
        val zoomState = camera.cameraInfo.zoomState.value ?: return@LaunchedEffect
        val clamped = zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        if (clamped != zoomRatio) onZoomRatioChanged(clamped)
        camera.cameraControl.setZoomRatio(clamped)
    }

    DisposableEffect(cameraProvider, previewView, desiredLens, selectedCameraId) {
        val provider = cameraProvider
        val view = previewView
        if (provider == null || view == null) {
            onDispose {}
        } else {
            onCameraReady(null)
            boundCamera.value = null
            val available = availableCameraOptions(provider)
            onAvailableCameras(available)
            val activeCamera =
                available.firstOrNull { it.id == selectedCameraId && it.lens == desiredLens }
                    ?: available.firstOrNull { it.lens == desiredLens }
                    ?: available.firstOrNull()
            if (activeCamera != null) {
                val previewBuilder = Preview.Builder()
                val analysisBuilder =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activeCamera.physicalCameraId?.let { physicalCameraId ->
                    // CameraSelector carries the physical ID through CameraX's lifecycle
                    // binding. Set it on each use-case as well: this is the Camera2 interop
                    // path that writes OutputConfiguration.setPhysicalCameraId(), which is
                    // required by logical multi-camera implementations such as Pixel's.
                    Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(physicalCameraId)
                    Camera2Interop.Extender(analysisBuilder).setPhysicalCameraId(physicalCameraId)
                    }
                }
                val preview =
                    previewBuilder.build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis =
                    analysisBuilder.build().also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(onCodeScanned))
                    }
                runCatching {
                        // CameraX must be fully unbound before a different physical or facing
                        // camera can be selected. Keeping this in one synchronous effect avoids
                        // an old async listener rebinding the previous lens after a user switch.
                        provider.unbindAll()
                        provider
                            .bindToLifecycle(
                                lifecycleOwner,
                                activeCamera.selector,
                                preview,
                                analysis,
                            )
                            .also {
                                // Physical rear-lens selection chooses the sensor; this zoom is a
                                // separate digital crop applied to whichever sensor is active.
                                it.cameraControl.setZoomRatio(zoomRatio)
                            }
                    }
                    .onSuccess {
                        boundCamera.value = it
                        onCameraReady(it)
                    }
                    .onFailure {
                        Timber.e(it, "Unable to bind ${activeCamera.id}")
                    }
            } else {
                Timber.w("No camera available for lens %s", desiredLens)
            }
            onDispose {
                boundCamera.value = null
                onCameraReady(null)
                runCatching { provider.unbindAll() }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().zIndex(-1f),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            // SurfaceView (the default PERFORMANCE mode) can sit above Compose's controls and
            // consume their touch events on some devices, notably Pixel and Zenfone. TextureView
            // keeps the preview below the lens/facing controls while retaining tap-to-focus.
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

            // Tap-to-focus: set directly on the PreviewView rather than a Compose pointerInput
            // modifier, since AndroidView touch dispatch to an embedded native View can otherwise
            // swallow gestures before Compose sees them - this is the standard CameraX recipe.
            var scalingGesture = false
            val scaleDetector =
                ScaleGestureDetector(
                    ctx,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            scalingGesture = true
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val camera = boundCamera.value ?: return true
                            val zoomState = camera.cameraInfo.zoomState.value ?: return true
                            val nextZoom =
                                (zoomState.zoomRatio * detector.scaleFactor).coerceIn(
                                    zoomState.minZoomRatio,
                                    zoomState.maxZoomRatio,
                                )
                            camera.cameraControl.setZoomRatio(nextZoom)
                            onZoomRatioChanged(nextZoom)
                            return true
                        }
                    },
                )
            previewView.setOnTouchListener { view, event ->
                val wasScaling = scalingGesture
                scaleDetector.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP && !wasScaling && !scalingGesture) {
                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                    val action =
                        FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                    boundCamera.value?.cameraControl?.startFocusAndMetering(action)
                    view.performClick()
                }
                if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    scalingGesture = false
                }
                true
            }

            previewView
        },
        update = { view ->
            previewView = view
        },
    )
}

private data class ScannerCameraOption(
    val id: String,
    val lens: ScannerLens,
    val label: String,
    val selector: CameraSelector,
    val physicalCameraId: String? = null,
    val focalLength: Float? = null,
    val zoomRatio: Float = 1f,
)

private fun defaultRearCamera(
    cameras: List<ScannerCameraOption>,
    preferredLens: ScannerRearLens,
): ScannerCameraOption? {
    if (cameras.isEmpty()) return null
    val targetZoom =
        when (preferredLens) {
            ScannerRearLens.Automatic,
            ScannerRearLens.Wide -> 1f
            ScannerRearLens.UltraWide -> 0.6f
            ScannerRearLens.Telephoto -> 2f
        }
    return cameras.minByOrNull { abs(it.zoomRatio - targetZoom) }
}

private fun availableCameraOptions(provider: ProcessCameraProvider): List<ScannerCameraOption> {
    val options =
        provider.availableCameraInfos
            .flatMap { info ->
                val camera2Info =
                    runCatching { Camera2CameraInfo.from(info) }.getOrNull()
                        ?: return@flatMap emptyList()
                val facing =
                    camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                        ?: return@flatMap emptyList()
                val lens =
                    when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> ScannerLens.Front
                        CameraCharacteristics.LENS_FACING_BACK -> ScannerLens.Back
                        else -> return@flatMap emptyList()
                    }
                val physicalInfos =
                    if (lens == ScannerLens.Back) info.physicalCameraInfos else emptySet()
                if (physicalInfos.isNotEmpty()) {
                    physicalInfos.mapNotNull { physicalInfo ->
                        val physicalCamera2Info =
                            runCatching { Camera2CameraInfo.from(physicalInfo) }.getOrNull()
                                ?: return@mapNotNull null
                        val cameraId = physicalCamera2Info.cameraId
                        val focalLength =
                            physicalCamera2Info
                                .getCameraCharacteristic(
                                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                                )
                                ?.firstOrNull()
                        ScannerCameraOption(
                            id = "physical:$cameraId",
                            lens = ScannerLens.Back,
                            label = "Rear lens",
                            selector =
                                // Pixel devices expose the ultrawide and wide sensors as physical
                                // cameras behind one logical camera. CameraX applies this ID to the
                                // Preview and ImageAnalysis output configurations, forcing the
                                // requested physical sensor instead of clamping a logical zoom.
                                info.selector(physicalCameraId = cameraId),
                            physicalCameraId = cameraId,
                            focalLength = focalLength,
                        )
                    }
                } else {
                    listOf(
                        ScannerCameraOption(
                            id = "logical:${camera2Info.cameraId}",
                            lens = lens,
                            label =
                                if (lens == ScannerLens.Front) "Front camera" else "Back camera",
                            selector = info.selector(),
                        )
                    )
                }
            }
            .distinctBy { it.id }

    return labelRearCameraOptions(options)
}

private fun CameraInfo.selector(physicalCameraId: String? = null): CameraSelector {
    val cameraId = Camera2CameraInfo.from(this).cameraId
    return CameraSelector.Builder()
        .addCameraFilter { infos ->
            infos.filter { info ->
                runCatching { Camera2CameraInfo.from(info).cameraId == cameraId }
                    .getOrDefault(false)
            }
        }
        .apply { physicalCameraId?.let(::setPhysicalCameraId) }
        .build()
}

private fun labelRearCameraOptions(options: List<ScannerCameraOption>): List<ScannerCameraOption> {
    val rear = options.filter { it.lens == ScannerLens.Back }
    if (rear.size <= 1) return options

    val sorted = rear.sortedWith(compareBy(nullsLast()) { it.focalLength })
    val referenceFocal = sorted[sorted.size / 2].focalLength
    val labelsById =
        sorted
            .mapIndexed { index, option ->
                val label =
                    if (option.focalLength != null && referenceFocal != null) {
                        val ratio = option.focalLength / referenceFocal
                        when {
                            ratio <= 0.75f -> "0.6×"
                            ratio >= 1.6f -> "2×"
                            else -> "1×"
                        }
                    } else {
                        "Rear ${index + 1}"
                    }
                val zoomRatio =
                    if (option.focalLength != null && referenceFocal != null) {
                        (option.focalLength / referenceFocal).coerceIn(0.5f, 8f)
                    } else {
                        1f
                    }
                option.id to (label to zoomRatio)
            }
            .toMap()

    return options.map { option ->
        val (label, zoomRatio) = labelsById[option.id] ?: (option.label to option.zoomRatio)
        option.copy(label = label, zoomRatio = zoomRatio)
    }
}
