package com.scooterre.client.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A scooter's cached documents (registration papers, insurance, ...) - the whole reason this
 * exists is being able to show them "im Kontrollfall ohne Handy", so this works purely from the
 * watch's own local [WearDocumentStore], never needs a live connection to the scooter or the
 * phone. [WatchState.documents] is kept current by StatusListenerService's passive push handling;
 * on top of that, opening this screen also actively re-reads the Data Layer's current DataItem for
 * this scooter directly (a second, independent path to the same state, in case a push was somehow
 * missed) - see WearDocumentStore's applyDocumentsPush doc comment for the shared parsing logic.
 */
@Composable
fun DocumentsScreen(mac: String, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val documentsByMac by WatchState.documents.collectAsState()
    val docs = documentsByMac[normalizeMac(mac)].orEmpty()

    LaunchedEffect(mac) {
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.Builder().scheme("wear").authority("*").path(DOCS_PATH_PREFIX + normalizeMac(mac)).build()
                val items = Tasks.await(Wearable.getDataClient(context).getDataItems(uri))
                try {
                    // DataItemBuffer is Iterable<DataItem>, same as onDataChanged's DataEventBuffer.
                    for (item in items) {
                        applyDocumentsPush(context, normalizeMac(mac), DataMapItem.fromDataItem(item).dataMap)
                    }
                } finally {
                    items.release()
                }
            }
        }
    }

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { ListHeader { Text("Dokumente") } }
                    if (docs.isEmpty()) {
                        item { Text("Keine Dokumente", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        docs.forEach { doc ->
                            item {
                                Button(onClick = { onOpenDocument(doc.id) }) { Text(doc.name) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen display for showing one document to someone: black background, swipe between
 * pages, pinch or double-tap to zoom - same interaction as the phone's DocumentViewerScreen. Every
 * page is already a plain JPEG (a PDF was rasterized on the phone before being sent - see :app's
 * WearDocsBridge), so this never needs a PDF renderer. */
@Composable
fun DocumentViewerScreen(mac: String, docId: String, onBack: () -> Unit) {
    val documentsByMac by WatchState.documents.collectAsState()
    val doc = documentsByMac[normalizeMac(mac)].orEmpty().firstOrNull { it.id == docId }
    if (doc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val store = remember { WearDocumentStore(context) }
    val pagerState = rememberPagerState(pageCount = { doc.pageCount })
    var zoomed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
            DocumentPage(file = store.pageFile(mac, doc, page).absolutePath, onZoomedChange = { zoomed = it })
        }
    }
}

@Composable
private fun DocumentPage(file: String, onZoomedChange: (Boolean) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file) }.getOrNull() }
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        ZoomableImage(loaded, onZoomedChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(bitmap: Bitmap, onZoomedChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, sc: Float): Offset {
        val maxX = size.width * (sc - 1f) / 2f
        val maxY = size.height * (sc - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = clamp(offset + panChange, scale)
    }
    LaunchedEffect(scale) { onZoomedChange(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // Panning only counts while zoomed in, so a one-finger swipe at normal size still turns the page.
            .transformable(transformState, canPan = { scale > 1.01f })
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}
