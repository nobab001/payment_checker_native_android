package online.paychek.app.ui.screen.sync

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.paychek.app.data.local.AppDatabase
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.domain.usecase.sync.FlushOfflineQueueUseCase
import online.paychek.app.services.connectivity.ConnectivityService
import online.paychek.app.utils.SmsSecuritySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Theme tokens (matching existing screens)
// ─────────────────────────────────────────────────────────────────────────────
private val RoyalIndigo   = Color(0xFF3D5AFE)
private val StatusGreen   = Color(0xFF00C853)
private val WarnAmber     = Color(0xFFFFB300)
private val DangerRed     = Color(0xFFE53935)
private val SurfaceDark   = Color(0xFF1A1D27)

private val CardBg: Color  @Composable get() = MaterialTheme.colorScheme.surface
private val BgColor: Color @Composable get() = MaterialTheme.colorScheme.background
private val OnBg: Color    @Composable get() = MaterialTheme.colorScheme.onBackground
private val OnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// ─────────────────────────────────────────────────────────────────────────────
// Data class for queue stats (loaded from Room on IO dispatcher)
// ─────────────────────────────────────────────────────────────────────────────
private data class QueueStats(
    val pending: Int         = 0,
    val permanentFailed: Int = 0,
    val lastSyncMs: Long     = 0L,
    val isOnline: Boolean    = false
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var stats          by remember { mutableStateOf(QueueStats()) }
    var isSyncing      by remember { mutableStateOf(false) }
    var syncMessage    by remember { mutableStateOf<String?>(null) }
    var isLoading      by remember { mutableStateOf(true) }
    val spinAngle      by animateFloatAsState(
        targetValue = if (isSyncing) 360f else 0f,
        animationSpec = tween(durationMillis = if (isSyncing) 800 else 0),
        label = "spin"
    )

    // Load stats on entry and after each sync
    fun loadStats() {
        scope.launch(Dispatchers.IO) {
            val dao          = AppDatabase.getInstance(context).pendingSmsDao()
            val nowMs        = System.currentTimeMillis()
            val pendingList  = dao.getPendingItemsForRetry(nowMs)
            val failedCount  = dao.countPermanentlyFailed()
            val lastSyncMs   = PrefsHelper.getLastWorkerSyncMs(context)
            val online       = ConnectivityService(context).isOnline()
            withContext(Dispatchers.Main) {
                stats     = QueueStats(pendingList.size, failedCount, lastSyncMs, online)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        text = "Sync & Queue Status",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadStats() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalIndigo)
            )
        },
        modifier = modifier
    ) { innerPadding ->

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoyalIndigo)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Network status banner ─────────────────────────────────────
            NetworkStatusBanner(isOnline = stats.isOnline)

            // ── Queue stats cards ─────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    modifier    = Modifier.weight(1f),
                    icon        = Icons.Default.Schedule,
                    iconColor   = if (stats.pending > 0) WarnAmber else StatusGreen,
                    label       = "Pending",
                    value       = stats.pending.toString(),
                    description = if (stats.pending == 0) "Queue is clear" else "Awaiting sync"
                )
                StatCard(
                    modifier    = Modifier.weight(1f),
                    icon        = Icons.Default.ErrorOutline,
                    iconColor   = if (stats.permanentFailed > 0) DangerRed else StatusGreen,
                    label       = "Failed",
                    value       = stats.permanentFailed.toString(),
                    description = if (stats.permanentFailed == 0) "No failures" else "Server parse error"
                )
            }

            // ── Last sync info ────────────────────────────────────────────
            LastSyncCard(lastSyncMs = stats.lastSyncMs)

            // ── Retry policy reference ────────────────────────────────────
            RetryPolicyCard()

            // ── Sync message feedback ─────────────────────────────────────
            AnimatedVisibility(
                visible = syncMessage != null,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                syncMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.startsWith("✅")) StatusGreen.copy(alpha = 0.12f)
                            else DangerRed.copy(alpha = 0.10f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text      = msg,
                            color     = if (msg.startsWith("✅")) StatusGreen else DangerRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize  = 14.sp,
                            modifier  = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // ── Manual sync button ────────────────────────────────────────
            Button(
                onClick = {
                    if (!isSyncing) {
                        isSyncing   = true
                        syncMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                FlushOfflineQueueUseCase(context).execute()
                            }
                            delay(800L) // let the spinner complete one full turn
                            result.fold(
                                onSuccess = { count ->
                                    syncMessage = if (count > 0)
                                        "✅ Sync triggered for $count pending item(s)"
                                    else
                                        "✅ Queue is already empty or device is offline"
                                },
                                onFailure = { e ->
                                    syncMessage = "❌ Sync failed: ${e.message}"
                                }
                            )
                            loadStats()
                            isSyncing = false
                        }
                    }
                },
                enabled  = !isSyncing,
                colors   = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(spinAngle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = if (isSyncing) "Syncing..." else "Sync Now",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }

            // ── Spec reference footer ─────────────────────────────────────
            SpecReferenceCard()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NetworkStatusBanner(isOnline: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) StatusGreen.copy(alpha = 0.12f)
            else WarnAmber.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) StatusGreen else WarnAmber)
            )
            Text(
                text       = if (isOnline) "Network: Online" else "Network: Offline — sync queued for next connection",
                color      = if (isOnline) StatusGreen else WarnAmber,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    description: String
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = label,
                tint            = iconColor,
                modifier        = Modifier.size(24.dp)
            )
            Text(
                text       = value,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = iconColor
            )
            Text(
                text     = label,
                fontSize = 12.sp,
                color    = OnSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text     = description,
                fontSize = 11.sp,
                color    = OnSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LastSyncCard(lastSyncMs: Long) {
    val lastSyncText = if (lastSyncMs == 0L) {
        "Never synced via WorkManager"
    } else {
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
        "Last WorkManager sync: ${fmt.format(Date(lastSyncMs))}"
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(
                imageVector     = Icons.Default.History,
                contentDescription = "Last sync",
                tint            = RoyalIndigo,
                modifier        = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = "WorkManager Fallback",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = OnBg
                )
                Text(
                    text     = lastSyncText,
                    fontSize = 12.sp,
                    color    = OnSurface
                )
                Text(
                    text     = "Runs every ${SmsSecuritySpec.RETRY_MAX_ATTEMPTS / SmsSecuritySpec.RETRY_MAX_ATTEMPTS}5 minutes as recovery fallback",
                    fontSize = 11.sp,
                    color    = OnSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun RetryPolicyCard() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector     = Icons.Default.Loop,
                    contentDescription = "Retry policy",
                    tint            = RoyalIndigo,
                    modifier        = Modifier.size(20.dp)
                )
                Text(
                    text       = "Exponential Backoff Policy",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = OnBg
                )
            }

            val retrySteps = listOf(
                "Attempt 1"  to "Retry in 30 seconds",
                "Attempt 2"  to "Retry in 2 minutes",
                "Attempt 3"  to "Retry in 10 minutes",
                "Attempt 4"  to "Retry in 1 hour",
                "Attempt 5+" to "Retry in 6 hours (cap)",
            )
            retrySteps.forEach { (attempt, delay) ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = attempt, fontSize = 12.sp, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(text = delay,   fontSize = 12.sp, color = OnSurface.copy(alpha = 0.8f))
                }
            }

            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector     = Icons.Default.Block,
                    contentDescription = "Permanent fail",
                    tint            = DangerRed,
                    modifier        = Modifier.size(16.dp)
                )
                Text(
                    text     = "HTTP 422 → Permanent fail (no retry) — server parse error",
                    fontSize = 11.sp,
                    color    = DangerRed
                )
            }
        }
    }
}

@Composable
private fun SpecReferenceCard() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = RoyalIndigo.copy(alpha = 0.06f)),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier            = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment   = Alignment.Top
        ) {
            Icon(
                imageVector     = Icons.Default.Shield,
                contentDescription = "Security spec",
                tint            = RoyalIndigo,
                modifier        = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text       = "Crypto Contract",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp,
                    color      = RoyalIndigo
                )
                Text(
                    text     = "HMAC-SHA256 · SHA-256 rawBodyHash · rawBody Immutability",
                    fontSize = 11.sp,
                    color    = OnSurface
                )
                Text(
                    text     = "See: utils/SmsSecuritySpec.kt ↔ backend/utils/smsSecuritySpec.js",
                    fontSize = 10.sp,
                    color    = OnSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
