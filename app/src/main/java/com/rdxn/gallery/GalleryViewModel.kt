package com.rdxn.gallery

import android.app.Application
import android.content.IntentSender
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Source {
    data object All : Source
    data class InAlbum(val key: String) : Source   // "b:<bucketId>", "u:<albumId>", atau "video"
    data class OfPerson(val pid: Int) : Source
    data object Hidden : Source
}

sealed interface Route {
    data object Home : Route
    data class AlbumView(val key: String, val name: String) : Route
    data class PersonView(val pid: Int) : Route
    data object HiddenView : Route
    data object TrashView : Route
    data class Viewer(val source: Source, val startId: Long) : Route
}

data class AlbumCard(val key: String, val name: String, val count: Int, val cover: Photo?)

/** Permintaan yang butuh dialog konfirmasi sistem (hapus / pulihkan). Activity yang menjalankannya. */
class PendingAction(val sender: IntentSender, val onDone: (Boolean) -> Unit)

val COL_STEPS = listOf(3, 5, 6, 10, 14)

fun albumPhotosOf(all: List<Photo>, ua: List<UserAlbum>, key: String): List<Photo> = when {
    key == "video" -> all.filter { it.isVideo }
    key.startsWith("b:") -> {
        val id = key.substring(2).toLongOrNull()
        all.filter { it.bucketId == id }
    }
    key.startsWith("u:") -> {
        val id = key.substring(2).toLongOrNull()
        val ids = ua.firstOrNull { it.id == id }?.mediaIds ?: emptySet()
        all.filter { it.id in ids }
    }
    else -> emptyList()
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)

    private val _all = MutableStateFlow<List<Photo>>(emptyList())
    private val _hidden = MutableStateFlow(prefs.loadHidden())
    private val _userAlbums = MutableStateFlow(prefs.loadAlbums())
    private val _trashed = MutableStateFlow<List<Photo>>(emptyList())

    val userAlbums: StateFlow<List<UserAlbum>> = _userAlbums.asStateFlow()
    val trashed: StateFlow<List<Photo>> = _trashed.asStateFlow()

    /** Foto + video yang tampil (tanpa yang disembunyikan). */
    val photos: StateFlow<List<Photo>> = combine(_all, _hidden) { a, h -> a.filter { it.id !in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenPhotos: StateFlow<List<Photo>> = combine(_all, _hidden) { a, h -> a.filter { it.id in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<AlbumCard>> = combine(photos, _userAlbums) { list, ua ->
        val dev = list.groupBy { it.bucketId }
            .map { (id, ps) -> AlbumCard("b:$id", ps.first().bucket.ifBlank { "Lainnya" }, ps.size, ps.first()) }
            .sortedByDescending { it.cover?.takenMs ?: 0L }
        val usr = ua.map { a ->
            val ps = list.filter { it.id in a.mediaIds }
            AlbumCard("u:${a.id}", a.name, ps.size, ps.firstOrNull())
        }
        usr + dev
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _people = MutableStateFlow<List<PersonUi>>(emptyList())
    val people: StateFlow<List<PersonUi>> = _people.asStateFlow()

    private val _status = MutableStateFlow(IndexStatus())
    val status: StateFlow<IndexStatus> = _status.asStateFlow()

    private val _requests = Channel<PendingAction>(Channel.BUFFERED)
    val requests = _requests.receiveAsFlow()
    private val _auth = Channel<Unit>(Channel.CONFLATED)
    val authRequests = _auth.receiveAsFlow()

    var stack by mutableStateOf<List<Route>>(listOf(Route.Home))
        private set
    var tab by mutableIntStateOf(0)
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var albumSel by mutableStateOf<Set<String>>(emptySet())
        private set
    var albumSelMode by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var cols by mutableIntStateOf(prefs.startCols)
        private set
    var autoScan by mutableStateOf(prefs.autoScan)
        private set
    var lockHidden by mutableStateOf(prefs.lockHidden)
        private set
    private var hiddenUnlocked = false

    private var hasAccess = false
    private var indexJob: Job? = null
    private var reloadJob: Job? = null

    // Realtime: dipanggil Android setiap ada foto/video baru, dihapus, atau selesai diunduh.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { onMediaChanged() }
    }

    init {
        ctx.contentResolver.registerContentObserver(MediaRepo.IMAGES, true, observer)
        ctx.contentResolver.registerContentObserver(MediaRepo.VIDEOS, true, observer)
        viewModelScope.launch(Dispatchers.IO) { refreshPeople() }
    }

    override fun onCleared() {
        ctx.contentResolver.unregisterContentObserver(observer)
    }

    private fun toast(msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    private fun onMediaChanged() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(500)
            reloadPhotos()
            if (autoScan) startIndexing()
        }
    }

    fun onAccessChanged(granted: Boolean) {
        hasAccess = granted
        if (granted) { reloadPhotos(); if (autoScan) startIndexing() }
    }

    fun reloadPhotos() {
        viewModelScope.launch {
            val (a, t) = withContext(Dispatchers.IO) { MediaRepo.load(ctx) to MediaRepo.load(ctx, trashed = true) }
            _all.value = a
            _trashed.value = t
            loaded = true
        }
    }

    private fun refreshPeople() { _people.value = Faces.get(ctx).snapshot() }

    fun startIndexing(force: Boolean = false) {
        if (!hasAccess || (!force && !autoScan) || indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            _status.value = IndexStatus(running = true)
            try {
                withContext(Dispatchers.IO) { refreshPeople() }
                Indexer(ctx, Faces.get(ctx)).run(
                    onProgress = { d, t -> _status.value = IndexStatus(running = true, done = d, total = t) },
                    onChanged = { refreshPeople() },
                )
                withContext(Dispatchers.IO) { refreshPeople() }
                _status.value = IndexStatus()
                IndexScheduler.schedule(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _status.value = IndexStatus(error = e.message ?: "Gagal memindai wajah")
            }
        }
    }

    fun rename(pid: Int, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = Faces.get(ctx)
            s.rename(pid, name)
            s.save()
            refreshPeople()
        }
    }

    // ---------------- pengaturan ----------------
    fun setAutoScan(v: Boolean) { autoScan = v; prefs.autoScan = v; if (v) startIndexing() }
    fun setLockHidden(v: Boolean) { lockHidden = v; prefs.lockHidden = v }
    fun cycleStartCols() {
        val order = listOf(3, 5, 6)
        val next = order[(order.indexOf(prefs.startCols) + 1).mod(order.size)]
        prefs.startCols = next
        cols = next
    }
    fun rescanAll() {
        viewModelScope.launch {
            indexJob?.cancelAndJoin()
            withContext(Dispatchers.IO) { Faces.get(ctx).clearAll(); refreshPeople() }
            startIndexing(force = true)
        }
    }
    fun clearFaceData() {
        viewModelScope.launch {
            indexJob?.cancelAndJoin()
            autoScan = false; prefs.autoScan = false
            withContext(Dispatchers.IO) { Faces.get(ctx).clearAll(); refreshPeople() }
            _status.value = IndexStatus()
        }
    }

    // ---------------- grid ----------------
    /** dir > 0: kolom lebih banyak (cubit masuk), dir < 0: kolom lebih sedikit (cubit keluar). */
    fun stepCols(dir: Int) {
        val i = COL_STEPS.indexOf(cols).let { if (it < 0) 0 else it }
        cols = COL_STEPS[(i + dir).coerceIn(0, COL_STEPS.lastIndex)]
    }

    // ---------------- navigasi ----------------
    fun openAlbum(key: String, name: String) { clearSelection(); stack = stack + Route.AlbumView(key, name) }
    fun openPerson(pid: Int) {
        clearSelection()
        stack = (if (stack.last() is Route.Viewer) stack.dropLast(1) else stack) + Route.PersonView(pid)
    }
    fun openViewer(source: Source, id: Long) { clearSelection(); stack = stack + Route.Viewer(source, id) }
    fun openTrash() { clearSelection(); stack = stack + Route.TrashView }
    fun openHidden() {
        clearSelection()
        if (!lockHidden || hiddenUnlocked) stack = stack + Route.HiddenView else _auth.trySend(Unit)
    }
    fun onAuthResult(ok: Boolean) {
        if (ok) { hiddenUnlocked = true; stack = stack + Route.HiddenView }
    }
    /** Dipanggil saat aplikasi ke latar belakang: album Tersembunyi terkunci lagi. */
    fun lockHiddenNow() {
        hiddenUnlocked = false
        if (lockHidden) stack = stack.filterNot { it is Route.HiddenView || (it is Route.Viewer && it.source == Source.Hidden) }
    }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    // ---------------- pilihan ----------------
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun clearSelection() { selected = emptySet() }
    fun selectedPhotos(): List<Photo> = (_all.value + _trashed.value).filter { it.id in selected }
    fun selectedUris() = selectedPhotos().map { it.uri }

    fun toggleAlbum(key: String) { albumSel = if (key in albumSel) albumSel - key else albumSel + key }
    fun beginAlbumSelect() { albumSelMode = true; albumSel = emptySet() }
    fun endAlbumSelect() { albumSelMode = false; albumSel = emptySet() }

    fun photosFor(src: Source): List<Photo> = when (src) {
        Source.All -> photos.value
        is Source.InAlbum -> albumPhotosOf(photos.value, userAlbums.value, src.key)
        is Source.OfPerson -> {
            val ids = people.value.firstOrNull { it.pid == src.pid }?.mediaIds ?: emptySet()
            photos.value.filter { it.id in ids }
        }
        Source.Hidden -> hiddenPhotos.value
    }

    fun peopleOf(mediaId: Long): List<PersonUi> = people.value.filter { mediaId in it.mediaIds }

    // ---------------- hapus / pulihkan / duplikat ----------------
    private fun afterChange() {
        clearSelection()
        if (stack.last() is Route.Viewer) pop()
        reloadPhotos()
    }

    private fun request(sender: IntentSender, onOk: () -> Unit) {
        _requests.trySend(PendingAction(sender) { ok -> if (ok) onOk() })
    }

    fun trash(items: List<Photo>) {
        if (items.isEmpty()) return
        request(MediaActions.trashIntent(ctx, items.map { it.uri }, true)) { afterChange() }
    }

    fun restore(items: List<Photo>) {
        if (items.isEmpty()) return
        request(MediaActions.trashIntent(ctx, items.map { it.uri }, false)) { afterChange() }
    }

    fun deleteForever(items: List<Photo>) {
        if (items.isEmpty()) return
        request(MediaActions.deleteIntent(ctx, items.map { it.uri })) { afterChange() }
    }

    fun duplicate(items: List<Photo>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { items.count { MediaActions.duplicate(ctx, it) } }
            clearSelection()
            toast(if (ok == items.size) "$ok item diduplikat" else "${items.size - ok} item gagal diduplikat")
            reloadPhotos()
        }
    }

    // ---------------- sembunyikan ----------------
    private fun afterLocal() {
        clearSelection()
        if (stack.last() is Route.Viewer) pop()
    }

    fun hide(items: List<Photo>) {
        if (items.isEmpty()) return
        _hidden.value = _hidden.value + items.map { it.id }
        prefs.saveHidden(_hidden.value)
        afterLocal()
    }

    fun unhide(items: List<Photo>) {
        if (items.isEmpty()) return
        _hidden.value = _hidden.value - items.map { it.id }.toSet()
        prefs.saveHidden(_hidden.value)
        afterLocal()
    }

    // ---------------- album buatan sendiri ----------------
    private fun saveAlbums(list: List<UserAlbum>) { _userAlbums.value = list; prefs.saveAlbums(list) }

    fun createAlbum(name: String, ids: Set<Long> = emptySet()): Long {
        val clean = name.trim().replace('\n', ' ').ifBlank { "Album baru" }
        val id = System.currentTimeMillis()
        saveAlbums(_userAlbums.value + UserAlbum(id, clean, ids))
        return id
    }

    fun addToAlbum(albumId: Long, ids: Set<Long>) {
        saveAlbums(_userAlbums.value.map { if (it.id == albumId) it.copy(mediaIds = it.mediaIds + ids) else it })
        clearSelection()
        toast("Ditambahkan ke album")
    }

    fun removeFromAlbum(albumId: Long, ids: Set<Long>) {
        saveAlbums(_userAlbums.value.map { if (it.id == albumId) it.copy(mediaIds = it.mediaIds - ids) else it })
        clearSelection()
    }

    fun renameAlbum(albumId: Long, name: String) {
        val clean = name.trim().replace('\n', ' ')
        if (clean.isBlank()) return
        saveAlbums(_userAlbums.value.map { if (it.id == albumId) it.copy(name = clean) else it })
    }

    /** Album buatan sendiri: hanya albumnya yang hilang. Album folder: isinya dipindah ke Baru Dihapus. */
    fun deleteAlbums(keys: Set<String>) {
        val userIds = keys.filter { it.startsWith("u:") }.mapNotNull { it.substring(2).toLongOrNull() }.toSet()
        if (userIds.isNotEmpty()) saveAlbums(_userAlbums.value.filter { it.id !in userIds })
        val bucketIds = keys.filter { it.startsWith("b:") }.mapNotNull { it.substring(2).toLongOrNull() }.toSet()
        if (bucketIds.isNotEmpty()) trash(photos.value.filter { it.bucketId in bucketIds })
        endAlbumSelect()
    }
}
