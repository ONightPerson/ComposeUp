package com.example.composeup.screenrecord

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ScreenRecordHub(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRecording by RecordingState.isRecording.collectAsState()
    val scope = rememberCoroutineScope()
    
    var videoList by remember { mutableStateOf(emptyList<RecordedVideo>()) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    
    val refreshVideos = {
        videoList = MediaStoreUtils.fetchVideos(context)
    }

    // Load videos
    LaunchedEffect(Unit) {
        refreshVideos()
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    val listPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refreshVideos()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Record Button
        Button(
            onClick = {
                if (isRecording) {
                    val intent = Intent(context, ScreenRecordService::class.java).apply {
                        action = ScreenRecordService.ACTION_STOP
                    }
                    context.startService(intent)
                } else {
                    val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(neededPermissions.toTypedArray())
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recorded Videos", style = MaterialTheme.typography.titleLarge)
            
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    @Suppress("DEPRECATION")
                    listPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }) {
                Text("Refresh")
            }
        }

        // Deletion Controls
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${selectedIds.size} selected")
                TextButton(
                    onClick = {
                        scope.launch {
                            selectedIds.forEach { id ->
                                videoList.find { it.id == id }?.let { video ->
                                    MediaStoreUtils.deleteVideo(context, video.uri)
                                }
                            }
                            selectedIds = emptySet()
                            refreshVideos()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete Selected")
                }
            }
        }

        // Video List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(videoList, key = { it.id }) { video ->
                VideoItem(
                    video = video,
                    isSelected = selectedIds.contains(video.id),
                    onToggleSelect = {
                        selectedIds = if (selectedIds.contains(video.id)) {
                            selectedIds - video.id
                        } else {
                            selectedIds + video.id
                        }
                    },
                    onDelete = {
                        scope.launch {
                            MediaStoreUtils.deleteVideo(context, video.uri)
                            refreshVideos()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VideoItem(
    video: RecordedVideo,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(video.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Size: ${video.size / 1024} KB | Duration: ${video.duration / 1000}s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}
