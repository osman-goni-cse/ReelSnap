package com.reelsnap.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.reelsnap.TargetApp
import com.reelsnap.data.ImageFormat
import com.reelsnap.data.Settings
import com.reelsnap.data.SettingsRepository
import com.reelsnap.service.ReelSnapService
import com.reelsnap.service.ServiceStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun isServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ReelSnapService::class.java)
    val enabled = AndroidSettings.Secure.getString(
        context.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
}

private fun isInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = null)
    val lastImage by repo.lastImageUri.collectAsState(initial = null)
    val status by ServiceStatus.state.collectAsState()
    var serviceEnabled by remember { mutableStateOf(isServiceEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        serviceEnabled = isServiceEnabled(context)
        onPauseOrDispose { }
    }

    fun update(f: (Settings) -> Settings) = scope.launch { repo.update(f) }

    Scaffold(topBar = { TopAppBar(title = { Text("ReelSnap") }) }) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Status / permission ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceEnabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!serviceEnabled) {
                        Text("Accessibility service is off", fontWeight = FontWeight.Bold)
                        Text(
                            "ReelSnap uses an accessibility service to take screenshots and swipe. " +
                                "Turn on \"ReelSnap auto capture\". If Android says \"Restricted setting\", " +
                                "open App info → ⋮ → Allow restricted settings, then try again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        val state = when {
                            !s.enabled -> "Disabled"
                            status.running -> "Running in ${status.appLabel ?: "target app"}"
                            else -> "Ready: open Instagram or Facebook Reels"
                        }
                        Text(state, fontWeight = FontWeight.Bold)
                        if (status.session != null) {
                            Text(
                                "Session ${status.session}: ${status.reels} reels, ${status.shots} screenshots",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        status.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text(if (serviceEnabled) "Accessibility" else "Turn on") }
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text("App info") }
                    }
                }
            }

            SwitchRow("Auto capture enabled", s.enabled) { v -> update { it.copy(enabled = v) } }

            HorizontalDivider()
            Text("Loop", style = MaterialTheme.typography.titleMedium)
            SecondsSlider("Wait before screenshots", s.waitMs, 0f..10f) { v -> update { it.copy(waitMs = v) } }
            IntSlider("Screenshots per reel", s.shots, 1..10) { v -> update { it.copy(shots = v) } }
            if (s.shots > 1) {
                SecondsSlider("Gap between screenshots", s.gapMs, 0f..10f) { v -> update { it.copy(gapMs = v) } }
                Text(
                    "Android allows about one screenshot per second, so gaps under 1.1 s are stretched.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SwitchRow("Random jitter (±20% timing, varied swipe)", s.jitter) { v -> update { it.copy(jitter = v) } }

            HorizontalDivider()
            Text("Stop automatically (0 = never)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("After reels", s.maxReels, Modifier.weight(1f)) { v -> update { it.copy(maxReels = v) } }
                NumberField("After minutes", s.maxMinutes, Modifier.weight(1f)) { v -> update { it.copy(maxMinutes = v) } }
            }

            HorizontalDivider()
            Text("Apps", style = MaterialTheme.typography.titleMedium)
            TargetApp.entries.forEach { app ->
                val installed = remember(app) { isInstalled(context, app.packageName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.packageName in s.enabledApps,
                        onCheckedChange = { checked ->
                            update {
                                it.copy(
                                    enabledApps = if (checked) it.enabledApps + app.packageName
                                    else it.enabledApps - app.packageName,
                                )
                            }
                        },
                    )
                    Text(app.label)
                    if (!installed) {
                        Text(
                            "  (not installed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("Output", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { f ->
                    FilterChip(
                        selected = s.format == f,
                        onClick = { update { it.copy(format = f) } },
                        label = { Text(f.name) },
                    )
                }
            }
            Text(
                "Saved to Pictures/ReelSnap/<session>/ on this device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow("Toast + vibration on start/pause", s.feedback) { v -> update { it.copy(feedback = v) } }
            OutlinedButton(
                enabled = lastImage != null,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(lastImage), "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure { Toast.makeText(context, "No gallery app found", Toast.LENGTH_SHORT).show() }
                },
            ) { Text("Open last screenshot") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Slider in 0.5 s steps; commits on release to avoid a DataStore write per frame. */
@Composable
private fun SecondsSlider(label: String, valueMs: Long, range: ClosedFloatingPointRange<Float>, onChange: (Long) -> Unit) {
    var local by remember(valueMs) { mutableStateOf(valueMs / 1000f) }
    Column {
        Text("$label: ${"%.1f".format(local)} s")
        Slider(
            value = local,
            onValueChange = { local = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = { onChange((local * 1000).toLong()) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) * 2).toInt() - 1,
        )
    }
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Text("$label: ${local.roundToInt()}")
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter(Char::isDigit).take(5)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
