package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SanatanamFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SanatanamFCM"
        const val CHANNEL_ID = "sanatanam_updates_channel"
        const val CHANNEL_NAME = "Sanatan Seva Samiti Announcements"
        const val WALLET_CHANNEL_ID = "sanatanam_wallet_channel"
        const val WALLET_CHANNEL_NAME = "Wallet Transactions"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        // Save token locally
        getSharedPreferences("sss_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_device_token", token)
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val rawTitle = data["title"] ?: notification?.title ?: "Sanatan Seva Samiti"
        val rawBody = data["body"] ?: notification?.body ?: "नई सूचना प्राप्त हुई है"
        val imageUrl = data["imageUrl"] ?: notification?.imageUrl?.toString()
        val targetScreen = data["screen"] ?: "Home"
        val extraData = data["extra"] ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            showNotification(rawTitle, rawBody, imageUrl, targetScreen, extraData, data)
        }
    }

    private fun showNotification(
        title: String,
        bodyHtml: String,
        imageUrl: String?,
        targetScreen: String,
        extraData: String,
        data: Map<String, String> = emptyMap()
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val isWallet = data["channel_id"] == WALLET_CHANNEL_ID ||
                data["type"] == "wallet" ||
                targetScreen.contains("wallet", ignoreCase = true)

        val activeChannelId = if (isWallet) WALLET_CHANNEL_ID else CHANNEL_ID
        val ringtoneUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.sanatanam_ringtone)

        // Create Notification Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isWallet) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                val walletChannel = NotificationChannel(
                    WALLET_CHANNEL_ID,
                    WALLET_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for wallet credits and debits"
                    setSound(ringtoneUri, audioAttributes)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(walletChannel)
            } else {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Official announcements, darshan updates, and seva alerts"
                    enableLights(true)
                    lightColor = Color.parseColor("#D93F01")
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        // Support HTML / Rich text formatting
        val formattedBody = HtmlCompat.fromHtml(bodyHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val formattedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Deep-linking Intent to open target screen in app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("screen", targetScreen)
            putExtra("extra", extraData)
            putExtra("title", title)
            putExtra("body", bodyHtml)
            putExtra("imageUrl", imageUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Download Banner Image if present for BigPictureStyle
        var bannerBitmap: Bitmap? = null
        if (!imageUrl.isNullOrBlank()) {
            bannerBitmap = downloadBitmap(imageUrl)
        }

        val notificationBuilder = NotificationCompat.Builder(this, activeChannelId)
            .setSmallIcon(R.drawable.app_logo)
            .setColor(Color.parseColor("#D93F01"))
            .setContentTitle(formattedTitle)
            .setContentText(formattedBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        if (isWallet) {
            notificationBuilder.setSound(ringtoneUri)
        }

        if (bannerBitmap != null) {
            val bigPictureStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(bannerBitmap)
                .setBigContentTitle(formattedTitle)
                .setSummaryText(formattedBody)
            notificationBuilder.setStyle(bigPictureStyle)
        } else {
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText(formattedBody)
                .setBigContentTitle(formattedTitle)
            notificationBuilder.setStyle(bigTextStyle)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading notification image: ${e.message}")
            null
        }
    }
}
