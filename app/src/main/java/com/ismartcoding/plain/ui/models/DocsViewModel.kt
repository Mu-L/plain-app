package com.ismartcoding.plain.ui.models

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import com.ismartcoding.lib.extensions.scanFileByConnection
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.R
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.features.TagHelper
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.features.locale.LocaleHelper.getString
import com.ismartcoding.plain.features.media.DocsHelper
import com.ismartcoding.plain.ui.helpers.DialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@OptIn(SavedStateHandleSaveableApi::class)
class DocsViewModel(private val savedStateHandle: SavedStateHandle) :
    ISelectableViewModel<DFile>,
    ISearchableViewModel<DFile>,
    ViewModel() {
    private val _itemsFlow = MutableStateFlow(mutableStateListOf<DFile>())
    override val itemsFlow: StateFlow<List<DFile>> get() = _itemsFlow
    val showLoading = mutableStateOf(true)
    val offset = mutableIntStateOf(0)
    val limit = mutableIntStateOf(1000)
    val noMore = mutableStateOf(false)
    var total = mutableIntStateOf(0)
    var totalTrash = mutableIntStateOf(0)
    val trash = mutableStateOf(false)
    val sortBy = mutableStateOf(FileSortBy.DATE_DESC)
    val selectedItem = mutableStateOf<DFile?>(null)
    val showRenameDialog = mutableStateOf(false)
    val showSortDialog = mutableStateOf(false)
    val fileType = mutableStateOf("")
    var bucketId = mutableStateOf("")
    var tag = mutableStateOf<DTag?>(null)
    var tabsShowTags = mutableStateOf(false)
    val showFoldersDialog = mutableStateOf(false)
    val showTagsDialog = mutableStateOf(false)
    var tabs = mutableStateOf(listOf<VTabData>())

    override val showSearchBar = mutableStateOf(false)
    override val searchActive = mutableStateOf(false)
    override val queryText = mutableStateOf("")

    override var selectMode = mutableStateOf(false)
    override val selectedIds = mutableStateListOf<String>()

    suspend fun moreAsync(context: Context, tagsVM: TagsViewModel? = null) {
        val query = buildQuery()
        offset.value += limit.intValue
        val items = DocsHelper.searchAsync(context, query, limit.intValue, offset.intValue, sortBy.value)
        _itemsFlow.value.addAll(items)
        tagsVM?.loadMoreAsync(items.map { it.id }.toSet())
        showLoading.value = false
        noMore.value = items.size < limit.intValue
    }

    suspend fun loadAsync(context: Context, tagsVM: TagsViewModel? = null) {
        val query = buildQuery()
        val tabsQuery = buildQuery(includeExt = false)
        offset.intValue = 0
        val items = DocsHelper.searchAsync(context, query, limit.intValue, 0, sortBy.value)
        _itemsFlow.value = items.toMutableStateList()
        tagsVM?.loadAsync(items.map { it.id }.toSet())
        noMore.value = items.size < limit.intValue
        if (!trash.value) {
            val extGroups = DocsHelper.getDocExtGroupsAsync(context, tabsQuery)
            total.intValue = extGroups.sumOf { it.second }
            if (AppFeatureType.MEDIA_TRASH.has()) {
                totalTrash.intValue = DocsHelper.countAsync(context, "trash:true")
            }
            val trashTabs = if (AppFeatureType.MEDIA_TRASH.has()) listOf(VTabData(getString(R.string.trash), "trash", totalTrash.intValue)) else emptyList()
            if (tabsShowTags.value && tagsVM != null) {
                val tagsState = tagsVM.itemsFlow.value
                tabs.value = listOf(VTabData(getString(R.string.all), "all", total.intValue)) + trashTabs + tagsState.map { VTabData(it.name, it.id, it.count) }
            } else {
                val extensions = extGroups.map { VTabData(it.first, it.first.lowercase(), it.second) }
                tabs.value = listOf(VTabData(getString(R.string.all), "", total.intValue)) + trashTabs + extensions
            }
        }
        showLoading.value = false
    }

    fun delete(context: Context, paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (trash.value) {
                val mediaIds = _itemsFlow.value.filter { paths.contains(it.id) }.map { it.mediaId }.toSet()
                DocsHelper.deleteByIdsAsync(context, mediaIds)
            } else {
                paths.forEach { File(it).deleteRecursively() }
                MainApp.instance.scanFileByConnection(paths.toTypedArray())
            }
            loadAsync(context)
            _itemsFlow.update {
                it.toMutableStateList().apply {
                    removeIf { i -> paths.contains(i.id) }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun trash(context: Context, paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            DialogHelper.showLoading()
            val mediaIds = _itemsFlow.value.filter { paths.contains(it.id) }.map { it.mediaId }.toSet()
            DocsHelper.trashByIdsAsync(context, mediaIds)
            loadAsync(context)
            DialogHelper.hideLoading()
            _itemsFlow.update {
                it.toMutableStateList().apply { removeIf { i -> paths.contains(i.id) } }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun restore(context: Context, paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            DialogHelper.showLoading()
            val mediaIds = _itemsFlow.value.filter { paths.contains(it.id) }.map { it.mediaId }.toSet()
            DocsHelper.restoreByIdsAsync(context, mediaIds)
            loadAsync(context)
            DialogHelper.hideLoading()
            _itemsFlow.update {
                it.toMutableStateList().apply { removeIf { i -> paths.contains(i.id) } }
            }
        }
    }

    private fun buildQuery(includeExt: Boolean = true): String {
        val text = queryText.value.trim()
        val textPart = if (text.isEmpty()) "" else "text:$text"
        val trashPart = "trash:${trash.value}"
        val bucketPart = if (bucketId.value.isNotEmpty()) "bucket_id:${bucketId.value}" else ""
        val tagPart = if (tag.value != null) {
            val tagId = tag.value!!.id
            val ids = TagHelper.getKeysByTagId(tagId)
            if (ids.isNotEmpty()) "ids:${ids.joinToString(",")}" else ""
        } else ""
        val extPart = if (includeExt && !tabsShowTags.value && fileType.value.isNotEmpty()) "ext:${fileType.value}" else ""
        return listOf(textPart, trashPart, bucketPart, tagPart, extPart).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
