package dev.matejgroombridge.habittracker.ui.screens

import android.app.Activity
import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * In-app NFC tag writer. The user picks one of their habits and then taps a
 * blank (or rewritable) NFC tag against the back of their phone — we write
 * the habit's deep-link URL onto it as an NDEF URI record.
 *
 * Implementation:
 *  - Uses [NfcAdapter.enableReaderMode] while this composable is in the
 *    Resumed state. ReaderMode bypasses Android's foreground-dispatch tap
 *    sound + intent firing, giving us a clean callback for each tag scan.
 *  - Disables the NDEF check delay so writes feel instant.
 *  - Writes via [Ndef] when the tag is already formatted, falling back to
 *    [NdefFormatable] for fresh tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteNfcTagScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<Habit?>(null) }
    var writing by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<WriteResult?>(null) }

    // Enable / disable reader mode in lock-step with [selected].
    DisposableEffect(selected, activity, nfcAdapter) {
        val sel = selected
        if (sel == null || activity == null || nfcAdapter == null) {
            onDispose { }
        } else {
            val callback = NfcAdapter.ReaderCallback { tag ->
                writing = true
                val result = writeUrlToTag(tag, sel.nfcUrl)
                lastResult = result
                writing = false
                if (result is WriteResult.Success) {
                    activity.runOnUiThread {
                        haptics.completion()
                    }
                    scope.launch { snackbar.showSnackbar("Wrote tag for ${sel.name}") }
                } else {
                    scope.launch { snackbar.showSnackbar((result as WriteResult.Failure).message) }
                }
            }
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            nfcAdapter.enableReaderMode(activity, callback, flags, null)
            onDispose { nfcAdapter.disableReaderMode(activity) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Write NFC Tag") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (nfcAdapter == null) {
                Text(
                    text = "This device doesn't have NFC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            if (!nfcAdapter.isEnabled) {
                Text(
                    text = "NFC is turned off — enable it from system Settings to write a tag.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = if (selected == null) "Pick a habit to write to a tag:"
                else "Hold a blank NFC tag against the back of your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selected == null) {
                HabitPicker(
                    habits = state.activeHabits,
                    onSelect = { selected = it },
                )
            } else {
                ScanPanel(
                    habit = selected!!,
                    writing = writing,
                    onChange = { selected = null },
                )
            }
        }
    }
}

@Composable
private fun HabitPicker(habits: List<Habit>, onSelect: (Habit) -> Unit) {
    if (habits.isEmpty()) {
        Text(
            text = "Add a habit first to write a tag for it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        habits.forEach { h ->
            val color = HabitColors.entry(h.colorKey)
            val icon = HabitIcons.entry(h.iconKey)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(h) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon.icon,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = h.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPanel(habit: Habit, writing: Boolean, onChange: () -> Unit) {
    val color = HabitColors.entry(habit.colorKey)
    val icon = HabitIcons.entry(habit.iconKey)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(color.accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (writing) Icons.Outlined.CheckCircle else Icons.Outlined.Nfc,
                    contentDescription = null,
                    tint = if (writing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (writing) "Writing…" else "Waiting for tag…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                onClick = onChange,
            ) {
                Text(
                    text = "Pick a different habit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private sealed interface WriteResult {
    data object Success : WriteResult
    data class Failure(val message: String) : WriteResult
}

/**
 * Synchronously writes [url] to [tag] as an NDEF URI record. Called off the
 * main thread by NFC reader-mode.
 */
private fun writeUrlToTag(tag: Tag, url: String): WriteResult {
    return try {
        val record = NdefRecord.createUri(url)
        val message = NdefMessage(arrayOf(record))

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            if (!ndef.isWritable) return WriteResult.Failure("Tag is read-only")
            if (ndef.maxSize < message.byteArrayLength) {
                return WriteResult.Failure("Tag too small")
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            WriteResult.Success
        } else {
            val format = NdefFormatable.get(tag)
                ?: return WriteResult.Failure("Tag doesn't support NDEF")
            format.connect()
            format.format(message)
            format.close()
            WriteResult.Success
        }
    } catch (e: FormatException) {
        WriteResult.Failure("Format error: ${e.message ?: "unknown"}")
    } catch (e: Exception) {
        WriteResult.Failure(e.message ?: "Write failed")
    }
}
