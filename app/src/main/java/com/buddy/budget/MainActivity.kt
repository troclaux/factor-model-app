package com.buddy.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.budget.ui.BudgetScreen
import com.buddy.budget.ui.theme.BuddyTheme
import com.buddy.budget.viewmodel.BudgetViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                val viewModel: BudgetViewModel = viewModel()
                BudgetScreen(viewModel = viewModel)
            }
        }
    }
}
