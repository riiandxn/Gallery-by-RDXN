@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.rdxn.gallery

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun ZoomableImage(uri: Uri, active: Boolean, onZoomed: (Boolean) -> Unit, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale, active) { if (active) onZoomed(scale > 1.01f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1 || scale > 1f) {
                            val ns = (scale * ev.calculateZoom()).coerceIn(1f, 5f)
                            val pan = ev.calculatePan()
                            val maxX = box.width * (ns - 1f) / 2f
                            val maxY = box.height * (ns - 1f) / 2f
                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            scale = ns
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = remember(uri) { ImageRequest.Builder(ctx).data(uri).size(2048).crossfade(true).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        )
    }
}

/** Video: pemutar hanya dibuat untuk halaman yang sedang aktif; halaman lain tampil sebagai gambar diam. */
@Composable
private fun VideoPage(uri: Uri, active: Boolean) {
    val ctx = LocalContext.current
    if (active) {
        val exo = remember(uri) {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(exo) { onDispose { exo.release() } }
        AndroidView(
            factory = { c -> PlayerView(c).apply { this.player = exo; useController = true } },
            modifier = Modifier.fillMaxSize().padding(vertical = 96.dp),
        )
    } else {
        AsyncImage(
            model = remember(uri) { ImageRequest.Builder(ctx).data(uri).size(1024).build() },
            contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ViewerScreen(vm: GalleryViewModel, r: Route.Viewer) {
    val ctx = LocalContext.current
    val people by vm.people.collectAsStateWithLifecycle()
    // Daftar dikunci saat viewer dibuka supaya posisi tidak lompat kalau ada foto baru masuk.
    val photos = remember(r) { vm.photosFor(r.source) }
    if (photos.isEmpty()) { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val start = remember(r) { photos.indexOfFirst { it.id == r.startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var pickAlbum by remember { mutableStateOf(false) }
    var currentId by remember { mutableLongStateOf(photos[start].id) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { i -> photos.getOrNull(i)?.let { currentId = it.id } }
    }
    val current = photos.firstOrNull { it.id == currentId } ?: photos[start]
    val inPhoto = remember(people, currentId) { people.filter { currentId in it.mediaIds } }
    val hiddenMode = r.source == Source.Hidden

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { photos[it].id }, modifier = Modifier.fillMaxSize()) { page ->
            val p = photos[page]
            if (p.isVideo) VideoPage(p.uri, active = page == pager.currentPage)
            else ZoomableImage(
                uri = p.uri, active = page == pager.currentPage,
                onZoomed = { zoomed = it }, onTap = { chrome = !chrome },
            )
        }
        AnimatedVisibility(visible = chrome, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
                Text(
                    SimpleDateFormat("d MMM yyyy · HH:mm", Locale("id", "ID")).format(Date(current.takenMs)),
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(visible = chrome, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Color(0x99000000)).navigationBarsPadding().padding(8.dp)) {
                if (inPhoto.isNotEmpty() && !current.isVideo) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(start = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (p in inPhoto) {
                            AssistChip(
                                onClick = { vm.openPerson(p.pid) },
                                label = { Text(p.name.ifBlank { "Tanpa nama" }) },
                                leadingIcon = {
                                    AsyncImage(
                                        model = Faces.thumbFile(ctx, p.coverKey), contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x33FFFFFF), labelColor = Color.White),
                                border = null,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionBtn(Icons.Default.Share, "Bagikan", Color.White) { shareUris(ctx, listOf(current.uri)) }
                    ActionBtn(IconCopy, "Duplikat", Color.White) { vm.duplicate(listOf(current)) }
                    ActionBtn(Icons.Default.Add, "Album", Color.White) { pickAlbum = true }
                    ActionBtn(
                        if (hiddenMode) IconEye else IconEyeOff, if (hiddenMode) "Tampilkan" else "Sembunyikan", Color.White,
                    ) { if (hiddenMode) vm.unhide(listOf(current)) else vm.hide(listOf(current)) }
                    ActionBtn(Icons.Default.Delete, "Hapus", Danger) { vm.trash(listOf(current)) }
                }
            }
        }
    }
    if (pickAlbum) AddToAlbumDialog(vm, setOf(current.id)) { pickAlbum = false }
}
