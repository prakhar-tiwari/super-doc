package com.superdoc.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.superdoc.app.R
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.help.HelpIndexScreen
import com.superdoc.app.ui.help.HelpScreen
import com.superdoc.app.ui.help.PracticeSettingsSection
import com.superdoc.app.ui.help.PracticeViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.ui.ai.AiPanel
import com.superdoc.app.ui.ai.AiSettingsScreen
import com.superdoc.app.ui.ai.AiViewModel
import com.superdoc.app.ui.export.ExportScreen
import com.superdoc.app.ui.home.HomeActions
import com.superdoc.app.ui.home.HomeScreen
import com.superdoc.app.ui.language.LanguageScreen
import com.superdoc.app.ui.language.LanguageViewModel
import com.superdoc.app.ui.library.LibraryScreen
import com.superdoc.app.ui.library.RecycleBinScreen
import com.superdoc.app.ui.note.NoteActions
import com.superdoc.app.ui.note.NoteScreen
import com.superdoc.app.ui.scan.rememberScanFlow
import com.superdoc.app.ui.settings.SettingsScreen
import com.superdoc.app.ui.tools.MergeScreen
import com.superdoc.app.ui.tools.PagesScreen
import com.superdoc.app.ui.tools.PickDocumentScreen
import com.superdoc.app.ui.tools.SplitScreen
import com.superdoc.app.ui.tools.ToolsScreen
import com.superdoc.app.ui.viewer.ViewerActions
import com.superdoc.app.ui.viewer.ViewerScreen
import com.superdoc.app.ui.viewer.ViewerViewModel

@Composable
fun AppNavHost(startAtLanguage: Boolean, nav: NavHostController = rememberNavController()) {
    val home = { nav.goHome() }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.goHome() }
    val help = { topic: String -> nav.navigate(Routes.help(topic)) }
    val open = { item: ItemEntity ->
        if (item.type == ItemType.NOTE) nav.navigate(Routes.note(item.id)) else nav.navigate(Routes.viewer(item.id))
    }
    /** Opens what a tool made; Back from there returns to the screen before the tool. */
    val openResult = { route: String, item: ItemEntity ->
        nav.navigate(Routes.viewer(item.id)) { popUpTo(route) { inclusive = true } }
    }

    NavHost(navController = nav, startDestination = if (startAtLanguage) Routes.LANGUAGE else Routes.HOME) {
        composable(Routes.LANGUAGE) {
            val vm: LanguageViewModel = hiltViewModel()
            LanguageScreen(onChoose = { tag -> vm.choose(tag) { nav.goHome() } })
        }
        composable(Routes.HOME) {
            val scan = rememberScanFlow { id -> nav.navigate(Routes.viewer(id)) }
            HomeScreen(
                vm = hiltViewModel(),
                actions = HomeActions(
                    newNote = { nav.navigate(Routes.note()) },
                    scan = scan,
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
            val ai: AiViewModel = hiltViewModel()
            NoteScreen(
                vm = hiltViewModel(),
                actions = NoteActions(
                    back = back, home = home, help = { help("note") },
                    export = { id -> nav.navigate(Routes.export(id)) },
                    deleted = back,
                ),
                aiPanel = if (ai.deviceSupported) {
                    { host -> AiPanel(host, ai, onSetup = { nav.navigate(Routes.AI_SETTINGS) }) }
                } else null,
            )
        }
        composable(Routes.AI_SETTINGS) {
            AiSettingsScreen(vm = hiltViewModel(), onBack = back, onHome = home, onHelp = { help("ai") })
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
        composable(Routes.VIEWER) {
            val vm: ViewerViewModel = hiltViewModel()
            ViewerScreen(
                vm = vm,
                actions = ViewerActions(
                    back = back, home = home, help = { help("viewer") },
                    pages = { mode -> nav.navigate(Routes.pages(vm.id, mode)) },
                    split = { nav.navigate(Routes.split(vm.id)) },
                    deleted = back,
                ),
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("export") },
                onOpen = { item -> nav.navigate(Routes.viewer(item.id)) { popUpTo(Routes.EXPORT) { inclusive = true } } },
            )
        }
        composable(Routes.TOOLS) {
            ToolsScreen(
                onBack = back, onHome = home, onHelp = { help("tools") },
                onMerge = { nav.navigate(Routes.MERGE) },
                onTool = { nav.navigate(Routes.pickForTool(it)) },
            )
        }
        composable(Routes.PICK_FOR_TOOL) {
            PickDocumentScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("tools") },
                onPicked = { tool, item ->
                    val route = when (tool) {
                        PageTool.SPLIT -> Routes.split(item.id)
                        PageTool.REMOVE -> Routes.pages(item.id, PageMode.REMOVE)
                        PageTool.ADD -> Routes.pages(item.id, PageMode.ADD)
                        PageTool.REORDER -> Routes.pages(item.id, PageMode.REORDER)
                    }
                    nav.navigate(route) { popUpTo(Routes.PICK_FOR_TOOL) { inclusive = true } }
                },
            )
        }
        composable(Routes.MERGE) {
            MergeScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("tools") },
                onOpen = { openResult(Routes.MERGE, it) }, onDone = back,
            )
        }
        composable(Routes.PAGES) {
            PagesScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("tools") },
                onOpen = { openResult(Routes.PAGES, it) }, onDone = back,
            )
        }
        composable(Routes.SPLIT) {
            SplitScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("tools") },
                onOpen = { openResult(Routes.SPLIT, it) }, onDone = back,
            )
        }
        composable(Routes.SETTINGS) {
            val practice: PracticeViewModel = hiltViewModel()
            SettingsScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home, onHelp = { help("settings") },
                onAi = { nav.navigate(Routes.AI_SETTINGS) },
                extraSections = listOf { PracticeSettingsSection(practice) },
            )
        }
        composable(Routes.HELP_INDEX) {
            HelpIndexScreen(
                vm = hiltViewModel(),
                practice = hiltViewModel(),
                onBack = back, onHome = home,
                onTopic = help,
                onOpenDocuments = { nav.navigate(Routes.LIBRARY) { popUpTo(Routes.HELP_INDEX) { inclusive = true } } },
            )
        }
        composable(Routes.HELP) {
            val practice: PracticeViewModel = hiltViewModel()
            HelpScreen(
                vm = hiltViewModel(),
                onBack = back, onHome = home,
                onPractice = { practice.add { nav.goHome() } },
            )
            if (practice.busy) BusyOverlay(stringResource(R.string.export_saving))
        }
    }
}

fun NavHostController.goHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
