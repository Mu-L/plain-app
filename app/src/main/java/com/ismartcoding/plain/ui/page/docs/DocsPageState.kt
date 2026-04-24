package com.ismartcoding.plain.ui.page.docs

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.enums.DataType
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.db.DTagRelation
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.MediaFoldersViewModel
import com.ismartcoding.plain.ui.models.TagsViewModel

@OptIn(ExperimentalMaterial3Api::class)
data class DocsPageState(
    val pagerState: PagerState,
    val itemsState: List<DFile>,
    val filteredItemsState: List<DFile>,
    val tagsState: List<DTag>,
    val tagsMapState: Map<String, List<DTagRelation>>,
    val scrollStateMap: MutableMap<Int, LazyListState>,
    val scrollBehavior: TopAppBarScrollBehavior,
    val hasPermission: androidx.compose.runtime.MutableState<Boolean>,
) {
    companion object {
        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun create(
            docsVM: DocsViewModel,
            tagsVM: TagsViewModel,
            mediaFoldersVM: MediaFoldersViewModel,
        ): DocsPageState {
            LaunchedEffect(Unit) {
                tagsVM.dataType.value = DataType.DOC
                mediaFoldersVM.dataType.value = DataType.DOC
            }

            val itemsState by docsVM.itemsFlow.collectAsState()
            val tagsState by tagsVM.itemsFlow.collectAsState()
            val tagsMapState by tagsVM.tagsMapFlow.collectAsState()
            val filteredItemsState by remember(itemsState, docsVM.fileType.value) {
                derivedStateOf {
                    itemsState.filter {
                        docsVM.fileType.value.isEmpty() || it.extension == docsVM.fileType.value
                    }
                }
            }
            val scrollStateMap = remember { mutableStateMapOf<Int, LazyListState>() }
            val pagerState = rememberPagerState(pageCount = { docsVM.tabs.value.size })
            val context = LocalContext.current
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
                canScroll = {
                    (scrollStateMap[pagerState.currentPage]?.firstVisibleItemIndex ?: 0) > 0 && !docsVM.selectMode.value
                }
            )
            val hasPermission = remember {
                mutableStateOf(AppFeatureType.FILES.hasPermission(context))
            }

            return DocsPageState(
                pagerState = pagerState,
                itemsState = itemsState,
                filteredItemsState = filteredItemsState,
                tagsState = tagsState,
                tagsMapState = tagsMapState,
                scrollStateMap = scrollStateMap,
                scrollBehavior = scrollBehavior,
                hasPermission = hasPermission,
            )
        }
    }
}
