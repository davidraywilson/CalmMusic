package com.calmapps.calmtunes

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*

class MediaNotificationListenerService : NotificationListenerService() {

    private val TAG = "MediaListener"
    private val RADIO_PACKAGES = setOf("com.android.fmradio", "com.mediatek.fmradio", "com.caf.fmradio")

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        ExternalMediaRepository.notificationCanceller = { targetPackage ->
            cancelNotificationsForPackage(targetPackage)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scanForActiveMedia()
    }

    private fun cancelNotificationsForPackage(packageName: String) {
        try {
            val notifications = activeNotifications
            notifications.filter { it.packageName == packageName }.forEach { sbn ->
                cancelNotification(sbn.key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification for $packageName", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (RADIO_PACKAGES.contains(sbn.packageName)) {
            ExternalMediaRepository.updateController(null)

            if (pollingJob == null || !pollingJob!!.isActive) {
                startPolling()
            }
            updateRawFromSbn(sbn)
        } else if (isMediaNotification(sbn)) {
            if (pollingJob?.isActive == true) return

            updateControllerFromSbn(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == ExternalMediaRepository.value.packageName || RADIO_PACKAGES.contains(sbn.packageName)) {
            ExternalMediaRepository.clear()
            stopPolling()
            scanForActiveMedia()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        serviceScope.cancel()
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val notifications = activeNotifications
                    val radioNotification = notifications.firstOrNull { RADIO_PACKAGES.contains(it.packageName) }

                    if (radioNotification != null) {
                        ExternalMediaRepository.updateController(null)
                        updateRawFromSbn(radioNotification)
                    } else {
                        if (ExternalMediaRepository.value.isRawNotification) {
                            ExternalMediaRepository.clear()
                            stopPolling()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(500)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun scanForActiveMedia() {
        val notifications = activeNotifications
        val radioNotification = notifications.firstOrNull { RADIO_PACKAGES.contains(it.packageName) }

        if (radioNotification != null) {
            ExternalMediaRepository.updateController(null)
            startPolling()
            updateRawFromSbn(radioNotification)
            return
        }

        val mediaNotification = notifications.firstOrNull { isMediaNotification(it) }
        if (mediaNotification != null) {
            updateControllerFromSbn(mediaNotification)
            return
        }

        ExternalMediaRepository.clear()
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        return sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    }

    private fun updateControllerFromSbn(sbn: StatusBarNotification) {
        val token = sbn.notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token != null) {
            ExternalMediaRepository.updateController(MediaController(this, token))
        }
    }

    private fun getSystemFmFrequency(): String? {
        try {
            val param = audioManager?.getParameters("fm_freq") ?: ""
            if (param.isNotBlank() && param.contains("=")) {
                val value = param.split("=").lastOrNull()?.trim()?.toIntOrNull()
                if (value != null) {
                    if (value > 1000) {
                        return "${value / 100}.${(value % 100) / 10}" // 10330 -> 103.3
                    } else if (value > 0) {
                        return "$value"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun updateRawFromSbn(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "FM Radio"

        val sb = StringBuilder()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { sb.append(it).append(" ") }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { sb.append(it).append(" ") }

        val hardwareFreq = getSystemFmFrequency()
        if (hardwareFreq != null) {
            sb.append(hardwareFreq).append(" ")
        }

        val combinedText = sb.toString().trim()

        var actionPrev: PendingIntent? = null
        var actionPlayPause: PendingIntent? = null
        var actionNext: PendingIntent? = null

        val actions = n.actions
        if (actions != null) {
            for (action in actions) {
                val name = action.title?.toString()?.lowercase() ?: ""
                if (name.contains("prev") || name.contains("back")) actionPrev = action.actionIntent
                if (name.contains("next") || name.contains("skip")) actionNext = action.actionIntent
                if (name.contains("pause") || name.contains("play") || name.contains("stop")) actionPlayPause = action.actionIntent
            }
            if (actionPrev == null && actions.isNotEmpty()) actionPrev = actions[0].actionIntent
            if (actionNext == null && actions.size > 1) actionNext = actions[actions.size - 1].actionIntent
            if (actions.size == 3 && actionPlayPause == null) actionPlayPause = actions[1].actionIntent
        }

        ExternalMediaRepository.updateRawState(
            packageName = sbn.packageName,
            title = title,
            text = combinedText,
            isPlaying = true,
            actionPrev = actionPrev,
            actionPlayPause = actionPlayPause,
            actionNext = actionNext
        )
    }
}