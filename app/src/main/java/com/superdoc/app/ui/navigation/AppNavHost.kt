package com.superdoc.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.ui.home.HomeActions
import com.superdoc.app.ui.home.HomeScreen
import com.superdoc.app.ui.language.LanguageScreen
import com.superdoc.app.ui.language.LanguageViewModel
import com.superdoc.app.ui.library.LibraryScreen
import com.superdoc.app.ui.library.RecycleBinScreen
import com.superdoc.app.ui.note.NoteActions
import com.superdoc.app.ui.note.NoteScreen

@Composable
fun AppNavHost(startAtLanguage: Boolean, nav: NavHostController = rememberNavController()) {
    val home = { nav.goHome() }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.goHome() }
    val help = { topic: String -> nav.navigate(Routes.help(topic)) }
    val open = { item: ItemEntity ->
        if (item.type == ItemType.NOTE) nav.navigate(Routes.note(item.id)) else nav.navigate(Routes.viewer(item.id))
    }

    NavHost(navController = nav, startDestination = if (startAtLanguage) Routes.LANGUAGE else Routes.HOME) {
        composable(Routes.LANGUAGE) {
            val vm: LanguageViewModel = hiltViewModel()
            LanguageScreen(onChoose = { tag -> vm.choose(tag) { nav.goHome() } })
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm = hiltViewModel(),
                actions = HomeActions(
                    newNote = { nav.navigate(Routes.note()) },
                    scan = {},
                    library = { nav.navigate(Routes.LIBRARY) },
                    help = { nav.navigate(Routes.HELP_INDEX) },
                    tools = { nav.navigate(Routes.TOOLS) },
                    merge = { nav.navigate(Routes.MERGE) },
                    pageTool = { nav.navigate(Routes.pickForTool(it)) },
                    settings = { nav.navigate(Routes.SETTINGS) },
                    open = open,
                    startTour = { help("tour") },
                ),
            )
        }
        composable(Routes.NOTE) {
            NoteScreen(
                vm = hiltViewModel(),
                actions = NoteActions(
                    back = back, home = home, help = { help("note") },
                    export = { id -> nav.navigate(Routes.export(id)) },
                    deleted = back,
                ),
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                vm = hiltViewModel(),
                onOpen = open,
                onRecycleBin = { nav.navigate(Routes.RECYCLE_BIN) },
                onBack = back, onHome = home, onHelp = { help("library") },
                onMakePdf = { id -> nav.navigate(Routes.export(id)) },
            )
        }
        composable(Routes.RECYCLE_BIN) {
            RecycleBinScreen(vm = hiltViewModel(), onBack = back, onHome = home, onHelp = { help("library") })
        }
    }
}

fun NavHostController.goHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
