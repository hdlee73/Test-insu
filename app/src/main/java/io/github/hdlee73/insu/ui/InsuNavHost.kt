package io.github.hdlee73.insu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.hdlee73.insu.ui.about.AboutScreen
import io.github.hdlee73.insu.ui.about.AboutViewModel
import io.github.hdlee73.insu.ui.components.appViewModel
import io.github.hdlee73.insu.ui.detail.DetailScreen
import io.github.hdlee73.insu.ui.detail.DetailViewModel
import io.github.hdlee73.insu.ui.home.HomeScreen
import io.github.hdlee73.insu.ui.home.HomeViewModel
import io.github.hdlee73.insu.ui.importer.ImportScreen
import io.github.hdlee73.insu.ui.importer.ImportViewModel
import io.github.hdlee73.insu.ui.list.PolicyListScreen
import io.github.hdlee73.insu.ui.list.PolicyListViewModel
import io.github.hdlee73.insu.ui.review.EditScreen
import io.github.hdlee73.insu.ui.review.EditViewModel
import io.github.hdlee73.insu.ui.settings.SettingsScreen
import io.github.hdlee73.insu.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIST = "policies"
    const val IMPORT = "import"
    const val DETAIL = "policy/{id}"
    const val EDIT = "edit/{id}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun detail(id: Long) = "policy/$id"
    fun edit(id: Long) = "edit/$id"
}

private data class DrawerEntry(val route: String, val label: String, val icon: ImageVector)

private val drawerEntries = listOf(
    DrawerEntry(Routes.HOME, "홈", Icons.Filled.Home),
    DrawerEntry(Routes.LIST, "내 증권", Icons.AutoMirrored.Filled.List),
    DrawerEntry(Routes.IMPORT, "증권 가져오기", Icons.Filled.Add),
    DrawerEntry(Routes.SETTINGS, "설정·보안", Icons.Filled.Settings),
    DrawerEntry(Routes.ABOUT, "정보·사용 방법", Icons.Filled.Info),
)

private val topLevel = setOf(Routes.HOME, Routes.LIST, Routes.SETTINGS, Routes.ABOUT)

/** 왼쪽에서 열리는 Modal Navigation Drawer + 화면 이동. */
@Composable
fun InsuNavHost(nav: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    fun go(route: String) {
        scope.launch { drawerState.close() }
        if (route == current) return
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = route != Routes.IMPORT }
            launchSingleTop = true
            restoreState = route != Routes.IMPORT
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 하위 화면(검수·상세)에서는 가장자리 스와이프가 뒤로가기와 겹치지 않게 서랍 제스처를 끈다.
        gesturesEnabled = drawerState.isOpen || current in topLevel,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("증권노트", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                drawerEntries.forEach { e ->
                    NavigationDrawerItem(
                        label = { Text(e.label) },
                        icon = { Icon(e.icon, contentDescription = null) },
                        selected = current == e.route,
                        onClick = { go(e.route) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = appViewModel { c, _ -> HomeViewModel(c) },
                    onOpenDrawer = openDrawer,
                    onStartImport = { nav.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenPolicy = { nav.navigate(Routes.detail(it)) },
                    onOpenList = { go(Routes.LIST) },
                )
            }
            composable(Routes.LIST) {
                PolicyListScreen(
                    vm = appViewModel { c, _ -> PolicyListViewModel(c) },
                    onOpenDrawer = openDrawer,
                    onOpenPolicy = { nav.navigate(Routes.detail(it)) },
                    onAdd = { nav.navigate(Routes.IMPORT) { launchSingleTop = true } },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    vm = appViewModel { c, _ -> ImportViewModel(c) },
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.navigate(Routes.detail(id)) { popUpTo(Routes.IMPORT) { inclusive = true } }
                    },
                )
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DetailScreen(
                    vm = appViewModel { c, h -> DetailViewModel(c, h) },
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(it)) },
                )
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EditScreen(vm = appViewModel { c, h -> EditViewModel(c, h) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm = appViewModel { c, _ -> SettingsViewModel(c) }, onOpenDrawer = openDrawer)
            }
            composable(Routes.ABOUT) {
                AboutScreen(vm = appViewModel { c, _ -> AboutViewModel(c) }, onOpenDrawer = openDrawer)
            }
        }
        // 마지막에 선언해야 다른 BackHandler보다 우선한다: 서랍이 열려 있으면 먼저 닫는다.
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    }
}
