package dev.matejgroombridge.habittracker.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.BuildConfig
import dev.matejgroombridge.habittracker.R
import dev.matejgroombridge.habittracker.data.settings.NfcAction
import dev.matejgroombridge.habittracker.data.settings.WeekStart
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.theme.ThemeMode
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Settings is intentionally kept calm and uncluttered: a couple of grouped
 * tiles on a soft container background, with each section having its own
 * card and labelled with a small caption above. Adding a setting? Drop a new
 * row inside the relevant [SettingsCard], or add a new card entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenReorder: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenWriteNfc: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = homeViewModel.exportJson() ?: return@launch
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            }.onSuccess {
                snackbar.showSnackbar("Habits exported")
            }.onFailure {
                snackbar.showSnackbar("Export failed")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw == null) {
                snackbar.showSnackbar("Couldn't read file")
                return@launch
            }
            val count = homeViewModel.importJson(raw)
            if (count != null) snackbar.showSnackbar("Imported $count habits")
            else snackbar.showSnackbar("Import failed — invalid JSON")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Appearance ----------------------------------------------------
            SectionCaption("Appearance")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ThemePickerRow(
                        selected = settings.themeMode,
                        onChange = {
                            haptics.light()
                            viewModel.setThemeMode(it)
                        },
                    )
                    // Compact AMOLED toggle — no subtitle, since the label
                    // is already self-explanatory and the row sits directly
                    // under the theme picker so context is obvious.
                    CompactSwitchRow(
                        icon = Icons.Outlined.Brightness3,
                        label = "AMOLED dark mode",
                        checked = settings.amoled,
                        onChange = {
                            haptics.light()
                            viewModel.setAmoled(it)
                        },
                    )
                }
            }

            // Reminders -----------------------------------------------------
            SectionCaption("Reminders")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val notifPermission = if (android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
                        androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            // If the user denies notifications, immediately
                            // disable reminders so settings + reality match.
                            if (!granted) viewModel.setReminderEnabled(false)
                        }
                    } else null
                    SwitchRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = "Daily reminders",
                        subtitle = if (settings.reminders.enabled)
                            "Pinging you ${settings.reminders.timesPerDay}× per day"
                        else "Off",
                        checked = settings.reminders.enabled,
                        onChange = { wantsOn ->
                            haptics.light()
                            viewModel.setReminderEnabled(wantsOn)
                            // On Android 13+, request POST_NOTIFICATIONS the
                            // moment the user opts in. The launcher is gated
                            // by an SDK check so older devices skip this.
                            if (wantsOn && notifPermission != null) {
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    if (settings.reminders.enabled) {
                        // Reminders per day stepper
                        StepperRow(
                            icon = Icons.Outlined.Schedule,
                            label = "Reminders per day",
                            value = settings.reminders.timesPerDay,
                            min = 1, max = 6,
                            onChange = {
                                haptics.light()
                                viewModel.setReminderTimesPerDay(it)
                            },
                        )
                        TimeRow(
                            label = if (settings.reminders.timesPerDay == 1) "Reminder time"
                            else "First reminder",
                            time = settings.reminders.firstTime,
                            onPick = {
                                haptics.light()
                                viewModel.setReminderFirstTime(it)
                            },
                            context = context,
                        )
                        if (settings.reminders.timesPerDay > 1) {
                            TimeRow(
                                label = "Last reminder",
                                time = settings.reminders.lastTime,
                                onPick = {
                                    haptics.light()
                                    viewModel.setReminderLastTime(it)
                                },
                                context = context,
                            )
                        }
                    }
                }
            }

            // General -------------------------------------------------------
            // Consolidates the simple-actions: week start, reorder, archive,
            // export, import. Keeps the page from sprawling and matches what
            // a user expects under a "general" heading.
            SectionCaption("General")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    WeekStartNavRow(
                        selected = settings.weekStart,
                        onChange = {
                            haptics.light()
                            viewModel.setWeekStart(it)
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Reorder,
                        label = "Reorder Habits",
                        onClick = {
                            haptics.light()
                            onOpenReorder()
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Archive,
                        label = "Archived Habits",
                        onClick = {
                            haptics.light()
                            onOpenArchive()
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Upload,
                        label = "Export to JSON",
                        onClick = {
                            haptics.light()
                            exportLauncher.launch("habits-backup.json")
                        },
                    )
                    Divider()
                    NavRow(
                        icon = Icons.Outlined.Download,
                        label = "Import from JSON",
                        onClick = {
                            haptics.light()
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                    )
                }
            }

            // NFC -----------------------------------------------------------
            SectionCaption("NFC tag scans")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NfcActionRow(
                        selected = settings.nfcAction,
                        onChange = {
                            haptics.light()
                            viewModel.setNfcAction(it)
                        },
                    )
                    NavRowCompact(
                        icon = Icons.Outlined.Nfc,
                        label = "Write a tag",
                        onClick = {
                            haptics.light()
                            onOpenWriteNfc()
                        },
                    )
                }
            }

            // About ---------------------------------------------------------
            SectionCaption("About")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// --- Building blocks -------------------------------------------------------

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun ThemePickerRow(
    selected: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    Column {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeButton(
                label = "System", icon = Icons.Outlined.SettingsBrightness,
                selected = selected == ThemeMode.System, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.System) },
            )
            ThemeButton(
                label = "Light", icon = Icons.Outlined.LightMode,
                selected = selected == ThemeMode.Light, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Light) },
            )
            ThemeButton(
                label = "Dark", icon = Icons.Outlined.DarkMode,
                selected = selected == ThemeMode.Dark, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Dark) },
            )
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = border,
        ),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Compact switch row with no subtitle — used for self-explanatory toggles
 * like AMOLED dark mode, where the label alone is enough.
 */
@Composable
private fun CompactSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * NavRow-shaped two-state toggle for week start. Tap anywhere to flip
 * Monday ↔ Sunday; the trailing text shows the current selection so the
 * user doesn't need to drill into a separate sub-screen.
 */
@Composable
private fun WeekStartNavRow(selected: WeekStart, onChange: (WeekStart) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onChange(if (selected == WeekStart.Monday) WeekStart.Sunday else WeekStart.Monday)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Week starts on",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (selected == WeekStart.Monday) "Mon" else "Sun",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NfcActionRow(selected: NfcAction, onChange: (NfcAction) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "When you scan a tag",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipChoice("Background", selected == NfcAction.Background, Modifier.weight(1f)) {
                onChange(NfcAction.Background)
            }
            ChipChoice("Overlay", selected == NfcAction.Overlay, Modifier.weight(1f)) {
                onChange(NfcAction.Overlay)
            }
            ChipChoice("Open app", selected == NfcAction.OpenApp, Modifier.weight(1f)) {
                onChange(NfcAction.OpenApp)
            }
        }
    }
}

@Composable
private fun ChipChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = border,
        ),
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(
    icon: ImageVector,
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    time: String,
    context: android.content.Context,
    onPick: (String) -> Unit,
) {
    val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9, 0))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                TimePickerDialog(context, { _, h, m ->
                    onPick(LocalTime.of(h, m).toString())
                }, parsed.hour, parsed.minute, true).show()
            }
            .padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(48.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = parsed.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavRowCompact(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
