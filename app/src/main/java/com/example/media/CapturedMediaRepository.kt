package com.example.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MediaType {
  PHOTO,
  VIDEO,
}

enum class GalleryFilter {
  ALL,
  PHOTOS,
  VIDEOS,
}

data class CapturedMediaItem(
  val id: Long,
  val uri: Uri,
  val displayName: String,
  val type: MediaType,
  val dateAddedMillis: Long,
  val durationMillis: Long = 0L,
  val sizeBytes: Long = 0L,
)

object CapturedMediaRepository {

  fun createVideoMediaStoreOutput(
    context: Context,
    location: Location?,
  ): MediaStoreOutputOptions {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "GeoCam_VID_$timestamp.mp4"
    val contentValues =
      ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/GeoCam")
        }
      }
    return MediaStoreOutputOptions.Builder(
      context.contentResolver,
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    )
      .setContentValues(contentValues)
      .apply {
        if (location != null) setLocation(location)
      }
      .build()
  }

  fun createVideoFileOutput(
    context: Context,
    location: Location?,
  ): FileOutputOptions {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir =
      File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GeoCam").apply {
        mkdirs()
      }
    val outFile = File(dir, "GeoCam_VID_$timestamp.mp4")
    return FileOutputOptions.Builder(outFile)
      .apply {
        if (location != null) setLocation(location)
      }
      .build()
  }

  suspend fun loadCapturedMedia(context: Context): List<CapturedMediaItem> =
    withContext(Dispatchers.IO) {
      val items = mutableListOf<CapturedMediaItem>()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        items += queryMediaStoreImages(context)
        items += queryMediaStoreVideos(context)
      } else {
        items += scanAppExternalImages(context)
        items += scanAppExternalVideos(context)
      }
      items.sortedByDescending { it.dateAddedMillis }
    }

  suspend fun deleteMediaItem(context: Context, item: CapturedMediaItem): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        if (item.uri.authority == "${context.packageName}.fileprovider") {
          val fileName = item.displayName
          val subDir =
            if (item.type == MediaType.PHOTO) Environment.DIRECTORY_PICTURES
            else Environment.DIRECTORY_MOVIES
          val file = File(File(context.getExternalFilesDir(subDir), "GeoCam"), fileName)
          file.exists() && file.delete()
        } else {
          context.contentResolver.delete(item.uri, null, null) > 0
        }
      }.getOrDefault(false)
    }

  fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
      String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }

  private fun queryMediaStoreImages(context: Context): List<CapturedMediaItem> {
    val results = mutableListOf<CapturedMediaItem>()
    val projection =
      arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
      )
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%GeoCam%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    runCatching {
      context.contentResolver
        .query(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          selectionArgs,
          sortOrder,
        )
        ?.use { cursor ->
          val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
          val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
          val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
          val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
          val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
          while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "GeoCam_$id.jpg"
            val addedSec = cursor.getLong(addedCol).takeIf { it > 0L } ?: cursor.getLong(modCol)
            val size = cursor.getLong(sizeCol)
            val uri =
              ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            results +=
              CapturedMediaItem(
                id = id,
                uri = uri,
                displayName = name,
                type = MediaType.PHOTO,
                dateAddedMillis = addedSec * 1000L,
                sizeBytes = size,
              )
          }
        }
    }
    return results
  }

  private fun queryMediaStoreVideos(context: Context): List<CapturedMediaItem> {
    val results = mutableListOf<CapturedMediaItem>()
    val projection =
      arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
      )
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%GeoCam%")
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    runCatching {
      context.contentResolver
        .query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          selectionArgs,
          sortOrder,
        )
        ?.use { cursor ->
          val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
          val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
          val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
          val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
          val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
          val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
          while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "GeoCam_VID_$id.mp4"
            val addedSec = cursor.getLong(addedCol).takeIf { it > 0L } ?: cursor.getLong(modCol)
            val duration = cursor.getLong(durCol)
            val size = cursor.getLong(sizeCol)
            val uri =
              ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            results +=
              CapturedMediaItem(
                id = id,
                uri = uri,
                displayName = name,
                type = MediaType.VIDEO,
                dateAddedMillis = addedSec * 1000L,
                durationMillis = duration,
                sizeBytes = size,
              )
          }
        }
    }
    return results
  }

  private fun scanAppExternalImages(context: Context): List<CapturedMediaItem> {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeoCam")
    val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg") }
      ?: return emptyList()
    return files.map { file ->
      val uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      CapturedMediaItem(
        id = file.name.hashCode().toLong(),
        uri = uri,
        displayName = file.name,
        type = MediaType.PHOTO,
        dateAddedMillis = file.lastModified(),
        sizeBytes = file.length(),
      )
    }
  }

  private fun scanAppExternalVideos(context: Context): List<CapturedMediaItem> {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GeoCam")
    val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() == "mp4" }
      ?: return emptyList()
    return files.map { file ->
      val uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val duration = readVideoDuration(file.absolutePath)
      CapturedMediaItem(
        id = file.name.hashCode().toLong(),
        uri = uri,
        displayName = file.name,
        type = MediaType.VIDEO,
        dateAddedMillis = file.lastModified(),
        durationMillis = duration,
        sizeBytes = file.length(),
      )
    }
  }

  private fun readVideoDuration(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return runCatching {
      retriever.setDataSource(path)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }.getOrDefault(0L).also {
      runCatching { retriever.release() }
    }
  }
}
