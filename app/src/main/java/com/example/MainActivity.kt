package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONArray
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
        fun saveNotificationLog(
            title: String,
            body: String,
            imageUrl: String,
            targetScreen: String,
            audience: String,
            extraData: String
        ): String {
            return try {
                val docRef = firestore.collection("notification_logs").document()
                val logData = hashMapOf(
                    "id" to docRef.id,
                    "title" to title,
                    "body" to body,
                    "imageUrl" to imageUrl,
                    "targetScreen" to targetScreen,
                    "audience" to audience,
                    "extraData" to extraData,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "status" to "Sent"
                )
                docRef.set(logData)
                docRef.id
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error logging notification to Firestore: ${e.message}", e)
                "error"
            }
        }

        @JavascriptInterface
        fun triggerLocalNotification(
            title: String,
            body: String,
            imageUrl: String,
            targetScreen: String,
            extraData: String
        ) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        SanatanamFirebaseMessagingService.CHANNEL_ID,
                        SanatanamFirebaseMessagingService.CHANNEL_NAME,
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Official announcements, darshan updates, and seva alerts"
                        enableLights(true)
                        lightColor = Color.parseColor("#D93F01")
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val formattedTitle = androidx.core.text.HtmlCompat.fromHtml(title, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                val formattedBody = androidx.core.text.HtmlCompat.fromHtml(body, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("screen", targetScreen)
                    putExtra("extra", extraData)
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("imageUrl", imageUrl)
                }

                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt(),
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val builder = androidx.core.app.NotificationCompat.Builder(context, SanatanamFirebaseMessagingService.CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_logo)
                    .setColor(Color.parseColor("#D93F01"))
                    .setContentTitle(formattedTitle)
                    .setContentText(formattedBody)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(formattedBody))
                    .setAutoCancel(true)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)

                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error triggering notification: ${e.message}", e)
            }
        }

        @JavascriptInterface
        fun fetchNotificationLogs() {
            firestore.collection("notification_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(25)
                .get()
                .addOnSuccessListener { snapshot ->
                    val list = JSONArray()
                    for (doc in snapshot.documents) {
                        val obj = JSONObject()
                        obj.put("id", doc.getString("id") ?: doc.id)
                        obj.put("title", doc.getString("title") ?: "")
                        obj.put("body", doc.getString("body") ?: "")
                        obj.put("imageUrl", doc.getString("imageUrl") ?: "")
                        obj.put("targetScreen", doc.getString("targetScreen") ?: "Home")
                        obj.put("audience", doc.getString("audience") ?: "all_users")
                        obj.put("status", doc.getString("status") ?: "Sent")
                        obj.put("timestamp", doc.getTimestamp("timestamp")?.toDate()?.toString() ?: "")
                        list.put(obj)
                    }
                    val jsonStr = list.toString()
                    webViewProvider()?.post {
                        webViewProvider()?.evaluateJavascript(
                            "if (window.onFirestoreLogsLoaded) { window.onFirestoreLogsLoaded($jsonStr); }",
                            null
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AndroidBridge", "Error fetching Firestore logs: ${e.message}")
                }
        }

        @JavascriptInterface
        fun onRegisterClick(lang: String) {
            webViewProvider()?.post {
                webViewProvider()?.loadUrl("file:///android_asset/app_register.html?lang=$lang")
            }
        }

        @JavascriptInterface
        fun onLoginClick(lang: String) {
            webViewProvider()?.post {
                webViewProvider()?.loadUrl("file:///android_asset/app_login.html?lang=$lang")
            }
        }

        @JavascriptInterface
        fun openNativeScreen(screen: String) {
            val lang = getSavedLanguage()
            openNativeScreen(screen, lang)
        }

        @JavascriptInterface
        fun openNativeScreen(screen: String, lang: String) {
            webViewProvider()?.post {
                when (screen.lowercase()) {
                    "app_register", "register" -> webViewProvider()?.loadUrl("file:///android_asset/app_register.html?lang=$lang")
                    "app_login", "login" -> webViewProvider()?.loadUrl("file:///android_asset/app_login.html?lang=$lang")
                    "app_home", "home" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html")
                    "terms_conditions", "terms" -> webViewProvider()?.loadUrl("file:///android_asset/terms_conditions.html?lang=$lang")
                    else -> webViewProvider()?.loadUrl("file:///android_asset/$screen.html?lang=$lang")
                }
            }
        }

        @JavascriptInterface
        fun saveUserData(userDataJson: String) {
            try {
                context.getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("sss_user_data", userDataJson)
                    .apply()
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error saving user data: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onAuthSuccess(token: String, userDataJson: String) {
            try {
                context.getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("sss_user_token", token)
                    .putString("sss_user_data", userDataJson)
                    .putString("sanatanam_session_token", "active_" + System.currentTimeMillis())
                    .apply()
                Log.d("AndroidBridge", "Auth successfully recorded in preferences.")
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error in onAuthSuccess: ${e.message}")
            }
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error opening external URL: ${e.message}")
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            webViewProvider()?.post {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun registerMember(name: String, phone: String, email: String, city: String, sevaCategory: String): String {
            return try {
                val userId = auth.currentUser?.uid ?: ("user_" + System.currentTimeMillis())
                val data = hashMapOf(
                    "userId" to userId,
                    "displayName" to name,
                    "phone" to phone,
                    "email" to email,
                    "city" to city,
                    "sevaCategory" to sevaCategory,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(userId).set(data)
                userId
            } catch (e: Exception) {
                Log.e("AndroidBridge", "Error saving member: ${e.message}")
                "local_success"
            }
        }

        @JavascriptInterface
        fun navigateToScreen(screenName: String) {
            webViewProvider()?.post {
                when (screenName.lowercase()) {
                    "home" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html")
                    "welcome" -> webViewProvider()?.loadUrl("file:///android_asset/app_welcome.html")
                    "register", "app_register" -> webViewProvider()?.loadUrl("file:///android_asset/app_register.html")
                    "login", "app_login" -> webViewProvider()?.loadUrl("file:///android_asset/app_login.html")
                    "traininglab", "training_lab" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=training_lab")
                    "profile" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=profile")
                    "enginedetails", "engine_details" -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=engine_details")
                    "admin" -> webViewProvider()?.loadUrl("file:///android_asset/admin/notifications.html")
                    "wallet", "app_wallet" -> webViewProvider()?.loadUrl("file:///android_asset/app_wallet.html")
                    else -> webViewProvider()?.loadUrl("file:///android_asset/app_home.html?screen=$screenName")
                }
            }
        }

        @JavascriptInterface
        fun syncUserFcmTopic(uniqueId: String) {
            if (uniqueId.isNotBlank()) {
                val cleanTopic = "user_" + uniqueId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                FirebaseMessaging.getInstance().subscribeToTopic(cleanTopic)
                    .addOnSuccessListener {
                        Log.d("WALLET_FCM", "Subscribed to personal topic: $cleanTopic")
                    }
                    .addOnFailureListener { e ->
                        Log.w("WALLET_FCM", "Failed to subscribe to personal topic $cleanTopic: ${e.message}")
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

        // Safely check and retrieve FCM device token; only subscribe to topics when token is valid and Play Services are ready
        try {
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
            if (availability == ConnectionResult.SUCCESS) {
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (!token.isNullOrEmpty()) {
                            getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("fcm_device_token", token)
                                .apply()
                            Log.d("FCM", "Current FCM Token: $token")

                            // Subscribe to global announcements topic only when token is valid
                            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("FCM", "Subscribed to all_users topic successfully")
                                    } else {
                                        Log.i("FCM", "Topic subscription postponed: ${task.exception?.message}")
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.i("FCM", "FCM token registration skipped in emulator/test environment: ${e.message}")
                    }
            } else {
                Log.i("FCM", "Google Play Services not ready ($availability). FCM registration safely bypassed in current runtime.")
            }
        } catch (e: Exception) {
            Log.i("FCM", "FirebaseMessaging init notice: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val soundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.sanatanam_ringtone)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                val channel = NotificationChannel(
                    "sanatanam_wallet_channel",
                    "Wallet Transactions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for wallet credits and debits"
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
                Log.d("WALLET_FCM", "sanatanam_wallet_channel created with custom ringtone")
            } catch (e: Exception) {
                Log.e("WALLET_FCM", "Error creating wallet notification channel: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
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
