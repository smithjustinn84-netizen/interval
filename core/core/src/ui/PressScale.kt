package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shrinks a control to [Motion.PRESS_SCALE] while [interaction] reports it pressed, springing back
 * on release: the touch feedback every button and stepper shares, under its ripple.
 *
 * A node rather than state: it hears the presses, springs and scales in its own layer, so a press recomposes
 * nothing, not even the control it is on, and a row of tappable things holds no animation state per item.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource): Modifier =
    this then PressScaleElement(interaction, still = prefersReducedMotion())

private data class PressScaleElement(val interaction: InteractionSource, val still: Boolean) :
    ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interaction, still)

    override fun update(node: PressScaleNode) = node.update(interaction, still)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["still"] = still
    }
}

private class PressScaleNode(private var interaction: InteractionSource, private var still: Boolean) :
    Modifier.Node(),
    LayoutModifierNode {
    private val scale = Animatable(1f)
    private var pressed = false
    private var watching: Job? = null

    // Read in the layer, so a frame of the spring is a redraw of the layer alone.
    private val layer: GraphicsLayerScope.() -> Unit = {
        val shown = scale.value
        scaleX = shown
        scaleY = shown
    }

    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() = watch()

    override fun onDetach() {
        pressed = false
        watching = null
    }

    fun update(interaction: InteractionSource, still: Boolean) {
        val heard = interaction != this.interaction
        val stilled = still != this.still
        this.interaction = interaction
        this.still = still
        if (!isAttached) return
        if (heard) {
            watch()
        } else if (stilled) {
            settle()
        }
    }

    /** Follows [interaction]'s presses, as `collectIsPressedAsState` does: pressed while any press is unreleased. */
    private fun watch() {
        watching?.cancel()
        pressed = false
        val source = interaction
        watching = coroutineScope.launch {
            scale.snapTo(1f)
            val presses = mutableListOf<PressInteraction.Press>()
            source.interactions.collect { heard ->
                when (heard) {
                    is PressInteraction.Press -> presses.add(heard)
                    is PressInteraction.Release -> presses.remove(heard.press)
                    is PressInteraction.Cancel -> presses.remove(heard.press)
                }
                pressed = presses.isNotEmpty()
                settle()
            }
        }
    }

    /** Springs toward the scale the press calls for; a new target takes over from one under way. */
    private fun settle() {
        val target = if (pressed && !still) Motion.PRESS_SCALE else 1f
        if (scale.targetValue == target) return
        coroutineScope.launch { scale.animateTo(target, pressSpring()) }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }
}
