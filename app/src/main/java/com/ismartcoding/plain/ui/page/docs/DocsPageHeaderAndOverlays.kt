package com.ismartcoding.plain.ui.page.docs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ismartcoding.plain.R
import com.ismartcoding.plain.features.locale.LocaleHelper
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.MediaFoldersViewModel
import com.ismartcoding.plain.ui.models.TagsViewModel
import com.ismartcoding.plain.ui.page.tags.TagsBottomSheet

@Composable
internal fun DocsPageOverlays(
    docsVM: DocsViewModel,
    tagsVM: TagsViewModel,
    mediaFoldersVM: MediaFoldersViewModel,
) {
    ViewDocBottomSheet(docsVM, tagsVM)
    DocFoldersBottomSheet(docsVM, mediaFoldersVM, tagsVM)
    if (docsVM.showTagsDialog.value) {
        TagsBottomSheet(tagsVM) { docsVM.showTagsDialog.value = false }
    }
}

@Composable
internal fun docsPageTitle(
    docsVM: DocsViewModel,
    folderName: String? = null,
): String {
    return if (docsVM.selectMode.value) {
        LocaleHelper.getStringF(R.string.x_selected, "count", docsVM.selectedIds.size)
    } else if (!folderName.isNullOrEmpty()) {
        folderName
    } else if (docsVM.tag.value != null) {
        stringResource(id = R.string.docs) + " - " + docsVM.tag.value!!.name
    } else if (docsVM.trash.value) {
        stringResource(id = R.string.docs) + " - " + stringResource(id = R.string.trash)
    } else {
        stringResource(id = R.string.docs)
    }
}
