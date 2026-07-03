package online.paychek.app.ui.screen.apicenter.docs

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val AccentCyan = Color(0xFF22D3EE)

/**
 * ApiDocsScreen — Step-by-step multi-language integration documentation.
 * Content is data-driven from [ApiDocsCatalog] so new languages/sections are
 * added without touching UI code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = ApiDocsCatalog.languages
    var selectedLang by remember { mutableStateOf(languages.first().id) }
    val lang = languages.first { it.id == selectedLang }

    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface
    val isDark = bg == Color(0xFF0B0E14)

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("ইন্টিগ্রেশন ডকুমেন্টেশন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Language selector
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { l ->
                    val sel = l.id == selectedLang
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (sel) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (sel) AccentCyan else Color(0xFF3A3F4A), RoundedCornerShape(20.dp))
                            .clickable { selectedLang = l.id }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(l.name, color = if (sel) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            HorizontalDivider(color = Color(0xFF2A2F3A))

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("সাধারণ তথ্য", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                ApiDocsCatalog.overviewSections.forEach { section ->
                    DocSectionCard(section, card, isDark)
                }
                HorizontalDivider(color = Color(0xFF2A2F3A))
                Text("ভাষা অনুযায়ী উদাহরণ — ${lang.name}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                lang.sections.forEach { section ->
                    DocSectionCard(section, card, isDark)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DocSectionCard(section: DocSection, card: Color, isDark: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column {
        Text(section.title, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (section.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(section.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (section.code.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF0F131C) else Color(0xFFF1F3F5))
                    .border(1.dp, Color(0xFF2A2F3A), RoundedCornerShape(12.dp))
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText("code", section.code).toClipEntry())
                                Toast.makeText(context, "কোড কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        Text(section.code, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
