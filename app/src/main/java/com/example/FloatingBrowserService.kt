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
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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
    private val blobDataMap = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    private val chunkedTransfers = java.util.concurrent.ConcurrentHashMap<String, ChunkedTransferSession>()
    private val blobChunksMap = java.util.concurrent.ConcurrentHashMap<String, StoreBlobSession>()

    class ChunkedTransferSession(
        val fileName: String,
        val mimeType: String,
        val totalChunks: Int
    ) {
        val chunks = java.util.concurrent.ConcurrentHashMap<Int, String>()
    }

    class StoreBlobSession(
        val mimeType: String,
        val totalChunks: Int
    ) {
        val chunks = java.util.concurrent.ConcurrentHashMap<Int, String>()
    }

    private var isExpanded by mutableStateOf(true)
    private var startUrl = "https://aistudio.google.com"

    private var windowWidth by mutableStateOf(800)
    private var windowHeight by mutableStateOf(1100)

    private var posX by mutableStateOf(100)
    private var posY by mutableStateOf(150)

    private var dpScale: Float = 1f

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
        windowWidth = (screenWidth * 0.90f).toInt().coerceIn(360, 1080)
        windowHeight = (screenHeight * 0.65f).toInt().coerceIn(480, 1440)
        
        posX = (screenWidth - windowWidth) / 2
        posY = (screenHeight - windowHeight) / 5

        initWebView()
        setupFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("EXTRA_URL")?.let { url ->
            if (url.isNotBlank()) {
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                startUrl = formattedUrl
                webView.post {
                    webView.loadUrl(formattedUrl)
                }
                // Automatically expand to show new website loaded
                isExpanded = true
                updateWindowDimensions()
            }
        }
        return START_STICKY
    }

    inner class WebAppInterface(private val context: Context) {
        @android.webkit.JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("FloatingBrowserJS", "JS Log: $message")
        }

        @android.webkit.JavascriptInterface
        fun storeBlob(url: String, base64Data: String, mimeType: String) {
            val length = base64Data.length
            android.util.Log.d("FloatingBrowser", "Intercepted blob stored. URL: $url, MimeType: $mimeType, Size: $length")
            blobDataMap[url] = Pair(base64Data, mimeType)
        }

        @android.webkit.JavascriptInterface
        fun initChunkedDownload(transferId: String, fileName: String, mimeType: String, totalChunks: Int) {
            android.util.Log.d("FloatingBrowser", "Init chunked download: id=$transferId, name=$fileName, totalChunks=$totalChunks")
            chunkedTransfers[transferId] = ChunkedTransferSession(fileName, mimeType, totalChunks)
        }

        @android.webkit.JavascriptInterface
        fun appendChunk(transferId: String, chunkIndex: Int, chunkData: String) {
            val session = chunkedTransfers[transferId]
            if (session != null) {
                session.chunks[chunkIndex] = chunkData
            }
        }

        @android.webkit.JavascriptInterface
        fun commitChunkedDownload(transferId: String) {
            val session = chunkedTransfers[transferId] ?: return
            android.util.Log.d("FloatingBrowser", "Commit chunked download: id=$transferId, receivedChunks=${session.chunks.size}/${session.totalChunks}")
            Thread {
                try {
                    val sb = java.lang.StringBuilder()
                    for (i in 0 until session.totalChunks) {
                        val chunk = session.chunks[i]
                        if (chunk != null) {
                            sb.append(chunk)
                        } else {
                            android.util.Log.e("FloatingBrowser", "Missing chunk $i in transfer $transferId")
                        }
                    }
                    val fullBase64 = sb.toString()
                    processBase64(fullBase64, session.mimeType, session.fileName)
                    chunkedTransfers.remove(transferId)
                } catch (e: Exception) {
                    android.util.Log.e("FloatingBrowser", "Failed to assemble chunked download", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Assembly failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        @android.webkit.JavascriptInterface
        fun initStoreBlob(url: String, mimeType: String, totalChunks: Int) {
            blobChunksMap[url] = StoreBlobSession(mimeType, totalChunks)
        }

        @android.webkit.JavascriptInterface
        fun appendStoreBlobChunk(url: String, chunkIndex: Int, chunkData: String) {
            val session = blobChunksMap[url]
            if (session != null) {
                session.chunks[chunkIndex] = chunkData
            }
        }

        @android.webkit.JavascriptInterface
        fun commitStoreBlob(url: String) {
            val session = blobChunksMap[url] ?: return
            android.util.Log.d("FloatingBrowser", "Commit store blob: url=$url, receivedChunks=${session.chunks.size}/${session.totalChunks}")
            Thread {
                try {
                    val sb = java.lang.StringBuilder()
                    for (i in 0 until session.totalChunks) {
                        val chunk = session.chunks[i]
                        if (chunk != null) {
                            sb.append(chunk)
                        }
                    }
                    blobDataMap[url] = Pair(sb.toString(), session.mimeType)
                    blobChunksMap.remove(url)
                    android.util.Log.d("FloatingBrowser", "Successfully reconstructed blob in memory")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingBrowser", "Failed to assemble stored blob", e)
                }
            }.start()
        }

        @android.webkit.JavascriptInterface
        fun processBase64(base64Data: String, mimeType: String, fileName: String?) {
            try {
                var base64Cleaned = base64Data.trim()
                if (base64Cleaned.startsWith("\"") && base64Cleaned.endsWith("\"")) {
                    base64Cleaned = base64Cleaned.substring(1, base64Cleaned.length - 1)
                }
                if (base64Cleaned.startsWith("'") && base64Cleaned.endsWith("'")) {
                    base64Cleaned = base64Cleaned.substring(1, base64Cleaned.length - 1)
                }
                if (base64Cleaned.contains(",")) {
                    base64Cleaned = base64Cleaned.substring(base64Cleaned.indexOf(",") + 1)
                }
                base64Cleaned = base64Cleaned.trim()
                
                val fileBytes = android.util.Base64.decode(base64Cleaned, android.util.Base64.DEFAULT)
                
                val name = if (fileName.isNullOrBlank() || fileName == "null") {
                    "downloaded_file_${System.currentTimeMillis()}.${getExtFromMimetype(mimeType)}"
                } else {
                    fileName
                }
                
                android.util.Log.d("FloatingBrowser", "Saving Base64 file: $name, bytes: ${fileBytes.size}, pattern: $mimeType")
                var success = false

                // Use modern MediaStore for Android 10+ (Q)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/FloatingBrowser")
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        
                        val collectionUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        val uri = resolver.insert(collectionUri, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(fileBytes)
                                outputStream.flush()
                            }
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                            success = true
                            android.util.Log.d("FloatingBrowser", "Successfully downloaded file directly via MediaStore to /FloatingBrowser: $name")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FloatingBrowser", "MediaStore insert failed, using direct fallback", e)
                    }
                }

                if (!success) {
                    // Legacy manual file saving fallback
                    val downloadPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val path = java.io.File(downloadPath, "FloatingBrowser")
                    if (!path.exists()) {
                        path.mkdirs()
                    }
                    val file = java.io.File(path, name)
                    java.io.FileOutputStream(file, false).use { os ->
                        os.write(fileBytes)
                        os.flush()
                    }
                    
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                    success = true
                    android.util.Log.d("FloatingBrowser", "Successfully downloaded file directly via Direct legacy output: ${file.absolutePath}")
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "$name downloaded successfully!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("FloatingBrowser", "Exception saving Base64 file", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        private fun getExtFromMimetype(mimeType: String?): String {
            if (mimeType == null) return "bin"
            return when {
                mimeType.contains("pdf") -> "pdf"
                mimeType.contains("zip") -> "zip"
                mimeType.contains("image/png") -> "png"
                mimeType.contains("image/jpeg") || mimeType.contains("image/jpg") -> "jpg"
                mimeType.contains("text/plain") -> "txt"
                mimeType.contains("html") -> "html"
                mimeType.contains("json") -> "json"
                else -> "bin"
            }
        }
    }

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
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
                allowContentAccess = true
                
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            addJavascriptInterface(WebAppInterface(this@FloatingBrowserService), "AndroidDownloadInterface")

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("FloatingBrowserConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    FileChooserRegistry.filePathCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent()
                    FileChooserRegistry.fileChooserIntent = intent
                    
                    val activityIntent = Intent(this@FloatingBrowserService, FileChooserActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(activityIntent)
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectBlobInterceptor(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectBlobInterceptor(view)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                if (url.startsWith("blob:")) {
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    val interceptedUrl = url
                    val intercepted = blobDataMap[interceptedUrl]
                    
                    if (intercepted != null) {
                        val base64Data = intercepted.first
                        val resolvedMimetype = if (mimetype.isNullOrBlank() || mimetype == "application/octet-stream") intercepted.second else mimetype
                        android.util.Log.d("FloatingBrowser", "Instant blob download from intercepted cache: $interceptedUrl")
                        Thread {
                            WebAppInterface(this@FloatingBrowserService).processBase64(base64Data, resolvedMimetype, fileName)
                        }.start()
                        Toast.makeText(
                            applicationContext,
                            "Downloading: $fileName",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Safe same-origin fallback mechanism: dynamically search and fetch the blob URL inside every same-origin window frame recursively
                        val escapedUrl = url.replace("'", "\\'")
                        val escapedMimeType = mimetype?.replace("'", "\\'") ?: "application/octet-stream"
                        val escapedFileName = fileName?.replace("'", "\\'") ?: "downloaded_file"

                        val javaScript = """
                            (function() {
                                var url = '$escapedUrl';
                                var mime = '$escapedMimeType';
                                var name = '$escapedFileName';
                                
                                function getAndroidInterface() {
                                    try {
                                        if (window.AndroidDownloadInterface) return window.AndroidDownloadInterface;
                                        var p = window;
                                        while (p !== window.top) {
                                            p = p.parent;
                                            if (p && p.AndroidDownloadInterface) return p.AndroidDownloadInterface;
                                        }
                                        if (window.top && window.top.AndroidDownloadInterface) return window.top.AndroidDownloadInterface;
                                    } catch(e) {}
                                    return null;
                                }

                                function log(msg) {
                                    try {
                                        var bridge = getAndroidInterface();
                                        if (bridge && bridge.log) {
                                            bridge.log(msg);
                                        } else {
                                            console.log("Fallback Log: " + msg);
                                        }
                                    } catch (e) {
                                        console.log("Fallback Log: " + msg);
                                    }
                                }

                                log('JS: Init recursive frame blob fetch for: ' + url);
                                
                                function sendBlobInChunks(base64Data, mime, filename) {
                                    try {
                                        var bridge = getAndroidInterface();
                                        if (!bridge) {
                                            log("Android interface not available during fallback. Broadcasting via postMessage to parent...");
                                            if (window.top && window.top !== window) {
                                                window.top.postMessage({
                                                    type: 'BLOB_DOWNLOAD',
                                                    base64: base64Data,
                                                    mime: mime,
                                                    filename: filename
                                                }, '*');
                                            }
                                            return;
                                        }
                                        var chunkSize = 200000;
                                        var totalChunks = Math.ceil(base64Data.length / chunkSize);
                                        var transferId = 'trans_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
                                        
                                        bridge.initChunkedDownload(transferId, filename, mime, totalChunks);
                                        for (var i = 0; i < totalChunks; i++) {
                                            var start = i * chunkSize;
                                            var end = Math.min(start + chunkSize, base64Data.length);
                                            var chunk = base64Data.substring(start, end);
                                            bridge.appendChunk(transferId, i, chunk);
                                        }
                                        bridge.commitChunkedDownload(transferId);
                                    } catch(e) {
                                        log('Chunked transfer crash: ' + e.message);
                                    }
                                }
                                
                                function getAllFrames(win, list) {
                                    if (!win) return list;
                                    list.push(win);
                                    try {
                                        var len = win.frames.length;
                                        for (var i = 0; i < len; i++) {
                                            try {
                                                var f = win.frames[i];
                                                if (f && list.indexOf(f) === -1) {
                                                    getAllFrames(f, list);
                                                }
                                            } catch (eInner) {}
                                        }
                                    } catch(e) {}
                                    return list;
                                }
                                
                                var frames = getAllFrames(window, []);
                                var currentFrameIndex = 0;
                                
                                function attemptNextFrame() {
                                    if (currentFrameIndex >= frames.length) {
                                        log('JS: Blob fetch failed in all frames.');
                                        return;
                                    }
                                    
                                    var f = frames[currentFrameIndex];
                                    currentFrameIndex++;
                                    
                                    var isSameOrigin = false;
                                    try {
                                        if (f && f.document) {
                                            isSameOrigin = true;
                                        }
                                    } catch (eOrigin) {}
                                    
                                    if (!isSameOrigin) {
                                        log('JS: Skipping frame ' + (currentFrameIndex - 1) + ' due to cross-origin boundary');
                                        attemptNextFrame();
                                        return;
                                    }
                                    
                                    log('JS: Attempting fetch in same-origin frame ' + (currentFrameIndex - 1));
                                    try {
                                        f.fetch(url)
                                            .then(function(res) { 
                                                log('JS: Fetch success in same-origin frame ' + (currentFrameIndex - 1));
                                                return res.blob(); 
                                            })
                                            .then(function(blob) {
                                                log('JS: Blob captured. Size: ' + blob.size);
                                                var reader = new f.FileReader();
                                                reader.onloadend = function() {
                                                    var base64data = reader.result;
                                                    log('JS: Converting blob to Base64 data...');
                                                    sendBlobInChunks(base64data, mime, name);
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                log('JS: Fetch in frame ' + (currentFrameIndex - 1) + ' failed: ' + err.message + '. Retrying with XHR inside frame.');
                                                try {
                                                    var xhr = new f.XMLHttpRequest();
                                                    xhr.open('GET', url, true);
                                                    xhr.responseType = 'blob';
                                                    xhr.onload = function() {
                                                        log('JS: XHR success in same-origin frame ' + (currentFrameIndex - 1) + '. Status: ' + xhr.status);
                                                        if (xhr.status === 200 || xhr.status === 0) {
                                                            var reader = new f.FileReader();
                                                            reader.onloadend = function() {
                                                                sendBlobInChunks(reader.result, mime, name);
                                                            };
                                                            reader.readAsDataURL(xhr.response);
                                                        } else {
                                                            attemptNextFrame();
                                                        }
                                                    };
                                                    xhr.onerror = function() { 
                                                        log('JS: XHR error in frame ' + (currentFrameIndex - 1));
                                                        attemptNextFrame(); 
                                                    };
                                                    xhr.send();
                                                } catch(e) {
                                                    log('JS: Frame XHR crash: ' + e.message);
                                                    attemptNextFrame();
                                                }
                                            });
                                    } catch(e) {
                                        log('JS: Frame fetch block crash: ' + e.message);
                                        attemptNextFrame();
                                    }
                                }
                                
                                attemptNextFrame();
                            })();
                        """.trimIndent()

                        post {
                            evaluateJavascript(javaScript, null)
                        }

                        Toast.makeText(
                            applicationContext,
                            "Processing blob download: $fileName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    try {
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setMimeType(mimetype)
                            val cookies = CookieManager.getInstance().getCookie(url)
                            addRequestHeader("cookie", cookies)
                            addRequestHeader("User-Agent", userAgent)
                            setDescription("Downloading file from Floating Browser...")
                            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                            setTitle(fileName)
                            @Suppress("DEPRECATION")
                            allowScanningByMediaScanner()
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "FloatingBrowser/$fileName")
                        }
                        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(
                            applicationContext,
                            "Starting download: ${URLUtil.guessFileName(url, contentDisposition, mimetype)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            applicationContext,
                            "Download failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            loadUrl(startUrl)
        }
    }

    private fun injectBlobInterceptor(view: WebView?) {
        val script = """
            (function main() {
                var scriptCode = "(" + main.toString() + ")();";

                function getAndroidInterface() {
                    try {
                        if (window.AndroidDownloadInterface) return window.AndroidDownloadInterface;
                        var p = window;
                        while (p !== window.top) {
                            p = p.parent;
                            if (p && p.AndroidDownloadInterface) return p.AndroidDownloadInterface;
                        }
                        if (window.top && window.top.AndroidDownloadInterface) return window.top.AndroidDownloadInterface;
                    } catch(e) {}
                    return null;
                }

                function log(msg) {
                    try {
                        var bridge = getAndroidInterface();
                        if (bridge && bridge.log) {
                            bridge.log(msg);
                        } else {
                            console.log("Interceptor: " + msg);
                        }
                    } catch (e) {
                        console.log("Interceptor: " + msg);
                    }
                }

                // Register postMessage handler on the top window to bridge cross-origin iframe data
                if (window === window.top) {
                    window.addEventListener('message', function(event) {
                        try {
                            if (!event.data || typeof event.data !== 'object') return;
                            if (event.data.type === 'BLOB_STORE') {
                                log('Received BLOB_STORE message from iframe. URL: ' + event.data.url);
                                storeBlobInChunks(event.data.url, event.data.base64, event.data.mime);
                            } else if (event.data.type === 'BLOB_DOWNLOAD') {
                                log('Received BLOB_DOWNLOAD message from iframe. Filename: ' + event.data.filename);
                                sendBlobInChunks(event.data.base64, event.data.mime, event.data.filename);
                            }
                        } catch (ePost) {
                            log('postMessage top handler error: ' + ePost.message);
                        }
                    }, false);
                }

                function sendBlobInChunks(base64Data, mime, filename) {
                    try {
                        var bridge = getAndroidInterface();
                        if (!bridge) {
                            log("Android interface not available locally. Broadcasting via postMessage to top window...");
                            if (window.top && window.top !== window) {
                                window.top.postMessage({
                                    type: 'BLOB_DOWNLOAD',
                                    base64: base64Data,
                                    mime: mime,
                                    filename: filename
                                }, '*');
                            }
                            return;
                        }
                        var chunkSize = 200000;
                        var totalChunks = Math.ceil(base64Data.length / chunkSize);
                        var transferId = 'trans_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
                        
                        bridge.initChunkedDownload(transferId, filename, mime, totalChunks);
                        for (var i = 0; i < totalChunks; i++) {
                            var start = i * chunkSize;
                            var end = Math.min(start + chunkSize, base64Data.length);
                            var chunk = base64Data.substring(start, end);
                            bridge.appendChunk(transferId, i, chunk);
                        }
                        bridge.commitChunkedDownload(transferId);
                    } catch(e) {
                        log('Chunked transfer crash: ' + e.message);
                    }
                }

                function storeBlobInChunks(url, base64Data, mime) {
                    try {
                        var bridge = getAndroidInterface();
                        if (!bridge) {
                            log("Android interface not available locally for storing. Delegating storage to top window...");
                            if (window.top && window.top !== window) {
                                window.top.postMessage({
                                    type: 'BLOB_STORE',
                                    url: url,
                                    base64: base64Data,
                                    mime: mime
                                }, '*');
                            }
                            return;
                        }
                        var chunkSize = 200000;
                        var totalChunks = Math.ceil(base64Data.length / chunkSize);
                        
                        bridge.initStoreBlob(url, mime, totalChunks);
                        for (var i = 0; i < totalChunks; i++) {
                            var start = i * chunkSize;
                            var end = Math.min(start + chunkSize, base64Data.length);
                            var chunk = base64Data.substring(start, end);
                            bridge.appendStoreBlobChunk(url, i, chunk);
                        }
                        bridge.commitStoreBlob(url);
                    } catch(e) {
                        log('storeBlob chunked transfer crash: ' + e.message);
                    }
                }

                function injectScriptIntoHtml(html) {
                    try {
                        var scriptTag = '<script>' + scriptCode + '<\/script>';
                        var idx = html.toLowerCase().indexOf('<head>');
                        if (idx !== -1) {
                            return html.substring(0, idx + 6) + scriptTag + html.substring(idx + 6);
                        }
                        idx = html.toLowerCase().indexOf('<html>');
                        if (idx !== -1) {
                            return html.substring(0, idx + 6) + scriptTag + html.substring(idx + 6);
                        }
                        idx = html.toLowerCase().indexOf('<body>');
                        if (idx !== -1) {
                            return html.substring(0, idx) + scriptTag + html.substring(idx);
                        }
                        return scriptTag + html;
                    } catch (e) {
                        return html;
                    }
                }

                function installInterceptor(win) {
                    try {
                        if (!win || win.__blob_interceptor_installed) return;
                        win.__blob_interceptor_installed = true;

                        log('Installing Blob interceptor in frame: ' + (win.location ? win.location.href : 'unknown'));

                        // 1. Intercept URL.createObjectURL
                        if (win.URL && win.URL.createObjectURL) {
                            var originalCreateObjectURL = win.URL.createObjectURL;
                            win.URL.createObjectURL = function(blob) {
                                var url = originalCreateObjectURL.call(win.URL, blob);
                                if (blob) {
                                    try {
                                        var reader = new win.FileReader();
                                        reader.onloadend = function() {
                                            var base64data = reader.result;
                                            var mime = blob.type || 'application/octet-stream';
                                            storeBlobInChunks(url, base64data, mime);
                                        };
                                        reader.readAsDataURL(blob);
                                    } catch (err) {
                                        log('createObjectURL convert error: ' + err.message);
                                    }
                                }
                                return url;
                            };
                        }

                        // 2. Intercept window.open
                        var originalOpen = win.open;
                        win.open = function(url, target, features) {
                            if (url && url.substring(0, 5) === 'blob:') {
                                log('window.open intercepted for blob: ' + url);
                                try {
                                    win.fetch(url)
                                        .then(function(res) { return res.blob(); })
                                        .then(function(blob) {
                                            var reader = new win.FileReader();
                                            reader.onloadend = function() {
                                                sendBlobInChunks(reader.result, blob.type || 'application/octet-stream', 'downloaded_file');
                                            };
                                            reader.readAsDataURL(blob);
                                        })
                                        .catch(function(err) {
                                            log('window.open fetch failed: ' + err.message);
                                        });
                                } catch(e) {
                                    log('window.open handler crash: ' + e.message);
                                }
                                return null;
                            }
                            return originalOpen.apply(this, arguments);
                        };

                        // 3. Intercept HTMLAnchorElement.prototype.click
                        if (win.HTMLAnchorElement && win.HTMLAnchorElement.prototype) {
                            var originalClick = win.HTMLAnchorElement.prototype.click;
                            win.HTMLAnchorElement.prototype.click = function() {
                                var href = this.href;
                                if (href && href.substring(0, 5) === 'blob:') {
                                    var filename = this.download || 'downloaded_file';
                                    log('Anchor prototype click() intercepted: ' + href);
                                    try {
                                        win.fetch(href)
                                            .then(function(res) { return res.blob(); })
                                            .then(function(blob) {
                                                var reader = new win.FileReader();
                                                reader.onloadend = function() {
                                                    sendBlobInChunks(reader.result, blob.type || 'application/octet-stream', filename);
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                log('Anchor prototype click fetch failed: ' + err.message);
                                            });
                                    } catch(e) {
                                        log('Anchor prototype click handler crash: ' + e.message);
                                    }
                                    return;
                                }
                                return originalClick.apply(this, arguments);
                            };
                        }

                        // 4. Dom Clicks Event Listener
                        if (win.document) {
                            win.document.addEventListener('click', function(e) {
                                var target = e.target;
                                while (target && target.tagName !== 'A') {
                                    target = target.parentNode;
                                    if (!target) break;
                                }
                                if (target && target.tagName === 'A' && target.href && target.href.substring(0, 5) === 'blob:') {
                                    var url = target.href;
                                    var filename = target.download || 'downloaded_file';
                                    log('DOM click intercepted for blob URL: ' + url);
                                    try {
                                        win.fetch(url)
                                            .then(function(res) { return res.blob(); })
                                            .then(function(blob) {
                                                var reader = new win.FileReader();
                                                reader.onloadend = function() {
                                                    sendBlobInChunks(reader.result, blob.type || 'application/octet-stream', filename);
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                log('DOM click fetch failed: ' + err.message);
                                            });
                                    } catch(err2) {
                                        log('DOM click crash: ' + err2.message);
                                    }
                                }
                            }, true);
                        }

                    } catch(e) {
                         log('Failed to install Blob interceptor in frame: ' + e.message);
                    }
                }

                // Install on current window immediately
                installInterceptor(window);

                // Patch creation and setter of elements to hook dynamic/iframe loading dynamically
                try {
                    if (window.HTMLIFrameElement) {
                        var descriptor = Object.getOwnPropertyDescriptor(window.HTMLIFrameElement.prototype, 'srcdoc');
                        if (descriptor && descriptor.set) {
                            var originalSet = descriptor.set;
                            Object.defineProperty(window.HTMLIFrameElement.prototype, 'srcdoc', {
                                configurable: true,
                                enumerable: true,
                                get: descriptor.get,
                                set: function(val) {
                                    log('Intercepted srcdoc setter write');
                                    if (typeof val === 'string') {
                                        val = injectScriptIntoHtml(val);
                                    }
                                    return originalSet.call(this, val);
                                }
                            });
                        }
                    }
                } catch(e) {
                    log('Failed to patch srcdoc property: ' + e.message);
                }

                try {
                    var originalSetAttribute = window.Element.prototype.setAttribute;
                    window.Element.prototype.setAttribute = function(name, val) {
                        if (name && name.toLowerCase() === 'srcdoc' && typeof val === 'string') {
                            log('Intercepted setAttribute for srcdoc');
                            val = injectScriptIntoHtml(val);
                        }
                        return originalSetAttribute.call(this, name, val);
                    };
                } catch(e) {
                    log('Failed to patch setAttribute: ' + e.message);
                }

                try {
                    var originalWrite = window.Document.prototype.write;
                    window.Document.prototype.write = function() {
                        log('Intercepted document.write');
                        if (arguments.length > 0 && typeof arguments[0] === 'string') {
                            arguments[0] = injectScriptIntoHtml(arguments[0]);
                        }
                        return originalWrite.apply(this, arguments);
                    };

                    var originalWriteln = window.Document.prototype.writeln;
                    window.Document.prototype.writeln = function() {
                        log('Intercepted document.writeln');
                        if (arguments.length > 0 && typeof arguments[0] === 'string') {
                            arguments[0] = injectScriptIntoHtml(arguments[0]);
                        }
                        return originalWriteln.apply(this, arguments);
                    };
                } catch(e) {
                    log('Failed to patch document.write: ' + e.message);
                }

                // Check same-origin frames recursively (as a fallback)
                try {
                    for (var i = 0; i < window.frames.length; i++) {
                        try {
                            var f = window.frames[i];
                            if (f && f.document) {
                                installInterceptor(f);
                            }
                        } catch(eFrame) {}
                    }
                } catch(e) {}

                // In case there are late loaders, install interceptor periodically on same-origin window frames
                setInterval(function() {
                    try {
                        installInterceptor(window);
                    } catch(e) {}
                    try {
                        for (var i = 0; i < window.frames.length; i++) {
                            try {
                                var f = window.frames[i];
                                if (f && f.document) {
                                    installInterceptor(f);
                                }
                            } catch(eFrame) {}
                        }
                    } catch(e) {}
                }, 1500);

            })();
        """.trimIndent()
        view?.post {
            view.evaluateJavascript(script, null)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "floating_browser_channel"
        val channelName = "Floating Browser Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the background floating web browser active and functional"
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
            .setContentTitle("Floating Browser is Active")
            .setContentText("Keep code processing from Gemini Canvas alive in the background")
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    202612,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(202612, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startForeground(202612, notification)
        }
    }

    private fun setupFloatingWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            layoutType,
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
                // Background Keep Alive render trick:
                // We keep AndroidView attached to the window manager layout hierachy with 1.dp size.
                // This prevents WebView from being paused/suspended by system during minimized background bubble state,
                // keeping active timers, animations, and JS code compiled from Gemini Canvas fully running!
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .align(Alignment.Center)
                ) {
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                    .size(48.dp)
                    .alpha(0.55f)
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .border(2.dp, Color(0xFFD0BCFF), CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF6750A4), Color(0xFF381E72))
                        ),
                        shape = CircleShape
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1AFFFFFF), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Expand Floating Canvas Browser",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
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
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, Color(0xFF381E72), RoundedCornerShape(18.dp))
                .background(Color(0xFF131118))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
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
                                .size(26.dp)
                                .background(Color(0xFFD0BCFF), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Floating Browser Window",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { toggleExpandState() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Minimize to Bubble",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { stopSelf() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Terminate Browser",
                                tint = Color(0xFFF3B3B3),
                                modifier = Modifier.size(18.dp)
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

                // WebView Container occupies the central dynamic layout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2C2A35))
                )

                // Navigation Control Bottom Toolbar (No address bar per request, only clean action buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1C1A22))
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (webView.canGoBack()) webView.goBack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (webView.canGoForward()) webView.goForward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { webView.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Page",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { webView.loadUrl(startUrl) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Reload Default Startup Link",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Interactive Drag Resizing element at the bottom right corner
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
                    contentDescription = "Resize window",
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

        try {
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
