package com.notmugil.uta.ui.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notmugil.uta.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropDone: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    LaunchedEffect(imageUri) {
        isLoading = true
        loadError = null
        withContext(Dispatchers.IO) {
            val bitmap = loadOrientedBitmap(context, imageUri)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    sourceBitmap = bitmap
                } else {
                    loadError = "Failed to load image"
                }
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.image_crop_loading), color = Color.White)
                    }
                }
            } else if (loadError != null || sourceBitmap == null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(loadError ?: stringResource(R.string.image_crop_failed_load), color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            } else {
                val currentBitmap = sourceBitmap!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = Color.White
                            )
                        }

                        Text(
                            text = stringResource(R.string.image_crop_ratio_1_1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        IconButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90) % 360
                                offset = Offset.Zero
                            }
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.RotateClockwise,
                                contentDescription = stringResource(R.string.image_crop_rotate_90),
                                tint = Color.White
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val viewportSize = min(maxWidth.value, maxHeight.value).dp
                        val density = LocalDensity.current
                        val viewportPx = with(density) { viewportSize.toPx() }

                        val isRotated90or270 = rotationDegrees % 180 != 0
                        val bmpW = if (isRotated90or270) currentBitmap.height.toFloat() else currentBitmap.width.toFloat()
                        val bmpH = if (isRotated90or270) currentBitmap.width.toFloat() else currentBitmap.height.toFloat()

                        val baseScale = max(viewportPx / bmpW, viewportPx / bmpH)

                        Box(
                            modifier = Modifier
                                .size(viewportSize)
                                .clipToBounds()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        val effectiveScale = baseScale * scale
                                        val maxOffsetX = max(0f, (bmpW * effectiveScale - viewportPx) / 2f)
                                        val maxOffsetY = max(0f, (bmpH * effectiveScale - viewportPx) / 2f)

                                        val newOffsetX = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        val newOffsetY = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        offset = Offset(newOffsetX, newOffsetY)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val effectiveScale = baseScale * scale
                                val centerX = size.width / 2f + offset.x
                                val centerY = size.height / 2f + offset.y

                                drawContext.canvas.save()
                                drawContext.canvas.translate(centerX, centerY)
                                drawContext.canvas.rotate(rotationDegrees.toFloat())
                                drawContext.canvas.scale(effectiveScale, effectiveScale)

                                val srcW = currentBitmap.width.toFloat()
                                val srcH = currentBitmap.height.toFloat()

                                drawImage(
                                    image = currentBitmap.asImageBitmap(),
                                    dstOffset = IntOffset((-srcW / 2f).roundToInt(), (-srcH / 2f).roundToInt()),
                                    dstSize = IntSize(srcW.roundToInt(), srcH.roundToInt())
                                )

                                drawContext.canvas.restore()

                                val gridColor = Color.White.copy(alpha = 0.35f)
                                val oneThirdW = size.width / 3f
                                val oneThirdH = size.height / 3f

                                drawLine(gridColor, Offset(oneThirdW, 0f), Offset(oneThirdW, size.height), strokeWidth = 1f)
                                drawLine(gridColor, Offset(oneThirdW * 2, 0f), Offset(oneThirdW * 2, size.height), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, oneThirdH), Offset(size.width, oneThirdH), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, oneThirdH * 2), Offset(size.width, oneThirdH * 2), strokeWidth = 1f)

                                drawRect(
                                    color = Color.White,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                    rotationDegrees = 0
                                }
                            ) {
                                Icon(Tabler.Outline.RotateClockwise2, contentDescription = null, tint = Color.LightGray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.image_crop_reset), color = Color.LightGray)
                            }

                            Button(
                                onClick = {
                                    val cropped = createCroppedBitmap(
                                        source = currentBitmap,
                                        rotation = rotationDegrees,
                                        scale = scale,
                                        offset = offset,
                                        outputSize = 512
                                    )
                                    onCropDone(cropped)
                                }
                            ) {
                                Icon(Tabler.Outline.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_done))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val exif = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ExifInterface(inputStream)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun createCroppedBitmap(
    source: Bitmap,
    rotation: Int,
    scale: Float,
    offset: Offset,
    outputSize: Int
): Bitmap {
    val orientedBitmap = if (rotation % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
        source
    }

    val srcW = orientedBitmap.width.toFloat()
    val srcH = orientedBitmap.height.toFloat()

    val minDim = min(srcW, srcH)
    val cropWindowSize = minDim / scale

    val normalizedOffsetX = -offset.x / (scale * max(1f, minDim))
    val normalizedOffsetY = -offset.y / (scale * max(1f, minDim))

    val cropCenterX = (srcW / 2f) + (normalizedOffsetX * minDim)
    val cropCenterY = (srcH / 2f) + (normalizedOffsetY * minDim)

    var cropX = (cropCenterX - cropWindowSize / 2f).roundToInt()
    var cropY = (cropCenterY - cropWindowSize / 2f).roundToInt()
    var cropSize = cropWindowSize.roundToInt()

    if (cropX < 0) cropX = 0
    if (cropY < 0) cropY = 0
    if (cropX + cropSize > orientedBitmap.width) {
        cropSize = orientedBitmap.width - cropX
    }
    if (cropY + cropSize > orientedBitmap.height) {
        cropSize = orientedBitmap.height - cropY
    }
    cropSize = max(1, cropSize)

    val cropped = Bitmap.createBitmap(orientedBitmap, cropX, cropY, cropSize, cropSize)
    return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
}
