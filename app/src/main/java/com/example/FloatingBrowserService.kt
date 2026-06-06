package com.example

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    private lateinit var webView: WebView

    // Window layout state
    private var isExpanded by mutableStateOf(false)
    private var urlState by mutableStateOf("https://www.google.com")
    private var inputUrl by mutableStateOf("https://www.google.com")
    private var webTitle by mutableStateOf("Google")
    private var pageProgress by mutableStateOf(0)
    private var isLoading by mutableStateOf(false)

    // Current dimensions (in pixels)
    private var windowWidth by mutableStateOf(900)
    private var windowHeight by mutableStateOf(1300)

    // Layout position
    private var posX by mutableStateOf(150)
    private var posY by mutableStateOf(250)

    private var dpScale: Float = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Calculate display scale
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        dpScale = metrics.density

        // Setup responsive default dimensions based on screen resolution
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        windowWidth = (screenWidth * 0.85f).toInt().coerceIn(400, 1100)
        windowHeight = (screenHeight * 0.65f).toInt().coerceIn(600, 1600)
        
        // Center the expanded window
        posX = (screenWidth - windowWidth) / 2
        posY = (screenHeight - windowHeight) / 3

        initWebView()
        setupFloatingWindow()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            CookieManager.getInstance().setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let {
                        urlState = it
                        inputUrl = it
                    }
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let {
                        urlState = it
                        inputUrl = it
                    }
                    webTitle = view?.title ?: "Web Page"
                    isLoading = false
                    pageProgress = 0
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    view?.loadUrl(url)
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    pageProgress = newProgress
                    if (newProgress == 100) {
                        isLoading = false
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { webTitle = it }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    FileChooserRegistry.filePathCallback?.onReceiveValue(null)
                    FileChooserRegistry.filePathCallback = filePathCallback
                    FileChooserRegistry.fileChooserIntent = fileChooserParams?.createIntent()
                    
                    val intent = Intent(this@FloatingBrowserService, FileChooserActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        this@FloatingBrowserService.startActivity(intent)
                    } catch (e: Exception) {
                        FileChooserRegistry.filePathCallback?.onReceiveValue(null)
                        FileChooserRegistry.filePathCallback = null
                        FileChooserRegistry.fileChooserIntent = null
                        return false
                    }
                    return true
                }
            }

            loadUrl(urlState)
        }
    }

    private fun setupFloatingWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            (64 * dpScale).toInt(),
            (64 * dpScale).toInt(),
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
        if (isExpanded) {
            BrowserWindow()
        } else {
            BrowserBubble()
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
                    .size(48.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .border(1.dp, Color.White, CircleShape)
                    .background(
                        color = Color(0xFFEADDFF),
                        shape = CircleShape
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        progress = { pageProgress.toFloat() / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF210F4A),
                        strokeWidth = 2.dp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF6750A4), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Expand Browser",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
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
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(24.dp))
                .background(Color(0xFFF3EDF7))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tier 1: Window Controls and Drag Handle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFFE7E0EC))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                updatePosition(dragAmount.x.toInt(), dragAmount.y.toInt())
                            }
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF6750A4), shape = RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, shape = CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = webTitle,
                        color = Color(0xFF1D1B20),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Minimize
                    IconButton(
                        onClick = { toggleExpandState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            tint = Color(0xFF49454F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))

                    // Close
                    IconButton(
                        onClick = { stopSelf() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF49454F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Divider below top header
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFCAC4D0))
                )

                // Tier 2: Address bar & navigation keys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFFFEF7FF))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoBack = webView.canGoBack()
                    IconButton(
                        onClick = { if (canGoBack) webView.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = if (canGoBack) Color(0xFF49454F) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val canGoForward = webView.canGoForward()
                    IconButton(
                        onClick = { if (canGoForward) webView.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go Forward",
                            tint = if (canGoForward) Color(0xFF49454F) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (isLoading) webView.stopLoading() else webView.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color(0xFF49454F),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Customized Address Input (Geometric Balance rounded pill)
                    val focusManager = LocalFocusManager.current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(Color(0xFFE7E0EC), shape = CircleShape)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = Color(0xFF49454F), fontSize = 13.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(Color(0xFF6750A4)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        var rawInput = inputUrl.trim()
                                        if (rawInput.isNotEmpty()) {
                                            if (!rawInput.startsWith("http://") && !rawInput.startsWith("https://")) {
                                                if (rawInput.contains(".") && !rawInput.contains(" ")) {
                                                    rawInput = "https://$rawInput"
                                                } else {
                                                    rawInput = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(rawInput, "UTF-8")
                                                }
                                            }
                                            webView.loadUrl(rawInput)
                                        }
                                        focusManager.clearFocus()
                                    }
                                )
                            )
                            if (inputUrl.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear address text",
                                    tint = Color(0xFF49454F),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { inputUrl = "" }
                                )
                            }
                        }
                    }
                }

                // Loading Progress line
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress.toFloat() / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFF6750A4),
                        trackColor = Color(0xFFE7E0EC)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Webpage host View area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Sleek layout footer bar matching bottom radius
                Spacer(modifier = Modifier.height(14.dp).fillMaxWidth().background(Color(0xFFE7E0EC)))
            }

            // Interactive Bottom-Right Corner Resize Handle
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
                    tint = Color(0xFFCAC4D0),
                    modifier = Modifier
                        .size(22.dp)
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
            // Enable touch focus for key entrance & input
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.width = (64 * dpScale).toInt()
            params.height = (64 * dpScale).toInt()
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

        if (::webView.isInitialized) {
            try {
                webView.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        super.onDestroy()
    }
}
