package online.paychek.app.ui.screen.apicenter.docs

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0E1117)
private val Panel = Color(0xFF161B22)
private val Line = Color(0xFF2A313C)
private val Accent = Color(0xFF1AA6A0)
private val AccentDim = Color(0xFF0D7370)
private val Warm = Color(0xFFC4A574)
private val Muted = Color(0xFF8B949E)

private enum class DocsTab(val label: String) {
    OVERVIEW("ওভারভিউ"),
    ADD_BALANCE("Add Balance"),
    PAYMENT("Payment"),
    BOTH("Both"),
    CODE("কোড")
}

/**
 * Integration documentation — opened from Website Settings (primary) or API tab.
 * Layout: quiet editorial panels, numbered steps, framework chips — not generic “AI purple”.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialWebsitePurpose: String? = null
) {
    val languages = ApiDocsCatalog.languages
    var selectedLang by remember { mutableStateOf(languages.first().id) }
    val lang = languages.first { it.id == selectedLang }

    var tab by remember {
        mutableStateOf(
            when (initialWebsitePurpose) {
                "payment" -> DocsTab.PAYMENT
                "both" -> DocsTab.BOTH
                "add_balance" -> DocsTab.ADD_BALANCE
                else -> DocsTab.OVERVIEW
            }
        )
    }

    val bg = MaterialTheme.colorScheme.background
    val isDark = bg == Color(0xFF0B0E14) || bg == Ink
    val surface = if (isDark) Panel else Color(0xFFF7F5F1)
    val onBg = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1C1917)
    val muted = if (isDark) Muted else Color(0xFF78716C)
    val line = if (isDark) Line else Color(0xFFE7E5E4)
    val codeBg = if (isDark) Color(0xFF0D1117) else Color(0xFF1C1917)
    val codeFg = if (isDark) Color(0xFFD1D5DB) else Color(0xFFF5F5F4)

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ইন্টিগ্রেশন গাইড", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = onBg)
                        Text("ওয়েবসাইট সেটিংস থেকে ডেভেলপারদের জন্য", fontSize = 11.sp, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = onBg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Hero strip
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                if (isDark) AccentDim.copy(alpha = 0.35f) else Accent.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        "এক ওয়েবসাইট → এক Purpose → স্পষ্ট API",
                        color = onBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "নিচের ট্যাব থেকে মোড বুঝুন, তারপর আপনার ফ্রেমওয়ার্কের স্টেপ ফলো করুন।",
                        color = muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // Topic tabs
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocsTab.entries.forEach { t ->
                    val sel = tab == t
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Accent.copy(alpha = if (isDark) 0.22f else 0.16f) else Color.Transparent)
                            .border(1.dp, if (sel) Accent else line, RoundedCornerShape(8.dp))
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            t.label,
                            color = if (sel) Accent else muted,
                            fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }

            if (tab == DocsTab.CODE) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { l ->
                        val sel = l.id == selectedLang
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Warm.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (sel) Warm else line, RoundedCornerShape(20.dp))
                                .clickable { selectedLang = l.id }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                l.name,
                                color = if (sel) Warm else muted,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = line)

            val sections = when (tab) {
                DocsTab.OVERVIEW -> ApiDocsCatalog.overviewSections
                DocsTab.ADD_BALANCE -> ApiDocsCatalog.addBalanceGuide
                DocsTab.PAYMENT -> ApiDocsCatalog.paymentGuide
                DocsTab.BOTH -> ApiDocsCatalog.bothGuide
                DocsTab.CODE -> lang.sections
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (tab == DocsTab.CODE) {
                    Text(
                        lang.name,
                        color = onBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "স্টেপ অনুযায়ী কপি করে আপনার প্রজেক্টে বসান। Secret শুধু সার্ভারে।",
                        color = muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }

                sections.forEach { section ->
                    DocStepCard(
                        section = section,
                        surface = surface,
                        onBg = onBg,
                        muted = muted,
                        line = line,
                        codeBg = codeBg,
                        codeFg = codeFg,
                        isDark = isDark
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun DocStepCard(
    section: DocSection,
    surface: Color,
    onBg: Color,
    muted: Color,
    line: Color,
    codeBg: Color,
    codeFg: Color,
    isDark: Boolean
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.18f))
                    .border(1.dp, Accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    section.step?.toString() ?: "·",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (section.code.isNotBlank() || section.description.isNotBlank()) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(line)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(section.title, color = onBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (section.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(section.description, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (section.code.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBg)
                        .border(1.dp, line.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 2.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("কোড", color = Warm.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipData.newPlainText("code", section.code).toClipEntry()
                                        )
                                        Toast.makeText(context, "কোড কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = Accent, modifier = Modifier.size(15.dp))
                            }
                        }
                        Box(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            Text(
                                section.code,
                                color = codeFg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
