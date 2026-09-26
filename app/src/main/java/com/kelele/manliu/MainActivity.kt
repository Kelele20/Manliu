package com.kelele.manliu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val background = Color(0xFF111319)
private val surface = Color(0xFF20232C)
private val accent = Color(0xFFFFBE72)
private val softText = Color(0xFFB5B9C4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ComicRepository(applicationContext)
        setContentView(repository)
    }

    private fun setContentView(repository: ComicRepository) {
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = accent,
                    onPrimary = background,
                    background = background,
                    surface = surface,
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
        containerColor = if (destination == "reader") Color.Black else background,
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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("漫流", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("把图片读成一段故事", color = softText)
            }
            Button(onClick = { showCreate = true }) { Text("新建图集") }
        }
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有图集", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("新建图集，再导入手机里的图片", color = softText)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { showCreate = true }) { Text("开始创建") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(album.id) },
                        colors = CardDefaults.cardColors(containerColor = surface),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("▤", color = accent, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(album.title, style = MaterialTheme.typography.titleMedium)
                                Text("点击管理或继续阅读", color = softText)
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
    var deletingAlbum by remember { mutableStateOf(false) }
    var deletingPage by remember { mutableStateOf<ComicPage?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                try {
                    val result = repository.importImages(albumId, uris)
                    val suffix = if (result.failed > 0) "，${result.failed} 张导入失败" else ""
                    notify("已导入 ${result.imported} 张$suffix")
                } finally {
                    importing = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                album?.title ?: "图集",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = { deletingAlbum = true }) { Text("删除图集") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                enabled = !importing,
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text(if (importing) "正在导入…" else "添加图片") }
            Button(
                enabled = pages.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = Color.White),
                onClick = onRead,
            ) { Text("开始阅读") }
        }
        Text("共 ${pages.size} 张 · 使用上移、下移调整阅读顺序", Modifier.padding(20.dp, 8.dp), color = softText)

        if (pages.isEmpty() && !importing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("添加图片，组成你的第一段条漫", color = softText)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pages, key = { it.id }) { page ->
                    val index = pages.indexOfFirst { it.id == page.id }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = repository.imageFile(page),
                                contentDescription = page.originalName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(68.dp, 88.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("第 ${index + 1} 张", fontWeight = FontWeight.SemiBold)
                                Text(page.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = softText)
                                Row {
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = { scope.launch { repository.movePage(albumId, page.id, -1) } },
                                    ) { Text("上移") }
                                    TextButton(
                                        enabled = index < pages.lastIndex,
                                        onClick = { scope.launch { repository.movePage(albumId, page.id, 1) } },
                                    ) { Text("下移") }
                                    TextButton(onClick = { deletingPage = page }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
        }
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
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                    .background(Color(0xD9111319)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = exit) { Text("返回") }
                Text(
                    album.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${state.firstVisibleItemIndex + 1}/${pages.size}", color = softText)
            }
        }
    }
}
