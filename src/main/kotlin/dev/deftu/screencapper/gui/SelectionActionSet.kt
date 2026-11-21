package dev.deftu.screencapper.gui

internal enum class SelectionAction {
    COPY,
    UPLOAD,
    DELETE,
}

internal fun selectionActions(count: Int): List<SelectionAction> =
    if (count > 0) SelectionAction.entries else emptyList()
