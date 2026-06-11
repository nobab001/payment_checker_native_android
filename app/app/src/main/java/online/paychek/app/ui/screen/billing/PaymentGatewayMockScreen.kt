package online.paychek.app.ui.screen.billing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.paychek.app.ui.theme.RoyalIndigo

private val GateBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val GateCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val TextW: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextM: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewayMockScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ConstructAnimation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Scaffold(
        containerColor = GateBg,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(56.dp),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = {
                    Text(
                        text = "পেমেন্ট গেটওয়ে আপগ্রেড",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalIndigo
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GateBg)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Premium animated pulsing circle for construction theme
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                    .border(2.dp, Color(0xFFF59E0B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Construction,
                    contentDescription = "Under Construction",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = GateCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "পেমেন্ট গেটওয়ে আপডেট চলছে",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextW,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "⚠️ বর্তমানে পেমেন্ট গেটওয়ে আপগ্রেডের কাজ চলছে। সাময়িকভাবে সাবস্ক্রিপশন বা রিনিউ করতে অনুগ্রহ করে সরাসরি এডমিনের সাথে যোগাযোগ করুন।",
                        fontSize = 14.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "ফিরে যান",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.weight(1.2f))
        }
    }
}
