package com.ismartcoding.plain.ui.page.docs

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.lib.isQPlus
import com.ismartcoding.plain.R
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.ui.base.ActionButtonFolders
import com.ismartcoding.plain.ui.base.ActionButtonSearch
import com.ismartcoding.plain.ui.base.HorizontalSpace
import com.ismartcoding.plain.ui.base.NavigationBackIcon
import com.ismartcoding.plain.ui.base.NavigationCloseIcon
import com.ismartcoding.plain.ui.base.PDropdownMenu
import com.ismartcoding.plain.ui.base.PDropdownMenuItem
import com.ismartcoding.plain.ui.base.PIconButton
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.PTopRightButton
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.enterSearchMode
import com.ismartcoding.plain.ui.models.exitSelectMode
import com.ismartcoding.plain.ui.models.isAllSelected
import com.ismartcoding.plain.ui.models.toggleSelectAll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocsTopBar(
    navController: NavHostController,
    docsVM: DocsViewModel,
    hasPermission: Boolean,
    pageTitle: String,
    scrollBehavior: TopAppBarScrollBehavior,
    scrollToTop: () -> Unit,
    onOpenTagsManager: () -> Unit,
    onOpenFolders: () -> Unit,
    onModeSelected: (showTagsMode: Boolean) -> Unit,
    onSortSelected: (FileSortBy) -> Unit,
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    PTopAppBar(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onDoubleClick = scrollToTop,
        ),
        navController = navController,
        navigationIcon = {
            if (docsVM.selectMode.value) {
                NavigationCloseIcon { docsVM.exitSelectMode() }
            } else {
                NavigationBackIcon { navController.navigateUp() }
            }
        },
        title = pageTitle,
        scrollBehavior = scrollBehavior,
        actions = {
            if (!hasPermission) {
                return@PTopAppBar
            }
            if (docsVM.selectMode.value) {
                PTopRightButton(
                    label = stringResource(if (docsVM.isAllSelected()) R.string.unselect_all else R.string.select_all),
                    click = { docsVM.toggleSelectAll() },
                )
                HorizontalSpace(dp = 8.dp)
                return@PTopAppBar
            }

            ActionButtonSearch { docsVM.enterSearchMode() }
            if (isQPlus()) {
                ActionButtonFolders { onOpenFolders() }
            }
            PIconButton(
                icon = R.drawable.tag,
                contentDescription = stringResource(R.string.tags),
                tint = MaterialTheme.colorScheme.onSurface,
                click = onOpenTagsManager,
            )
            PIconButton(
                icon = R.drawable.sort,
                contentDescription = stringResource(R.string.sort),
                tint = MaterialTheme.colorScheme.onSurface,
                click = { isSortMenuOpen = true },
            )
            PDropdownMenu(
                expanded = isSortMenuOpen,
                onDismissRequest = { isSortMenuOpen = false },
            ) {
                PDropdownMenuItem(
                    text = { Text(stringResource(R.string.tags)) },
                    trailingIcon = if (docsVM.tabsShowTags.value) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        isSortMenuOpen = false
                        onModeSelected(true)
                    },
                )
                PDropdownMenuItem(
                    text = { Text(stringResource(R.string.type)) },
                    trailingIcon = if (!docsVM.tabsShowTags.value) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        isSortMenuOpen = false
                        onModeSelected(false)
                    },
                )
                FileSortBy.entries.forEach { sortByOption ->
                    if (sortByOption == FileSortBy.TAKEN_AT_DESC) {
                        return@forEach
                    }
                    PDropdownMenuItem(
                        text = { Text(stringResource(sortByOption.getTextId())) },
                        trailingIcon = if (docsVM.sortBy.value == sortByOption) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            isSortMenuOpen = false
                            onSortSelected(sortByOption)
                        },
                    )
                }
            }
        },
    )
}
