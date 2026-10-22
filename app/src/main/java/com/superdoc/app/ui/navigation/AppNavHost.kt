package com.superdoc.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superdoc.app.ui.home.HomeActions
import com.superdoc.app.ui.home.HomeScreen
import com.superdoc.app.ui.language.LanguageScreen
import com.superdoc.app.ui.language.LanguageViewModel

@Composable
fun AppNavHost(startAtLanguage: Boolean, nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = if (startAtLanguage) Routes.LANGUAGE else Routes.HOME) {
        composable(Routes.LANGUAGE) {
            val vm: LanguageViewModel = hiltViewModel()
            LanguageScreen(onChoose = { tag -> vm.choose(tag) { nav.goHome() } })
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm = hiltViewModel(),
                actions = HomeActions(
                    newNote = {}, scan = {}, library = {}, help = {}, tools = {}, merge = {},
                    pageTool = {}, settings = {}, open = {}, startTour = {},
                ),
            )
        }
    }
}

fun NavHostController.goHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
