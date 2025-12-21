package com.steamdeck.mobile.presentation.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.steamdeck.mobile.presentation.ui.home.NavigationDrawerContent
import kotlinx.coroutines.launch

/**
 * SteamDeck Mobile App Main Component - WITH PERSISTENT MINI DRAWER
 *
 * Architecture:
 * - Mini drawer persists across all screens (Home, Settings, Downloads, etc.)
 * - Drawer state managed at app level for consistency
 * - 280dp expanded, 24dp mini sidebar (Steam Big Picture style)
 *
 * Fullscreen mode for gaming/controller experience
 * No bottom navigation - maximizes screen space
 *
 * NOTE: Winlator initialization is now handled on-demand when launching games,
 * not on app startup
 */
@Composable
fun SteamDeckApp() {
 val navController = rememberNavController()
 val navBackStackEntry by navController.currentBackStackEntryAsState()
 val currentRoute = navBackStackEntry?.destination?.route ?: "home"
 val scope = rememberCoroutineScope()

 // Mini drawer width state (280.dp expanded, 24.dp mini sidebar)
 var drawerWidth by remember { mutableStateOf(24.dp) }
 val isDrawerExpanded = drawerWidth == 280.dp

 // Drawer actions (Steam Big Picture style)
 val openDrawer: () -> Unit = {
  scope.launch {
   drawerWidth = 280.dp // Expand to full width
  }
 }
 val closeDrawer: () -> Unit = {
  scope.launch {
   drawerWidth = 24.dp // Collapse to mini sidebar
  }
 }

 // Auto-collapse drawer after navigation
 LaunchedEffect(currentRoute) {
  if (drawerWidth == 280.dp) {
   drawerWidth = 24.dp // Collapse to mini sidebar after navigation
  }
 }

 Surface(
  modifier = Modifier.fillMaxSize(),
  color = MaterialTheme.colorScheme.background
 ) {
  Row(modifier = Modifier.fillMaxSize()) {
   // Persistent mini sidebar (always visible, Steam Big Picture style)
   Surface(
    modifier = Modifier
     .fillMaxHeight()
     .width(drawerWidth)
     .animateContentSize(
      animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
     ),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 3.dp
   ) {
     NavigationDrawerContent(
      onNavigateToHome = {
       closeDrawer()
       navController.navigate("home") {
        popUpTo("home") { inclusive = true }
       }
      },
      onNavigateToDownloads = {
       closeDrawer()
       navController.navigate(Screen.Downloads.route)
      },
      onNavigateToSteamLogin = {
       closeDrawer()
       navController.navigate(Screen.SteamLogin.route)
      },
      onNavigateToSyncLibrary = {
       closeDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 2))
      },
      onNavigateToSteamClient = {
       closeDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 1))
      },
      onNavigateToController = {
       closeDrawer()
       navController.navigate(Screen.ControllerSettings.route)
      },
      onNavigateToContainerManagement = {
       closeDrawer()
       navController.navigate(Screen.ContainerManagement.route)
      },
      onNavigateToWineTest = {
       closeDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 4))
      },
      onNavigateToAppSettings = {
       closeDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 5))
      },
      onAddGame = {
       // Navigate to home and show add game dialog
       closeDrawer()
       navController.navigate(Screen.Home.createRoute(showAddGame = true)) {
        popUpTo("home") { inclusive = true }
       }
      },
      currentRoute = currentRoute,
      isCollapsed = !isDrawerExpanded,
      onExpandDrawer = openDrawer,
      onCloseDrawer = closeDrawer
     )
    }
   }

   // Main navigation content
   SteamDeckNavHost(
    navController = navController,
    onOpenDrawer = openDrawer,
    modifier = Modifier.fillMaxSize()
   )
  }
 }
}
