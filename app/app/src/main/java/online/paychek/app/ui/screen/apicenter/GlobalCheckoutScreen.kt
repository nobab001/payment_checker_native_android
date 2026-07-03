package online.paychek.app.ui.screen.apicenter

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.ui.screen.apicenter.website.WebsiteCheckoutLiveEditor

private val AccentCyan = Color(0xFF22D3EE)
private val AccentGreen = Color(0xFF10B981)

private val CHECKOUT_TAB_KEYS = listOf(
    "send_money" to "Send Money",
    "cash_out" to "Cash Out",
    "payment" to "Payment",
    "bank" to "Bank",
    "card" to "Card Payment"
)

/**
 * Global Checkout Page — changes here propagate live to every merchant website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalCheckoutScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlobalCheckoutViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surface

    var theme by remember(state.checkoutTheme) { mutableStateOf(state.checkoutTheme.takeIf { it.startsWith("design-") } ?: "design-1") }
    var checkoutMode by remember(state.checkoutMode) { mutableStateOf(state.checkoutMode) }
    var designerTab by remember { mutableIntStateOf(0) }

    val tabStates = remember {
        CHECKOUT_TAB_KEYS.associate { (key, _) -> key to mutableStateOf(key != "bank") }
    }
    LaunchedEffect(state.checkoutTabs) {
        CHECKOUT_TAB_KEYS.forEach { (key, _) ->
            state.checkoutTabs[key]?.let { tabStates[key]?.value = it.enabled }
        }
    }

    LaunchedEffect(state.error, state.infoMessage) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
        state.infoMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessages() }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Check Out Page", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "গ্লোবাল — ${state.websiteCount}টি ওয়েবসাইটে প্রয়োগ হবে",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card)
            )
        },
        modifier = modifier
    ) { padding ->
        if (state.isLoading && state.checkoutNumbers.isEmpty() && state.checkoutTabs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(0.12f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, null, tint = AccentCyan)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "এখানে সংরক্ষণ করলে আপনার সব ওয়েবসাইটের চেকআউট পেজ একসাথে আপডেট হবে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            TabRow(
                selectedTabIndex = designerTab,
                containerColor = card,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                divider = {}
            ) {
                Tab(selected = designerTab == 0, onClick = { designerTab = 0 }) {
                    Text("ডিজাইনার সেটিংস", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = designerTab == 1, onClick = { designerTab = 1 }) {
                    Text("কাস্টমার প্রিভিউ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            val tabMap = CHECKOUT_TAB_KEYS.associate { (key, _) -> key to (tabStates[key]?.value ?: true) }
            val previewNumbers = state.checkoutNumbers.filter { it.enabled }

            when (designerTab) {
                0 -> {
                    WebsiteCheckoutLiveEditor(
                        companyName = "Paychek",
                        logoUrl = null,
                        checkoutMode = checkoutMode,
                        onCheckoutModeChange = { checkoutMode = it },
                        design = theme,
                        onDesignChange = { theme = it },
                        tabStates = tabMap,
                        onTabToggle = { key, enabled -> tabStates[key]?.value = enabled },
                        numbers = state.checkoutNumbers,
                        editable = true,
                        onMoveNumber = { f, t ->
                            viewModel.moveCheckoutNumber(f, t)
                            viewModel.scheduleAutoSave(theme, checkoutMode, tabMap)
                        },
                        onToggleNumber = { id, en ->
                            viewModel.toggleCheckoutNumber(id, en)
                            viewModel.scheduleAutoSave(theme, checkoutMode, tabMap)
                        },
                        checkoutTabs = state.checkoutTabs,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.save(theme, checkoutMode, tabMap) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                "সব ওয়েবসাইটে সংরক্ষণ করুন (${state.websiteCount})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                1 -> {
                    WebsiteCheckoutLiveEditor(
                        companyName = "Paychek",
                        logoUrl = null,
                        checkoutMode = checkoutMode,
                        onCheckoutModeChange = {},
                        design = theme,
                        onDesignChange = {},
                        tabStates = tabMap,
                        onTabToggle = { _, _ -> },
                        numbers = previewNumbers,
                        editable = false,
                        checkoutTabs = state.checkoutTabs,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
