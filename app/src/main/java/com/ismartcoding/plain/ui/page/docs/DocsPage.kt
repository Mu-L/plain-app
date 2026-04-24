package com.ismartcoding.plain.ui.page.docs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.plain.R
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.preferences.DocSortByPreference
import com.ismartcoding.plain.preferences.DocTabsModePreference
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.pullrefresh.RefreshContentState
import com.ismartcoding.plain.ui.base.pullrefresh.rememberRefreshLayoutState
import com.ismartcoding.plain.ui.base.pullrefresh.setRefreshState
import com.ismartcoding.plain.ui.components.ListSearchBar
import com.ismartcoding.plain.ui.extensions.reset
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.MediaFoldersViewModel
import com.ismartcoding.plain.ui.models.TagsViewModel
import com.ismartcoding.plain.ui.models.exitSearchMode
import com.ismartcoding.plain.ui.models.showBottomActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocsPage(
    navController: NavHostController,
    docsVM: DocsViewModel = viewModel(),
    tagsVM: TagsViewModel = viewModel(key = "docTagsVM"),
    mediaFoldersVM: MediaFoldersViewModel = viewModel(key = "docFoldersVM"),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docsState = DocsPageState.create(docsVM, tagsVM, mediaFoldersVM)
    val pagerState = docsState.pagerState
    val scrollBehavior = docsState.scrollBehavior
    val hasPermission = docsState.hasPermission
    val tagsState = docsState.tagsState
    val tagsMapState = docsState.tagsMapState
    val bucketsMap by mediaFoldersVM.bucketsMapFlow.collectAsState()
    var isFirstPageEffect by remember { mutableStateOf(true) }
    val docsTagsMap = remember(tagsMapState, tagsState) {
        tagsMapState.mapValues { entry ->
            entry.value.mapNotNull { relation -> tagsState.find { it.id == relation.tagId } }
        }
    }

    val topRefreshLayoutState = rememberRefreshLayoutState {
        scope.launch {
            withIO { docsVM.loadAsync(context, tagsVM) }
            setRefreshState(RefreshContentState.Finished)
        }
    }

    BackHandler(enabled = docsVM.selectMode.value || docsVM.showSearchBar.value) {
        if (docsVM.selectMode.value) {
            docsVM.selectMode.value = false
            docsVM.selectedIds.clear()
        } else if (docsVM.showSearchBar.value && (!docsVM.searchActive.value || docsVM.queryText.value.isEmpty())) {
            docsVM.exitSearchMode()
            docsVM.showLoading.value = true
            scope.launch(Dispatchers.IO) { docsVM.loadAsync(context, tagsVM) }
        }
    }

    DocsPageEffects(docsState, docsVM, tagsVM, mediaFoldersVM)

    LaunchedEffect(pagerState.currentPage) {
        if (isFirstPageEffect) {
            isFirstPageEffect = false
            return@LaunchedEffect
        }
        val tab = docsVM.tabs.value.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (docsVM.tabsShowTags.value) {
            when (tab.value) {
                "all" -> {
                    docsVM.trash.value = false
                    docsVM.tag.value = null
                }

                "trash" -> {
                    docsVM.trash.value = true
                    docsVM.tag.value = null
                }

                else -> {
                    docsVM.trash.value = false
                    docsVM.tag.value = tagsVM.itemsFlow.value.find { it.id == tab.value }
                }
            }
        } else {
            when (tab.value) {
                "" -> {
                    docsVM.trash.value = false
                    docsVM.fileType.value = ""
                }

                "trash" -> {
                    docsVM.trash.value = true
                    docsVM.fileType.value = ""
                }

                else -> {
                    docsVM.trash.value = false
                    docsVM.fileType.value = tab.value
                }
            }
        }
        scope.launch {
            scrollBehavior.reset()
            docsState.scrollStateMap[pagerState.currentPage]?.scrollToItem(0)
        }
        scope.launch(Dispatchers.IO) { docsVM.loadAsync(context, tagsVM) }
    }

    DocsPageOverlays(docsVM, tagsVM, mediaFoldersVM)
    val pageTitle = docsPageTitle(docsVM, bucketsMap[docsVM.bucketId.value]?.name)

    PScaffold(
        topBar = {
            if (docsVM.showSearchBar.value) {
                ListSearchBar(viewModel = docsVM) {
                    docsVM.searchActive.value = false
                    docsVM.showLoading.value = true
                    scope.launch(Dispatchers.IO) { docsVM.loadAsync(context, tagsVM) }
                }
                return@PScaffold
            }
            DocsTopBar(
                navController = navController,
                docsVM = docsVM,
                hasPermission = hasPermission.value,
                pageTitle = pageTitle,
                scrollBehavior = scrollBehavior,
                scrollToTop = { scope.launch { docsState.scrollStateMap[pagerState.currentPage]?.scrollToItem(0) } },
                onOpenTagsManager = { docsVM.showTagsDialog.value = true },
                onOpenFolders = { docsVM.showFoldersDialog.value = true },
                onModeSelected = { showTagsMode ->
                    if (docsVM.tabsShowTags.value == showTagsMode) {
                        return@DocsTopBar
                    }
                    scope.launch(Dispatchers.IO) {
                        DocTabsModePreference.putAsync(context, showTagsMode)
                        docsVM.tabsShowTags.value = showTagsMode
                        docsVM.fileType.value = ""
                        docsVM.tag.value = null
                        docsVM.trash.value = false
                        docsVM.loadAsync(context, tagsVM)
                    }
                    scope.launch { pagerState.scrollToPage(0) }
                },
                onSortSelected = { sortBy ->
                    scope.launch(Dispatchers.IO) {
                        DocSortByPreference.putAsync(context, sortBy)
                        docsVM.sortBy.value = sortBy
                        docsVM.loadAsync(context, tagsVM)
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = docsVM.showBottomActions(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                DocFilesSelectModeBottomActions(docsVM, tagsVM)
            }
        },
    ) { paddingValues ->
        DocsPageBody(
            navController = navController,
            docsVM = docsVM,
            tagsVM = tagsVM,
            filteredItemsState = docsState.filteredItemsState,
            docsTagsMap = docsTagsMap,
            hasPermission = hasPermission.value,
            scrollStateMap = docsState.scrollStateMap,
            pagerState = pagerState,
            scrollBehavior = scrollBehavior,
            topRefreshLayoutState = topRefreshLayoutState,
            scope = scope,
            context = context,
            topPadding = paddingValues.calculateTopPadding(),
            bottomPadding = paddingValues.calculateBottomPadding(),
        )
    }
}
