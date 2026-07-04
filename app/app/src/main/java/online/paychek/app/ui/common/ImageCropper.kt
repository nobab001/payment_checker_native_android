package online.paychek.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CropAccent = Color(0xFF22D3EE)

/**
 * Circular crop + pinch/zoom + pan dialog. The user positions the logo/icon
 * inside a round frame and the exact framed region is exported as a square
 * bitmap, so "what you see is what gets saved to the server".
 */
@Composable
fun ImageCropperDialog(
    bitmap: android.graphics.Bitmap,
    title: String = "লোগো সাজান",
    subtitle: String = "আঙ্গুল দিয়ে জুম/ড্র্যাগ করে অথবা নিচের বাটন দিয়ে ছোট-বড় করুন",
    onDismiss: () -> Unit,
    onCropSuccess: (android.graphics.Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val viewportSizePx = remember { with(density) { 300.dp.toPx() } }
    val coroutineScope = rememberCoroutineScope()
    var isCropping by remember { mutableStateOf(false) }

    val imgWidth = bitmap.width.toFloat()
    val imgHeight = bitmap.height.toFloat()
    val baseScale = remember(bitmap) { minOf(viewportSizePx / imgWidth, viewportSizePx / imgHeight) }
    val baseTranslateX = remember(bitmap) { (viewportSizePx - imgWidth * baseScale) / 2f }
    val baseTranslateY = remember(bitmap) { (viewportSizePx - imgHeight * baseScale) / 2f }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Text(
                    subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(CircleShape)
                            .border(2.dp, CropAccent, CircleShape)
                            .background(Color.Black)
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 6f)
                                    offset = Offset(offset.x + pan.x, offset.y + pan.y)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "To Crop",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Zoom slider buttons (explicit small/large control)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { scale = (scale - 0.2f).coerceIn(0.5f, 6f) },
                        border = BorderStroke(1.dp, CropAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CropAccent),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                    }
                    Text("জুম", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    OutlinedButton(
                        onClick = { scale = (scale + 0.2f).coerceIn(0.5f, 6f) },
                        border = BorderStroke(1.dp, CropAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CropAccent),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        border = BorderStroke(1.dp, CropAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CropAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("বাতিল", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!isCropping) {
                                isCropping = true
                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        val cropped = cropBitmap(
                                            source = bitmap,
                                            viewportSizePx = viewportSizePx,
                                            targetSize = 512,
                                            scale = scale,
                                            offset = offset,
                                            baseScale = baseScale,
                                            baseTranslateX = baseTranslateX,
                                            baseTranslateY = baseTranslateY
                                        )
                                        withContext(Dispatchers.Main) { onCropSuccess(cropped) }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isCropping = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CropAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCropping) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("সেভ করুন", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun cropBitmap(
    source: android.graphics.Bitmap,
    viewportSizePx: Float,
    targetSize: Int,
    scale: Float,
    offset: Offset,
    baseScale: Float,
    baseTranslateX: Float,
    baseTranslateY: Float
): android.graphics.Bitmap {
    val cropped = android.graphics.Bitmap.createBitmap(targetSize, targetSize, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(cropped)

    val totalScale = baseScale * scale
    val center = viewportSizePx / 2f
    val totalTranslateX = (baseTranslateX - center) * scale + center + offset.x
    val totalTranslateY = (baseTranslateY - center) * scale + center + offset.y

    val ratio = targetSize.toFloat() / viewportSizePx

    val matrix = android.graphics.Matrix()
    matrix.postScale(totalScale, totalScale)
    matrix.postTranslate(totalTranslateX, totalTranslateY)
    matrix.postScale(ratio, ratio)

    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    return cropped
}

/** Encode a bitmap to a raw base64 PNG string (accepted by the upload endpoint). */
fun bitmapToBase64Png(bmp: android.graphics.Bitmap, maxDim: Int = 512): String {
    val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        android.graphics.Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else bmp
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}

/** Decode a picked content URI into a bitmap off the main thread. */
suspend fun decodeUriToBitmap(context: android.content.Context, uri: android.net.Uri): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            input?.close()
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
