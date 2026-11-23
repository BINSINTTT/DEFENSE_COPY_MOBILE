package com.tomatoinsight.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.TorchState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.tomatoinsight.ui.theme.TomatoGreen
import java.util.concurrent.Executor

private const val TAG = "LeafCamera"

@Composable
fun LeafCameraScreen(
    onImageCaptured: (android.graphics.Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val cameraPrefs = remember(context) { context.getSharedPreferences("leaf_camera", Context.MODE_PRIVATE) }
    var torchRequested by remember { mutableStateOf(cameraPrefs.getBoolean("torch_enabled", false)) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = "Camera permission is required to scan a leaf."
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val guideBottom = TomatoLeafShape.guideBounds(maxWidth.value, maxHeight.value).bottom.dp
        // Leave room for the torch, lighting hint, and shutter below the square.
        val compactControls = maxHeight - guideBottom < 208.dp
        when {
            !hasCameraPermission -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Allow camera access to continue.",
                        color = Color.White,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            else -> {
                val previewView = remember(context) {
                    PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it },
                    factory = { previewView }
                )
                DisposableEffect(previewView, lifecycleOwner, previewSize) {
                    var disposed = false
                    var provider: ProcessCameraProvider? = null
                    val preview = Preview.Builder().build()
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    if (previewSize.width > 0 && previewSize.height > 0) {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            if (disposed) return@addListener
                            try {
                                val cameraProvider = future.get()
                                provider = cameraProvider
                                val viewport = requireNotNull(previewView.viewPort)
                                capture.targetRotation = previewView.display.rotation
                                preview.setSurfaceProvider(previewView.surfaceProvider)
                                val group = UseCaseGroup.Builder()
                                    .setViewPort(viewport)
                                    .addUseCase(preview)
                                    .addUseCase(capture)
                                    .build()
                                boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                                imageCapture = capture
                                errorMessage = null
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera bind failed", e)
                                errorMessage = "Could not start camera: ${e.message}"
                            }
                        }, mainExecutor)
                    }
                    onDispose {
                        disposed = true
                        imageCapture = null
                        boundCamera = null
                        provider?.unbind(preview, capture)
                    }
                }

                LeafFrameOverlay(showLightingHint = !compactControls)
            }
        }

        val camera = boundCamera
        var torchOn by remember(camera) { mutableStateOf(camera?.cameraInfo?.torchState?.value == TorchState.ON) }
        if (camera != null && camera.cameraInfo.hasFlashUnit()) {
            DisposableEffect(camera, lifecycleOwner, torchRequested) {
                var active = true
                val info = camera.cameraInfo
                val torchObserver = Observer<Int> { torchOn = it == TorchState.ON }
                val cameraObserver = Observer<CameraState> { state ->
                    if (state.type == CameraState.Type.OPEN && (info.torchState.value == TorchState.ON) != torchRequested) {
                        val request = camera.cameraControl.enableTorch(torchRequested)
                        request.addListener({
                            if (active && info.cameraState.value?.type == CameraState.Type.OPEN) {
                                runCatching { request.get() }.onFailure {
                                    Log.w(TAG, "Torch request failed", it)
                                    errorMessage = "Could not switch the camera light. Try again."
                                    torchRequested = info.torchState.value == TorchState.ON
                                    cameraPrefs.edit().putBoolean("torch_enabled", torchRequested).apply()
                                }
                            }
                        }, mainExecutor)
                    }
                }
                info.torchState.observe(lifecycleOwner, torchObserver)
                info.cameraState.observe(lifecycleOwner, cameraObserver)
                onDispose {
                    active = false
                    info.torchState.removeObserver(torchObserver)
                    info.cameraState.removeObserver(cameraObserver)
                }
            }
            IconToggleButton(
                checked = torchOn,
                enabled = !isCapturing,
                onCheckedChange = { enabled ->
                    errorMessage = null
                    torchRequested = enabled
                    cameraPrefs.edit().putBoolean("torch_enabled", enabled).apply()
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = guideBottom + 8.dp)
                    .size(48.dp)
                    .background(if (torchOn) TomatoGreen else Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Continuous camera light",
                    tint = Color.White
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close camera",
                tint = Color.White
            )
        }

        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .align(if (compactControls) Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(end = if (compactControls) 12.dp else 0.dp, bottom = if (compactControls) 8.dp else 36.dp)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        color = Color.White
                    )
                } else {
                    IconButton(
                        enabled = imageCapture != null && (!torchRequested || torchOn || camera?.cameraInfo?.hasFlashUnit() != true),
                        onClick = {
                            val capture = imageCapture ?: return@IconButton
                            if (isCapturing) return@IconButton
                            isCapturing = true
                            errorMessage = null
                            takePhoto(
                                context = context,
                                imageCapture = capture,
                                executor = mainExecutor,
                                onSuccess = { bitmap ->
                                    isCapturing = false
                                    onImageCaptured(bitmap)
                                },
                                onError = { message ->
                                    isCapturing = false
                                    errorMessage = message
                                }
                            )
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .border(3.dp, Color.White, CircleShape)
                            .background(TomatoGreen.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Capture Tomato leaf photo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        if (!hasCameraPermission && errorMessage == null) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp)
            )
        }
    }
}

private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onSuccess: (android.graphics.Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val photoFile = CameraCaptureUtils.createCaptureFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val oriented = CameraCaptureUtils.loadOrientedBitmap(photoFile)
                    val cropped = CameraCaptureUtils.cropToGuideFrame(oriented)
                    photoFile.delete()
                    onSuccess(cropped)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process capture", e)
                    photoFile.delete()
                    onError("Could not process photo: ${e.message}")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                photoFile.delete()
                onError("Capture failed: ${exception.message}")
            }
        }
    )
}
