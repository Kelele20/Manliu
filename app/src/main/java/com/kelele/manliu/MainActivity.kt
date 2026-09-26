package com.kelele.manliu

import android.net.Uri
import android.os.Bundle
import androidx.core.view.WindowCompat
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(250, 248, 244)
        window.navigationBarColor = android.graphics.Color.rgb(250, 248, 244)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val repository = ComicRepository(applicationContext)
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
    val notifications = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = destination == "album") { destination = "library" }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(notifications) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                "library" -> LibraryScreen(repository, onOpen = { id ->
                    albumId = id
                    destination = "album"
                })
                "album" -> key(albumId) {
                    AlbumScreen(
                        repository = repository,
                        albumId = albumId,
                        onBack = { destination = "library" },
                        onRead = { destination = "reader" },
                        notify = { message -> scope.launch { notifications.showSnackbar(message) } },
                    )
                }
                "reader" -> key(albumId) {
                    ReaderScreen(repository, albumId) { destination = "album" }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(repository: ComicRepository, onOpen: (Long) -> Unit) {
    val albums by repository.albums.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    val pages by remember(album.id) { repository.pages(album.id) }
                        .collectAsState(initial = emptyList())
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(album.id) },
                        colors = CardDefaults.cardColors(containerColor = surface),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, hairline),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (pages.isNotEmpty()) {
                                AsyncImage(
                                    model = repository.imageFile(pages.first()),
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
                                    if (pages.isEmpty()) "等待添加图片" else pages.size.toString() + " 张图片",
                                    color = softText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (pages.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (album.progressPage > 0) "继续阅读" else "开始阅读",
                                        color = accent,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
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
    val pages by remember(albumId) { repository.pages(albumId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var showImportSource by remember { mutableStateOf(false) }
    var deletingAlbum by remember { mutableStateOf(false) }
    var deletingPage by remember { mutableStateOf<ComicPage?>(null) }

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

    var showMenu by remember { mutableStateOf(false) }

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
                        enabled = !importing,
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
                        enabled = !importing,
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

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("阅读顺序", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(pages.size.toString() + " 张 · 可逐张调整", color = softText, style = MaterialTheme.typography.bodySmall)
        }

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (importing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent)
                        Spacer(Modifier.height(14.dp))
                        Text("正在导入图片…", color = softText)
                    }
                } else {
                    Text("图片会按选择顺序排列在这里", color = softText)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surface),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, hairline),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        enabled = index > 0,
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        onClick = { scope.launch { repository.movePage(albumId, page.id, -1) } },
                                    ) { Text("上移") }
                                    TextButton(
                                        enabled = index < pages.lastIndex,
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        onClick = { scope.launch { repository.movePage(albumId, page.id, 1) } },
                                    ) { Text("下移") }
                                    IconButton(onClick = { deletingPage = page }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除第 " + (index + 1) + " 张")
                                    }
                                }
                            }
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
            text = { Text("相册里的照片选「相册」；下载、聊天软件或文件夹中的图片选「文件」。可一次选择多张。") },
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
}

@Composable
private fun ReaderScreen(repository: ComicRepository, albumId: Long, onBack: () -> Unit) {
    val album by remember(albumId) { repository.album(albumId) }.collectAsState(initial = null)
    val pages by remember(albumId) { repository.pages(albumId) }.collectAsState(initial = emptyList())
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
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = album.progressPage.coerceIn(0, pages.lastIndex),
        initialFirstVisibleItemScrollOffset = album.progressOffset.coerceAtLeast(0),
    )
    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    val interaction = remember { MutableInteractionSource() }
    val exit: () -> Unit = {
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        scope.launch {
            repository.saveProgress(album.id, index, offset)
            onBack()
        }
    }
    BackHandler(onBack = exit)

    LaunchedEffect(album.id, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(500)
            .collect { (index, offset) -> repository.saveProgress(album.id, index, offset) }
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
                        .background(accentSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
