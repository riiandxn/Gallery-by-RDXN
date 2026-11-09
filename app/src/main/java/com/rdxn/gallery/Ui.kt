@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.rdxn.gallery

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val Accent = Color(0xFF4C8DFF)
internal val Danger = Color(0xFFFF6B6B)

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

internal val IconPhotos: ImageVector by lazy {
    vec("photos", "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z")
}
internal val IconFolder: ImageVector by lazy {
    vec("folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
}
internal val IconCopy: ImageVector by lazy {
    vec("copy", "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z")
}
internal val IconEye: ImageVector by lazy {
    vec("eye", "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z")
}
internal val IconEyeOff: ImageVector by lazy {
    vec("eyeoff", "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z")
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

fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun shareUris(ctx: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        val clip = ClipData.newUri(ctx.contentResolver, "media", uris[0])
        for (u in uris.drop(1)) clip.addItem(ClipData.Item(u))
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(intent, "Bagikan"))
    } catch (e: ActivityNotFoundException) {
        // tidak ada aplikasi tujuan
    }
}

internal fun onPhotoClick(vm: GalleryViewModel, source: Source, p: Photo) {
    if (vm.selected.isNotEmpty()) vm.toggle(p.id) else vm.openViewer(source, p.id)
}

// ---------------------------------------------------------------- komponen umum
@Composable
private fun Thumb(
    p: Photo, selected: Boolean, selecting: Boolean, small: Boolean, badge: String?,
    onClick: () -> Unit, onLong: () -> Unit,
) {
    val ctx = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        AsyncImage(
            model = remember(p.id) { ImageRequest.Builder(ctx).data(p.uri).size(256).crossfade(true).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val label = badge ?: if (p.isVideo) fmtDur(p.durationMs) else null
        if (label != null && !small) {
            Text(
                label, color = Color.White, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        } else if (p.isVideo) {
            Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.BottomEnd).size(12.dp), tint = Color.White)
        }
        if (selected) Box(Modifier.fillMaxSize().background(Color(0x553D8BFF)))
        if (selecting) {
            val cs = if (small) 14.dp else 22.dp
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(if (small) 2.dp else 6.dp)
                    .size(cs)
                    .clip(CircleShape)
                    .background(if (selected) Accent else Color(0x66000000))
                    .border(if (small) 1.dp else 2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(cs - 6.dp), tint = Color.White)
            }
        }
    }
}

/** Grid foto/video. Cubit dua jari mengubah jumlah kolom (3 → 5 → 6 → 10 → 14). */
@Composable
fun PhotoGrid(
    photos: List<Photo>,
    selected: Set<Long>,
    grouped: Boolean,
    cols: Int,
    onStep: (Int) -> Unit,
    onClick: (Photo) -> Unit,
    onLong: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    badge: (Photo) -> String? = { null },
    header: (@Composable () -> Unit)? = null,
) {
    val groups = remember(photos, grouped) { if (grouped) groupByDay(photos) else emptyList() }
    val selecting = selected.isNotEmpty()
    val step by rememberUpdatedState(onStep)
    val state = rememberLazyGridState()
    val small = cols >= 10
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.size > 1) {
                            zoom *= ev.calculateZoom()
                            if (zoom > 1.3f) { step(-1); zoom = 1f }
                            else if (zoom < 0.77f) { step(1); zoom = 1f }
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            modifier = Modifier.fillMaxSize(),
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
                        Thumb(p, p.id in selected, selecting, small, badge(p), { onClick(p) }, { onLong(p) })
                    }
                }
            } else {
                items(photos, key = { it.id }) { p ->
                    Thumb(p, p.id in selected, selecting, small, badge(p), { onClick(p) }, { onLong(p) })
                }
            }
        }
    }
}

@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, onShare: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text("$count dipilih") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Batal") } },
        actions = { if (onShare != null) IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Bagikan") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
fun ActionBtn(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val c = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Column(
        Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = c)
        Text(label, color = c, fontSize = 11.sp)
    }
}

@Composable
fun Banner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
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
fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nama") }) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Dialog "Tambah ke album": pilih album buatan sendiri atau buat yang baru dari item terpilih. */
@Composable
fun AddToAlbumDialog(vm: GalleryViewModel, ids: Set<Long>, onDismiss: () -> Unit) {
    val albums by vm.userAlbums.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("Album baru", "", "Buat", onDismiss) { vm.createAlbum(it, ids); vm.clearSelection() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah ke album") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { creating = true }) { Text("+ Album baru") }
                for (a in albums) {
                    TextButton(onClick = { vm.addToAlbum(a.id, ids); onDismiss() }) { Text(a.name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Bar aksi di bawah saat ada foto/video terpilih. */
@Composable
fun SelActions(vm: GalleryViewModel, hidden: Boolean, albumKey: String?) {
    val ctx = LocalContext.current
    var pickAlbum by remember { mutableStateOf(false) }
    val items = vm.selectedPhotos()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionBtn(IconCopy, "Duplikat") { vm.duplicate(items) }
        ActionBtn(Icons.Default.Add, "Album") { pickAlbum = true }
        if (albumKey != null && albumKey.startsWith("u:")) {
            ActionBtn(Icons.Default.Close, "Keluarkan") {
                albumKey.substring(2).toLongOrNull()?.let { vm.removeFromAlbum(it, items.map { p -> p.id }.toSet()) }
            }
        }
        ActionBtn(if (hidden) IconEye else IconEyeOff, if (hidden) "Tampilkan" else "Sembunyikan") {
            if (hidden) vm.unhide(items) else vm.hide(items)
        }
        ActionBtn(Icons.Default.Delete, "Hapus", Danger) { vm.trash(items) }
    }
    if (pickAlbum) AddToAlbumDialog(vm, items.map { it.id }.toSet()) { pickAlbum = false }
}
