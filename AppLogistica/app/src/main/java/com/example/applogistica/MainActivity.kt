package com.example.applogistica

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingWebPermissions = emptyArray<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        setContentView(webView)

        // Configurações de WebView
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webSettings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        // Enable remote debugging (para ver console.log via Chrome DevTools)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e(TAG, "SSL Error: ${error?.primaryError} - ${error?.url}")
                // Permitir certificados auto-assinados para testes
                handler?.proceed()
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String
            ) {
                Log.e(TAG, "Web Error Code: $errorCode - $description - $failingUrl")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(TAG, "Page finished loading: $url")
                super.onPageFinished(view, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(TAG, "Page started loading: $url")
                super.onPageStarted(view, url, favicon)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "Console: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                return true
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return showImageChooser(fileChooserParams)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedResources = request.resources
                val permissions = buildList {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requestedResources) {
                        add(Manifest.permission.CAMERA)
                    }
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requestedResources) {
                        add(Manifest.permission.RECORD_AUDIO)
                    }
                }

                if (permissions.isEmpty()) {
                    request.deny()
                    return
                }

                val missingPermissions = permissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                        PackageManager.PERMISSION_GRANTED
                }
                pendingPermissionRequest = request
                pendingWebPermissions = requestedResources.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }.toTypedArray()

                if (missingPermissions.isEmpty()) {
                    grantPendingWebPermission()
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        missingPermissions.toTypedArray(),
                        WEB_PERMISSION_REQUEST_CODE
                    )
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermissionRequest == request) {
                    clearPendingWebPermission()
                }
            }
        }

        webView.loadUrl("https://serp-app.indufix.com.br/login")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == WEB_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                grantPendingWebPermission()
            } else {
                pendingPermissionRequest?.deny()
                clearPendingWebPermission()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            return
        }

        val result = if (resultCode == Activity.RESULT_OK) {
            when {
                data?.clipData != null -> Array(data.clipData!!.itemCount) {
                    data.clipData!!.getItemAt(it).uri
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(result)
        filePathCallback = null
        cameraImageUri?.let {
            revokeUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        cameraImageUri = null
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun showImageChooser(fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).takeIf {
            it.resolveActivity(packageManager) != null
        }?.apply {
            val imageFile = File.createTempFile("camera_", ".jpg", cacheDir)
            cameraImageUri = FileProvider.getUriForFile(
                this@MainActivity,
                "$packageName.fileprovider",
                imageFile
            )
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            clipData = ClipData.newRawUri("captured_image", cameraImageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        startActivityForResult(
            Intent.createChooser(galleryIntent, getString(R.string.select_image)).apply {
                cameraIntent?.let { putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it)) }
            },
            FILE_CHOOSER_REQUEST_CODE
        )
        return true
    }

    private fun grantPendingWebPermission() {
        pendingPermissionRequest?.grant(pendingWebPermissions)
        clearPendingWebPermission()
    }

    private fun clearPendingWebPermission() {
        pendingPermissionRequest = null
        pendingWebPermissions = emptyArray()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val WEB_PERMISSION_REQUEST_CODE = 100
        const val FILE_CHOOSER_REQUEST_CODE = 101
    }
}