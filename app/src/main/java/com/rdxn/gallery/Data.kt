package com.rdxn.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Photo(val id: Long, val uri: Uri, val takenMs: Long, val bucketId: Long, val bucket: String)
data class Album(val id: Long, val name: String, val count: Int, val cover: Photo)
data class PersonUi(val pid: Int, val name: String, val mediaIds: Set<Long>, val coverKey: Long)
data class IndexStatus(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val error: String? = null)

object MediaRepo {
    val IMAGES: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** Semua foto di HP, terbaru dulu. */
    fun load(ctx: Context): List<Photo> {
        val out = ArrayList<Photo>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        try {
            ctx.contentResolver.query(IMAGES, proj, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded)
                    out.add(
                        Photo(
                            id = id,
                            uri = ContentUris.withAppendedId(IMAGES, id),
                            takenMs = if (taken > 0) taken else added * 1000,
                            bucketId = c.getLong(iBucket),
                            bucket = c.getString(iName) ?: "",
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
