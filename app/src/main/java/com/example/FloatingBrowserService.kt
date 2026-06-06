package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.net.Uri
import android.graphics.Rect
import android.app.ActivityOptions
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?
)

@Composable
fun rememberDrawablePainter(drawable: android.graphics.drawable.Drawable?): androidx.compose.ui.graphics.painter.Painter {
    return remember(drawable) {
        if (drawable == null) {
            androidx.compose.ui.graphics.painter.ColorPainter(Color.Gray)
        } else {
            object : androidx.compose.ui.graphics.painter.Painter() {
                override val intrinsicSize: androidx.compose.ui.geometry.Size
                    get() = androidx.compose.ui.geometry.Size(
                        drawable.intrinsicWidth.toFloat().coerceAtLeast(1f),
                        drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
                    )

                override fun DrawScope.onDraw() {
                    drawIntoCanvas { canvas ->
                        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        drawable.draw(canvas.nativeCanvas)
                    }
                }
            }
        }
    }
}

fun launchAppInFreeform(context: Context, packageName: String) {
    try {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "This app cannot be launched!", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        
        val options = ActivityOptions.makeBasic()
        try {
            val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            method.invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val width = (screenWidth * 0.8f).toInt()
            val height = (screenHeight * 0.7f).toInt()
            val left = (screenWidth - width) / 2
            val top = (screenHeight - height) / 2
            options.launchBounds = Rect(left, top, left + width, top + height)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        context.startActivity(intent, options.toBundle())
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

class FloatingBrowserService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        var isRunning by mutableStateOf(false)
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = serviceViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams

    private var isExpanded by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")

    private var windowWidth by mutableStateOf(800)
    private var windowHeight by mutableStateOf(1100)

    private var posX by mutableStateOf(100)
    private var posY by mutableStateOf(200)

    private var dpScale: Float = 1f
    private val installedApps = mutableStateListOf<InstalledApp>()
    private var isLoadingApps by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundServiceWithNotification()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        dpScale = metrics.density

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        windowWidth = (screenWidth * 0.85f).toInt().coerceIn(400, 1000)
        windowHeight = (screenHeight * 0.60f).toInt().coerceIn(500, 1400)
        
        posX = (screenWidth - windowWidth) / 2
        posY = (screenHeight - windowHeight) / 3

        loadInstalledApps()
        setupFloatingWindow()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "floating_launcher_channel"
        val channelName = "Floating Launcher Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running floating application launcher in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Apps Launcher are Active")
            .setContentText("Tap overlay bubble to select and launch apps in floating windows")
            .setSmallIcon(android.R.drawable.ic_menu_search) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    202611,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(202611, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startForeground(202611, notification)
        }
    }

    private fun loadInstalledApps() {
        isLoadingApps = true
        Thread {
            try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val appList = resolveInfos.map { resolveInfo ->
                    val name = resolveInfo.loadLabel(pm).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    val icon = resolveInfo.activityInfo.loadIcon(pm)
                    InstalledApp(name, packageName, icon)
                }.sortedBy { it.name.lowercase() }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    installedApps.clear()
                    installedApps.addAll(appList)
                    isLoadingApps = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isLoadingApps = false
                }
            }
        }.start()
    }

    private fun setupFloatingWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            (60 * dpScale).toInt(),
            (60 * dpScale).toInt(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBrowserService)
            setViewTreeViewModelStoreOwner(this@FloatingBrowserService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBrowserService)

            setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    FloatingContent()
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    @Composable
    private fun FloatingContent() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isExpanded) {
                BrowserWindow()
            } else {
                BrowserBubble()
            }
        }
    }

    @Composable
    private fun BrowserBubble() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        var accumulatedDrag = 0f
                        var hasMoved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) {
                                if (!hasMoved) {
                                    toggleExpandState()
                                }
                                break
                            }
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val diff = change.position - change.previousPosition
                                val dist = kotlin.math.abs(diff.x) + kotlin.math.abs(diff.y)
                                accumulatedDrag += dist
                                if (accumulatedDrag > 15f) {
                                    hasMoved = true
                                }
                                if (hasMoved) {
                                    change.consume()
                                    updatePosition(diff.x.toInt(), diff.y.toInt())
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape)
                    .border(1.5.dp, Color(0xFFD0BCFF), CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF381E72), Color(0xFF1C0D3D))
                        ),
                        shape = CircleShape
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x22FFFFFF), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Expand Floating Apps Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun BrowserWindow() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFF381E72), RoundedCornerShape(20.dp))
                .background(Color(0xFF131118))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color(0xFF211F26))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                updatePosition(dragAmount.x.toInt(), dragAmount.y.toInt())
                            }
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFD0BCFF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Floating Apps Launcher",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { toggleExpandState() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Minimize Launcher Panel",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { stopSelf() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Floating Launcher",
                                tint = Color(0xFFF3B3B3),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2C2A35))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131118))
                        .padding(12.dp)
                ) {
                    val focusManager = LocalFocusManager.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0xFF211F26), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF381E72), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(Color(0xFFD0BCFF)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search installed apps...",
                                            color = Color(0x7FFFFFFF),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search Text",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2C2A35))
                )

                val filteredList = if (searchQuery.isBlank()) {
                    installedApps
                } else {
                    installedApps.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (isLoadingApps) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching apps found", color = Color(0x7FFFFFFF), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent, shape = RoundedCornerShape(10.dp))
                                    .clickable {
                                        toggleExpandState()
                                        launchAppInFreeform(this@FloatingBrowserService, app.packageName)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val appPainter = rememberDrawablePainter(app.icon)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(3.dp, RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1C1A22), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = appPainter,
                                        contentDescription = app.name,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = Color(0x99FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Launch",
                                    tint = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xFF211F26))
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap an app to launch in dynamic floating mode",
                        color = Color(54000 /* 0x99FFFFFF-ish grey */).copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val minW = (240 * dpScale).toInt()
                            val minH = (320 * dpScale).toInt()
                            windowWidth = (windowWidth + dragAmount.x.toInt()).coerceAtLeast(minW)
                            windowHeight = (windowHeight + dragAmount.y.toInt()).coerceAtLeast(minH)
                            updateWindowBounds()
                        }
                    },
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Scale Window Dimension",
                    tint = Color(0xFFD0BCFF).copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(bottom = 3.dp, end = 3.dp)
                )
            }
        }
    }

    private fun toggleExpandState() {
        isExpanded = !isExpanded
        updateWindowDimensions()
    }

    private fun updateWindowDimensions() {
        if (isExpanded) {
            params.width = windowWidth
            params.height = windowHeight
            params.x = posX
            params.y = posY
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.width = (60 * dpScale).toInt()
            params.height = (60 * dpScale).toInt()
            params.x = posX
            params.y = posY
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePosition(dx: Int, dy: Int) {
        posX += dx
        posY += dy
        params.x = posX
        params.y = posY
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowBounds() {
        params.width = windowWidth
        params.height = windowHeight
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        isRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceViewModelStore.clear()

        if (::composeView.isInitialized && composeView.parent != null) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        super.onDestroy()
    }
}
