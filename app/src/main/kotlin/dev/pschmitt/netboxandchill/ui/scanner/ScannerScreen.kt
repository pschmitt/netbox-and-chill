package dev.pschmitt.netboxandchill.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.scanner.BarcodeAnalyzer
import dev.pschmitt.netboxandchill.scanner.NetBoxTarget
import dev.pschmitt.netboxandchill.data.repository.ScannerLens
import dev.pschmitt.netboxandchill.ui.common.BottomTab
import dev.pschmitt.netboxandchill.ui.common.NetBoxBottomBar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onTargetFound: (NetBoxTarget) -> Unit,
    onBack: () -> Unit,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit,
    showBottomBar: Boolean = true,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scannerLens by viewModel.scannerLens.collectAsStateWithLifecycle()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var availableLenses by remember { mutableStateOf<Set<ScannerLens>>(emptySet()) }
    var torchOn by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(state) {
        val found = state as? ScanResultState.Found ?: return@LaunchedEffect
        onTargetFound(found.target)
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NetBoxBottomBar(
                    selected = BottomTab.Scan,
                    onDashboardClick = onDashboardClick,
                    onScanClick = {},
                    onSearchClick = onSearchClick,
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
                actions = {
                    if (availableLenses.size > 1) {
                        IconButton(
                            onClick = {
                                val nextLens = availableLenses.firstOrNull { it != scannerLens } ?: scannerLens
                                viewModel.setScannerLens(nextLens)
                                camera?.cameraControl?.enableTorch(false)
                                torchOn = false
                            }
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera")
                        }
                    }
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                            }
                        ) {
                            Icon(
                                if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (hasCameraPermission) {
                CameraPreview(
                    desiredLens = scannerLens,
                    onCodeScanned = viewModel::onCodeScanned,
                    onAvailableLenses = { availableLenses = it },
                    onCameraReady = { camera = it },
                )
                ScannerViewfinder(modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    "Camera permission is required to scan device stickers",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
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
                else -> Unit
            }
        }
    }
}

/** A dimmed frame around a centered square cutout, like most QR scanner apps - purely cosmetic,
 * the analyzer scans the whole camera frame regardless of what's inside the square. */
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
        drawRect(color = dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(color = dim, topLeft = Offset(0f, top), size = Size(left, squareSize))
        drawRect(color = dim, topLeft = Offset(right, top), size = Size(size.width - right, squareSize))

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
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CameraPreview(
    desiredLens: ScannerLens,
    onCodeScanned: (String) -> Unit,
    onAvailableLenses: (Set<ScannerLens>) -> Unit,
    onCameraReady: (Camera?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    DisposableEffect(previewView, desiredLens) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            var disposed = false
            onCameraReady(null)
            cameraProviderFuture.addListener(
                {
                    if (disposed) return@addListener
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val available =
                            ScannerLens.values().filter { lens ->
                                runCatching { cameraProvider.hasCamera(lens.selector()) }.getOrDefault(false)
                            }.toSet()
                        onAvailableLenses(available)
                        val activeLens = if (desiredLens in available) desiredLens else available.firstOrNull()
                        if (activeLens != null) {
                            val preview =
                                Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            val analysis =
                                ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(onCodeScanned)) }
                            cameraProvider.unbindAll()
                            onCameraReady(
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    activeLens.selector(),
                                    preview,
                                    analysis,
                                )
                            )
                        }
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            onDispose {
                disposed = true
                onCameraReady(null)
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            var boundCamera: Camera? = null

            // Tap-to-focus: set directly on the PreviewView rather than a Compose pointerInput
            // modifier, since AndroidView touch dispatch to an embedded native View can otherwise
            // swallow gestures before Compose sees them - this is the standard CameraX recipe.
            previewView.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                    val action =
                        FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                    boundCamera?.cameraControl?.startFocusAndMetering(action)
                    view.performClick()
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

private fun ScannerLens.selector(): CameraSelector =
    CameraSelector.Builder()
        .requireLensFacing(
            if (this == ScannerLens.Front) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        )
        .build()
