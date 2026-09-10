package com.example.composeup.screenrecord

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class RecordedVideo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Int,
    val size: Long,
    val dateAdded: Long
)

object MediaStoreUtils {
    private val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    fun saveVideoToMediaStore(context: Context, fileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ComposeUpRecordings")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return context.contentResolver.insert(collection, values)
    }

    fun finishRecording(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun fetchVideos(context: Context): List<RecordedVideo> {
        val videos = mutableListOf<RecordedVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        // Filter by the directory we save to
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            null
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Movies/ComposeUpRecordings%")
        } else {
            null
        }
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                videos.add(RecordedVideo(id, contentUri, name, duration, size, date))
            }
        }
        return videos
    }

    /**
     * Deletes one or more videos.
     * On Android 11+, it returns an IntentSender if user confirmation is required.
     * On Android 10, it may throw RecoverableSecurityException.
     */
    fun deleteVideos(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else {
            uris.forEach { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        return e.userAction.actionIntent.intentSender
                    } else {
                        throw e
                    }
                }
            }
        }
        return null
    }

    fun deleteVideo(context: Context, uri: Uri): Boolean {
        try {
            val result = context.contentResolver.delete(uri, null, null)
            return result > 0
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                // Let the caller handle this exception
                throw e
            }
            return false
        }
    }
}
