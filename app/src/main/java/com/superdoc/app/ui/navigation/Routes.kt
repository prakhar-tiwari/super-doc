package com.superdoc.app.ui.navigation

object Routes {
    const val LANGUAGE = "language"
    const val HOME = "home"
    const val NOTE = "note/{id}"
    const val LIBRARY = "library"
    const val RECYCLE_BIN = "recycle"
    const val VIEWER = "viewer/{id}"
    const val EXPORT = "export/{id}"
    const val TOOLS = "tools"
    const val MERGE = "tools/merge"
    const val PAGES = "pages/{id}/{mode}"
    const val SPLIT = "split/{id}"
    const val PICK_FOR_TOOL = "tools/pick/{tool}"
    const val SETTINGS = "settings"
    const val AI_SETTINGS = "settings/ai"
    const val HELP = "help/{topic}"
    const val HELP_INDEX = "help"

    fun note(id: Long = 0) = "note/$id"
    fun viewer(id: Long) = "viewer/$id"
    fun export(id: Long) = "export/$id"
    fun pages(id: Long, mode: PageMode) = "pages/$id/${mode.name}"
    fun split(id: Long) = "split/$id"
    fun pickForTool(tool: PageTool) = "tools/pick/${tool.name}"
    fun help(topic: String) = "help/$topic"
}

enum class PageMode { REMOVE, ADD, REORDER }

/** Tools that first need the user to choose a document. */
enum class PageTool { SPLIT, REMOVE, ADD, REORDER }
