package com.ismartcoding.plain.features.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.ismartcoding.lib.content.ContentWhere
import com.ismartcoding.lib.extensions.find
import com.ismartcoding.lib.extensions.forEach
import com.ismartcoding.lib.extensions.getPagingCursor
import com.ismartcoding.lib.extensions.getSearchCursor
import com.ismartcoding.lib.extensions.getLongValue
import com.ismartcoding.lib.extensions.getStringValue
import com.ismartcoding.lib.pinyin.Pinyin
import com.ismartcoding.plain.data.DMediaBucket
import com.ismartcoding.lib.extensions.map
import com.ismartcoding.lib.extensions.queryCursor
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.TagRelationStub
import com.ismartcoding.plain.extensions.normalizeComparison
import com.ismartcoding.plain.extensions.parseSizeToBytes
import com.ismartcoding.plain.extensions.toFile
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.helpers.QueryHelper

object DocsHelper : BaseContentHelper() {
    private val extraDocumentMimeTypes = arrayListOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/javascript"
    )

    override val uriExternal: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override fun getProjection(): Array<String> {
        val cols = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cols.add(MediaStore.MediaColumns.BUCKET_ID)
        }
        return cols.toTypedArray()
    }

    override suspend fun buildWhereAsync(query: String): ContentWhere {
        val where = ContentWhere()

        // Base filter: doc MIME types and non-empty files
        val mimeTypePlaceholders = extraDocumentMimeTypes.joinToString(",") { "?" }
        where.add("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} IN ($mimeTypePlaceholders))")
        where.args.add("text/%")
        where.args.addAll(extraDocumentMimeTypes)
        where.addGt(MediaStore.Files.FileColumns.SIZE, "0")

        var showHidden = false
        if (query.isNotEmpty()) {
            QueryHelper.parseAsync(query).forEach {
                when (it.name) {
                    "text" -> where.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?", "%${it.value}%")
                    "ext" -> where.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?", "%.${it.value}")
                    "parent" -> where.add("${MediaStore.Files.FileColumns.PARENT} = ?", getIdByPathAsync(MainApp.instance, it.value) ?: "-1")
                    "type" -> where.add("${MediaStore.Files.FileColumns.MIME_TYPE} = ?", it.value)
                    "show_hidden" -> showHidden = it.value.toBoolean()
                    "file_size" -> {
                        val (rawOp, rawValue) = it.normalizeComparison(defaultOp = "=")
                        val bytes = rawValue.parseSizeToBytes() ?: return@forEach
                        val op = when (rawOp) {
                            ">", ">=", "<", "<=", "!=", "=" -> rawOp
                            else -> "="
                        }
                        where.add("${MediaStore.Files.FileColumns.SIZE} $op ?", bytes.toString())
                    }
                    "ids" -> {
                        where.addIn(MediaStore.Files.FileColumns._ID, it.value.split(","))
                    }
                    "bucket_id" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        where.addEqual(MediaStore.MediaColumns.BUCKET_ID, it.value)
                    }
                    "trash" -> where.trash = it.value.toBooleanStrictOrNull()
                }
            }
        }

        if (!showHidden) {
            where.addNotStartsWith(MediaStore.Files.FileColumns.DISPLAY_NAME, ".")
        }
        return where
    }

    suspend fun searchAsync(
        context: Context,
        query: String,
        limit: Int,
        offset: Int,
        sortBy: FileSortBy,
    ): List<DFile> {
        return context.contentResolver.getPagingCursor(
            uriExternal, getProjection(), buildWhereAsync(query),
            limit, offset, sortBy.toFileSortBy()
        )?.map { cursor, cache ->
            cursor.toFile(cache)
        } ?: emptyList()
    }

    suspend fun getTagRelationStubsAsync(
        context: Context,
        query: String,
    ): List<TagRelationStub> {
        return context.contentResolver.getSearchCursor(uriExternal, getProjection(), buildWhereAsync(query))?.map { cursor, cache ->
            val id = cursor.getStringValue(MediaStore.Files.FileColumns._ID, cache)
            val title = cursor.getStringValue(MediaStore.Files.FileColumns.DISPLAY_NAME, cache)
            val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE, cache)
            TagRelationStub(id, title, size)
        } ?: emptyList()
    }

    suspend fun getDocExtGroupsAsync(context: Context, query: String = ""): List<Pair<String, Int>> {
        val where = buildWhereAsync(query)
        val extCounts = mutableMapOf<String, Int>()
        context.contentResolver.queryCursor(
            uriExternal,
            arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME),
            where.toSelection(),
            where.args.toTypedArray()
        )?.forEach { cursor, cache ->
            val name = cursor.getStringValue(MediaStore.Files.FileColumns.DISPLAY_NAME, cache)
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                extCounts[ext] = extCounts.getOrDefault(ext, 0) + 1
            }
        }
        return extCounts.map { Pair(it.key.uppercase(), it.value) }.sortedBy { it.first }
    }

    private fun getIdByPathAsync(context: Context, path: String): String? {
        return context.contentResolver
            .queryCursor(uriExternal, arrayOf(MediaStore.Files.FileColumns._ID), "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(path))?.find { cursor, cache ->
                cursor.getStringValue(MediaStore.Files.FileColumns._ID, cache)
            }
    }

    fun getBucketsAsync(context: Context): List<DMediaBucket> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val bucketMap = mutableMapOf<String, DMediaBucket>()
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA,
        )
        val mimeTypePlaceholders = extraDocumentMimeTypes.joinToString(",") { "?" }
        val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} IN ($mimeTypePlaceholders)) AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} != ''"
        val selectionArgs = (listOf("text/%") + extraDocumentMimeTypes).toTypedArray()
        context.contentResolver.query(uriExternal, projection, selection, selectionArgs, null)?.forEach { cursor, cache ->
            val bucketId = cursor.getStringValue(MediaStore.MediaColumns.BUCKET_ID, cache)
            val bucketName = cursor.getStringValue(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, cache)
            val size = cursor.getLongValue(MediaStore.MediaColumns.SIZE, cache)
            val path = cursor.getStringValue(MediaStore.MediaColumns.DATA, cache)
            val bucket = bucketMap[bucketId]
            if (bucket != null) {
                if (bucket.topItems.size < 4) bucket.topItems.add(path)
                bucket.size += size
                bucket.itemCount++
            } else {
                bucketMap[bucketId] = DMediaBucket(bucketId, bucketName, 1, size, mutableListOf(path))
            }
        }
        return bucketMap.values.sortedBy { Pinyin.toPinyin(it.name).lowercase() }
    }

    fun getItemUri(id: String): Uri = Uri.withAppendedPath(uriExternal, id)

    suspend fun getTrashedIdsAsync(context: Context, query: String): Set<String> {
        val where = buildWhereAsync(query)
        where.trash = true
        return context.contentResolver.getSearchCursor(uriExternal, getProjection(), where)?.map { cursor, cache ->
            cursor.getStringValue(MediaStore.Files.FileColumns._ID, cache)
        }?.toSet() ?: emptySet()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun trashByIdsAsync(context: Context, ids: Set<String>) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 1)
        }
        ids.forEach { id ->
            try {
                context.contentResolver.update(getItemUri(id), contentValues, null, null)
            } catch (ex: Exception) {
                LogCat.w("Failed to trash doc id=$id: ${ex.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun restoreByIdsAsync(context: Context, ids: Set<String>) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 0)
        }
        ids.forEach { id ->
            try {
                context.contentResolver.update(getItemUri(id), contentValues, null, null)
            } catch (ex: Exception) {
                LogCat.w("Failed to restore doc id=$id: ${ex.message}")
            }
        }
    }
}
