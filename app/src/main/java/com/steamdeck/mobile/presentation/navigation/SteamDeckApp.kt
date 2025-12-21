package com.steamdeck.mobile.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
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
 * - 280dp expanded, 80dp collapsed icon-only mode
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
 val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

 // Mini drawer width state (280.dp expanded, 80.dp collapsed icon-only mode)
 var drawerWidth by remember { mutableStateOf(280.dp) }
 val isDrawerCollapsed = drawerWidth == 80.dp

 // Drawer actions
 val openDrawer: () -> Unit = {
  scope.launch {
   drawerWidth = 280.dp
   drawerState.open()
  }
 }
 val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
 val collapseDrawer: () -> Unit = {
  scope.launch {
   drawerWidth = 80.dp
   if (!drawerState.isOpen) {
    drawerState.open()
   }
  }
 }
 val expandDrawer: () -> Unit = {
  scope.launch {
   drawerWidth = 280.dp
   if (!drawerState.isOpen) {
    drawerState.open()
   }
  }
 }

 // Reset drawer when returning to home
 LaunchedEffect(currentRoute, isDrawerCollapsed) {
  if (currentRoute == "home" && isDrawerCollapsed) {
   drawerWidth = 280.dp
   drawerState.close()
  }
 }

 Surface(
  modifier = Modifier.fillMaxSize(),
  color = MaterialTheme.colorScheme.background
 ) {
  Row(modifier = Modifier.fillMaxSize()) {
   // Persistent mini drawer (visible across all screens)
   AnimatedVisibility(
    visible = drawerState.isOpen || isDrawerCollapsed,
    enter = androidx.compose.animation.expandHorizontally(
     animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ),
    exit = androidx.compose.animation.shrinkHorizontally(
     animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )
   ) {
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
       collapseDrawer()
       navController.navigate("home") {
        popUpTo("home") { inclusive = true }
       }
      },
      onNavigateToDownloads = {
       collapseDrawer()
       navController.navigate(Screen.Downloads.route)
      },
      onNavigateToSteamLogin = {
       collapseDrawer()
       navController.navigate(Screen.SteamLogin.route)
      },
      onNavigateToSyncLibrary = {
       collapseDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 2))
      },
      onNavigateToSteamClient = {
       collapseDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 1))
      },
      onNavigateToController = {
       collapseDrawer()
       navController.navigate(Screen.ControllerSettings.route)
      },
      onNavigateToContainerManagement = {
       collapseDrawer()
       navController.navigate(Screen.ContainerManagement.route)
      },
      onNavigateToWineTest = {
       collapseDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 4))
      },
      onNavigateToAppSettings = {
       collapseDrawer()
       navController.navigate(Screen.Settings.createRoute(section = 5))
      },
      onAddGame = {
       // Navigate to home and show add game dialog
       collapseDrawer()
       navController.navigate(Screen.Home.createRoute(showAddGame = true)) {
        popUpTo("home") { inclusive = true }
       }
      },
      currentRoute = currentRoute,
      isCollapsed = isDrawerCollapsed,
      onExpandDrawer = expandDrawer
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
