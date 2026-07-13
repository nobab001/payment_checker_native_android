package online.paychek.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig

/**
 * Lightweight remote image loading for provider logos / tab icons without adding
 * a heavy dependency. Decoded bitmaps are held in a process-wide LRU cache so a
 * logo is fetched once and reused everywhere (checkout preview, number rows,
 * admin editor) — matching the "load once, then from cache" requirement.
 */
private val bitmapCache = object : android.util.LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
}

/** Turn a server-relative path (e.g. "uploads/branding/x.png") into a full URL. */
fun resolveImageUrl(pathOrUrl: String?): String {
    val p = pathOrUrl?.trim().orEmpty()
    if (p.isEmpty()) return ""
    if (p.startsWith("http://") || p.startsWith("https://") ||
        p.startsWith("file://") || p.startsWith("/data") || p.startsWith("/storage")
    ) return p
    return "${AppConfig.BASE_URL}${p.trimStart('/')}"
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit = {}
) {
    val resolved = remember(url) { resolveImageUrl(url) }
    var bitmap by remember(resolved) { mutableStateOf(bitmapCache.get(resolved)) }

    LaunchedEffect(resolved) {
        if (resolved.isEmpty()) return@LaunchedEffect
        val cached = bitmapCache.get(resolved)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            try {
                if (resolved.startsWith("http")) {
                    val c = java.net.URL(resolved).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 12000
                    c.readTimeout = 12000
                    c.instanceFollowRedirects = true
                    c.doInput = true
                    // ngrok free tier may block bare clients
                    c.setRequestProperty("User-Agent", "PaychekAndroid/1.0")
                    c.setRequestProperty("ngrok-skip-browser-warning", "true")
                    c.connect()
                    val input = c.inputStream
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    c.disconnect()
                    bmp
                } else {
                    android.graphics.BitmapFactory.decodeFile(resolved.removePrefix("file://"))
                }
            } catch (_: Exception) {
                null
            }
        }
        if (decoded != null) {
            bitmapCache.put(resolved, decoded)
            bitmap = decoded
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        fallback()
    }
}
