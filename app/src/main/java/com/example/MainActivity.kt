package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var appWebView: WebView? = null
    private var pendingDeepLinkScreen: String? = null
    private var pendingDeepLinkExtra: String? = null

    class AndroidBridge(
        private val context: Context,
        private val firestore: FirebaseFirestore,
        private val auth: FirebaseAuth,
        private val webViewProvider: () -> WebView?
    ) {
        @JavascriptInterface
        fun onLanguageSelected(code: String) {
            context.getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("sanatanam_app_lang", code)
                .apply()

            val currentUser = auth.currentUser
            if (currentUser != null) {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .update(
                        mapOf(
                            "preferredLanguage" to code,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
            }
        }

        @JavascriptInterface
        fun getSavedLanguage(): String {
            return context.getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                .getString("sanatanam_app_lang", "hi") ?: "hi"
        }

        @JavascriptInterface
        fun getFcmToken(): String {
            return context.getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                .getString("fcm_device_token", "Fetching...") ?: "Not Registered"
        }

        @JavascriptInterface
        fun getFirebaseStatus(): String {
            val user = auth.currentUser
            return if (user != null) "authenticated:${user.uid}" else "unauthenticated"
        }

        @JavascriptInterface
        fun openAdminPanel() {
            webViewProvider()?.post {
                webViewProvider()?.loadUrl("file:///android_asset/admin/notifications.html")
            }
        }

        @JavascriptInterface
        fun navigateToScreen(screenName: String) {
            webViewProvider()?.post {
                when (screenName.lowercase()) {
                    "home" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html")
                    "welcome" -> webViewProvider()?.loadUrl("file:///android_asset/app_welcome.html")
                    "traininglab", "training_lab" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=training_lab")
                    "profile" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=profile")
                    "enginedetails", "engine_details" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=engine_details")
                    "admin" -> webViewProvider()?.loadUrl("file:///android_asset/admin/notifications.html")
                    else -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=$screenName")
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseApp.initializeApp(this)
        val databaseId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId)
        val auth = FirebaseAuth.getInstance()

        // Subscribe to global announcements topic "all_users"
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to all_users topic successfully")
                }
            }

        // Retrieve current device token
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("fcm_device_token", token)
                    .apply()
                Log.d("FCM", "Current FCM Token: $token")
            }

        handleNotificationIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = appWebView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                appWebView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(Color.parseColor("#170105"))

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = true
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }

                                addJavascriptInterface(
                                    AndroidBridge(ctx, firestore, auth) { appWebView },
                                    "AndroidBridge"
                                )

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        dispatchPendingDeepLink()
                                    }
                                }

                                val initialUrl = if (pendingDeepLinkScreen != null) {
                                    "file:///android_asset/app_home.html?screen=$pendingDeepLinkScreen"
                                } else {
                                    "file:///android_asset/app_splash.html"
                                }

                                loadUrl(initialUrl)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        dispatchPendingDeepLink()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val screen = intent.getStringExtra("screen")
        val extra = intent.getStringExtra("extra")
        if (!screen.isNullOrBlank()) {
            pendingDeepLinkScreen = screen
            pendingDeepLinkExtra = extra
            Log.d("FCM", "Deep link target detected: $screen, extra: $extra")
        }
    }

    private fun dispatchPendingDeepLink() {
        val screen = pendingDeepLinkScreen ?: return
        val extra = pendingDeepLinkExtra ?: ""
        val jsonPayload = JSONObject().apply {
            put("screen", screen)
            put("extra", extra)
        }.toString()

        appWebView?.post {
            appWebView?.evaluateJavascript(
                "if (window.onNotificationDeepLink) { window.onNotificationDeepLink($jsonPayload); }",
                null
            )
        }
        pendingDeepLinkScreen = null
        pendingDeepLinkExtra = null
    }

    override fun onDestroy() {
        appWebView?.destroy()
        appWebView = null
        super.onDestroy()
    }
}
