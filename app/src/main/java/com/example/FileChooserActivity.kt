package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback

object FileChooserRegistry {
    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var fileChooserIntent: Intent? = null
}

class FileChooserActivity : Activity() {

    private val FILE_CHOOSER_REQUEST_CODE = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (FileChooserRegistry.filePathCallback == null) {
            finish()
            return
        }

        clearOldCacheFiles()

        val intent = FileChooserRegistry.fileChooserIntent ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Choose File"),
                FILE_CHOOSER_REQUEST_CODE
            )
        } catch (e: Exception) {
            FileChooserRegistry.filePathCallback?.onReceiveValue(null)
            FileChooserRegistry.filePathCallback = null
            FileChooserRegistry.fileChooserIntent = null
            finish()
        }
    }

    private fun clearOldCacheFiles() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.startsWith("upload_temp_") || file.name.startsWith("upload_"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyUriToCache(uri: Uri): Uri {
        try {
            val contentResolver = contentResolver
            var fileName = "upload_temp_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        fileName = displayName
                    }
                }
            }
            
            // Clean filename to prevent filesystem issues
            fileName = "upload_" + fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            if (!fileName.contains(".")) {
                fileName += ".bin"
            }
            
            val tempFile = java.io.File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                java.io.FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return Uri.fromFile(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return uri // Fallback to raw uri on failure
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val callback = FileChooserRegistry.filePathCallback
            if (callback != null) {
                var results: Array<Uri>? = null
                if (resultCode == RESULT_OK && data != null) {
                    val clipData = data.clipData
                    val dataString = data.dataString
                    val dataUri = data.data
                    
                    if (clipData != null) {
                        val list = mutableListOf<Uri>()
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            list.add(copyUriToCache(uri))
                        }
                        results = list.toTypedArray()
                    } else if (dataUri != null) {
                        results = arrayOf(copyUriToCache(dataUri))
                    } else if (dataString != null) {
                        results = arrayOf(copyUriToCache(Uri.parse(dataString)))
                    }
                }
                callback.onReceiveValue(results)
                FileChooserRegistry.filePathCallback = null
                FileChooserRegistry.fileChooserIntent = null
            }
        }
        finish()
    }

    override fun onDestroy() {
        // Fallback: If callback is still active and activity is finishing,
        // we must invoke it with null to prevent WebView from getting stuck.
        if (isFinishing) {
            FileChooserRegistry.filePathCallback?.let {
                it.onReceiveValue(null)
                FileChooserRegistry.filePathCallback = null
            }
            FileChooserRegistry.fileChooserIntent = null
        }
        super.onDestroy()
    }
}
