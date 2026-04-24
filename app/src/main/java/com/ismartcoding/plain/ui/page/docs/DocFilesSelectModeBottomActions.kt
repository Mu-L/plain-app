package com.ismartcoding.plain.ui.page.docs

import android.os.Build
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ismartcoding.lib.isRPlus
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.enums.AppFeatureType
import com.ismartcoding.plain.helpers.ShareHelper
import com.ismartcoding.plain.ui.base.BottomActionButtons
import com.ismartcoding.plain.ui.base.IconTextSmallButtonDelete
import com.ismartcoding.plain.ui.base.IconTextSmallButtonLabel
import com.ismartcoding.plain.ui.base.IconTextSmallButtonLabelOff
import com.ismartcoding.plain.ui.base.IconTextSmallButtonRestore
import com.ismartcoding.plain.ui.base.IconTextSmallButtonShare
import com.ismartcoding.plain.ui.base.IconTextSmallButtonTrash
import com.ismartcoding.plain.ui.base.PBottomAppBar
import com.ismartcoding.plain.ui.helpers.DialogHelper
import com.ismartcoding.plain.ui.models.DocsViewModel
import com.ismartcoding.plain.ui.models.TagsViewModel
import com.ismartcoding.plain.ui.models.exitSelectMode
import com.ismartcoding.plain.ui.page.tags.BatchSelectTagsDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocFilesSelectModeBottomActions(
    docsVM: DocsViewModel,
    tagsVM: TagsViewModel,
) {
    val context = LocalContext.current
    var showSelectTagsDialog by remember { mutableStateOf(false) }
    var removeFromTags by remember { mutableStateOf(false) }
    val tagsState = tagsVM.itemsFlow.value

    if (showSelectTagsDialog) {
        val selectedItems = docsVM.itemsFlow.value.filter { docsVM.selectedIds.contains(it.id) }
        BatchSelectTagsDialog(tagsVM, tagsState, selectedItems, removeFromTags) {
            showSelectTagsDialog = false
            docsVM.exitSelectMode()
        }
    }

    PBottomAppBar {
        BottomActionButtons {
            if (!docsVM.trash.value) {
                IconTextSmallButtonLabel {
                    showSelectTagsDialog = true
                    removeFromTags = false
                }
                IconTextSmallButtonLabelOff {
                    showSelectTagsDialog = true
                    removeFromTags = true
                }
                IconTextSmallButtonShare {
                    ShareHelper.sharePaths(context, docsVM.selectedIds.toSet())
                }
            }
            if (AppFeatureType.MEDIA_TRASH.has()) {
                if (docsVM.trash.value) {
                    IconTextSmallButtonRestore {
                        if (isRPlus()) {
                            docsVM.restore(context, docsVM.selectedIds.toSet())
                            docsVM.exitSelectMode()
                        }
                    }
                    IconTextSmallButtonDelete {
                        DialogHelper.confirmToDelete {
                            docsVM.delete(context, docsVM.selectedIds.toSet())
                            docsVM.exitSelectMode()
                        }
                    }
                } else {
                    IconTextSmallButtonTrash {
                        if (isRPlus()) {
                            docsVM.trash(context, docsVM.selectedIds.toSet())
                            docsVM.exitSelectMode()
                        }
                    }
                }
            } else {
                IconTextSmallButtonDelete {
                    DialogHelper.confirmToDelete {
                        docsVM.delete(context, docsVM.selectedIds.toSet())
                        docsVM.exitSelectMode()
                    }
                }
            }
        }
    }
}
