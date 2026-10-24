package com.sneha.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.ChatMsg
import com.sneha.ai.apps.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: MainViewModel, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val ph by vm.uiPhase.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val exec by vm.exec.collectAsStateWithLifecycle()
    val keySet by vm.keySet.collectAsStateWithLifecycle()
    val connOk by vm.connOk.collectAsStateWithLifecycle()
    val retryStatus by vm.retryStatus.collectAsStateWithLifecycle()
    val handsFree by vm.handsFree.collectAsStateWithLifecycle()
    val lastReply by vm.lastSpokenReply.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showApps by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) vm.startListening()
        else vm.banner.value = "মাইক্রোফোন অনুমতি ছাড়া ভয়েস কমান্ড চলবে না। Settings > পারমিশন থেকে অনুমতি দিন।"
    }

    fun onMic() {
        when {
            ph == Phase.Listening -> vm.stopListening()
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.startListening()
            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(keySet, handsFree) {
        if (keySet && handsFree && ph == Phase.Idle) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                vm.startListening()
            }
        }
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !keySet -> MaterialTheme.colorScheme.error
                                connOk == true -> Color(0xFF10B981)
                                connOk == false -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Doreman",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = s.model.removePrefix("gemini-"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = when (ph) {
                            Phase.Idle -> if (handsFree) "অটো-লিসেনিং চালু • নাম ধরে ডাকুন" else "প্রস্তুত"
                            Phase.Listening -> "শুনছি… বলুন"
                            Phase.Thinking -> retryStatus ?: "চিন্তা করছি…"
                            Phase.Speaking -> "বলছি…"
                            Phase.Executing -> exec.text
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (handsFree || ph == Phase.Listening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (!handsFree && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            vm.toggleHandsFree(!handsFree)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = "অটো-ওয়েক মোড",
                        tint = if (handsFree) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nav("coding") }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "কোডিং মোড",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nav("settings") }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "সেটিংস",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!keySet) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { nav("settings:ai") }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "এখানে ট্যাপ করে Gemini API Key সেট করুন",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Orb(
                    phase = ph,
                    level = level,
                    reduceMotion = s.reduceMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .clickable { onMic() }
                )

                Spacer(Modifier.height(18.dp))

                val liveCaption = when {
                    partial.isNotBlank() -> "“$partial”"
                    ph == Phase.Executing -> exec.text
                    ph == Phase.Thinking -> retryStatus ?: "প্রসেস করছি…"
                    lastReply.isNotBlank() -> lastReply
                    else -> "“Doreman” বলে ডাকুন অথবা যেকোনো আদেশ দিন"
                }

                Text(
                    text = liveCaption,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }
        }

        banner?.let { bText ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { vm.banner.value = null }) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = ph == Phase.Executing || ph == Phase.Thinking || ph == Phase.Speaking) {
            Button(
                onClick = { vm.stopAll() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("থামান (STOP)", color = MaterialTheme.colorScheme.onError)
            }
        }

        AnimatedVisibility(visible = showTools) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolPill(Icons.Default.Security, "১০০% পারমিশন হাব") { showTools = false; nav("settings:privacy") }
                    ToolPill(Icons.Default.CleaningServices, "ফোন ক্লিন/বুস্ট") { showTools = false; vm.send("মোবাইল ক্লিন করো") }
                    ToolPill(Icons.Default.Apps, "সব অ্যাপ") { showTools = false; showApps = true }
                    ToolPill(Icons.Default.MusicNote, "গান চালাও") { showTools = false; vm.send("একটা জনপ্রিয় বাংলা গান চালাও") }
                    ToolPill(Icons.Default.Lock, "ফোন লক") { showTools = false; vm.send("ফোন লক করো") }
                    ToolPill(Icons.Default.PowerSettingsNew, "পাওয়ার মেনু") { showTools = false; vm.send("পাওয়ার মেনু খোলো") }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showTools = !showTools }) {
                    Icon(
                        imageVector = if (showTools) Icons.Default.Close else Icons.Default.Build,
                        contentDescription = "টুলস",
                        tint = if (showTools) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Doreman-কে বলুন বা লিখুন…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 2,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                if (input.isNotBlank()) {
                    FilledIconButton(
                        onClick = {
                            val toSend = input
                            input = ""
                            vm.send(toSend)
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "পাঠান", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    FilledIconButton(
                        onClick = { onMic() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (ph == Phase.Listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (ph == Phase.Listening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "ভয়েস",
                            tint = if (ph == Phase.Listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    if (showApps) {
        val app = ctx.applicationContext as SnehaApp
        var searchApp by remember { mutableStateOf("") }
        val apps by produceState(emptyList<AppInfo>()) {
            value = withContext(Dispatchers.Default) { app.launcher.launchable() }
        }
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("কোন অ্যাপ খুলবেন?") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchApp,
                        onValueChange = { searchApp = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("অ্যাপের নাম লিখুন…") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(apps.filter { it.label.contains(searchApp, ignoreCase = true) }, key = { it.pkg }) { ap ->
                            TextButton(
                                onClick = {
                                    showApps = false
                                    vm.openApp(ap.label)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(ap.label, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApps = false }) { Text("বন্ধ") }
            }
        )
    }
}

@Composable
private fun ToolPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun Bubble(
    m: ChatMsg,
    onSpeak: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {}
