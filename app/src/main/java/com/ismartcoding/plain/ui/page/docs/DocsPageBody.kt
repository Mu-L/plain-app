package com.ismartcoding.plain.ui.page.docs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.R
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.ui.base.NeedPermissionColumn
import com.ismartcoding.plain.ui.base.PFilterChip
import com.ismartcoding.plain.ui.base.PScrollableTabRow
import com.ismartcoding.plain.ui.base.pullrefresh.RefreshLayoutState
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.TagsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocsPageBody(
    navController: NavHostController,
    docsVM: DocsViewModel,
    tagsVM: TagsViewModel,
    filteredItemsState: List<DFile>,
    docsTagsMap: Map<String, List<DTag>>,
    hasPermission: Boolean,
    scrollStateMap: MutableMap<Int, LazyListState>,
    pagerState: PagerState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    topRefreshLayoutState: RefreshLayoutState,
    scope: CoroutineScope,
    context: android.content.Context,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    Column(modifier = Modifier.padding(top = topPadding)) {
        if (!hasPermission) {
            NeedPermissionColumn(R.drawable.file_text, AppFeatureType.FILES.getPermission()!!)
            return@Column
        }
        if (!docsVM.selectMode.value) {
            PScrollableTabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier.fillMaxWidth()) {
                docsVM.tabs.value.forEachIndexed { index, tab ->
                    PFilterChip(
                        modifier = Modifier.padding(start = if (index == 0) 0.dp else 8.dp),
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.scrollToPage(index) } },
                        label = { Text(text = "${tab.title} (${tab.count})") },
                    )
                }
            }
        }
        DocsPageContent(
            navController = navController,
            docsVM = docsVM,
            tagsVM = tagsVM,
            filteredItemsState = filteredItemsState,
            docsTagsMap = docsTagsMap,
            scrollStateMap = scrollStateMap,
            pagerState = pagerState,
            scrollBehavior = scrollBehavior,
            topRefreshLayoutState = topRefreshLayoutState,
            scope = scope,
            context = context,
            bottomPadding = bottomPadding,
        )
    }
}
