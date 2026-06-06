package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback

object FileChooserRegistry {
    var filePathCallback: ValueCallback<Array<Uri>>? = null
}

class FileChooserActivity : Activity() {

    private val FILE_CHOOSER_REQUEST_CODE = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (FileChooserRegistry.filePathCallback == null) {
            finish()
            return
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
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
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val callback = FileChooserRegistry.filePathCallback
            if (callback != null) {
                var results: Array<Uri>? = null
                if (resultCode == RESULT_OK && data != null) {
                    val dataString = data.dataString
                    val clipData = data.clipData
                    if (clipData != null) {
                        results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    } else if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
                callback.onReceiveValue(results)
                FileChooserRegistry.filePathCallback = null
            }
        }
        finish()
    }

    override fun onDestroy() {
        // Fallback: If callback is still active (e.g. activity destroyed without result),
        // we must invoke it with null to prevent WebView from getting stuck.
        FileChooserRegistry.filePathCallback?.let {
            it.onReceiveValue(null)
            FileChooserRegistry.filePathCallback = null
        }
        super.onDestroy()
    }
}
