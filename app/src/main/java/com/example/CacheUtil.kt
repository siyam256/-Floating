package com.example

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File

object CacheUtil {
    fun getCacheSize(context: Context): Long {
        var size: Long = 0
        
        try {
            // 1. App's cache directory
            size += getDirSize(context.cacheDir)
            
            // 2. App's code cache directory if it exists
            size += getDirSize(context.codeCacheDir)
            
            // 3. app_webview directory (specifically testing cache-related subdirectory)
            val appWebviewDir = File(context.applicationInfo.dataDir, "app_webview")
            if (appWebviewDir.exists()) {
                val httpCache = File(appWebviewDir, "Default/HTTP Cache")
                if (httpCache.exists()) size += getDirSize(httpCache)
                
                val codeCache = File(appWebviewDir, "Default/Code Cache")
                if (codeCache.exists()) size += getDirSize(codeCache)

                val gpuCache = File(appWebviewDir, "Default/GPUCache")
                if (gpuCache.exists()) size += getDirSize(gpuCache)
                
                val blobStorage = File(appWebviewDir, "Default/Blob Storage")
                if (blobStorage.exists()) size += getDirSize(blobStorage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return size
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        if (dir.isFile) return dir.length()
        
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += getDirSize(file)
        }
        return size
    }

    fun clearCache(context: Context, onComplete: (() -> Unit)? = null) {
        try {
            // Must run on UI Thread or from main loop for WebView methods
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val tempWebView = WebView(context)
                    tempWebView.clearCache(true)
                    tempWebView.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Now delete the files safely in background threads/immediately
                Thread {
                    try {
                        deleteDir(context.cacheDir)
                        deleteDir(context.codeCacheDir)
                        
                        val appWebviewDir = File(context.applicationInfo.dataDir, "app_webview")
                        if (appWebviewDir.exists()) {
                            deleteDir(File(appWebviewDir, "Default/HTTP Cache"))
                            deleteDir(File(appWebviewDir, "Default/Code Cache"))
                            deleteDir(File(appWebviewDir, "Default/GPUCache"))
                            deleteDir(File(appWebviewDir, "Default/Blob Storage"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (onComplete != null) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onComplete()
                            }
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke()
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        if (dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (child in children) {
                    deleteDir(File(dir, child))
                }
            }
        }
        return dir.delete()
    }

    fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 Bytes"
        val units = arrayOf("Bytes", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups < units.size) digitGroups else units.size - 1
        return String.format("%.2f %s", sizeInBytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
