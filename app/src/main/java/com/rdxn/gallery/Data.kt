package com.rdxn.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

data class Photo(
    val id: Long,
    val uri: Uri,
    val takenMs: Long,
    val bucketId: Long,
    val bucket: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0,
    val expiresMs: Long = 0,
)
data class PersonUi(val pid: Int, val name: String, val mediaIds: Set<Long>, val coverKey: Long)
data class IndexStatus(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val error: String? = null)

object MediaRepo {
    val FILES: Uri = MediaStore.Files.getContentUri("external")
    val IMAGES: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val VIDEOS: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** Foto + video di HP, terbaru dulu. trashed=true: isi "Baru Dihapus" milik sistem Android. */
    fun load(ctx: Context, trashed: Boolean = false): List<Photo> {
        val out = ArrayList<Photo>()
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "media_type IN (1,3)")
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        try {
            ctx.contentResolver.query(FILES, proj, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iExp = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded)
                    out.add(
                        Photo(
                            id = id,
                            uri = ContentUris.withAppendedId(if (video) VIDEOS else IMAGES, id),
                            takenMs = if (taken > 0) taken else added * 1000,
                            bucketId = c.getLong(iBucket),
                            bucket = c.getString(iName) ?: "",
                            isVideo = video,
                            durationMs = if (video) c.getLong(iDur) else 0,
                            expiresMs = c.getLong(iExp) * 1000,
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // izin belum diberikan -> daftar kosong
        }
        out.sortByDescending { it.takenMs }
        return out
    }
}

object MediaActions {
    /** Pindah ke / pulihkan dari "Baru Dihapus" (Android 11+). Sistem menampilkan dialog konfirmasi. */
    fun trashIntent(ctx: Context, uris: List<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(ctx.contentResolver, uris, trash).intentSender

    /** Hapus permanen. Sistem menampilkan dialog konfirmasi. */
    fun deleteIntent(ctx: Context, uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender

    /** Salin foto/video jadi file baru bernama "... (salinan)" di folder yang sama. */
    fun duplicate(ctx: Context, p: Photo): Boolean {
        val cr = ctx.contentResolver
        try {
            val meta = cr.query(
                p.uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) Triple(c.getString(0) ?: "foto", c.getString(1) ?: "", c.getString(2)) else null
            } ?: return false
            val (name, mime, rel) = meta
            val dot = name.lastIndexOf('.')
            val newName = if (dot > 0) name.substring(0, dot) + " (salinan)" + name.substring(dot) else "$name (salinan)"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                if (mime.isNotEmpty()) put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (rel != null) put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = cr.insert(if (p.isVideo) MediaRepo.VIDEOS else MediaRepo.IMAGES, values) ?: return false
            try {
                cr.openInputStream(p.uri)!!.use { input -> cr.openOutputStream(target)!!.use { output -> input.copyTo(output) } }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                cr.update(target, done, null, null)
                return true
            } catch (e: Exception) {
                cr.delete(target, null, null)
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }
}
