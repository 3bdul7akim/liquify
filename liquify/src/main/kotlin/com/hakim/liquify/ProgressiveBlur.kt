package com.hakim.liquify

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.hakim.liquify.internal.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderCacheImpl
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.chain
import org.intellij.lang.annotations.Language
import kotlin.math.ceil

/**
 * Blurs what lies behind this element on a ramp — fully at the top, easing off towards the bottom
 * edge — and lays [tint] over it on a ramp of its own, so the element ends in the page instead of on
 * a line: the header a list scrolls under.
 *
 * ```
 * Column(Modifier.fillMaxWidth().progressiveBlur(radius = 20.dp, tint = background.copy(alpha = 0.8f))) {
 *     Title()
 *     SearchField()
 * }
 * ```
 *
 * It is a real progressive blur, whose radius falls off with the height, not one blur faded out over
 * a sharp copy — which reads as a sharp page and a blurred one laid over each other half way down.
 * The page is drawn at half resolution and blurred by two passes of a Gaussian, down and then
 * across; the blur hides the resolution. The element is not glass: it has no outline to refract
 * along, no rim and no shadow, and pays for none of that.
 *
 * It adapts like every pane. Android 12, which has no runtime shaders, blurs at [radius] all the way
 * down and fades it out through a gradient. Where nothing may be sampled — under [GlassLook.Solid],
 * and below Android 12 — the tint is drawn alone, opaque at the top and fading out towards the
 * bottom: with nothing blurred behind it, it is what keeps text on the element readable.
 *
 * @param radius the blur at the top, as a platform blur radius.
 * @param tint laid over the blur, strongest at the top; its alpha is the strength there.
 * @param backdrop what is blurred; inherited from [LiquifyTheme] when `null`.
 * @throws IllegalStateException when it samples and has no backdrop, given or inherited.
 */
@Composable
public fun Modifier.progressiveBlur(
    radius: Dp,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = null,
): Modifier {
    val samples = LocalGlassLook.current != GlassLook.Solid && isRenderEffectSupported()
    val source = if (samples) backdrop ?: LocalBackdrop.current ?: noProgressiveBlurBackdrop() else null
    return this then ProgressiveBlurElement(source, tint, radius)
}

private fun noProgressiveBlurBackdrop(): Nothing =
    error(
        "progressiveBlur() has no backdrop. Pass one, or declare it once with " +
            "LiquifyTheme(backdrop = …)."
    )

/**
 * Where the blur begins to ease off, as a share of the height from the top. Down to there it is at
 * full strength — under a status bar and a title — and below it eases away to nothing at the bottom.
 */
private const val BlurEaseFrom = 0.45f

/** Where the tint begins to ease off, likewise. */
private const val TintEaseFrom = 0.5f

/**
 * Where the element's own pixels begin to fade into the page's. Only the last stretch: the blur has
 * all but run out there, and what is left to hide is the half-resolution copy — softer than the page
 * it sits on.
 */
private const val FadeFrom = 0.95f

/**
 * The platform's blur radius to Gaussian sigma — the conversion Android's own blur applies — so the
 * progressive blur at its strongest matches what a plain blur of the same radius looks like.
 */
private const val RadiusToSigma = 0.57735f

/** The page is blurred at half resolution: a quarter of the pixels, and a blur hides the rest. */
private const val Downsample = 0.5f

private const val DownShaderKey = "liquify.progressiveBlur.down"
private const val AcrossShaderKey = "liquify.progressiveBlur.across"

/** Android 12's dissolve: the blurred page kept from [FadeFrom] down, faded out below it. */
private val FadeMask: Brush = Brush.verticalGradient(FadeFrom to Color.Black, 1f to Color.Transparent)

/**
 * One pass of a Gaussian blur whose strength depends on the height — strongest at the top, none at
 * the bottom — run once down the layer and once across it. Separable blurring is only exact for a
 * constant radius, but the radius changes slowly enough over a header that the difference does not
 * show, and two passes of 17 samples cost a fraction of one 2D kernel of the same reach.
 *
 * The samples are spread over ±2 sigma at a spacing that grows with the radius, which makes every
 * weight a constant of its index. Not fewer: each pass leaves a copy of an edge at every sample, and
 * two passes of 9 samples half a sigma apart laid those copies out as a visible lattice of dots wherever
 * a sharp edge ran under the strong end of the blur. The second pass also tints and fades, on ramps of
 * their own.
 */
@Language("AGSL")
private const val ProgressiveBlurShader = """
uniform shader content;
uniform float2 size;
uniform float2 direction;
uniform float sigma;
uniform float blurFrom;
uniform float isLastPass;
layout(color) uniform half4 tint;
uniform float tintIntensity;
uniform float tintFrom;
uniform float fadeFrom;

half4 main(float2 coord) {
    float t = coord.y / size.y;
    float s = sigma * smoothstep(1.0, blurFrom, t);
    half4 color = half4(0.0);
    if (s < 0.3) {
        color = content.eval(coord);
    } else {
        float spacing = s * 0.25;
        half4 sum = half4(0.0);
        float total = 0.0;
        for (int i = -8; i <= 8; i++) {
            float w = exp(-float(i * i) / 32.0);
            // Clamped to the layer: past its edge there is nothing, and the top would darken into it.
            float2 p = clamp(coord + direction * (float(i) * spacing), float2(0.5), size - 0.5);
            sum += content.eval(p) * half(w);
            total += w;
        }
        color = sum / half(total);
    }
    if (isLastPass > 0.5) {
        color = mix(color, tint, half(tintIntensity * smoothstep(1.0, tintFrom, t)));
        color *= half(smoothstep(1.0, fadeFrom, t));
    }
    return color;
}"""

private class ProgressiveBlurElement(
    val backdrop: Backdrop?,
    val tint: Color,
    val radius: Dp,
) : ModifierNodeElement<ProgressiveBlurNode>() {

    override fun create(): ProgressiveBlurNode = ProgressiveBlurNode(backdrop, tint, radius)

    override fun update(node: ProgressiveBlurNode) {
        node.backdrop = backdrop
        node.tint = tint
        node.radius = radius
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "progressiveBlur"
        properties["radius"] = radius
        properties["tint"] = tint
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        other is ProgressiveBlurElement &&
            backdrop == other.backdrop &&
            tint == other.tint &&
            radius == other.radius

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + tint.hashCode()
        result = 31 * result + radius.hashCode()
        return result
    }
}

/**
 * Draws the blur behind the element's content. What it builds — the two programs, their uniforms,
 * Android 12's blur and gradients — is kept until the size, the radius or the tint changes, not
 * rebuilt every frame the page scrolls underneath. The page itself is not copied either: the layer
 * only holds a reference to the backdrop's own drawing, so the blur follows the page as it moves.
 */
private class ProgressiveBlurNode(
    var backdrop: Backdrop?,
    var tint: Color,
    var radius: Dp,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var coordinates: LayoutCoordinates? = null
    private var position = Offset.Unspecified

    private var pageLayer: GraphicsLayer? = null
    private var maskLayer: GraphicsLayer? = null
    private val shaders = RuntimeShaderCacheImpl()

    /** Everything the chain of the two programs was last built for. */
    private var effectKey = Long.MIN_VALUE

    private var maskedBlur: RenderEffect? = null
    private var maskedBlurRadius = Float.NaN
    private var tintRamp: Brush? = null
    private var tintRampColor = Color.Unspecified
    private var fade: Brush? = null
    private var fadeColor = Color.Unspecified

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        // A layer backdrop is drawn relative to the element, so the element moving is a new picture.
        val now = coordinates.positionInRoot()
        if (now != position) {
            position = now
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val source = backdrop
        if (size.width > 0f && size.height > 0f) {
            when {
                source == null -> drawFade()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> drawProgressive(source)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> drawMasked(source)
                else -> drawFade()
            }
        }
        drawContent()
    }

    private fun obtainPageLayer(): GraphicsLayer =
        pageLayer ?: requireGraphicsContext().createGraphicsLayer().also { pageLayer = it }

    /** Draws the backdrop as this element sees it into [layer], scaled by [scale]. */
    private fun DrawScope.recordPage(source: Backdrop, layer: GraphicsLayer, layerSize: IntSize, scale: Float) {
        val coordinates = coordinates ?: return
        val density = this
        layer.record(layerSize) {
            scale(scale, scale, pivot = Offset.Zero) {
                with(source) { drawBackdrop(density, coordinates, null) }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun DrawScope.drawProgressive(source: Backdrop) {
        val layer = obtainPageLayer()
        val layerSize = IntSize(
            ceil(size.width * Downsample).toInt(),
            ceil(size.height * Downsample).toInt(),
        )
        recordPage(source, layer, layerSize, Downsample)

        val sigma = radius.toPx() * RadiusToSigma * Downsample
        var key = 17L
        key = key * 31L + layerSize.width
        key = key * 31L + layerSize.height
        key = key * 31L + sigma.toRawBits()
        key = key * 31L + tint.hashCode()
        if (key != effectKey || layer.renderEffect == null) {
            effectKey = key
            val down = shaders.obtainRuntimeShader(DownShaderKey, ProgressiveBlurShader)
            val across = shaders.obtainRuntimeShader(AcrossShaderKey, ProgressiveBlurShader)
            down.setPassUniforms(layerSize, directionX = 0f, directionY = 1f, sigma = sigma, lastPass = false)
            across.setPassUniforms(layerSize, directionX = 1f, directionY = 0f, sigma = sigma, lastPass = true)
            // Down the layer first, then across it.
            layer.renderEffect = RuntimeShaderEffect(down, "content").chain(RuntimeShaderEffect(across, "content"))
        }

        clipRect {
            scale(1f / Downsample, 1f / Downsample, pivot = Offset.Zero) { drawLayer(layer) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.setPassUniforms(
        layerSize: IntSize,
        directionX: Float,
        directionY: Float,
        sigma: Float,
        lastPass: Boolean,
    ) {
        val tinted = tint.isSpecified
        setFloatUniform("size", layerSize.width.toFloat(), layerSize.height.toFloat())
        setFloatUniform("direction", directionX, directionY)
        setFloatUniform("sigma", sigma)
        setFloatUniform("blurFrom", BlurEaseFrom)
        setFloatUniform("isLastPass", if (lastPass) 1f else 0f)
        // Opaque: the strength travels in tintIntensity, and the uniform's own alpha would apply twice.
        setColorUniform("tint", if (tinted) tint.copy(alpha = 1f) else Color.Transparent)
        setFloatUniform("tintIntensity", if (tinted) tint.alpha else 0f)
        setFloatUniform("tintFrom", TintEaseFrom)
        setFloatUniform("fadeFrom", FadeFrom)
    }

    /**
     * Android 12: a plain blur of one strength, faded out towards the bottom by a gradient mask in an
     * offscreen layer, with the tint as a gradient over it — the same dissolve, without the radius
     * falling off.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun DrawScope.drawMasked(source: Backdrop) {
        val page = obtainPageLayer()
        val mask = maskLayer ?: requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }.also { maskLayer = it }
        val layerSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())
        recordPage(source, page, layerSize, 1f)

        val px = radius.toPx()
        if (px != maskedBlurRadius) {
            maskedBlurRadius = px
            maskedBlur = if (px > 0f) BlurEffect(px, px, TileMode.Clamp) else null
        }
        page.renderEffect = maskedBlur

        val ramp = if (!tint.isSpecified) {
            null
        } else {
            tintRamp?.takeIf { tintRampColor == tint }
                ?: Brush.verticalGradient(TintEaseFrom to tint, 1f to tint.copy(alpha = 0f)).also {
                    tintRamp = it
                    tintRampColor = tint
                }
        }
        mask.record(layerSize) {
            drawLayer(page)
            drawRect(FadeMask, blendMode = BlendMode.DstIn)
            ramp?.let { drawRect(it) }
        }
        drawLayer(mask)
    }

    /** Nothing sampled: the tint alone, opaque at the top, fading out towards the bottom edge. */
    private fun DrawScope.drawFade() {
        if (!tint.isSpecified) return
        val brush = fade?.takeIf { fadeColor == tint }
            ?: Brush.verticalGradient(0f to tint.copy(alpha = 1f), 1f to tint.copy(alpha = 0f)).also {
                fade = it
                fadeColor = tint
            }
        drawRect(brush)
    }

    override fun onDetach() {
        val context = requireGraphicsContext()
        pageLayer?.let { context.releaseGraphicsLayer(it) }
        maskLayer?.let { context.releaseGraphicsLayer(it) }
        pageLayer = null
        maskLayer = null
        shaders.clear()
        effectKey = Long.MIN_VALUE
        maskedBlur = null
        maskedBlurRadius = Float.NaN
        coordinates = null
        position = Offset.Unspecified
    }
}
