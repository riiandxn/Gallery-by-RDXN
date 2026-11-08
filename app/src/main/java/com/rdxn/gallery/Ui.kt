@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.rdxn.gallery

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF4C8DFF)

// ---------------------------------------------------------------- tema & ikon
@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF0B0D12),
            background = Color.Black, surface = Color(0xFF0F1012), surfaceVariant = Color(0xFF1C1D21),
            onSurface = Color(0xFFE8EAED), onSurfaceVariant = Color(0xFF9AA0A6), onBackground = Color(0xFFE8EAED),
            primaryContainer = Color(0xFF1F2A40), onPrimaryContainer = Color(0xFF9CC0FF),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2F6FE0), background = Color.White, surface = Color.White,
            surfaceVariant = Color(0xFFF1F3F4), primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF0B2A66),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

private val IconPhotos: ImageVector by lazy {
    vec("photos", "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z")
}
private val IconFolder: ImageVector by lazy {
    vec("folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
}

// ---------------------------------------------------------------- helper
private fun dayKey(ms: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(ms: Long): String {
    val now = System.currentTimeMillis()
    val k = dayKey(ms)
    return when {
        k == dayKey(now) -> "Hari ini"
        k == dayKey(now - 86_400_000L) -> "Kemarin"
        else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date(ms))
    }
}

private class DayGroup(val key: Int, val label: String, val photos: List<Photo>)

private fun groupByDay(list: List<Photo>): List<DayGroup> {
    val m = LinkedHashMap<Int, MutableList<Photo>>()
    for (p in list) m.getOrPut(dayKey(p.takenMs)) { ArrayList() }.add(p)
    return m.map { (k, v) -> DayGroup(k, dayLabel(v.first().takenMs), v) }
}

fun shareUris(ctx: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        val clip = ClipData.newUri(ctx.contentResolver, "foto", uris[0])
        for (u in uris.drop(1)) clip.addItem(ClipData.Item(u))
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(intent, "Bagikan foto"))
    } catch (e: ActivityNotFoundException) {
        // tidak ada aplikasi tujuan
    }
}

private fun onPhotoClick(vm: GalleryViewModel, source: Source, p: Photo) {
    if (vm.selected.isNotEmpty()) vm.toggle(p.id) else vm.openViewer(source, p.id)
}

// ---------------------------------------------------------------- komponen umum
@Composable
private fun Thumb(p: Photo, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        AsyncImage(
            model = remember(p.id) { ImageRequest.Builder(ctx).data(p.uri).size(320).crossfade(true).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) Box(Modifier.fillMaxSize().background(Color(0x553D8BFF)))
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Accent else Color(0x66000000))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<Photo>,
    selected: Set<Long>,
    grouped: Boolean,
    onClick: (Photo) -> Unit,
    onLong: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val groups = remember(photos, grouped) { if (grouped) groupByDay(photos) else emptyList() }
    val selecting = selected.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        if (grouped) {
            for (g in groups) {
                item(key = "day${g.key}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        g.label, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                items(g.photos, key = { it.id }) { p ->
                    Thumb(p, p.id in selected, selecting, { onClick(p) }, { onLong(p) })
                }
            }
        } else {
            items(photos, key = { it.id }) { p ->
                Thumb(p, p.id in selected, selecting, { onClick(p) }, { onLong(p) })
            }
        }
    }
}

@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, onShare: () -> Unit) {
    TopAppBar(
        title = { Text("$count dipilih") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Batal") } },
        actions = { IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Bagikan") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun Banner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nama orang ini") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nama") }) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()); onDismiss() }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- layar utama (3 tab)
@Composable
fun HomeScreen(vm: GalleryViewModel, limited: Boolean, onOpenSettings: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val titles = listOf("Foto", "Album", "Orang")

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(vm.selected.size, onClose = { vm.clearSelection() }, onShare = { shareUris(ctx, vm.selectedUris()) })
            } else {
                TopAppBar(
                    title = { Text(titles[vm.tab], fontWeight = FontWeight.Bold) },
                    actions = {
                        if (vm.tab == 2) IconButton(onClick = { vm.startIndexing() }) { Icon(Icons.Default.Refresh, "Pindai ulang") }
                    },
                )
            }
        },
        bottomBar = {
            if (!selecting) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(selected = vm.tab == 0, onClick = { vm.tab = 0 }, icon = { Icon(IconPhotos, null) }, label = { Text("Foto") })
                    NavigationBarItem(selected = vm.tab == 1, onClick = { vm.tab = 1 }, icon = { Icon(IconFolder, null) }, label = { Text("Album") })
                    NavigationBarItem(selected = vm.tab == 2, onClick = { vm.tab = 2 }, icon = { Icon(Icons.Default.Face, null) }, label = { Text("Orang") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> if (photos.isEmpty()) {
                    CenterMessage(if (vm.loaded) "Belum ada foto di HP ini." else "Memuat foto…")
                } else {
                    PhotoGrid(
                        photos = photos, selected = vm.selected, grouped = true,
                        onClick = { onPhotoClick(vm, Source.All, it) }, onLong = { vm.toggle(it.id) },
                        header = if (limited) {
                            { Banner("Akses terbatas: hanya foto yang kamu pilih yang tampil.", "Pengaturan", onOpenSettings) }
                        } else null,
                    )
                }
                1 -> AlbumsTab(vm, albums)
                else -> PeopleTab(vm, people, status)
            }
        }
    }
}

@Composable
private fun AlbumsTab(vm: GalleryViewModel, albums: List<Album>) {
    val ctx = LocalContext.current
    if (albums.isEmpty()) { CenterMessage("Belum ada album."); return }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            Column(Modifier.clickable { vm.openAlbum(a) }) {
                AsyncImage(
                    model = remember(a.cover.id) { ImageRequest.Builder(ctx).data(a.cover.uri).size(480).crossfade(true).build() },
                    contentDescription = a.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(a.name, Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PeopleTab(vm: GalleryViewModel, people: List<PersonUi>, status: IndexStatus) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (status.running) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    if (status.total > 0) "Mengenali wajah… ${status.done} / ${status.total} foto" else "Menyiapkan pemindaian wajah…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                LinearProgressIndicator(progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
        status.error?.let { Banner("Pemindaian berhenti: $it", "Coba lagi") { vm.startIndexing() } }
        if (people.isEmpty()) {
            CenterMessage(
                if (status.running) "Orang-orang akan muncul di sini begitu wajahnya dikenali."
                else "Belum ada wajah yang dikenali. Wajah muncul setelah foto selesai dipindai."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(people, key = { it.pid }) { p ->
                    Column(
                        Modifier.clickable { vm.openPerson(p.pid) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = Faces.thumbFile(ctx, p.coverKey), contentDescription = p.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Text(
                            p.name.ifBlank { "Tambah nama" }, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            color = if (p.name.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("${p.mediaIds.size} foto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- layar album & orang
@Composable
fun AlbumScreen(vm: GalleryViewModel, r: Route.AlbumView) {
    val all by vm.photos.collectAsStateWithLifecycle()
    val photos = remember(all, r.id) { all.filter { it.bucketId == r.id } }
    val ctx = LocalContext.current
    Scaffold(topBar = {
        if (vm.selected.isNotEmpty()) SelectionBar(vm.selected.size, { vm.clearSelection() }, { shareUris(ctx, vm.selectedUris()) })
        else TopAppBar(
            title = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (photos.isEmpty()) CenterMessage("Album ini kosong.")
            else PhotoGrid(photos, vm.selected, false, { onPhotoClick(vm, Source.InAlbum(r.id), it) }, { vm.toggle(it.id) })
        }
    }
}

@Composable
fun PersonScreen(vm: GalleryViewModel, pid: Int) {
    val all by vm.photos.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.pid == pid }
    val photos = remember(all, person) { person?.let { p -> all.filter { it.id in p.mediaIds } } ?: emptyList() }
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        if (vm.selected.isNotEmpty()) SelectionBar(vm.selected.size, { vm.clearSelection() }, { shareUris(ctx, vm.selectedUris()) })
        else TopAppBar(
            title = { Text(person?.name?.ifBlank { "Tanpa nama" } ?: "Orang", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            actions = { IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Ubah nama") } },
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (photos.isEmpty()) CenterMessage("Belum ada foto untuk orang ini.")
            else PhotoGrid(photos, vm.selected, false, { onPhotoClick(vm, Source.OfPerson(pid), it) }, { vm.toggle(it.id) })
        }
    }
    if (renaming) RenameDialog(person?.name ?: "", { renaming = false }) { vm.rename(pid, it) }
}

// ---------------------------------------------------------------- penampil foto
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

@Composable
fun ViewerScreen(vm: GalleryViewModel, r: Route.Viewer) {
    val ctx = LocalContext.current
    val people by vm.people.collectAsStateWithLifecycle()
    // Daftar foto dikunci saat viewer dibuka supaya posisi tidak lompat kalau ada foto baru masuk.
    val photos = remember(r) { vm.photosFor(r.source) }
    if (photos.isEmpty()) { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val start = remember(r) { photos.indexOfFirst { it.id == r.startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var currentId by remember { mutableLongStateOf(photos[start].id) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { i -> photos.getOrNull(i)?.let { currentId = it.id } }
    }
    val current = photos.firstOrNull { it.id == currentId } ?: photos[start]
    val inPhoto = remember(people, currentId) { people.filter { currentId in it.mediaIds } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { photos[it].id }, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(
                uri = photos[page].uri, active = page == pager.currentPage,
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
            Column(Modifier.fillMaxWidth().background(Color(0x99000000)).navigationBarsPadding().padding(12.dp)) {
                if (inPhoto.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FilledTonalButton(onClick = { shareUris(ctx, listOf(current.uri)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bagikan")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- izin & kerangka
@Composable
fun PermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(IconPhotos, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(20.dp))
        Text("Izinkan akses ke foto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(10.dp))
        Text(
            "RDXN Gallery menampilkan foto kamu dan mengenali wajah langsung di HP. Aplikasi ini tidak punya izin internet, jadi foto tidak pernah keluar dari HP.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Izinkan akses") }
        TextButton(onClick = onSettings) { Text("Buka Pengaturan") }
    }
}

@Composable
private fun Overlay(r: Route, vm: GalleryViewModel) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = state, enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 8 }) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (r) {
                is Route.AlbumView -> AlbumScreen(vm, r)
                is Route.PersonView -> PersonScreen(vm, r.pid)
                is Route.Viewer -> ViewerScreen(vm, r)
                Route.Home -> {}
            }
        }
    }
}

@Composable
fun GalleryApp(vm: GalleryViewModel, limited: Boolean, onOpenSettings: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = vm.selected.isNotEmpty() || vm.stack.size > 1) {
        if (vm.selected.isNotEmpty()) vm.clearSelection() else vm.pop()
    }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm, limited, onOpenSettings)   // tetap tersusun di bawah, jadi posisi scroll tidak hilang
        for (r in vm.stack.drop(1)) key(r) { Overlay(r, vm) }
    }
}
