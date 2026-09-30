package com.kelele.manliu

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kelele.manliu.ui.album.AlbumScreen
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ImportInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var repository: ComicRepository
    private var albumId: Long = 0
    private var provider: BlockingImportProvider? = null
    private var serviceController: ServiceController<ImportService>? = null

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        ComicRepository::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        context.deleteDatabase("comics.db")
        context.filesDir.deleteRecursively()
        context.filesDir.mkdirs()
        repository = ComicRepository.get(context)
        albumId = repository.createAlbum("验证图集")
    }
    @After fun teardown() {
        provider?.release?.countDown()
        compose.activityRule.scenario.close()
        serviceController?.let { controller ->
            val serviceScope = ImportService::class.java.getDeclaredField("scope").apply { isAccessible = true }
                .get(controller.get()) as CoroutineScope
            controller.destroy()
            runBlocking { serviceScope.coroutineContext[Job]?.children?.forEach { it.join() } }
        }
        repository.database.close()
        ComicRepository::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        context.filesDir.deleteRecursively()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
    @Test fun leavingAlbumDuringArchivePreparationMustKeepAResumableTask() {
        val file = File(context.cacheDir, "slow.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry("1.png"))
            zip.write(largeTestPng())
            zip.closeEntry()
        }
        val archiveUri = Uri.parse("content://verification/archive")
        provider = BlockingImportProvider(file).also { it.register(context, archiveUri) }
        var showAlbum by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showAlbum) AlbumScreen(repository, albumId, { showAlbum = false }, {}, {})
                else Text("已离开图集")
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("添加图片")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("添加图片").performClick()
        compose.onNodeWithText("从 ZIP / CBZ 压缩包导入").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull("真实页面应启动系统文件选择器", request)
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK,
                Intent().setData(archiveUri))
        }
        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertNotNull("文件选择结果应交给前台服务", serviceIntent)
        serviceController = Robolectric.buildService(ImportService::class.java).create()
        serviceController!!.get().onStartCommand(serviceIntent, 0, 1)
        val staging = File(context.filesDir, "import-staging")
        compose.waitUntil(10_000) { staging.walkTopDown().any { it.isFile && it.length() >= 64 * 1024 } }
        assertTrue("读取应暂停，确保离开时仍在解压", provider!!.blocked.await(10, TimeUnit.SECONDS))
        val stagedBeforeLeaving = staging.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        compose.onNodeWithText("漫画包尚未解压完成").assertExists()
        compose.onNodeWithContentDescription("返回图集列表").performClick()
        compose.onNodeWithText("已离开图集").assertExists()
        val job = runBlocking { repository.dao.nextPreparingImport() }
        assertNotNull("离开页面后应保留可恢复导入任务", job)
        provider!!.release.countDown()
        compose.waitUntil(15_000) {
            runBlocking { repository.dao.findImportJob(job!!.id)?.status == "DONE" } &&
                staging.walkTopDown().none { it.isFile }
        }
        assertEquals(1, runBlocking { repository.pageSnapshot(albumId).size })
        assertEquals(0, runBlocking { repository.dao.findImportJob(job!!.id)!!.failed })
        println("EVIDENCE compose_lifecycle stagedBeforeLeaving=$stagedBeforeLeaving jobAfterLeaving=$job remainingFiles=${staging.walkTopDown().count { it.isFile }}")
    }
    @Test fun autoScrollMustKeepDraggedCardUnderStationaryFinger() {
        lateinit var drag: DragReorderState
        lateinit var scope: CoroutineScope
        val ids = (1L..20L).toList()
        compose.setContent {
            MaterialTheme {
                val list = rememberLazyListState()
                scope = rememberCoroutineScope()
                drag = remember(list) { DragReorderState(list) }
                val shown = drag.arranged(ids) { it }
                LazyColumn(
                    state = list,
                    modifier = Modifier.width(300.dp).height(400.dp).testTag("drag-list"),
                ) {
                    items(shown, key = { it }) { id ->
                        Box(Modifier.fillMaxWidth().height(100.dp)
                            .offset { IntOffset(0, drag.offsetFor(id)) }.background(Color.Gray)) {
                            Text("Page $id")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        var before = 0
        var draggedId = 0L
        compose.runOnIdle {
            val layout = drag.listState.layoutInfo
            val last = layout.visibleItemsInfo.last { it.offset < layout.viewportEndOffset }
            draggedId = last.key as Long
            before = last.offset
            drag.start(draggedId, ids)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { drag.autoScroll(80f) }
        }
        compose.waitUntil(10_000) {
            drag.listState.firstVisibleItemIndex > 0 || drag.listState.firstVisibleItemScrollOffset >= 20
        }
        var after = 0
        var scrollIndex = 0
        var scrollOffset = 0
        compose.runOnIdle {
            val item = drag.listState.layoutInfo.visibleItemsInfo.first { it.key == draggedId }
            after = item.offset + drag.offsetFor(draggedId)
            scrollIndex = drag.listState.firstVisibleItemIndex
            scrollOffset = drag.listState.firstVisibleItemScrollOffset
            drag.cancel()
        }
        compose.mainClock.autoAdvance = true
        println("EVIDENCE compose_auto_scroll id=$draggedId before=$before after=$after scrollIndex=$scrollIndex scrollOffset=$scrollOffset")
        assertTrue("检查必须发生真实列表滚动", scrollIndex > 0 || scrollOffset > 0)
        assertEquals("手指不移动时卡片应保持视口位置", before, after)
    }
}
