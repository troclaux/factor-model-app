package com.buddy.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.budget.i18n.AppLanguage
import com.buddy.budget.portfolio.PortfolioViewModel
import com.buddy.budget.portfolio.ui.PortfolioScreen
import com.buddy.budget.ui.BudgetScreen
import com.buddy.budget.ui.theme.BuddyTheme
import com.buddy.budget.viewmodel.BudgetViewModel
import com.buddy.budget.i18n.tr

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.load(this)
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                var selectedScreen by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedScreen == 0,
                                onClick = { selectedScreen = 0 },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                label = { Text(tr("Budget")) }
                            )
                            NavigationBarItem(
                                selected = selectedScreen == 1,
                                onClick = { selectedScreen = 1 },
                                icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                                label = { Text(tr("Portfolio")) }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        if (selectedScreen == 0) {
                            val viewModel: BudgetViewModel = viewModel()
                            BudgetScreen(viewModel = viewModel)
                        } else {
                            val viewModel: PortfolioViewModel = viewModel()
                            PortfolioScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
