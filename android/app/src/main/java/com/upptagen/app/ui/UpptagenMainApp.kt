package com.upptagen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.ui.screens.EntriesScreen
import com.upptagen.app.ui.screens.ExportImportScreen
import com.upptagen.app.ui.screens.TagsScreen
import com.upptagen.app.ui.screens.TimerScreen
import com.upptagen.app.ui.viewmodel.EntriesViewModel
import com.upptagen.app.ui.viewmodel.TagsViewModel
import com.upptagen.app.ui.viewmodel.TimerViewModel

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TIMER("Timer", Icons.Filled.Timer, Icons.Outlined.Timer),
    ENTRIES("Entries", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
    TAGS("Tags", Icons.Filled.Label, Icons.Outlined.Label),
    EXPORT("Export & Sync", Icons.Filled.SyncAlt, Icons.Outlined.SyncAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpptagenMainApp(repository: TimeTrackingRepository) {
    val timerViewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory(repository))
    val entriesViewModel: EntriesViewModel = viewModel(factory = EntriesViewModel.Factory(repository))
    val tagsViewModel: TagsViewModel = viewModel(factory = TagsViewModel.Factory(repository))

    val timerState by timerViewModel.uiState.collectAsState()
    val entriesState by entriesViewModel.uiState.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.TIMER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "upptagen",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        if (timerState.activeTimer != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == NavigationTab.TIMER && timerState.activeTimer != null) {
                                        Badge(containerColor = Color(0xFF10B981))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.TIMER -> TimerScreen(
                    viewModel = timerViewModel,
                    availableProjects = entriesState.availableProjects,
                    availableTags = entriesState.availableTags
                )
                NavigationTab.ENTRIES -> EntriesScreen(
                    viewModel = entriesViewModel
                )
                NavigationTab.TAGS -> TagsScreen(
                    viewModel = tagsViewModel
                )
                NavigationTab.EXPORT -> ExportImportScreen(
                    repository = repository
                )
            }
        }
    }
}
