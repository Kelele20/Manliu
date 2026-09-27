package com.kelele.manliu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val background = Color(0xFFFAF8F4)
private val surface = Color.White
private val accent = Color(0xFFC96E58)
private val accentSoft = Color(0xFFF7E8DE)
private val ink = Color(0xFF282B34)
private val softText = Color(0xFF6D717C)
private val hairline = Color(0xFFEDE7E0)
private data class FolderSelection(val uri: Uri, val images: List<FolderImage>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(250, 248, 244)
        window.navigationBarColor = android.graphics.Color.rgb(250, 248, 244)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val repository = ComicRepository.get(applicationContext)
        setContentView(repository)
    }

    private fun setContentView(repository: ComicRepository) {
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = accent,
                    onPrimary = Color.White,
                    background = background,
                    surface = surface,
                    onBackground = ink,
                    onSurface = ink,
                    surfaceVariant = accentSoft,
                ),
            ) {
                ComicApp(repository)
            }
        }
    }
}

@Composable
private fun ComicApp(repository: ComicRepository) {
    var destination by rememberSaveable { mutableStateOf("library") }
    var albumId by rememberSaveable { mutableStateOf(0L) }
    var readerOrigin by rememberSaveable { mutableStateOf("album") }
    val notifications = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = destination == "album") { destination = "library" }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(notifications) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                "library" -> LibraryScreen(
                    repository = repository,
                    onOpen = { id ->
                        albumId = id
                        destination = "album"
                    },
                    onRead = { id ->
                        albumId = id
                        readerOrigin = "library"
                        destination = "reader"
                    },
                    notify = { message -> scope.launch { notifications.showSnackbar(message) } },
                )
                "album" -> key(albumId) {
                    AlbumScreen(
                        repository = repository,
                        albumId = albumId,
                        onBack = { destination = "library" },
                        onRead = {
                            readerOrigin = "album"
                            destination = "reader"
                        },
                        notify = { message -> scope.launch { notifications.showSnackbar(message) } },
                    )
                }
                "reader" -> key(albumId) {
                    ReaderScreen(repository, albumId) { destination = readerOrigin }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    repository: ComicRepository,
    onOpen: (Long) -> Unit,
    onRead: (Long) -> Unit,
    notify: (String) -> Unit,
) {
    val albums by repository.overviews.collectAsState(initial = emptyList())
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

    Column(Modifier.fillMaxSize().background(background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 24.dp, bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accentSoft),
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
                Text("让画面一页页流动", color = softText, style = MaterialTheme.typography.bodySmall)
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
                        Modifier.size(148.dp).clip(RoundedCornerShape(42.dp)).background(accentSoft),
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
                        color = softText,
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
                Text(albums.size.toString() + " 部作品", color = softText, style = MaterialTheme.typography.bodySmall)
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
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (dragState.draggingId == album.id) 1f else 0f)
                            .offset { IntOffset(0, dragState.offsetFor(album.id)) },
                        colors = CardDefaults.cardColors(containerColor = surface),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, hairline),
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
                                    model = repository.imageFile(album.id, album.coverName),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(82.dp, 104.dp).clip(RoundedCornerShape(14.dp)),
                                )
                            } else {
                                Box(
                                    Modifier.size(82.dp, 104.dp).clip(RoundedCornerShape(14.dp))
                                        .background(accentSoft),
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
                                    color = softText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (album.pageCount > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (album.progressPage > 0) "继续阅读" else "开始阅读",
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable { onRead(album.id) }
                                            .padding(vertical = 4.dp),
                                        color = accent,
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

@Composable
private fun AlbumScreen(
    repository: ComicRepository,
    albumId: Long,
    onBack: () -> Unit,
    onRead: () -> Unit,
    notify: (String) -> Unit,
) {
    val album by remember(albumId) { repository.album(albumId) }.collectAsState(initial = null)
    val loadedPages by remember(albumId) { repository.pages(albumId) }
        .collectAsState(initial = null)
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
            it.status in setOf("QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
        } != true
    val albumBackupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { ArchiveService.export(context, it, albumId) } }

    val importSelected: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty() && !importing) {
            val selected = uris.take(100)
            importing = true
            scope.launch {
                try {
                    val result = repository.importImages(albumId, selected)
                    val failed = if (result.failed > 0) "，" + result.failed + " 张导入失败" else ""
                    val extra = if (uris.size > selected.size) "，其余图片请分批导入" else ""
                    notify("已导入 " + result.imported + " 张" + failed + extra)
                } catch (_: Exception) {
                    notify("导入失败，请重试")
                } finally {
                    importing = false
                }
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
        Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(background)) {
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
                Text("整理你的阅读顺序", color = softText, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("按文件名排序：1 → 9") },
                        enabled = !importing && pages.size > 1,
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
                        enabled = !importing && pages.size > 1,
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
                .clip(RoundedCornerShape(24.dp)).background(accentSoft)
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
                color = softText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pages.isEmpty()) {
                    Button(
                        enabled = !importing && !folderLoading && latestImport?.let {
                            it.status in setOf("QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
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
                            it.status in setOf("QUEUED", "RUNNING", "PAUSED") && it.processed < it.total
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
                Text(pages.size.toString() + " 张 · 可排序", color = softText, style = MaterialTheme.typography.bodySmall)
            }
            if (pages.isNotEmpty()) {
                TextButton(
                    enabled = latestImport?.status !in setOf("RUNNING", "QUEUED") && !deletingBatch,
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
                Text("已选 ${selectedPageIds.size} 张", color = softText, style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = selectedPageIds.isNotEmpty() && !deletingBatch,
                    onClick = { confirmBatchDelete = true },
                ) { Text("删除所选", color = accent) }
            }
        }

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (importing || folderLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent)
                        Spacer(Modifier.height(14.dp))
                        Text(if (folderLoading) "正在读取文件夹…" else "正在导入图片…", color = softText)
                    }
                } else {
                    Text("图片会按选择顺序排列在这里", color = softText)
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
                        colors = CardDefaults.cardColors(containerColor = surface),
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
                        border = BorderStroke(1.dp, hairline),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectingPages) {
                                Checkbox(checked = page.id in selectedPageIds, onCheckedChange = null)
                            }
                            AsyncImage(
                                model = repository.imageFile(page),
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
                                    color = softText,
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
                    Text("Android/data 无法直接选择；可先复制到「下载」内新建的文件夹。", color = softText)
                    Spacer(Modifier.height(6.dp))
                    Text("也可以继续用系统相册或文件选择器多选。", color = softText)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            showImportSource = false
                            folderPicker.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从文件夹选择 · 全选/排序") }
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

private enum class FolderSort(val label: String) {
    NAME_ASC("名称升序（1 → 9）"),
    NAME_DESC("名称降序（9 → 1）"),
    NEWEST("最近修改优先"),
}

@Composable
private fun FolderImportDialog(
    images: List<FolderImage>,
    freeSpace: Long,
    onDismiss: () -> Unit,
    onImport: (List<FolderImage>) -> Unit,
) {
    var selection by remember { mutableStateOf(emptySet<Uri>()) }
    var sort by remember { mutableStateOf(FolderSort.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val sorted = remember(images, sort) {
        images.sortedWith { first, second ->
            val byName = compareImageNames(first.name, second.name)
            when (sort) {
                FolderSort.NAME_ASC -> byName
                FolderSort.NAME_DESC -> -byName
                FolderSort.NEWEST -> {
                    val byTime = second.modifiedAt.compareTo(first.modifiedAt)
                    if (byTime != 0) byTime else byName
                }
            }
        }
    }
    val batch = sorted.take(10_000)
    val allSelected = batch.all { it.uri in selection }
    val selectedImages = remember(sorted, selection) { sorted.filter { it.uri in selection } }
    val selectedBytes = selectedImages.sumOf { it.sizeBytes.coerceAtLeast(0) }
    val needsSpace = selectedBytes > 0 && selectedBytes + 64L * 1024 * 1024 > freeSpace

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(24.dp)).background(surface)
                .padding(vertical = 18.dp),
        ) {
            Text(
                "选择要添加的图片",
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "共 ${images.size} 张 · 已选 ${selection.size} 张 · 每次最多 10000 张",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                color = softText,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "预计需 ${showSize(selectedBytes)} · 可用 ${showSize(freeSpace)}" +
                    if (selectedImages.any { it.sizeBytes < 0 }) "（部分大小未知）" else "",
                modifier = Modifier.padding(horizontal = 18.dp),
                color = if (needsSpace) accent else softText,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    selection = if (allSelected) emptySet() else batch.map { it.uri }.toSet()
                }) {
                    Text(if (allSelected) "取消全选" else if (images.size > 10_000) "选前 10000 张" else "全选")
                }
                Box {
                    TextButton(onClick = { showSortMenu = true }) { Text(sort.label) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        FolderSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { sort = option; showSortMenu = false },
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                items(sorted, key = { it.uri.toString() }) { image ->
                    val checked = image.uri in selection
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selection = when {
                                checked -> selection - image.uri
                                selection.size < 10_000 -> selection + image.uri
                                else -> selection
                            }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        AsyncImage(
                            model = image.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(image.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = selection.isNotEmpty() && !needsSpace,
                    onClick = { onImport(selectedImages) },
                ) { Text("导入 (${selection.size})") }
            }
        }
    }
}

private fun showSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.0f MB".format(bytes / (1024.0 * 1024))
}

@Composable
private fun ArchiveProgressCard(progress: ArchiveProgress, context: android.content.Context) {
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp)).background(accentSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(progress.label, fontWeight = FontWeight.SemiBold)
        if (progress.running) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.processed.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "${progress.processed} / ${progress.total} 张图片",
            color = softText,
            style = MaterialTheme.typography.bodySmall,
        )
        progress.error?.let { Text(it, color = accent, style = MaterialTheme.typography.bodySmall) }
        TextButton(
            onClick = {
                if (progress.running) ArchiveService.cancel(context) else ArchiveStatus.update(null)
            },
        ) { Text(if (progress.running) "取消任务" else "关闭") }
    }
}

@Composable
private fun ImportProgressCard(
    job: ImportJob,
    repository: ComicRepository,
    onRestart: () -> Unit,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showFailures by remember(job.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp)
            .clip(RoundedCornerShape(18.dp)).background(surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            when (job.status) {
                "RUNNING", "QUEUED" -> "正在导入图片"
                "PAUSED" -> "导入已暂停"
                "CANCELLED" -> "导入已取消"
                else -> "导入完成"
            },
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { job.processed.toFloat() / job.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "已处理 ${job.processed} / ${job.total} · 成功 ${job.imported} · 失败 ${job.failed}",
            color = softText,
            style = MaterialTheme.typography.bodySmall,
        )
        job.message?.let {
            Text(it, color = accent, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (job.status == "RUNNING" || job.status == "QUEUED") {
                TextButton(onClick = {
                    scope.launch {
                        repository.pauseImport(job.id)
                        notify("导入已暂停，已完成的图片会保留")
                    }
                }) { Text("暂停导入") }
            }
            if (job.status == "PAUSED" || job.status == "RUNNING") {
                TextButton(onClick = {
                    scope.launch {
                        repository.resumeImport(job.id)
                        onRestart()
                    }
                }) { Text(if (job.status == "PAUSED") "继续导入" else "尝试恢复") }
            }
            if (job.status in setOf("RUNNING", "QUEUED", "PAUSED")) {
                TextButton(onClick = {
                    scope.launch {
                        repository.cancelImport(job.id)
                        notify("已取消；已导入的图片保留，可以开始新任务")
                    }
                }) { Text("取消任务") }
            }
            if (job.failed > 0) {
                TextButton(onClick = { showFailures = true }) { Text("失败清单") }
                if (job.status == "DONE") {
                    TextButton(onClick = {
                        scope.launch {
                            repository.resumeImport(job.id, retryFailures = true)
                            onRestart()
                        }
                    }) { Text("重试失败项") }
                }
            }
        }
    }

    if (showFailures) {
        val failed by remember(job.id) { repository.failedImports(job.id) }.collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { showFailures = false },
            title = { Text("失败图片（${job.failed} 张）") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(failed, key = { it.id }) { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.error ?: "读取失败", color = softText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFailures = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun ReaderScreen(repository: ComicRepository, albumId: Long, onBack: () -> Unit) {
    var album by remember(albumId) { mutableStateOf<ComicAlbum?>(null) }
    var pages by remember(albumId) { mutableStateOf(emptyList<ComicPage>()) }
    LaunchedEffect(albumId) {
        album = repository.albumSnapshot(albumId)
        pages = repository.pageSnapshot(albumId)
    }
    if (album != null && pages.isNotEmpty()) {
        ReaderContent(repository, album!!, pages, onBack)
    } else {
        Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ReaderContent(
    repository: ComicRepository,
    album: ComicAlbum,
    pages: List<ComicPage>,
    onBack: () -> Unit,
) {
    val resumeIndex = remember(album.id, pages) {
        pages.indexOfFirst { it.id == album.progressPageId }
            .takeIf { it >= 0 } ?: album.progressPage.coerceIn(0, pages.lastIndex)
    }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = resumeIndex,
        initialFirstVisibleItemScrollOffset = album.progressOffset.coerceAtLeast(0),
    )
    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val exit: () -> Unit = {
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        scope.launch {
            repository.saveProgress(album.id, index, offset, pages.getOrNull(index)?.id ?: 0)
            onBack()
        }
    }
    BackHandler(onBack = exit)

    LaunchedEffect(album.id, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(500)
            .collect { (index, offset) -> repository.saveProgress(album.id, index, offset, pages.getOrNull(index)?.id ?: 0) }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pages, key = { it.id }) { page ->
                AsyncImage(
                    model = repository.imageFile(page),
                    contentDescription = page.originalName,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(Modifier.aspectRatio(page.width.toFloat() / page.height.toFloat()))
                        .clickable(interactionSource = interaction, indication = null) {
                            showControls = !showControls
                        },
                )
            }
        }
        if (showControls) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(background.copy(alpha = 0.96f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = exit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回图集")
                }
                Text(
                    album.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    (state.firstVisibleItemIndex + 1).toString() + " / " + pages.size,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(accentSoft).clickable { showJump = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (showJump) {
        var target by remember { mutableStateOf((state.firstVisibleItemIndex + 1).toString()) }
        val pageNumber = target.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到第几页？") },
            text = {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter(Char::isDigit).take(6) },
                    label = { Text("1 ～ ${pages.size}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pageNumber != null && pageNumber in 1..pages.size,
                    onClick = {
                        showJump = false
                        scope.launch { state.scrollToItem(pageNumber!! - 1) }
                    },
                ) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消") } },
        )
    }
}
