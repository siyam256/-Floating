package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF1C1B1F)
                ) { innerPadding ->
                    OverlayControllerScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun OverlayControllerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // Check permission state in real-time
    var isPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = Settings.canDrawOverlays(context)
    }

    // Direct effect to re-evaluate when window regains focus (e.g. user back from settings)
    DisposableEffect(context) {
        isPermissionGranted = Settings.canDrawOverlays(context)
        onDispose { }
    }

    Column(
        modifier = modifier
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        
        // App Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                    .background(
                        color = Color(0xFFD0BCFF),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color(0xFF381E72),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Floating Browser Icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            Text(
                text = "Floating Browser",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Compact overlay browser supporting background persistence, dragging, and full screen resize.",
                color = Color(0xFFCAC4D0),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // Status Card Grid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Feature 1: Draw overlay permission status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Overlay State Indicator",
                        tint = if (isPermissionGranted) Color(0xFFD0BCFF) else Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isPermissionGranted) "Permission Enabled" else "Action Required",
                            color = if (isPermissionGranted) Color(0xFFD0BCFF) else Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )
                    }
                }

                Divider(color = Color(0xFF49454F), thickness = 1.dp)

                // Feature 2: Service tracker
                val isServiceActive = FloatingBrowserService.isRunning
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = "Engine Service Indicator",
                        tint = if (isServiceActive) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browser Service Engine",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isServiceActive) "Active & Floating" else "Stopped",
                            color = if (isServiceActive) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Controller Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isPermissionGranted) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        settingsLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant Overlay Permission",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                
                Text(
                    text = "Requesting permission allows the browser to draw over other running application layers.",
                    color = Color(0xFFCAC4D0),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                val isServiceActive = FloatingBrowserService.isRunning
                
                Button(
                    onClick = {
                        val intent = Intent(context, FloatingBrowserService::class.java)
                        if (isServiceActive) {
                            context.stopService(intent)
                        } else {
                            context.startService(intent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceActive) Color(0xFFB3261E) else Color(0xFF6750A4),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isServiceActive) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceActive) "Stop Floating Browser" else "Start Floating Browser",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Detailed Usage Guides Dashboard
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💡 Floating Browser Actions:",
                            color = Color(0xFFD0BCFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Click 'Start Floating Browser' to spawn the 🌐 bubble indicator.\n" +
                                   "• Drag the bubble with your finger around to position it.\n" +
                                   "• Click the bubble once to expand it into your functional Web Browser overlay.\n" +
                                   "• Drag the top border header of the window to slide it around.\n" +
                                   "• Drag the bottom-right corner scale tool (📐) to resize the browser.\n" +
                                   "• Tap Minimize (➖) in the window top bar to return to bubble mode while the active web page and sessions remain intact.",
                            color = Color(0xFFCAC4D0),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", color = Color.White, modifier = modifier)
}

