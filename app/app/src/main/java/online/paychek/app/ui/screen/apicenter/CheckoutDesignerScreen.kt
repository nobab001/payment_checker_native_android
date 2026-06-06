package online.paychek.app.ui.screen.apicenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
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

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            // Can show a Snackbar or short Toast-like notification
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("গেটওয়ে কাস্টমাইজার", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.DragHandle, // Mock menu/back
                            contentDescription = "Back",
                            tint = Color.White
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
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sim Slot Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RoyalIndigo)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
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
                                        // Drag indicator icon
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
                                            
                                            // Show SIM bindings description
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

                                    // Switch + Reorder arrows row
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

                                        // Move Up button
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

                                        // Move Down button
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
        }
    }

    // SIM Slot configuration dialog pop-up
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("সিম ও মেথড সেটিংস", fontWeight = FontWeight.Bold, color = RoyalIndigo) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("এখানে সিম-১ ও সিম-২ কোন পেমেন্ট গেটওয়ের সাথে যুক্ত তা সিলেক্ট করুন:", fontSize = 12.sp, color = TextSecondary)
                    
                    // SIM 1 Settings
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

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // SIM 2 Settings
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
