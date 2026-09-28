package com.kelele.manliu.ui.album

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kelele.manliu.ArchiveService
import com.kelele.manliu.ArchiveStatus
import com.kelele.manliu.ComicPage
import com.kelele.manliu.ComicRepository
import com.kelele.manliu.DragHandle
import com.kelele.manliu.DragReorderState
import com.kelele.manliu.FolderImage
import com.kelele.manliu.ImportService
import com.kelele.manliu.dragReorder
import com.kelele.manliu.ui.components.ArchiveProgressCard
import com.kelele.manliu.ui.components.ImportProgressCard
import com.kelele.manliu.ui.theme.Accent
import com.kelele.manliu.ui.theme.AccentSoft
import com.kelele.manliu.ui.theme.BackgroundLight
import com.kelele.manliu.ui.theme.Hairline
import com.kelele.manliu.ui.theme.SoftText
import com.kelele.manliu.ui.theme.SurfaceLight
import kotlinx.coroutines.launch

private data class FolderSelection(val uri: Uri, val images: List<FolderImage>)

@Composable
fun AlbumScreen(
    repository: ComicRepository,
    albumId: Long,
    onBack: () -> Unit,
    onRead: () -> Unit,
    notify: (String) -> Unit,
) {
    val album by remember(albumId) { repository.album(albumId) }.collectAsState(initial = null)
    val loadedPages by remember(albumId) { repository.pages(albumId) }.collectAsState(initial = null)
    val pages = loadedPages ?: emptyList()
    val latestImport by remember(albumId) { repository.latestImport(albumId) }.collectAsState(initial = null)
    val archive by ArchiveStatus.progress.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragState = remember(listState) { DragReorderState(listState) }
    LaunchedEffect(pages) { dragState.sourceChanged(pages.map { it.id }) }
    val shownPages = dragState.arranged(pages) { it.id }
    var importing by remember { mutableStateOf(false) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderSelection by remember { mutableStateOf<FolderSelection?>(null) }
    var showImportSource by remember { mutableStateOf(false) }
    var deletingAlbum by remember { mutableStateOf(false) }
    var deletingPage by remember { mutableStateOf<ComicPage?>(null) }
    var selectingPages by remember { mutableStateOf(false) }
    var selectedPageIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var deletingBatch by remember { mutableStateOf(false) }
    val pageDragEnabled = shownPages.size > 1 && !importing && !selectingPages && !deletingBatch &&
        latestImport?.let {
            it.status in setOf("PREPARING", "QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
        } != true
    val albumBackupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { ArchiveService.export(context, it, albumId) } }

    val importSelected: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            try {
                ImportService.startSelected(context, albumId, uris)
                val extra = if (uris.distinct().size > 100) "，其余图片请分批导入" else ""
                notify("正在后台准备导入${uris.distinct().take(100).size} 张图片$extra")
            } catch (error: Exception) {
                notify(error.message ?: "无法开始导入")
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100),
    ) { uris -> importSelected(uris) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> importSelected(uris) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ImportService.start(context) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            folderLoading = true
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    val images = repository.listFolderImages(uri)
                    if (images.isEmpty()) notify("这个文件夹里没有图片")
                    else folderSelection = FolderSelection(uri, images)
                } catch (_: Exception) {
                    notify("读取文件夹失败，请换一个文件夹重试")
                } finally {
                    folderLoading = false
                }
            }
        }
    }
    val archivePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    repository.createArchiveImport(albumId, uri)
                    launchImportService()
                    notify("已开始从漫画压缩包导入图片")
                } catch (error: Exception) {
                    notify(error.message ?: "无法解析压缩包")
                } finally {
                    importing = false
                }
            }
        }
    }

    val launchImportService: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ImportService.start(context)
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    if (album == null || loadedPages == null) {
        Box(Modifier.fillMaxSize().background(BackgroundLight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回图集列表")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    album?.title ?: "图集",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("整理你的阅读顺序", color = SoftText, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("按文件名排序：1 → 9") },
                        enabled = !importing && !dragState.waitingForSave && pages.size > 1,
                        onClick = {
                            showMenu = false
                            scope.launch {
                                repository.sortPagesByName(albumId, ascending = true)
                                notify("已按文件名升序排列")
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("按文件名排序：9 → 1") },
                        enabled = !importing && !dragState.waitingForSave && pages.size > 1,
                        onClick = {
                            showMenu = false
                            scope.launch {
                                repository.sortPagesByName(albumId, ascending = false)
                                notify("已按文件名降序排列")
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("备份这个图集") },
                        enabled = pages.isNotEmpty() && archive?.running != true,
                        onClick = {
                            showMenu = false
                            albumBackupPicker.launch("${album?.title ?: "图集"}.manliu")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除图集") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            deletingAlbum = true
                        },
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp)).background(AccentSoft)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                if (pages.isEmpty()) "给故事加上第一张画面" else "下一页，继续看",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (pages.isEmpty()) "从相册或文件夹选择图片，组成连续条漫。"
                else "已收集 " + pages.size + " 张图片，按顺序向下阅读。",
                color = SoftText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pages.isEmpty()) {
                    Button(
                        enabled = !importing && !folderLoading && latestImport?.let {
                            it.status in setOf("PREPARING", "QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
                        } != true,
                        onClick = { showImportSource = true },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (importing) "导入中" else "添加图片")
                    }
                } else {
                    Button(onClick = onRead, shape = RoundedCornerShape(14.dp)) {
                        Text(if ((album?.progressPage ?: 0) > 0) "继续阅读" else "开始阅读")
                    }
                    OutlinedButton(
                        enabled = !importing && !folderLoading && latestImport?.let {
                            it.status in setOf("PREPARING", "QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
                        } != true,
                        onClick = { showImportSource = true },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (importing) "导入中" else "添加图片")
                    }
                }
            }
        }

        latestImport?.let { job ->
            if (job.status != "DONE" || job.failed > 0) {
                ImportProgressCard(job, repository, onRestart = launchImportService, notify = notify)
            }
        }
        archive?.let { ArchiveProgressCard(it, context) }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("阅读顺序", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(pages.size.toString() + " 张 · 可排序", color = SoftText, style = MaterialTheme.typography.bodySmall)
            }
            if (pages.isNotEmpty()) {
                TextButton(
                    enabled = latestImport?.status !in setOf("PREPARING", "RUNNING", "QUEUED") && !deletingBatch,
                    onClick = {
                        selectingPages = !selectingPages
                        selectedPageIds = emptySet()
                    },
                ) { Text(if (selectingPages) "完成" else "批量选择") }
            }
        }

        if (selectingPages) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    selectedPageIds = if (selectedPageIds.size == pages.size) emptySet()
                    else pages.map { it.id }.toSet()
                }) { Text(if (selectedPageIds.size == pages.size) "取消全选" else "全选") }
                Spacer(Modifier.weight(1f))
                Text("已选 ${selectedPageIds.size} 张", color = SoftText, style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = selectedPageIds.isNotEmpty() && !deletingBatch,
                    onClick = { confirmBatchDelete = true },
                ) { Text("删除所选", color = Accent) }
            }
        }

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (importing || folderLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(14.dp))
                        Text(if (folderLoading) "正在读取文件夹…" else "正在导入图片…", color = SoftText)
                    }
                } else {
                    Text("图片会按选择顺序排列在这里", color = SoftText)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.dragReorder(
                    state = dragState,
                    displayedIds = shownPages.map { it.id },
                    enabled = pageDragEnabled,
                    onDrop = { drop ->
                        scope.launch {
                            try {
                                repository.reorderPage(albumId, drop.movedId, drop.orderedIds)
                            } catch (_: Exception) {
                                dragState.cancel()
                                notify("图片顺序保存失败，请重试")
                            }
                        }
                    },
                ),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(shownPages, key = { _, page -> page.id }) { index, page ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (dragState.draggingId == page.id) 1f else 0f)
                            .offset { IntOffset(0, dragState.offsetFor(page.id)) }
                            .then(
                                if (selectingPages) Modifier.clickable {
                                    selectedPageIds = if (page.id in selectedPageIds) selectedPageIds - page.id
                                    else selectedPageIds + page.id
                                } else Modifier,
                            ),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectingPages) {
                                Checkbox(checked = page.id in selectedPageIds, onCheckedChange = null)
                            }
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(repository.imageFile(page))
                                    .size(198, 258) // 针对 66dp x 86dp (3x 密度) 下采样解码
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "第 " + (index + 1) + " 张图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(66.dp, 86.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "第 " + (index + 1).toString().padStart(2, '0') + " 页",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    page.originalName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = SoftText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (!selectingPages) Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { deletingPage = page }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除第 " + (index + 1) + " 张")
                                    }
                                }
                            }
                            if (pageDragEnabled) DragHandle()
                        }
                    }
                }
            }
        }
    }

    if (showImportSource) {
        AlertDialog(
            onDismissRequest = { showImportSource = false },
            title = { Text("添加图片") },
            text = {
                Column {
                    Text("一万张导入请选图片文件夹，可全选和排序；相册、文件入口仍受系统选择数量限制。")
                    Spacer(Modifier.height(8.dp))
                    Text("Android/data 无法直接选择；可先复制到「下载」内新建的文件夹。", color = SoftText)
                    Spacer(Modifier.height(6.dp))
                    Text("也可以继续用系统相册或文件选择器多选。", color = SoftText)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            showImportSource = false
                            folderPicker.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从文件夹选择 · 全选/排序") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showImportSource = false
                            archivePicker.launch(arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从 ZIP / CBZ 压缩包导入") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportSource = false
                    filePicker.launch(arrayOf("image/*"))
                }) { Text("从文件选择") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportSource = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) { Text("从相册选择") }
            },
        )
    }

    folderSelection?.let { choice ->
        FolderImportDialog(
            images = choice.images,
            freeSpace = repository.freeSpace(),
            onDismiss = { folderSelection = null },
            onImport = { selected ->
                importing = true
                scope.launch {
                    try {
                        repository.createFolderImport(albumId, choice.uri, selected)
                        folderSelection = null
                        launchImportService()
                        notify("已开始导入 ${selected.size} 张图片")
                    } catch (error: Exception) {
                        notify(error.message ?: "无法开始导入")
                    } finally {
                        importing = false
                    }
                }
            },
        )
    }

    if (deletingAlbum) {
        AlertDialog(
            onDismissRequest = { deletingAlbum = false },
            title = { Text("删除整个图集？") },
            text = { Text("图集及导入到应用中的图片将被删除。手机相册中的原图不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingAlbum = false
                    scope.launch {
                        repository.deleteAlbum(albumId)
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingAlbum = false }) { Text("取消") } },
        )
    }
    deletingPage?.let { page ->
        AlertDialog(
            onDismissRequest = { deletingPage = null },
            title = { Text("删除这张图片？") },
            text = { Text("仅删除应用内的副本，手机里的原图不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingPage = null
                    scope.launch { repository.deletePage(page) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingPage = null }) { Text("取消") } },
        )
    }
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除所选 ${selectedPageIds.size} 张？") },
            text = { Text("只删除漫流内的图片副本，手机里的原图不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatchDelete = false
                    deletingBatch = true
                    scope.launch {
                        try {
                            repository.deletePages(albumId, selectedPageIds)
                            notify("已删除 ${selectedPageIds.size} 张")
                            selectedPageIds = emptySet()
                            selectingPages = false
                        } catch (_: Exception) {
                            notify("批量删除失败，请重试")
                        } finally {
                            deletingBatch = false
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") } },
        )
    }
}
