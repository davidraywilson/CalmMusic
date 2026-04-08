package com.calmapps.calmtunes.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmtunes.ExternalMediaRepository
import com.calmapps.calmtunes.ExternalMediaState
import com.calmapps.calmtunes.CalmTunesAccessibilityService
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat

enum class RadioCommand { NEXT, PREVIOUS, TOGGLE_POWER, STOP, FORCE_PLAY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    onNavigateBack: () -> Unit,
    onPausePlayback: () -> Unit,
    isAppPlaying: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaState by ExternalMediaRepository.mediaState.collectAsState()

    var showAccessibilitySheet by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }

    val accessibilitySheetState = rememberModalBottomSheetMMDState()
    val notificationSheetState = rememberModalBottomSheetMMDState()

    val isRadioActive = mediaState.packageName.contains("radio", ignoreCase = true) ||
            mediaState.packageName.contains("fm", ignoreCase = true)

    if (!isRadioActive) {
        EmptyRadioState(
            onPowerOn = {
                if (!isAccessibilityServiceEnabled(context, CalmTunesAccessibilityService::class.java)) {
                    showAccessibilitySheet = true
                } else if (!isNotificationListenerEnabled(context)) {
                    showNotificationSheet = true
                } else {
                    scope.launch {
                        val packageName = findRadioPackage(context)
                        if (packageName != null) {
                            if (isAppPlaying) onPausePlayback()

                            launchSystemRadioApp(context, packageName)

                            delay(5000)
                            val myIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            myIntent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            myIntent?.putExtra("FROM_RADIO_TUNER", true)
                            context.startActivity(myIntent)
                        }
                    }
                }
            }
        )
    } else {
        ActiveRadioState(
            context = context,
            mediaState = mediaState,
            targetPackage = mediaState.packageName
        )
    }

    // 1. Accessibility Permission Sheet
    if (showAccessibilitySheet) {
        ModalBottomSheetMMD(
            onDismissRequest = { showAccessibilitySheet = false },
            sheetState = accessibilitySheetState
        ) {
            PermissionSheetContent(
                title = "Control Permission Required",
                description = "To control the FM Radio tuner, CalmTunes requires the Accessibility Service permission. Please enable 'CalmTunes Helper' in the settings.",
                buttonText = "Open Accessibility Settings",
                onConfirm = {
                    showAccessibilitySheet = false
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                onCancel = { showAccessibilitySheet = false }
            )
        }
    }

    if (showNotificationSheet) {
        ModalBottomSheetMMD(
            onDismissRequest = { showNotificationSheet = false },
            sheetState = notificationSheetState
        ) {
            PermissionSheetContent(
                title = "Read Status Permission",
                description = "To see the current radio frequency and status, CalmTunes needs to read the 'Now Playing' notification from the radio app. Please allow 'Notification Access' for CalmTunes.",
                buttonText = "Open Notification Settings",
                onConfirm = {
                    showNotificationSheet = false
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                onCancel = { showNotificationSheet = false }
            )
        }
    }
}

@Composable
fun PermissionSheetContent(
    title: String,
    description: String,
    buttonText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        TextMMD(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextMMD(
            text = description,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        ButtonMMD(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            onClick = onConfirm
        ) {
            TextMMD(buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButtonMMD(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            onClick = onCancel
        ) {
            TextMMD("Cancel", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServicesSetting)
    while (splitter.hasNext()) {
        val componentNameString = splitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun EmptyRadioState(onPowerOn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onPowerOn() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                "Turn On Radio",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextMMD("Turn on FM Radio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        TextMMD("Tap to launch tuner", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
fun ActiveRadioState(
    context: Context,
    mediaState: ExternalMediaState,
    targetPackage: String
) {
    var systemFrequency by remember { mutableStateOf<Float?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(10000)
            isScanning = false
        }
    }

    LaunchedEffect(mediaState.title, mediaState.artist) {
        val rawText = "${mediaState.title} ${mediaState.artist}"
        val regex = Regex("(\\d{2,3}(?:\\.\\d)?)")
        val allMatches = regex.findAll(rawText)

        var foundMatch = false
        for (match in allMatches) {
            val parsed = match.value.toFloatOrNull()
            if (parsed != null && parsed >= 87.0f && parsed <= 108.0f) {
                foundMatch = true
                if (isScanning) {
                    if (systemFrequency == null || systemFrequency != parsed) {
                        systemFrequency = parsed
                        isScanning = false
                    }
                } else {
                    systemFrequency = parsed
                }
                break
            }
        }
        if (!foundMatch) systemFrequency = null
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // We removed the PermissionWarning here because we enforce it before entering this state now,
            // but keeping a small check doesn't hurt if the user revokes it while playing.
            if (!isNotificationListenerEnabled(context)) {
                TextMMD("Notification access revoked", color = MaterialTheme.colorScheme.error)
            } else if (!mediaState.title.contains("FM Radio", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                if (!mediaState.title.matches(Regex(".*\\d{2,3}.*"))) {
                    TextMMD(mediaState.title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    isScanning = true
                    performCommand(context, RadioCommand.PREVIOUS, targetPackage)
                }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Outlined.SkipPrevious, "Scan Down", modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextMMD(
                    text = systemFrequency?.let { DecimalFormat("0.0").format(it) } ?: "Unknown",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if(isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    isScanning = true
                    performCommand(context, RadioCommand.NEXT, targetPackage)
                }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Outlined.SkipNext, "Scan Up", modifier = Modifier.size(48.dp))
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        performCommand(context, RadioCommand.TOGGLE_POWER, targetPackage)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    "Turn Off Radio",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextMMD("Turn Off FM Radio", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

private fun performCommand(context: Context, command: RadioCommand, packageName: String) {
    if (ExternalMediaRepository.value.packageName == packageName &&
        command != RadioCommand.STOP && command != RadioCommand.FORCE_PLAY) {
        when (command) {
            RadioCommand.NEXT -> ExternalMediaRepository.skipToNext()
            RadioCommand.PREVIOUS -> ExternalMediaRepository.skipToPrevious()
            RadioCommand.TOGGLE_POWER -> ExternalMediaRepository.togglePlayPause()
            else -> {}
        }
        return
    }

    if (command == RadioCommand.STOP) {
        val offIntents = listOf(
            "fmradio.turnoff",
            "com.android.fmradio.turnoff",
            "fmradio.stop",
            "com.caf.fmradio.FMOFF"
        )
        offIntents.forEach { action ->
            val intent = Intent(action)
            intent.setPackage(packageName)
            context.sendBroadcast(intent)
        }
        return
    }

    val keyEventCode = when (command) {
        RadioCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
        RadioCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        RadioCommand.TOGGLE_POWER -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RadioCommand.FORCE_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }

    sendTargetedMediaKey(context, keyEventCode, packageName)
}

private fun sendTargetedMediaKey(context: Context, keyCode: Int, packageName: String) {
    val pm = context.packageManager
    val queryIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
    val receivers = pm.queryBroadcastReceivers(queryIntent, 0)

    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
    intent.setPackage(packageName)

    if (receivers.isNotEmpty()) {
        val receiver = receivers[0]
        intent.component = ComponentName(receiver.activityInfo.packageName, receiver.activityInfo.name)
    }

    intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    context.sendBroadcast(intent)

    intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
    context.sendBroadcast(intent)
}

private fun findRadioPackage(context: Context): String? {
    val candidates = listOf("com.android.fmradio", "com.mediatek.fmradio", "com.caf.fmradio")
    return candidates.firstOrNull {
        context.packageManager.getLaunchIntentForPackage(it) != null
    }
}

private fun launchSystemRadioApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) context.startActivity(intent)
    } catch (_: Exception) { }
}