package com.kelele.manliu.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.kelele.manliu.ArchiveService
import com.kelele.manliu.ArchiveStatus
import com.kelele.manliu.ComicRepository
import com.kelele.manliu.DragHandle
import com.kelele.manliu.DragReorderState
import com.kelele.manliu.R
import com.kelele.manliu.dragReorder
import com.kelele.manliu.ui.components.ArchiveProgressCard
import com.kelele.manliu.ui.theme.Accent
import com.kelele.manliu.ui.theme.AccentSoft
import com.kelele.manliu.ui.theme.BackgroundLight
import com.kelele.manliu.ui.theme.Hairline
import com.kelele.manliu.ui.theme.SoftText
import com.kelele.manliu.ui.theme.SurfaceLight
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repository: ComicRepository,
    onOpen: (Long) -> Unit,
    onRead: (Long) -> Unit,
    notify: (String) -> Unit,
) {
    val loadedAlbums by repository.overviews.collectAsState(initial = null)
    val albums = loadedAlbums ?: emptyList()
    val archive by ArchiveStatus.progress.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragState = remember(listState) { DragReorderState(listState) }
    LaunchedEffect(albums) { dragState.sourceChanged(albums.map { it.id }) }
    val shownAlbums = dragState.arranged(albums) { it.id }
    var showCreate by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<Uri?>(null) }
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { ArchiveService.export(context, it) } }
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> restoreFile = uri }

    if (loadedAlbums == null) {
        Box(Modifier.fillMaxSize().background(BackgroundLight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 24.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.manliu_muse),
                    contentDescription = "漫流少女",
                    modifier = Modifier.size(55.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("漫流", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("让画面一页页流动", color = SoftText, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { showLibraryMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "备份和恢复")
                }
                DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("备份全部图集") },
                        enabled = albums.isNotEmpty() && archive?.running != true,
                        onClick = {
                            showLibraryMenu = false
                            backupPicker.launch("漫流备份.manliu")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("恢复漫流备份") },
                        enabled = archive?.running != true,
                        onClick = {
                            showLibraryMenu = false
                            restorePicker.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
            Button(
                onClick = { showCreate = true },
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }

        archive?.let { ArchiveProgressCard(it, context) }

        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(148.dp).clip(RoundedCornerShape(42.dp)).background(AccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.manliu_muse),
                            contentDescription = null,
                            modifier = Modifier.size(140.dp),
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    Text("从第一部图集开始", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "挑选喜欢的图片，排好顺序，\n就可以一路往下阅读。",
                        color = SoftText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showCreate = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                    ) { Text("创建图集") }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("我的图集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(albums.size.toString() + " 部作品", color = SoftText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().dragReorder(
                    state = dragState,
                    displayedIds = shownAlbums.map { it.id },
                    enabled = shownAlbums.size > 1,
                    onDrop = { drop ->
                        scope.launch {
                            try {
                                repository.reorderAlbums(drop.orderedIds)
                            } catch (_: Exception) {
                                dragState.cancel()
                                notify("图集顺序保存失败，请重试")
                            }
                        }
                    },
                ),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shownAlbums, key = { it.id }) { album ->
                    val isDragging = dragState.draggingId == album.id
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(
                                if (!isDragging) {
                                    Modifier.animateItemPlacement(
                                        animationSpec = androidx.compose.animation.core.spring(
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        ),
                                    )
                                } else {
                                    Modifier.offset { IntOffset(0, dragState.offsetFor(album.id)) }
                                },
                            ),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier.weight(1f).clickable { onOpen(album.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (album.coverName != null) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(repository.imageFile(album.id, album.coverName))
                                            .size(246, 312) // 针对 82dp x 104dp (3x 密度) 下采样解码，极大节约内存
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(82.dp, 104.dp).clip(RoundedCornerShape(14.dp)),
                                    )
                                } else {
                                    Box(
                                        Modifier.size(82.dp, 104.dp).clip(RoundedCornerShape(14.dp))
                                            .background(AccentSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.manliu_muse),
                                            contentDescription = null,
                                            modifier = Modifier.size(78.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        album.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (album.pageCount == 0) "等待添加图片" else album.pageCount.toString() + " 张图片",
                                        color = SoftText,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (album.pageCount > 0) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            if (album.progressPage > 0) "继续阅读" else "开始阅读",
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .clickable { onRead(album.id) }
                                                .padding(vertical = 4.dp),
                                            color = Accent,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            if (shownAlbums.size > 1) DragHandle()
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建图集") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("图集名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        showCreate = false
                        scope.launch { onOpen(repository.createAlbum(title)) }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
    restoreFile?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreFile = null },
            title = { Text("恢复备份") },
            text = { Text("备份中的图集会作为新图集加入，现有图集和图片不会被覆盖。恢复大量图片时请保持足够可用空间。") },
            confirmButton = {
                TextButton(onClick = {
                    restoreFile = null
                    ArchiveService.restore(context, uri)
                }) { Text("开始恢复") }
            },
            dismissButton = { TextButton(onClick = { restoreFile = null }) { Text("取消") } },
        )
    }
}
