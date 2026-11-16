package com.vizeno.focuser

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vizeno.focuser.blocker.AccessibilityStatus
import com.vizeno.focuser.blocker.BlockPreferences
import com.vizeno.focuser.blocker.DisplayGreyscale
import com.vizeno.focuser.ui.theme.FocuserTheme

class MainActivity : ComponentActivity() {
    private lateinit var prefs: BlockPreferences
    private var serviceEnabled by mutableStateOf(false)
    private var blockReels by mutableStateOf(true)
    private var blockShorts by mutableStateOf(true)
    private var blockInstagram by mutableStateOf(false)
    private var blockYouTube by mutableStateOf(false)
    private var greyscale by mutableStateOf(false)
    private var greyscalePermitted by mutableStateOf(false)
    private var limitToday by mutableStateOf(0)
    private var limitTomorrow by mutableStateOf(0)
    private var instagramInstalled by mutableStateOf(true)
    private var youtubeInstalled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = BlockPreferences(this)
        blockReels = prefs.blockReels
        blockShorts = prefs.blockShorts
        blockInstagram = prefs.blockInstagram
        blockYouTube = prefs.blockYouTube
        greyscale = prefs.greyscale
        prefs.ensureToday()
        limitToday = prefs.limitToday
        limitTomorrow = prefs.limitTomorrow
        enableEdgeToEdge()
        setContent {
            FocuserTheme {
                FocuserScreen(
                    serviceEnabled = serviceEnabled,
                    blockReels = blockReels,
                    blockShorts = blockShorts,
                    blockInstagram = blockInstagram,
                    blockYouTube = blockYouTube,
                    greyscale = greyscale,
                    greyscalePermitted = greyscalePermitted,
                    limitToday = limitToday,
                    limitTomorrow = limitTomorrow,
                    instagramInstalled = instagramInstalled,
                    youtubeInstalled = youtubeInstalled,
                    onEnable = { AccessibilityStatus.openSettings(this) },
                    onReels = { enabled ->
                        blockReels = enabled
                        prefs.blockReels = enabled
                    },
                    onShorts = { enabled ->
                        blockShorts = enabled
                        prefs.blockShorts = enabled
                    },
                    onInstagram = { enabled ->
                        blockInstagram = enabled
                        prefs.blockInstagram = enabled
                    },
                    onYouTube = { enabled ->
                        blockYouTube = enabled
                        prefs.blockYouTube = enabled
                    },
                    onGreyscale = { enabled ->
                        greyscale = enabled
                        prefs.greyscale = enabled
                    },
                    onLimitMinutes = { minutes ->
                        prefs.scheduleTomorrow(minutes)
                        limitToday = prefs.limitToday
                        limitTomorrow = prefs.limitTomorrow
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AccessibilityStatus.isEnabled(this)
        greyscalePermitted = DisplayGreyscale(this).isPermitted()
        prefs.ensureToday()
        limitToday = prefs.limitToday
        limitTomorrow = prefs.limitTomorrow
        instagramInstalled = isInstalled(BlockPreferences.INSTAGRAM)
        youtubeInstalled = isInstalled(BlockPreferences.YOUTUBE)
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

@Composable
private fun FocuserScreen(
    serviceEnabled: Boolean,
    blockReels: Boolean,
    blockShorts: Boolean,
    blockInstagram: Boolean,
    blockYouTube: Boolean,
    greyscale: Boolean,
    greyscalePermitted: Boolean,
    limitToday: Int,
    limitTomorrow: Int,
    instagramInstalled: Boolean,
    youtubeInstalled: Boolean,
    onEnable: () -> Unit,
    onReels: (Boolean) -> Unit,
    onShorts: (Boolean) -> Unit,
    onInstagram: (Boolean) -> Unit,
    onYouTube: (Boolean) -> Unit,
    onGreyscale: (Boolean) -> Unit,
    onLimitMinutes: (Int) -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Focuser", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Close an app, wait before Instagram, YouTube, or TikTok opens, or keep a daily limit.",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (serviceEnabled) {
                Text("Blocking is on", style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable blocking")
                }
                Text(
                    "Allow Focuser in Accessibility. It watches Instagram, YouTube, TikTok, and browsers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            ToggleRow(
                title = "Greyscale",
                subtitle = greyscaleStatus(serviceEnabled, greyscale, greyscalePermitted),
                checked = greyscale,
                enabled = true,
                onChecked = onGreyscale,
            )
            MinuteRow(
                minutes = limitTomorrow,
                serviceEnabled = serviceEnabled,
                today = limitToday,
                tomorrow = limitTomorrow,
                onMinutes = onLimitMinutes,
            )
            ToggleRow(
                title = "Instagram",
                subtitle = statusText(serviceEnabled, instagramInstalled, blockInstagram, "Closes as soon as it opens"),
                checked = blockInstagram,
                enabled = instagramInstalled,
                onChecked = onInstagram,
            )
            ToggleRow(
                title = "Instagram Reels",
                subtitle = featureStatus(
                    serviceEnabled,
                    instagramInstalled,
                    blockInstagram,
                    blockReels,
                    "Closes Reels",
                ),
                checked = blockReels,
                enabled = instagramInstalled && !blockInstagram,
                onChecked = onReels,
            )
            ToggleRow(
                title = "YouTube",
                subtitle = statusText(serviceEnabled, youtubeInstalled, blockYouTube, "Closes as soon as it opens"),
                checked = blockYouTube,
                enabled = youtubeInstalled,
                onChecked = onYouTube,
            )
            ToggleRow(
                title = "YouTube Shorts",
                subtitle = featureStatus(serviceEnabled, youtubeInstalled, blockYouTube, blockShorts, "Closes on Shorts"),
                checked = blockShorts,
                enabled = youtubeInstalled && !blockYouTube,
                onChecked = onShorts,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
        )
    }
}

@Composable
private fun MinuteRow(
    minutes: Int,
    serviceEnabled: Boolean,
    today: Int,
    tomorrow: Int,
    onMinutes: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Time limit", style = MaterialTheme.typography.titleLarge)
            Text(
                limitStatus(serviceEnabled, today, tomorrow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = if (minutes == 0) "" else minutes.toString(),
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(3)
                onMinutes((digits.toIntOrNull() ?: 0).coerceIn(0, BlockPreferences.MAX_LIMIT_MINUTES))
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("Off") },
            suffix = { Text("min") },
        )
    }
}

private fun limitStatus(serviceEnabled: Boolean, today: Int, tomorrow: Int): String {
    if (today <= 0 && tomorrow <= 0) return "Off"
    if (!serviceEnabled) {
        if (today <= 0) return "Starts tomorrow at $tomorrow min"
        return "Will close after $today min today"
    }
    val scope = "Each of Instagram, YouTube, TikTok, and the browser."
    if (today == tomorrow) {
        if (today <= 0) return "Off"
        return "$today min today. Leaving the app does not reset it. $scope"
    }
    val todayText = if (today <= 0) "Off today" else "Today stays $today min"
    val tomorrowText = if (tomorrow <= 0) "Off tomorrow" else "$tomorrow min starts tomorrow"
    return "$todayText. $tomorrowText. $scope"
}

private fun greyscaleStatus(serviceEnabled: Boolean, enabled: Boolean, permitted: Boolean): String {
    if (!permitted) return "Needs one-time setup"
    if (!enabled) return "Off"
    if (!serviceEnabled) return "Will turn the screen grey"
    return "Grey while Instagram or YouTube is open"
}

private fun statusText(
    serviceEnabled: Boolean,
    installed: Boolean,
    enabled: Boolean,
    onLabel: String,
): String {
    if (!installed) return "Not installed"
    if (!enabled) return "Off"
    if (!serviceEnabled) return "Will close the app"
    return onLabel
}

private fun featureStatus(
    serviceEnabled: Boolean,
    installed: Boolean,
    wholeApp: Boolean,
    enabled: Boolean,
    onLabel: String,
): String {
    if (wholeApp && installed) return "Already covered"
    return statusText(serviceEnabled, installed, enabled, onLabel)
}
