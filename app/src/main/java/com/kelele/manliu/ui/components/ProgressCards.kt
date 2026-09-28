package com.kelele.manliu.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kelele.manliu.ArchiveProgress
import com.kelele.manliu.ArchiveService
import com.kelele.manliu.ArchiveStatus
import com.kelele.manliu.ComicRepository
import com.kelele.manliu.ImportJob
import com.kelele.manliu.ui.theme.Accent
import com.kelele.manliu.ui.theme.AccentSoft
import com.kelele.manliu.ui.theme.SoftText
import com.kelele.manliu.ui.theme.SurfaceLight
import kotlinx.coroutines.launch

@Composable
fun ArchiveProgressCard(progress: ArchiveProgress, context: Context) {
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp)).background(AccentSoft)
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
            color = SoftText,
            style = MaterialTheme.typography.bodySmall,
        )
        progress.error?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodySmall) }
        TextButton(
            onClick = {
                if (progress.running) ArchiveService.cancel(context) else ArchiveStatus.update(null)
            },
        ) { Text(if (progress.running) "取消任务" else "关闭") }
    }
}

@Composable
fun ImportProgressCard(
    job: ImportJob,
    repository: ComicRepository,
    onRestart: () -> Unit,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showFailures by remember(job.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp)
            .clip(RoundedCornerShape(18.dp)).background(SurfaceLight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            when (job.status) {
                "PREPARING" -> "正在准备图片"
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
            color = SoftText,
            style = MaterialTheme.typography.bodySmall,
        )
        job.message?.let {
            Text(it, color = Accent, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (job.status in setOf("PREPARING", "RUNNING", "QUEUED")) {
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
            if (job.status in setOf("PREPARING", "RUNNING", "QUEUED", "PAUSED")) {
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
        val failed by remember(job.id) { repository.failedImports(job.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
        AlertDialog(
            onDismissRequest = { showFailures = false },
            title = { Text("失败图片（${job.failed} 张）") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(failed, key = { it.id }) { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.error ?: "读取失败", color = SoftText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFailures = false }) { Text("关闭") } },
        )
    }
}
