package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode

fun Modifier.blockBringIntoView(): Modifier = this then BlockBringIntoViewElement

private data object BlockBringIntoViewElement : ModifierNodeElement<BlockBringIntoViewNode>() {
    override fun create(): BlockBringIntoViewNode = BlockBringIntoViewNode()

    override fun update(node: BlockBringIntoViewNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "blockBringIntoView"
    }
}

private class BlockBringIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        // Intentionally swallow relocation requests so focus changes do not auto-scroll the hero.
    }
}
