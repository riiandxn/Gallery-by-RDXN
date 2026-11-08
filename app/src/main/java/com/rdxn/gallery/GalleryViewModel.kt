package com.rdxn.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Source {
    data object All : Source
    data class InAlbum(val id: Long) : Source
    data class OfPerson(val pid: Int) : Source
}

sealed interface Route {
    data object Home : Route
    data class AlbumView(val id: Long, val name: String) : Route
    data class PersonView(val pid: Int) : Route
    data class Viewer(val source: Source, val startId: Long) : Route
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    val albums: StateFlow<List<Album>> = photos.map { list ->
        list.groupBy { it.bucketId }
            .map { (id, ps) -> Album(id, ps.first().bucket.ifBlank { "Lainnya" }, ps.size, ps.first()) }
            .sortedByDescending { it.cover.takenMs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _people = MutableStateFlow<List<PersonUi>>(emptyList())
    val people: StateFlow<List<PersonUi>> = _people.asStateFlow()

    private val _status = MutableStateFlow(IndexStatus())
    val status: StateFlow<IndexStatus> = _status.asStateFlow()

    var stack by mutableStateOf<List<Route>>(listOf(Route.Home))
        private set
    var tab by mutableIntStateOf(0)
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var loaded by mutableStateOf(false)
        private set

    private var hasAccess = false
    private var indexJob: Job? = null
    private var reloadJob: Job? = null

    // Realtime: dipanggil Android setiap ada foto baru / dihapus / selesai diunduh.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { onMediaChanged() }
    }

    init {
        ctx.contentResolver.registerContentObserver(MediaRepo.IMAGES, true, observer)
        viewModelScope.launch(Dispatchers.IO) { refreshPeople() }
    }

    override fun onCleared() {
        ctx.contentResolver.unregisterContentObserver(observer)
    }

    private fun onMediaChanged() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(500)   // tunggu sebentar kalau banyak foto masuk sekaligus
            reloadPhotos()
            startIndexing()
        }
    }

    fun onAccessChanged(granted: Boolean) {
        hasAccess = granted
        if (granted) { reloadPhotos(); startIndexing() }
    }

    fun reloadPhotos() {
        viewModelScope.launch {
            _photos.value = withContext(Dispatchers.IO) { MediaRepo.load(ctx) }
            loaded = true
        }
    }

    private fun refreshPeople() {
        _people.value = Faces.get(ctx).snapshot()
    }

    fun startIndexing() {
        if (!hasAccess || indexJob?.isActive == true) return
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

    // ---- navigasi ----
    fun openAlbum(a: Album) { clearSelection(); stack = stack + Route.AlbumView(a.id, a.name) }
    fun openPerson(pid: Int) {
        clearSelection()
        stack = (if (stack.last() is Route.Viewer) stack.dropLast(1) else stack) + Route.PersonView(pid)
    }
    fun openViewer(source: Source, id: Long) { clearSelection(); stack = stack + Route.Viewer(source, id) }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    // ---- pilihan (untuk share banyak foto) ----
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun clearSelection() { selected = emptySet() }
    fun selectedUris() = photos.value.filter { it.id in selected }.map { it.uri }

    fun photosFor(src: Source): List<Photo> = when (src) {
        Source.All -> photos.value
        is Source.InAlbum -> photos.value.filter { it.bucketId == src.id }
        is Source.OfPerson -> {
            val ids = people.value.firstOrNull { it.pid == src.pid }?.mediaIds ?: emptySet()
            photos.value.filter { it.id in ids }
        }
    }

    fun peopleOf(mediaId: Long): List<PersonUi> = people.value.filter { mediaId in it.mediaIds }
}
