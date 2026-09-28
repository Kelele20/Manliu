package com.kelele.manliu.ui.album

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kelele.manliu.FolderImage
import com.kelele.manliu.compareImageNames
import com.kelele.manliu.ui.theme.Accent
import com.kelele.manliu.ui.theme.SoftText
import com.kelele.manliu.ui.theme.SurfaceLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class FolderSort(val label: String) {
    NAME_ASC("名称升序（1 → 9）"),
    NAME_DESC("名称降序（9 → 1）"),
    NEWEST("最近修改优先"),
}

@Composable
fun FolderImportDialog(
    images: List<FolderImage>,
    freeSpace: Long,
    onDismiss: () -> Unit,
    onImport: (List<FolderImage>) -> Unit,
) {
    var selection by remember { mutableStateOf(emptySet<Uri>()) }
    var sort by remember { mutableStateOf(FolderSort.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    // 异步排序：避免在主线程阻塞排序大列表（可能高达 10000 项）导致 ANR
    var sorted by remember { mutableStateOf(images) }
    LaunchedEffect(images, sort) {
        sorted = withContext(Dispatchers.Default) {
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
    }
    val batch = sorted.take(10_000)
    val allSelected = batch.all { it.uri in selection }
    val selectedImages = remember(sorted, selection) { sorted.filter { it.uri in selection } }
    val selectedBytes = selectedImages.sumOf { it.sizeBytes.coerceAtLeast(0) }
    val needsSpace = selectedBytes > 0 && selectedBytes + 64L * 1024 * 1024 > freeSpace

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(24.dp)).background(SurfaceLight)
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
                color = SoftText,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "预计需 ${showSize(selectedBytes)} · 可用 ${showSize(freeSpace)}" +
                    if (selectedImages.any { it.sizeBytes < 0 }) "（部分大小未知）" else "",
                modifier = Modifier.padding(horizontal = 18.dp),
                color = if (needsSpace) Accent else SoftText,
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
