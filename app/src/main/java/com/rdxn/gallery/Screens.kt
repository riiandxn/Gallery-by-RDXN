@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.rdxn.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest

// ---------------------------------------------------------------- layar utama (4 tab)
@Composable
fun HomeScreen(vm: GalleryViewModel, limited: Boolean, onOpenSettings: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val hidden by vm.hiddenPhotos.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val albumSelecting = vm.tab == 1 && vm.albumSelMode
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val titles = listOf("Foto", "Album", "Orang", "Pengaturan")

    Scaffold(
        topBar = {
            when {
                selecting -> SelectionTopBar(vm.selected.size, onClose = { vm.clearSelection() }, onShare = { shareUris(ctx, vm.selectedUris()) })
                albumSelecting -> TopAppBar(
                    title = { Text("${vm.albumSel.size} dipilih") },
                    navigationIcon = { IconButton(onClick = { vm.endAlbumSelect() }) { Icon(Icons.Default.Close, "Batal") } },
                )
                else -> TopAppBar(
                    title = { Text(titles[vm.tab], fontWeight = FontWeight.Bold) },
                    actions = {
                        when (vm.tab) {
                            1 -> {
                                TextButton(onClick = { vm.beginAlbumSelect() }) { Text("Pilih") }
                                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Buat album") }
                            }
                            2 -> IconButton(onClick = { vm.startIndexing(force = true) }) { Icon(Icons.Default.Refresh, "Pindai ulang") }
                        }
                    },
                )
            }
        },
        bottomBar = {
            when {
                selecting -> SelActions(vm, hidden = false, albumKey = null)
                albumSelecting -> Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { confirmDelete = true }, enabled = vm.albumSel.isNotEmpty()) {
                        Text("Hapus album (${vm.albumSel.size})", color = Danger)
                    }
                }
                else -> NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(selected = vm.tab == 0, onClick = { vm.tab = 0 }, icon = { Icon(IconPhotos, null) }, label = { Text("Foto") })
                    NavigationBarItem(selected = vm.tab == 1, onClick = { vm.tab = 1 }, icon = { Icon(IconFolder, null) }, label = { Text("Album") })
                    NavigationBarItem(selected = vm.tab == 2, onClick = { vm.tab = 2 }, icon = { Icon(Icons.Default.Face, null) }, label = { Text("Orang") })
                    NavigationBarItem(selected = vm.tab == 3, onClick = { vm.tab = 3 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Pengaturan") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> if (photos.isEmpty()) {
                    CenterMessage(if (vm.loaded) "Belum ada foto atau video di HP ini." else "Memuat…")
                } else {
                    PhotoGrid(
                        photos = photos, selected = vm.selected, grouped = true,
                        cols = vm.cols, onStep = { vm.stepCols(it) },
                        onClick = { onPhotoClick(vm, Source.All, it) }, onLong = { vm.toggle(it.id) },
                        header = if (limited) {
                            { Banner("Akses terbatas: sebagian foto/video belum tampil.", "Pengaturan", onOpenSettings) }
                        } else null,
                    )
                }
                1 -> AlbumsTab(vm, albums, photos.count { it.isVideo }, hidden.size, trashed.size, people.size)
                2 -> PeopleTab(vm, people, status)
                else -> SettingsTab(vm)
            }
        }
    }
    if (creating) NameDialog("Album baru", "", "Buat", { creating = false }) { vm.createAlbum(it) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${vm.albumSel.size} album?") },
            text = { Text("Album buatan sendiri: hanya albumnya yang dihapus, foto tetap aman. Album folder: semua isinya dipindah ke Baru Dihapus.") },
            confirmButton = { TextButton(onClick = { vm.deleteAlbums(vm.albumSel); confirmDelete = false }) { Text("Hapus", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f))
        Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlbumsTab(
    vm: GalleryViewModel, albums: List<AlbumCard>,
    videoCount: Int, hiddenCount: Int, trashCount: Int, peopleCount: Int,
) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.key }) { a ->
            val sel = a.key in vm.albumSel
            Column(Modifier.clickable { if (vm.albumSelMode) vm.toggleAlbum(a.key) else vm.openAlbum(a.key, a.name) }) {
                Box {
                    val shape = RoundedCornerShape(14.dp)
                    val base = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                    if (a.cover != null) {
                        AsyncImage(
                            model = remember(a.cover.id) { ImageRequest.Builder(ctx).data(a.cover.uri).size(480).crossfade(true).build() },
                            contentDescription = a.name, contentScale = ContentScale.Crop,
                            modifier = if (sel) base.border(3.dp, Accent, shape) else base,
                        )
                    } else {
                        Box(if (sel) base.border(3.dp, Accent, shape) else base)
                    }
                    if (vm.albumSelMode) {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape)
                                .background(if (sel) Accent else Color(0x66000000)).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White) }
                    }
                }
                Text(a.name, Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Jenis Media dan Lainnya", Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { ListRow(Icons.Default.PlayArrow, "Video", "$videoCount") { vm.openAlbum("video", "Video") } }
        item(span = { GridItemSpan(maxLineSpan) }) { ListRow(Icons.Default.Face, "Orang", "$peopleCount") { vm.tab = 2 } }
        item(span = { GridItemSpan(maxLineSpan) }) { ListRow(Icons.Default.Lock, "Tersembunyi", "$hiddenCount") { vm.openHidden() } }
        item(span = { GridItemSpan(maxLineSpan) }) { ListRow(Icons.Default.Delete, "Baru Dihapus", "$trashCount") { vm.openTrash() } }
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
        status.error?.let { Banner("Pemindaian berhenti: $it", "Coba lagi") { vm.startIndexing(force = true) } }
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

// ---------------------------------------------------------------- pengaturan
@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsTab(vm: GalleryViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRescan by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionLabel("Tampilan")
        SettingRow("Kolom grid awal", "Ketuk untuk ganti (3, 5, atau 6). Cubit layar untuk ubah sementara.", onClick = { vm.cycleStartCols() }) {
            Text("${vm.cols}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingRow("Tema", "Mengikuti pengaturan HP (terang/gelap)")
        SectionLabel("Pengenalan wajah")
        SettingRow("Pindai foto baru otomatis", "Di latar belakang, hanya di HP") {
            Switch(checked = vm.autoScan, onCheckedChange = { vm.updateAutoScan(it) })
        }
        SettingRow("Pindai ulang semua foto", "Data wajah dihapus lalu dibuat ulang", onClick = { confirmRescan = true })
        SettingRow("Hapus data wajah", "Sekaligus mematikan pemindaian otomatis", onClick = { confirmClear = true })
        SectionLabel("Privasi")
        SettingRow("Kunci album Tersembunyi", "Pakai sidik jari, wajah, atau PIN HP") {
            Switch(checked = vm.lockHidden, onCheckedChange = { vm.updateLockHidden(it) })
        }
        SectionLabel("Baru Dihapus")
        SettingRow("Disimpan sebelum dihapus permanen", "Diatur oleh Android, sekitar 30 hari")
        SectionLabel("Tentang")
        SettingRow("RDXN Gallery", "Versi 2.0. Tidak ada izin internet.")
    }
    if (confirmRescan) AlertDialog(
        onDismissRequest = { confirmRescan = false },
        title = { Text("Pindai ulang semua foto?") },
        text = { Text("Nama orang yang sudah kamu isi ikut terhapus dan prosesnya bisa lama.") },
        confirmButton = { TextButton(onClick = { vm.rescanAll(); confirmRescan = false }) { Text("Pindai ulang") } },
        dismissButton = { TextButton(onClick = { confirmRescan = false }) { Text("Batal") } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus data wajah?") },
        text = { Text("Semua orang dan namanya dihapus, pemindaian otomatis dimatikan. Foto tidak tersentuh.") },
        confirmButton = { TextButton(onClick = { vm.clearFaceData(); confirmClear = false }) { Text("Hapus", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- layar album, orang, tersembunyi, baru dihapus
@Composable
fun AlbumScreen(vm: GalleryViewModel, r: Route.AlbumView) {
    val all by vm.photos.collectAsStateWithLifecycle()
    val ua by vm.userAlbums.collectAsStateWithLifecycle()
    val photos = remember(all, ua, r.key) { albumPhotosOf(all, ua, r.key) }
    val ctx = LocalContext.current
    val userId = if (r.key.startsWith("u:")) r.key.substring(2).toLongOrNull() else null
    val title = ua.firstOrNull { it.id == userId }?.name ?: r.name
    var renaming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (vm.selected.isNotEmpty()) SelectionTopBar(vm.selected.size, { vm.clearSelection() }) { shareUris(ctx, vm.selectedUris()) }
            else TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { if (userId != null) IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Ganti nama") } },
            )
        },
        bottomBar = { if (vm.selected.isNotEmpty()) SelActions(vm, hidden = false, albumKey = r.key) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (photos.isEmpty()) CenterMessage(if (userId != null) "Album kosong. Pilih foto di tab Foto lalu tekan Album." else "Album ini kosong.")
            else PhotoGrid(
                photos, vm.selected, false, vm.cols, { vm.stepCols(it) },
                { onPhotoClick(vm, Source.InAlbum(r.key), it) }, { vm.toggle(it.id) },
            )
        }
    }
    if (renaming && userId != null) NameDialog("Ganti nama album", title, "Simpan", { renaming = false }) { vm.renameAlbum(userId, it) }
}

@Composable
fun PersonScreen(vm: GalleryViewModel, pid: Int) {
    val all by vm.photos.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.pid == pid }
    val photos = remember(all, person) { person?.let { p -> all.filter { it.id in p.mediaIds } } ?: emptyList() }
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (vm.selected.isNotEmpty()) SelectionTopBar(vm.selected.size, { vm.clearSelection() }) { shareUris(ctx, vm.selectedUris()) }
            else TopAppBar(
                title = { Text(person?.name?.ifBlank { "Tanpa nama" } ?: "Orang", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Ubah nama") } },
            )
        },
        bottomBar = { if (vm.selected.isNotEmpty()) SelActions(vm, hidden = false, albumKey = null) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (photos.isEmpty()) CenterMessage("Belum ada foto untuk orang ini.")
            else PhotoGrid(
                photos, vm.selected, false, vm.cols, { vm.stepCols(it) },
                { onPhotoClick(vm, Source.OfPerson(pid), it) }, { vm.toggle(it.id) },
            )
        }
    }
    if (renaming) NameDialog("Nama orang ini", person?.name ?: "", "Simpan", { renaming = false }) { vm.rename(pid, it) }
}

@Composable
fun HiddenScreen(vm: GalleryViewModel) {
    val photos by vm.hiddenPhotos.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            if (vm.selected.isNotEmpty()) SelectionTopBar(vm.selected.size, { vm.clearSelection() }) { shareUris(ctx, vm.selectedUris()) }
            else TopAppBar(
                title = { Text("Tersembunyi") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        bottomBar = { if (vm.selected.isNotEmpty()) SelActions(vm, hidden = true, albumKey = null) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (photos.isEmpty()) CenterMessage("Belum ada foto atau video tersembunyi.")
            else PhotoGrid(
                photos, vm.selected, false, vm.cols, { vm.stepCols(it) },
                { onPhotoClick(vm, Source.Hidden, it) }, { vm.toggle(it.id) },
            )
        }
    }
}

@Composable
fun TrashScreen(vm: GalleryViewModel) {
    val items by vm.trashed.collectAsStateWithLifecycle()
    val sel = vm.selected
    val target = if (sel.isEmpty()) items else items.filter { it.id in sel }
    val label = if (sel.isEmpty()) "semua" else "${sel.size}"
    Scaffold(
        topBar = {
            if (sel.isNotEmpty()) SelectionTopBar(sel.size, { vm.clearSelection() })
            else TopAppBar(
                title = { Text("Baru Dihapus") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { vm.restore(target) }) { Text("Pulihkan $label") }
                TextButton(onClick = { vm.deleteForever(target) }) { Text("Hapus permanen $label", color = Danger) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) CenterMessage("Tidak ada item di Baru Dihapus.")
            else {
                Text(
                    "Item dihapus permanen oleh Android setelah sekitar 30 hari. Ketuk untuk memilih.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PhotoGrid(
                    items, sel, false, 3, {},
                    onClick = { vm.toggle(it.id) }, onLong = { vm.toggle(it.id) },
                    badge = { p ->
                        if (p.expiresMs > 0) "${((p.expiresMs - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)} hari" else null
                    },
                )
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
        Text("Izinkan akses ke foto dan video", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(10.dp))
        Text(
            "RDXN Gallery menampilkan foto dan video kamu dan mengenali wajah langsung di HP. Aplikasi ini tidak punya izin internet, jadi media tidak pernah keluar dari HP.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Izinkan akses") }
        TextButton(onClick = onSettings) { Text("Buka Pengaturan") }
    }
}

@Composable
private fun Overlay(r: Route, vm: GalleryViewModel) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = state,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { it / 8 },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (r) {
                is Route.AlbumView -> AlbumScreen(vm, r)
                is Route.PersonView -> PersonScreen(vm, r.pid)
                Route.HiddenView -> HiddenScreen(vm)
                Route.TrashView -> TrashScreen(vm)
                is Route.Viewer -> ViewerScreen(vm, r)
                Route.Home -> {}
            }
        }
    }
}

@Composable
fun GalleryApp(vm: GalleryViewModel, limited: Boolean, onOpenSettings: () -> Unit) {
    androidx.activity.compose.BackHandler(
        enabled = vm.selected.isNotEmpty() || vm.stack.size > 1 || vm.albumSelMode,
    ) {
        when {
            vm.selected.isNotEmpty() -> vm.clearSelection()
            vm.stack.size > 1 -> vm.pop()
            else -> vm.endAlbumSelect()
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm, limited, onOpenSettings)   // tetap tersusun di bawah, jadi posisi scroll tidak hilang
        for (r in vm.stack.drop(1)) androidx.compose.runtime.key(r) { Overlay(r, vm) }
    }
}
