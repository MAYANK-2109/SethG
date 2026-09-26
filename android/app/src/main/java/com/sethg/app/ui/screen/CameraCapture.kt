package com.sethg.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sethg.app.R
import com.sethg.app.ui.theme.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Full-screen in-app camera. This is the ONLY way a photo enters a lot —
 * there is deliberately no gallery / file picker, so downloaded, edited or
 * AI-generated images can't be attached.
 */
@Composable
fun CameraCapture(
    newPhotoFile: () -> File,
    onPhotoCaptured: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreviewWithShutter(newPhotoFile, onPhotoCaptured)
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📷", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.camera_permission_needed),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) { Text(stringResource(R.string.allow_camera)) }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
    }
}

@Composable
private fun BoxScope.CameraPreviewWithShutter(
    newPhotoFile: () -> File,
    onPhotoCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // ~1.2 MP JPEG at 85% quality keeps photos small for entry-level phones and 2G sync
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            .setJpegQuality(85)
            .build()
    }

    LaunchedEffect(Unit) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (e: Exception) {
            errorText = e.localizedMessage
        }
    }
    DisposableEffect(Unit) {
        onDispose { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    errorText?.let {
        Text(it, color = ErrorColor, modifier = Modifier.align(Alignment.Center).padding(24.dp))
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
    ) {
        FilledIconButton(
            onClick = {
                if (isCapturing) return@FilledIconButton
                isCapturing = true
                val file = newPhotoFile()
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            isCapturing = false
                            onPhotoCaptured(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                            file.delete()
                            errorText = exception.localizedMessage
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.take_photo),
                    tint = GreenPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
