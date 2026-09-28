package com.kelele.manliu.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kelele.manliu.ComicAlbum
import com.kelele.manliu.ComicPage
import com.kelele.manliu.ComicRepository
import com.kelele.manliu.readerPageIdAt
import com.kelele.manliu.readerResumeIndex
import com.kelele.manliu.ui.theme.Accent
import com.kelele.manliu.ui.theme.AccentSoft
import com.kelele.manliu.ui.theme.BackgroundLight
import com.kelele.manliu.ui.theme.ReaderAccentSoftDark
import com.kelele.manliu.ui.theme.ReaderBackgroundDark
import com.kelele.manliu.ui.theme.ReaderTextDark
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun ReaderScreen(repository: ComicRepository, albumId: Long, onBack: () -> Unit) {
    // 保持屏幕常亮：阅读漫画时防止自动熄屏
    val currentView = LocalView.current
    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val data by remember(albumId) {
        combine(repository.album(albumId), repository.pages(albumId)) { album, pages -> album to pages }
    }.collectAsStateWithLifecycle(initialValue = null)
    val loaded = data
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(BackgroundLight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
    } else if (loaded.first == null || loaded.second.isEmpty()) {
        Column(
            Modifier.fillMaxSize().background(BackgroundLight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (loaded.first == null) "图集不存在" else "图集里还没有图片")
            TextButton(onClick = onBack) { Text("返回") }
        }
    } else {
        ReaderContent(repository, loaded.first!!, loaded.second, onBack)
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
        readerResumeIndex(pages, album.progressPageId, album.progressPage)
    }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = resumeIndex,
        initialFirstVisibleItemScrollOffset = album.progressOffset.coerceAtLeast(0),
    )
    val currentPages by rememberUpdatedState(pages)
    val scope = rememberCoroutineScope()
    val progressMutex = remember { Mutex() }
    var leaving by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }

    // 手势缩放与平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 使用 derivedStateOf 隔离缩放阈值判断，避免 scale 每帧变化引发全量重组
    val isZoomed by remember { derivedStateOf { scale > 1.05f } }
    val isScrollEnabled by remember { derivedStateOf { scale <= 1.05f } }

    // 阅读器背景模式切换：纯黑模式 vs 浅色模式
    var isDarkBackground by rememberSaveable { mutableStateOf(false) }
    val currentBgColor = if (isDarkBackground) ReaderBackgroundDark else BackgroundLight
    val controlBarBg = if (isDarkBackground) Color(0xEE1E1E20) else BackgroundLight.copy(alpha = 0.96f)
    val controlTextColor = if (isDarkBackground) ReaderTextDark else MaterialTheme.colorScheme.onSurface
    val badgeBg = if (isDarkBackground) ReaderAccentSoftDark else AccentSoft

    val exit: () -> Unit = exit@{
        if (leaving) return@exit
        leaving = true
        val index = state.firstVisibleItemIndex
        val offsetPos = state.firstVisibleItemScrollOffset
        val pageId = readerPageIdAt(currentPages, index)
        scope.launch {
            progressMutex.withLock { repository.saveProgress(album.id, index, offsetPos, pageId) }
            onBack()
        }
    }
    BackHandler(onBack = exit)

    LaunchedEffect(album.id, state) {
        snapshotFlow {
            val index = state.firstVisibleItemIndex
            Triple(index, state.firstVisibleItemScrollOffset, readerPageIdAt(currentPages, index))
        }
            .distinctUntilChanged()
            .debounce(500)
            .collect { (index, offsetPos, pageId) ->
                progressMutex.withLock {
                    if (!leaving) repository.saveProgress(album.id, index, offsetPos, pageId)
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(currentBgColor)
            .pointerInput(Unit) {
                // 单击切换工具栏，双击 1x/2.2x 切换放大
                detectTapGestures(
                    onDoubleTap = {
                        if (isZoomed) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                            offset = Offset.Zero
                        }
                    },
                    onTap = {
                        showControls = !showControls
                    },
                )
            }
            .pointerInput(Unit) {
                // 双指捏合无级缩放与平移查看细节
                detectTransformGestures { _, pan, zoom, _ ->
                    val targetScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = targetScale
                    if (targetScale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
    ) {
        LazyColumn(
            state = state,
            userScrollEnabled = isScrollEnabled, // 放大模式下禁用列表滚动，优先支持自由平移查看画面细节
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            items(pages, key = { it.id }) { page ->
                AsyncImage(
                    model = repository.imageFile(page),
                    contentDescription = page.originalName,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(Modifier.aspectRatio(page.width.toFloat() / page.height.toFloat())),
                )
            }
        }

        if (showControls) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(controlBarBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = exit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回图集",
                        tint = controlTextColor,
                    )
                }
                Text(
                    album.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = controlTextColor,
                )
                // 若处于放大状态，提供一键重置 1x 按钮
                if (isZoomed) {
                    Text(
                        "重置",
                        color = Accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // 纯黑/浅色背景切换按钮
                Text(
                    if (isDarkBackground) "深黑" else "浅米",
                    color = Accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(badgeBg)
                        .clickable { isDarkBackground = !isDarkBackground }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                // 页码跳转按钮（独立 Composable 隔离滚动状态读取，避免整个控制栏重组）
                PageIndicator(
                    state = state,
                    totalPages = pages.size,
                    badgeBg = badgeBg,
                    onJump = { showJump = true },
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
                        scale = 1f
                        offset = Offset.Zero
                        scope.launch { state.scrollToItem(pageNumber!! - 1) }
                    },
                ) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消") } },
        )
    }
}

/**
 * 独立的页码指示器 Composable。
 * 将 [LazyListState.firstVisibleItemIndex] 的读取隔离在此组件内部，
 * 配合 [derivedStateOf] 确保只有页码数字真正变化时才触发重组，
 * 避免滚动时整个控制栏被频繁重组。
 */
@Composable
private fun PageIndicator(
    state: LazyListState,
    totalPages: Int,
    badgeBg: Color,
    onJump: () -> Unit,
) {
    val pageText by remember(totalPages) {
        derivedStateOf { "${state.firstVisibleItemIndex + 1} / $totalPages" }
    }
    Text(
        pageText,
        color = Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(badgeBg).clickable { onJump() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
