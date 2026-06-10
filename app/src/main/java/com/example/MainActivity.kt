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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.SavedLink
import com.example.db.SavedLinkDatabase
import com.example.db.SavedLinkRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

data class AppThemeColors(
    val bg: Color,
    val card: Color,
    val secondaryCard: Color,
    val border: Color,
    val text: Color,
    val subText: Color,
    val primary: Color,
    val buttonBg: Color,
    val inputBg: Color,
    val inputBorder: Color
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkMode) {
                MainAppContainer(isDarkMode = isDarkMode, onThemeToggle = { isDarkMode = !isDarkMode })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainAppContainer(isDarkMode: Boolean, onThemeToggle: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val colors = AppThemeColors(
        bg = if (isDarkMode) Color(0xFF0F0D15) else Color(0xFFF6F5FA),
        card = if (isDarkMode) Color(0xFF161320) else Color(0xFFFFFFFF),
        secondaryCard = if (isDarkMode) Color(0xFF1B1827) else Color(0xFFF3EDFD),
        border = if (isDarkMode) Color(0xFF2E2442) else Color(0xFFEDEAFA),
        text = if (isDarkMode) Color.White else Color(0xFF1B1A20),
        subText = if (isDarkMode) Color(0xFFCAC4D0) else Color(0xFF5D596C),
        primary = if (isDarkMode) Color(0xFFD0BCFF) else Color(0xFF6750A4),
        buttonBg = if (isDarkMode) Color(0xFF381E72) else Color(0xFF65558F),
        inputBg = if (isDarkMode) Color(0xFF0F0D15) else Color(0xFFFFFFFF),
        inputBorder = if (isDarkMode) Color(0xFF3E315C) else Color(0xFFD6CFF0)
    )

    val database = remember { SavedLinkDatabase.getDatabase(context) }
    val repository = remember { SavedLinkRepository(database.savedLinkDao()) }
    val savedLinks by repository.allLinks.collectAsState(initial = emptyList())
    
    var targetUrl by remember { mutableStateOf("https://aistudio.google.com") }
    var linkTitle by remember { mutableStateOf("") }
    var linkUrlInput by remember { mutableStateOf("") }

    var isPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = Settings.canDrawOverlays(context)
    }

    DisposableEffect(context) {
        isPermissionGranted = Settings.canDrawOverlays(context)
        onDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Floating Browser",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                },
                actions = {
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.Star else Icons.Default.Settings,
                            contentDescription = "Toggle Theme",
                            tint = colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.card
                ),
                modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.card,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.border,
                        unselectedIconColor = colors.subText,
                        unselectedTextColor = colors.subText
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Star, contentDescription = "Bookmarks") },
                    label = { Text("Bookmarks", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.border,
                        unselectedIconColor = colors.subText,
                        unselectedTextColor = colors.subText
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Privacy Hub") },
                    label = { Text("Privacy Hub", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.border,
                        unselectedIconColor = colors.subText,
                        unselectedTextColor = colors.subText
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(
                    context = context,
                    colors = colors,
                    isDarkMode = isDarkMode,
                    isPermissionGranted = isPermissionGranted,
                    targetUrl = targetUrl,
                    onUrlChange = { targetUrl = it },
                    onRequestPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        settingsLauncher.launch(intent)
                    }
                )
                1 -> BookmarksTab(
                    context = context,
                    colors = colors,
                    savedLinks = savedLinks,
                    repository = repository,
                    linkTitle = linkTitle,
                    onTitleChange = { linkTitle = it },
                    linkUrlInput = linkUrlInput,
                    onUrlChange = { linkUrlInput = it },
                    activeTargetUrl = targetUrl,
                    onBookmarkSelected = { url ->
                        targetUrl = url
                        val intent = Intent(context, FloatingBrowserService::class.java).apply {
                            putExtra("EXTRA_URL", url)
                        }
                        context.startService(intent)
                    }
                )
                2 -> PrivacyHubTab(
                    context = context,
                    colors = colors
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    context: Context,
    colors: AppThemeColors,
    isDarkMode: Boolean,
    isPermissionGranted: Boolean,
    targetUrl: String,
    onUrlChange: (String) -> Unit,
    onRequestPermission: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isServiceActive = FloatingBrowserService.isRunning

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Identity Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.card),
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
                            .border(1.5.dp, colors.primary, CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isDarkMode) {
                                        listOf(Color(0xFF6750A4), Color(0xFF381E72))
                                    } else {
                                        listOf(Color(0xFFB19DFF), Color(0xFF6750A4))
                                    }
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
                        color = colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "A flexible companion overlay designed to display web pages directly in a draggable bubble. Perfect for background execution, script testing, and split-screen web companion panels.",
                        color = colors.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Overlay Permission Block & Controllers
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.secondaryCard),
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
                            tint = if (isPermissionGranted) Color(0xFF81C784) else Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay System Permission",
                                color = colors.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isPermissionGranted) "Permission active: Overlay bubble enabled" else "Required to draw over other apps",
                                color = if (isPermissionGranted) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    if (!isPermissionGranted) {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.buttonBg,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Draw Permission", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        // URL Input field
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Floating Browser URL Targets:",
                                color = colors.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val focusManager = LocalFocusManager.current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(colors.inputBg, shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, colors.inputBorder, RoundedCornerShape(12.dp))
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
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = targetUrl,
                                        onValueChange = onUrlChange,
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                                        singleLine = true,
                                        cursorBrush = SolidColor(colors.primary),
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
                                                    text = "Enter website URL layout (http/https)...",
                                                    color = colors.subText.copy(alpha = 0.6f),
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
                                            tint = colors.primary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onUrlChange("") }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Trigger actions
                        Button(
                            onClick = {
                                val intent = Intent(context, FloatingBrowserService::class.java).apply {
                                    putExtra("EXTRA_URL", targetUrl)
                                }
                                context.startService(intent)
                                android.widget.Toast.makeText(context, "Launching: $targetUrl", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.buttonBg,
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
                                text = if (isServiceActive) "Load Active URL" else "Start Draggable Browser",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (isServiceActive) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, FloatingBrowserService::class.java)
                                    context.stopService(intent)
                                    android.widget.Toast.makeText(context, "Stopped Floating Browser Service", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC53929),
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
                                    text = "Stop Draggable Overlay",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Feature Guidelines details
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF2E2442), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161320)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 Floating Browser Companion Manual",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "1. Tap 'Grant Draw Permission' to permit overlays.\n" +
                               "2. Type or pick your website url layout target & tap 'Start'.\n" +
                               "3. Drag the float bubble anywhere; click to toggle the screen space.\n" +
                               "4. Quick browser buttons let you navigate backward, forward, home, and instantly collapse back into a lightweight floating sphere.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarksTab(
    context: Context,
    colors: AppThemeColors,
    savedLinks: List<SavedLink>,
    repository: SavedLinkRepository,
    linkTitle: String,
    onTitleChange: (String) -> Unit,
    linkUrlInput: String,
    onUrlChange: (String) -> Unit,
    activeTargetUrl: String,
    onBookmarkSelected: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Bookmarks input form header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Bookmarks Manager",
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Add and save websites layout below for immediate launch triggers from this dashboard.",
                    color = colors.subText,
                    fontSize = 13.sp
                )
            }
        }

        // Bookmark Addition Form
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.secondaryCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Create Bookmark Target",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Link name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bookmark Name", color = colors.subText, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(colors.inputBg, shape = RoundedCornerShape(10.dp))
                                .border(1.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = linkTitle,
                                onValueChange = onTitleChange,
                                textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(colors.primary),
                                decorationBox = { innerTextField ->
                                    if (linkTitle.isEmpty()) {
                                        Text("e.g. Google AI Studio Canvas", color = colors.subText.copy(alpha = 0.5f), fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    // Link url
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Website URL", color = colors.subText, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(colors.inputBg, shape = RoundedCornerShape(10.dp))
                                .border(1.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = linkUrlInput,
                                onValueChange = onUrlChange,
                                textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(colors.primary),
                                decorationBox = { innerTextField ->
                                    if (linkUrlInput.isEmpty()) {
                                        Text("e.g. https://aistudio.google.com", color = colors.subText.copy(alpha = 0.5f), fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    // Auto-fill active url option
                    if (activeTargetUrl.isNotBlank() && activeTargetUrl != "https://aistudio.google.com" && linkUrlInput.isEmpty()) {
                        Button(
                            onClick = { onUrlChange(activeTargetUrl) },
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use active link target: $activeTargetUrl", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Button(
                        onClick = {
                            if (linkUrlInput.isNotBlank()) {
                                val resolvedTitle = if (linkTitle.isBlank()) "Saved Bookmark" else linkTitle
                                coroutineScope.launch {
                                    repository.insert(SavedLink(title = resolvedTitle, url = linkUrlInput))
                                    onTitleChange("")
                                    onUrlChange("")
                                    android.widget.Toast.makeText(context, "Bookmark Saved Cleanly!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Please enter a valid URL layout!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.buttonBg,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Bookmark Link", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List Bookmarks Section Title
        item {
            Text(
                text = "My Saved Bookmarks (${savedLinks.size})",
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (savedLinks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = colors.primary.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved bookmarks yet",
                            color = colors.text.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add your frequently visited sites or custom scripts above to save them.",
                            color = colors.subText.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(savedLinks.size) { index ->
                val link = savedLinks[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .clickable { onBookmarkSelected(link.url) },
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    shape = RoundedCornerShape(12.dp)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colors.secondaryCard, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = link.title,
                                    color = colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = link.url,
                                    color = colors.subText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Launch Icon Button
                            IconButton(
                                onClick = { onBookmarkSelected(link.url) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.secondaryCard, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Launch",
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Delete Button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.delete(link.id)
                                        android.widget.Toast.makeText(context, "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.card, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFC53929),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyHubTab(context: Context, colors: AppThemeColors) {
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SavedLinkDatabase.getDatabase(context) }
    val repository = remember { SavedLinkRepository(database.savedLinkDao()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Tab Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Privacy Policy & Trust",
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Official information disclosure details required to submit and release Floating Browser cleanly on Google Play Console.",
                    color = colors.subText,
                    fontSize = 13.sp
                )
            }
        }

        // Complete Legal compliance statement card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.secondaryCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "🛡️ Privacy Protection Statement",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "At Floating Browser, we understand that trust is the foundation of our utility software. We hold ourselves to extreme privacy-respecting development standards:",
                        color = colors.text,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    // Details
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrivacyDetailRow(
                            colors = colors,
                            title = "Local SQLite Storage Only",
                            description = "Your saved bookmark lists and URLs are saved inside your device's sandbox environment via SQLite Room. ZERO database synchronization to third-party endpoints occurs."
                        )
                        PrivacyDetailRow(
                            colors = colors,
                            title = "No Personal Data collection",
                            description = "This app collects absolutely NO names, emails, telemetry logs, device identifiers, or tracking metrics. There is no analytic SDK bundled within this code."
                        )
                        PrivacyDetailRow(
                            colors = colors,
                            title = "WebView Sandbox & Session Control",
                            description = "Web browsing happens strictly via Android System WebKit components. We clear WebView start caches to ensure a clean sandbox experience with every initial load."
                        )
                        PrivacyDetailRow(
                            colors = colors,
                            title = "Justified System Permissions",
                            description = "Our companion services require 'System Alert Window' to create the physical floating bubble layout. This windowing allows code animations and web scripts to continue executing transparently while using other utilities."
                        )
                    }

                    HorizontalDivider(color = colors.border, thickness = 1.dp)

                    Text(
                        text = "This legal summary constitutes a fully legitimate Privacy Policy representation. If your developer dashboard requests a policy URL, you may reference our absolute user-facing protection declarations above.",
                        color = colors.subText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Compliance & Local Data Deletion Control Card (Solves Data Deletion Rules for Play Store)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Play Store Data Safety Compliance",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    Text(
                        text = "In obedience to modern Google Play Console guidelines, you can request total data elimination instantly. Press the button below to purge all sandbox bookmark records.",
                        color = colors.subText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("APP VERSION", color = colors.subText, fontSize = 9.sp)
                            Text("1.0.4 COMPLIANT", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DATA HOSTING", color = colors.subText, fontSize = 9.sp)
                            Text("OFFLINE-ONLY LOCAL", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteAll()
                                android.widget.Toast.makeText(context, "All Saved Bookmarks Purged Cleanly!", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC53929)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete All Local Bookmarks Data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyDetailRow(colors: AppThemeColors, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.primary, CircleShape)
            )
            Text(
                text = title,
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = description,
            color = colors.subText,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
