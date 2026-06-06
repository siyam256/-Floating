package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.SavedLink
import com.example.db.SavedLinkDatabase
import com.example.db.SavedLinkRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF131118) // Premium Cosmic Space Onyx
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OverlayControllerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val database = remember { SavedLinkDatabase.getDatabase(context) }
    val repository = remember { SavedLinkRepository(database.savedLinkDao()) }
    
    val savedLinks by repository.allLinks.collectAsState(initial = emptyList())
    var linkTitle by remember { mutableStateOf("") }

    var isPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var targetUrl by remember { mutableStateOf("https://aistudio.google.com") }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = Settings.canDrawOverlays(context)
    }

    DisposableEffect(context) {
        isPermissionGranted = Settings.canDrawOverlays(context)
        onDispose { }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Aesthetic Gradient Header Widget
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF381E72), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1A22)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(12.dp, CircleShape)
                            .border(1.5.dp, Color(0xFFD0BCFF), CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF6750A4), Color(0xFF381E72))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Floating Browser Logo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Floating Browser",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Run web apps and scripts in a dynamic, draggable floating overlay container. Keeps Gemini canvas code and interactive animations working perfectly in the background.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // Overlay Permission Status Indicator & Floating controls
        item {
            val isServiceActive = FloatingBrowserService.isRunning
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
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
                                text = if (isPermissionGranted) "Overlay bubble active" else "System permission required",
                                color = if (isPermissionGranted) Color(0xFFD0BCFF) else Color(0xFFF59E0B),
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

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
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Permission", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        // URL input field
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Enter Target URL starting with http/https:",
                                color = Color(0xFFCAC4D0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            val focusManager = LocalFocusManager.current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color(0xFF131118), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF381E72), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = targetUrl,
                                        onValueChange = { targetUrl = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFFD0BCFF)),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(onGo = {
                                            focusManager.clearFocus()
                                            val intent = Intent(context, FloatingBrowserService::class.java).apply {
                                                putExtra("EXTRA_URL", targetUrl)
                                            }
                                            context.startService(intent)
                                        }),
                                        decorationBox = { innerTextField ->
                                            if (targetUrl.isEmpty()) {
                                                Text(
                                                    text = "Enter web URL...",
                                                    color = Color(0x7FFFFFFF),
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (targetUrl.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear URL",
                                            tint = Color(0xFFD0BCFF),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { targetUrl = "" }
                                        )
                                    }
                                }
                            }
                        }

                        // Link Saving & Bookmark Section
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Save & Bookmark Current Link:",
                                color = Color(0xFFD0BCFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .background(Color(0xFF131118), shape = RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = linkTitle,
                                        onValueChange = { linkTitle = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFFD0BCFF)),
                                        decorationBox = { innerTextField ->
                                            if (linkTitle.isEmpty()) {
                                                Text(
                                                    text = "Enter Link Name (e.g. Google Canvas)...",
                                                    color = Color(0x7FFFFFFF),
                                                    fontSize = 12.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (targetUrl.isNotBlank()) {
                                            val savedTitle = if (linkTitle.isBlank()) "Saved Link" else linkTitle
                                            coroutineScope.launch {
                                                repository.insert(SavedLink(title = savedTitle, url = targetUrl))
                                                linkTitle = ""
                                                android.widget.Toast.makeText(context, "Link saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Please enter a URL first!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF381E72),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Saved Links List
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "My Bookmarks (${savedLinks.size}):",
                                color = Color(0xFFCAC4D0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (savedLinks.isEmpty()) {
                                Text(
                                    text = "No saved bookmarks yet. Enter details above to bookmark link.",
                                    color = Color(0x66FFFFFF),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    savedLinks.forEach { savedLink ->
                                        Surface(
                                            modifier = Modifier
                                                .clickable {
                                                    targetUrl = savedLink.url
                                                    val intent = Intent(context, FloatingBrowserService::class.java).apply {
                                                        putExtra("EXTRA_URL", savedLink.url)
                                                    }
                                                    context.startService(intent)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Loading: ${savedLink.title}",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF131118),
                                            border = BorderStroke(1.dp, Color(0xFF381E72))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD0BCFF),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                
                                                Text(
                                                    text = savedLink.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Spacer(modifier = Modifier.width(2.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            coroutineScope.launch {
                                                                repository.delete(savedLink.id)
                                                                android.widget.Toast.makeText(context, "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color(0xFFF3B3B3),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val intent = Intent(context, FloatingBrowserService::class.java)
                                if (isServiceActive) {
                                    // If active, restart it with current active URL target
                                    intent.putExtra("EXTRA_URL", targetUrl)
                                    context.startService(intent)
                                } else {
                                    intent.putExtra("EXTRA_URL", targetUrl)
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isServiceActive) "Launch / Load URL" else "Start Floating Browser",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (isServiceActive) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, FloatingBrowserService::class.java)
                                    context.stopService(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB3261E),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stop Floating Service",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Informative Guide Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF381E72), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1A22)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 Floating Browser Guide",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "1. Enable overlay permissions by clicking \"Grant Permission\" above.\n\n" +
                               "2. Type any web link, save it for easy access, and tap \"Start Floating Browser\".\n\n" +
                               "3. Use the floating controls (Back, Forward, Refresh, Home) to easily navigate sites. No distracting address bar overlayed on your screen!\n\n" +
                               "4. Collapse the window into an ambient floating bubble. The background 1.dp kept-alive attachment technique guarantees script execution continues running without background freezes or layout suspensions.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
