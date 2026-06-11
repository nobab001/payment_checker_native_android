package online.paychek.app.ui.screen.apicenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutDesignerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutDesignerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Dialog state controllers
    var tempSim1Method by remember { mutableStateOf("") }
    var tempSim1Number by remember { mutableStateOf("") }
    var tempSim2Method by remember { mutableStateOf("") }
    var tempSim2Number by remember { mutableStateOf("") }

    // Preview Mode selected provider & number index
    var selectedProviderFilter by remember { mutableStateOf("") }
    var selectedNumberIndex by remember { mutableIntStateOf(0) }

    // Input fields inside checkout preview
    var previewAmount by remember { mutableStateOf("500") }
    var previewTrxId by remember { mutableStateOf("") }
    var verificationSuccessMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = { Text("গেটওয়ে কাস্টমাইজার", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DragHandle, // Mock menu/back
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tempSim1Method = uiState.sim1Method
                        tempSim1Number = uiState.sim1Number
                        tempSim2Method = uiState.sim2Method
                        tempSim2Number = uiState.sim2Number
                        showSettingsDialog = true
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sim Slot Settings",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
        ) {
            // Twin-Mode Tabs: Edit Mode vs Customer Preview Mode
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = CardBackground,
                contentColor = RoyalIndigo,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                        color = RoyalIndigo
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("ডিজাইনার সেটিংস", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("কাস্টমার প্রিভিউ", fontWeight = FontWeight.Bold) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (uiState.selectedTab == 0) {
                    // =========================================================================
                    // EDIT MODE (ডিজাইনার সেটিংস)
                    // =========================================================================
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "পেমেন্ট নোটিফিকেশনের ক্রম সাজান (Drag & Order):",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            Text(
                                text = "গ্রাহক যখন পেমেন্ট পেইজ ওপেন করবে, তখন এখানে সাজানো সিরিয়াল অনুযায়ী মেথডগুলো উপরে-নিচে দেখতে পাবে।",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Saved Status notification card
                            uiState.statusMessage?.let { status ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.clearStatusMessage() }
                                ) {
                                    Text(
                                        text = status,
                                        color = StatusGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Reorderable list of blocks
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(uiState.blocks) { index, block ->
                                    val isSim1Bound = uiState.sim1Method.lowercase() == block.providerTag.lowercase()
                                    val isSim2Bound = uiState.sim2Method.lowercase() == block.providerTag.lowercase()

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Drag Handle",
                                                    tint = Color.LightGray
                                                )

                                                Column {
                                                    Text(
                                                        text = block.name,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when (block.providerTag) {
                                                            "bKash" -> BkashPink
                                                            "Nagad" -> NagadOrange
                                                            "Rocket" -> RocketPurple
                                                            "Upay" -> UpayTeal
                                                            else -> TextPrimary
                                                        }
                                                    )
                                                    
                                                    val bindingText = when {
                                                        isSim1Bound && isSim2Bound -> "Bound: SIM 1 (${uiState.sim1Number}) & SIM 2 (${uiState.sim2Number})"
                                                        isSim1Bound -> "Bound: SIM 1 (${uiState.sim1Number})"
                                                        isSim2Bound -> "Bound: SIM 2 (${uiState.sim2Number})"
                                                        else -> "কোনো সিম সংযুক্ত নেই (inactive)"
                                                    }
                                                    Text(
                                                        text = bindingText,
                                                        fontSize = 12.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Switch(
                                                    checked = block.isEnabled,
                                                    onCheckedChange = { viewModel.toggleBlock(block.id) },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = RoyalIndigo,
                                                        checkedTrackColor = RoyalIndigoLight.copy(alpha = 0.5f)
                                                    )
                                                )

                                                IconButton(
                                                    onClick = { viewModel.moveBlockUp(index) },
                                                    enabled = index > 0
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowUpward,
                                                        contentDescription = "Move Up",
                                                        tint = if (index > 0) RoyalIndigo else Color.LightGray
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.moveBlockDown(index) },
                                                    enabled = index < uiState.blocks.size - 1
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDownward,
                                                        contentDescription = "Move Down",
                                                        tint = if (index < uiState.blocks.size - 1) RoyalIndigo else Color.LightGray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Save button
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = RoyalIndigo,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp)
                            )
                        } else {
                            Button(
                                onClick = { viewModel.saveLayout() },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ডিজাইন সংরক্ষণ করুন",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // =========================================================================
                    // CUSTOMER PREVIEW MODE (কাস্টমার প্রিভিউ)
                    // =========================================================================
                    if (uiState.activeMethods.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "No active configurations",
                                    tint = NagadOrange,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "কোনো সচল মেথড পাওয়া যায়নি।",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "অনুগ্রহ করে আপনার ফোনে গেটওয়ে নম্বর সক্রিয় করুন।",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Group active methods by provider for master filter
                        val grouped = uiState.activeMethods.groupBy { it.provider }
                        val providers = grouped.keys.toList()

                        if (selectedProviderFilter.isEmpty() || !providers.contains(selectedProviderFilter)) {
                            selectedProviderFilter = providers.firstOrNull() ?: ""
                            selectedNumberIndex = 0
                        }

                        val activeNumbersForProvider = grouped[selectedProviderFilter] ?: emptyList()
                        val currentMethod = activeNumbersForProvider.getOrNull(selectedNumberIndex)

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Master Filter Header (সিরিয়াল অনুযায়ী গ্রুপ করা)
                            Text(
                                text = "সচল পেমেন্ট মেথড ফিল্টার:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            // Provider Tabs
                            ScrollableTabRow(
                                selectedTabIndex = providers.indexOf(selectedProviderFilter).coerceAtLeast(0),
                                containerColor = Color.Transparent,
                                edgePadding = 0.dp
                            ) {
                                providers.forEachIndexed { idx, provider ->
                                    val isSelected = provider == selectedProviderFilter
                                    Tab(
                                        selected = isSelected,
                                        onClick = {
                                            selectedProviderFilter = provider
                                            selectedNumberIndex = 0
                                            verificationSuccessMessage = null
                                        },
                                        text = {
                                            Text(
                                                text = "$provider (${grouped[provider]?.size ?: 0})",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) RoyalIndigo else TextSecondary
                                            )
                                        }
                                    )
                                }
                            }

                            // Active Numbers select within selected provider
                            if (activeNumbersForProvider.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    activeNumbersForProvider.forEachIndexed { idx, method ->
                                        val isSelected = idx == selectedNumberIndex
                                        Button(
                                            onClick = {
                                                selectedNumberIndex = idx
                                                verificationSuccessMessage = null
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) RoyalIndigo else Color.LightGray.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = method.number ?: "No Number",
                                                color = if (isSelected) Color.White else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Simulated Checkout Render
                            if (currentMethod != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        // Title
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = currentMethod.displayName ?: currentMethod.provider,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (currentMethod.provider) {
                                                    "bKash" -> BkashPink
                                                    "Nagad" -> NagadOrange
                                                    "Rocket" -> RocketPurple
                                                    "Upay" -> UpayTeal
                                                    else -> TextPrimary
                                                }
                                            )
                                            Badge(
                                                containerColor = StatusGreen.copy(alpha = 0.2f),
                                                contentColor = StatusGreen
                                            ) {
                                                Text("SIM ${currentMethod.simSlot}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Server-Side Security Lock check & indicator
                                        if (currentMethod.templateId == null) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "Security Lock",
                                                        tint = Color.Red
                                                    )
                                                    Text(
                                                        text = "🔒 Security Lock: এই মেথডটির কোনো টেমপ্লেট সেট করা নেই, তাই এটি গ্রাহক চেকআউট পেজে প্রদর্শিত হবে না।",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Red
                                                    )
                                                }
                                            }
                                        } else {
                                            // Render instruction based on total count
                                            val count = activeNumbersForProvider.size
                                            val instructionText = if (count > 1) {
                                                currentMethod.multipleNumberInstruction ?: "নিচের যেকোনো একটি সক্রিয় নম্বরে Send Money করুন।"
                                            } else {
                                                currentMethod.singleNumberInstruction ?: "নিচের নম্বরে Send Money করুন।"
                                            }

                                            Text(
                                                text = instructionText,
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            // Number card
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = AppBackground),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("ফোন নম্বর:", fontSize = 12.sp, color = TextSecondary)
                                                    Text(
                                                        text = currentMethod.number ?: "",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                }
                                            }

                                            HorizontalDivider()

                                            // Input verification simulation
                                            Text("পেমেন্ট যাচাই করুন:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                                            OutlinedTextField(
                                                value = previewAmount,
                                                onValueChange = { previewAmount = it },
                                                label = { Text("পেমেন্ট অ্যামাউন্ট (টাকা)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )

                                            OutlinedTextField(
                                                value = previewTrxId,
                                                onValueChange = { previewTrxId = it },
                                                label = { Text("ট্রানজেকশন আইডি (TrxID)") },
                                                placeholder = { Text("যেমন: K8F7G6S8") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )

                                            if (verificationSuccessMessage != null) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Verified", tint = StatusGreen)
                                                    Text(text = verificationSuccessMessage!!, color = StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    if (previewTrxId.isNotBlank()) {
                                                        verificationSuccessMessage = "পেমেন্ট সফলভাবে যাচাই করা হয়েছে! অর্ডার কনফার্মড।"
                                                    }
                                                },
                                                enabled = previewTrxId.isNotBlank(),
                                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("পেমেন্ট ভেরিফাই করুন", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // SIM Slot settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("সিম ও মেথড সেটিংস", fontWeight = FontWeight.Bold, color = RoyalIndigo) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("এখানে সিম-১ ও সিম-২ কোন পেমেন্ট গেটওয়ের সাথে যুক্ত তা সিলেক্ট করুন:", fontSize = 12.sp, color = TextSecondary)
                    
                    Text("সিম ১ (SIM 1) সেটিংস:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempSim1Method,
                        onValueChange = { tempSim1Method = it },
                        label = { Text("সিম ১ মেথড (যেমন: bKash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempSim1Number,
                        onValueChange = { tempSim1Number = it },
                        label = { Text("সিম ১ নম্বর") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("সিম ২ (SIM 2) সেটিংস:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempSim2Method,
                        onValueChange = { tempSim2Method = it },
                        label = { Text("সিম ২ মেথড (যেমন: Nagad)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempSim2Number,
                        onValueChange = { tempSim2Number = it },
                        label = { Text("সিম ২ নম্বর") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSimConfig(tempSim1Method, tempSim1Number, tempSim2Method, tempSim2Number)
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                ) {
                    Text("সংরক্ষণ করুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("বাতিল", color = TextSecondary)
                }
            }
        )
    }
}
