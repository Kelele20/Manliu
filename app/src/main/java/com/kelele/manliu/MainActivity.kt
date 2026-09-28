package com.kelele.manliu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.kelele.manliu.ui.album.AlbumScreen
import com.kelele.manliu.ui.library.LibraryScreen
import com.kelele.manliu.ui.reader.ReaderScreen
import com.kelele.manliu.ui.theme.BackgroundLight
import com.kelele.manliu.ui.theme.ManliuTheme
import kotlinx.coroutines.launch

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
            ManliuTheme {
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
    val importFeedback by ImportFeedback.message.collectAsState()
    LaunchedEffect(importFeedback) {
        importFeedback?.let { message ->
            notifications.showSnackbar(message)
            ImportFeedback.clear(message)
        }
    }
    BackHandler(enabled = destination == "album") { destination = "library" }

    Scaffold(
        containerColor = BackgroundLight,
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
