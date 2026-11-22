package com.buddy.budget.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.budget.data.Backup
import com.buddy.budget.i18n.AppLanguage
import com.buddy.budget.i18n.Language
import com.buddy.budget.data.BackupManager
import com.buddy.budget.portfolio.PortfolioViewModel
import com.buddy.budget.portfolio.ui.shareFile
import com.buddy.budget.portfolio.ui.sharedFile
import com.buddy.budget.viewmodel.BudgetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.buddy.budget.i18n.tr

/** Top-bar menu to back up all app data to a file and restore it. */
@Composable
fun BackupMenu(tint: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Activity-scoped, so these are the same view models the tabs use.
    val budgetViewModel: BudgetViewModel = viewModel()
    val portfolioViewModel: PortfolioViewModel = viewModel()
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Backup?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val backup = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    Backup.fromJson(json)
                }
            }
            backup.onSuccess { pending = it }
                .onFailure { Toast.makeText(context, it.message ?: tr("Could not read the backup."), Toast.LENGTH_LONG).show() }
        }
    }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = tr("More"), tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("Back up all data")) },
                onClick = {
                    open = false
                    scope.launch {
                        val file = sharedFile(context, "buddy_backup_${LocalDate.now()}.json")
                        withContext(Dispatchers.IO) { file.writeText(BackupManager(context).create().toJson()) }
                        shareFile(context, file, "application/json", tr("Save backup"))
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Restore from backup")) },
                onClick = {
                    open = false
                    picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                },
            )
            // Shows the current language; tapping switches to the other one.
            DropdownMenuItem(
                text = { Text(tr("Language: %s").format(AppLanguage.current.label)) },
                onClick = {
                    open = false
                    AppLanguage.change(context, if (AppLanguage.current == Language.EN) Language.PT else Language.EN)
                },
            )
        }
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("Restore this backup?")) },
            text = {
                Text(
                    tr("From %s: %d saved month(s). ").format(backup.exportedAt.replace("T", " ").ifEmpty { tr("an unknown date") }, backup.budgetHistory.size) +
                        tr("Your budget defaults, history, this month's plan and portfolio settings are replaced. ") +
                        tr("The Pluggy secret on this phone is kept (backups never include it)."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        withContext(Dispatchers.IO) { BackupManager(context).restore(backup) }
                        budgetViewModel.reload()
                        portfolioViewModel.reloadSettings()
                        Toast.makeText(context, tr("Backup restored."), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Restore")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("Cancel")) } },
        )
    }
}
