package com.hakim.liquify.internal

import org.intellij.lang.annotations.Language

/**
 * Upper bound on how many elements can take part in one merged surface.
 *
 * The merge pass evaluates every member's distance field once per pixel — together with its
 * gradient — so cost grows linearly with this. Twelve is generous for the interactions this is
 * meant for — a toolbar splitting apart, a menu growing out of a button.
 */
internal const val MAX_MERGED_ELEMENTS: Int = 12

/**
 * Uniform names, built once for the whole process.
 *
 * They are written on every member on every frame, so composing them with `"rect$i"` at the call
 * site would allocate a fresh string per member per shader per frame — up to a hundred and forty
 * of them for a full group at 120 Hz, for names that never change. Sharing the arrays with the
 * program generator also keeps the declaration and the write from drifting apart.
 */
internal val UniformRect: Array<String> = Array(MAX_MERGED_ELEMENTS) { "rect$it" }
internal val UniformRadii: Array<String> = Array(MAX_MERGED_ELEMENTS) { "radii$it" }
internal val UniformParam: Array<String> = Array(MAX_MERGED_ELEMENTS) { "param$it" }
internal val UniformTint: Array<String> = Array(MAX_MERGED_ELEMENTS) { "tint$it" }
internal val UniformFlow: Array<String> = Array(MAX_MERGED_ELEMENTS) { "flow$it" }
internal val UniformBlend: Array<String> = Array(MAX_MERGED_ELEMENTS) { "blend$it" }
internal val UniformLight: Array<String> = Array(MAX_MERGED_ELEMENTS) { "light$it" }
internal val UniformGlow: Array<String> = Array(MAX_MERGED_ELEMENTS) { "glow$it" }

private val GlassKeys: Array<Array<String>> = Array(MAX_MERGED_ELEMENTS + 1) { count ->
    arrayOf("liquify.merge.glass.$count.plain", "liquify.merge.glass.$count.tinted")
}
private val SilhouetteKeys: Array<String> =
    Array(MAX_MERGED_ELEMENTS + 1) { "liquify.merge.silhouette.$it" }
private val SilhouetteMaskKeys: Array<String> =
    Array(MAX_MERGED_ELEMENTS + 1) { "liquify.merge.silhouetteMask.$it" }

/** Cache key of the merged glass program for [count] members, tinted or plain. */
internal fun mergeGlassShaderKey(count: Int, tinted: Boolean): String =
    GlassKeys[count][if (tinted) 1 else 0]

/** Cache key of the silhouette program, and of the separate instance used to punch the shadow out. */
internal fun mergeSilhouetteShaderKey(count: Int): String = SilhouetteKeys[count]

internal fun mergeSilhouetteMaskShaderKey(count: Int): String = SilhouetteMaskKeys[count]

/**
 * Generated program text, memoised for the process.
 *
 * A node's own shader cache is dropped when it detaches, so a group scrolled out of the viewport
 * and back would otherwise rebuild several kilobytes of AGSL from scratch. The text depends on
 * nothing but the member count and the variant, so it is safe to keep and cheap to hold.
 *
 * Only ever touched from the draw pass, which is the main thread.
 */
private val glassSources = HashMap<Int, String>(8)
private val silhouetteSources = HashMap<Int, String>(4)

/**
 * Builds the shared prelude for a merged surface of exactly [count] elements.
 *
 * The members are declared as individual `float4` uniforms and the fold over them is emitted as
 * straight-line code, because SkSL only permits *constant* indices into uniforms — a loop counter
 * is rejected outright even when the trip count is known. Each distinct count therefore compiles to
 * its own cached program.
 *
 * Per member:
 * - `rectN`  — `xy` centre, `zw` half size, in group content space
 * - `radiiN` — corner radii, top-left, top-right, bottom-right, bottom-left
 * - `paramN` — `x` smoothing radius `k` in pixels, `y` tint strength, `z` refraction band and
 *   `w` refraction displacement in pixels — the member's own, so each one refracts by its size
 * - `blendN` — from the second member on: `x` the reach member N is folded in with, the largest `k`
 *   of it and every member before it, and `y` half its reciprocal, or 0 for no reach. The running
 *   maximum and the reciprocal are the same for every pixel, so the CPU works them out once instead
 *   of every pixel dividing by them once per member
 * - `tintN`  — the member's colour, only when [tinted]
 * - `flowN`  — `x` how much of the flowing colour has entered the member (see `absorb`), `y` one
 *   over the member's extent, only when [tinted]
 *
 * The glass program declares each member's touch light on top of these — `lightN` and `glowN`, see
 * [buildGlassShader]. The silhouettes share this prelude and have nothing to light.
 *
 * @param tinted emit the per-member colour uniforms and the weight helper the colour fold needs. A
 *   group whose members declare no colour compiles the plain program and pays nothing for a
 *   feature it does not use — the two variants are cached separately.
 */
@Language("AGSL")
private fun mergePrelude(count: Int, tinted: Boolean): String {
    val uniforms = (0 until count).joinToString("\n") { i ->
        buildString {
            append("uniform float4 ${UniformRect[i]};\n")
            append("uniform float4 ${UniformRadii[i]};\n")
            append("uniform float4 ${UniformParam[i]};")
            if (i > 0) append("\nuniform float2 ${UniformBlend[i]};")
            if (tinted) {
                append("\nlayout(color) uniform half4 ${UniformTint[i]};")
                append("\nuniform float2 ${UniformFlow[i]};")
            }
        }
    }

    val fold = buildString {
        appendLine("    float d0 = sdElement(coord, ${UniformRect[0]}, ${UniformRadii[0]});")
        for (i in 1 until count) {
            val p = i - 1
            appendLine("    float d$i = sdElement(coord, ${UniformRect[i]}, ${UniformRadii[i]});")
            // The pair blends over the wider of the two reaches, so raising one member's
            // merge() also thickens its bridges to quieter neighbours — see blendN.
            appendLine("    d$i = smoothMin(d$p, d$i, ${UniformBlend[i]});")
        }
        appendLine("    return d${count - 1};")
    }

    // Colour flowing from one member into another — only a tinted surface has colours to move.
    val absorbFunction = if (!tinted) "" else """
// The colour [incoming] has carried into a member, at a pixel [distance] away from the member it
// comes from. It enters from the side that touched, as a front with a soft edge: `flow.x` is how far
// it has come — 0.5 is half the member taken over, 1 all of it — and `flow.y` turns the distance into
// a share of the member's own extent. Mixed in premultiplied colour, so an untinted neighbour is
// coloured by it and an untinted source dilutes a coloured one towards clear glass.
half4 absorb(half4 own, float2 flow, float distance, half4 incoming) {
    if (flow.x <= 0.0) {
        return own;
    }
    float front = flow.x * 1.5;
    float reach = 1.0 - smoothstep(front - 0.6, front, distance * flow.y);
    return mix(own, incoming, half(reach));
}
"""

    // Both variants need it now: the members' refraction rides on the same blend as their colour.
    val weightFunction = """
// The factor smoothMin() blends two fields with: 1 keeps `a`, 0 keeps `b`. Pulling it out of
// smoothMin is what lets colour and refraction ride on exactly the blend the surface already uses.
// `k` is a member's blendN: the reach, and half its reciprocal. Both are uniforms, so the branch is
// coherent across the draw.
float blendWeight(float a, float b, float2 k) {
    if (k.x <= 0.0) {
        return step(a, b);
    }
    return clamp(0.5 + (b - a) * k.y, 0.0, 1.0);
}
"""

    return """
$uniforms

float radiusAt(float2 coord, float4 radii) {
    // Branchless, and it matters more here than anywhere else: this runs once per member per pixel,
    // twelve times for a full group. As nested ifs every one of those was a divergent branch.
    float2 pair = mix(radii.xw, radii.yz, step(0.0, coord.x));
    return mix(pair.y, pair.x, step(0.0, -coord.y));
}

float sdElement(float2 coord, float4 rect, float4 radii) {
    float2 local = coord - rect.xy;
    float2 halfSize = rect.zw;
    float radius = radiusAt(local, radii);
    float2 cornerCoord = abs(local) - (halfSize - float2(radius));
    return length(max(cornerCoord, 0.0)) - radius + min(max(cornerCoord.x, cornerCoord.y), 0.0);
}

// The same distance in .x, and its gradient in .yz — exact, from the same few values.
//
// The gradient used to be taken by central differences of the whole blended field: four more
// evaluations of every member for every pixel near an edge, five times the work of the distance
// alone. Each member's own gradient is known in closed form, and so is the blend's — see the fold
// below — so the one walk over the members now yields both.
float3 sdgElement(float2 coord, float4 rect, float4 radii) {
    float2 local = coord - rect.xy;
    float radius = radiusAt(local, radii);
    float2 cornerCoord = abs(local) - (rect.zw - float2(radius));
    float2 outside = max(cornerCoord, 0.0);
    // One reciprocal square root gives both the length and the direction. length() and the divide
    // that normalised by it were the two slowest instructions here, and this runs once per member
    // for every pixel of the group.
    float outsideSquared = dot(outside, outside);
    float inverseLength = inversesqrt(max(outsideSquared, 1e-12));
    float distance = outsideSquared * inverseLength - radius + min(max(cornerCoord.x, cornerCoord.y), 0.0);
    // Past a corner the field is a circle's, so the gradient points away from its centre; along a
    // straight edge, and everywhere inside, it is the axis of whichever side is nearest.
    float2 normal = outsideSquared > 0.0
        ? outside * inverseLength
        : (cornerCoord.x > cornerCoord.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    float2 quadrant = float2(local.x < 0.0 ? -1.0 : 1.0, local.y < 0.0 ? -1.0 : 1.0);
    return float3(distance, normal * quadrant);
}

// Quadratic polynomial smooth minimum. Below a separation of k the two fields blend into a single
// surface with a concave fillet instead of a hard crease -- the "gooey" bridge. `k` is a member's
// blendN, as in blendWeight().
float smoothMin(float a, float b, float2 k) {
    if (k.x <= 0.0) {
        return min(a, b);
    }
    float h = clamp(0.5 + (b - a) * k.y, 0.0, 1.0);
    return mix(b, a, h) - k.x * h * (1.0 - h);
}

float sceneDistance(float2 coord) {
$fold}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
$weightFunction$absorbFunction"""
}

/**
 * Resolves the blended distance and its gradient, the nearest member's centre, the blended
 * refraction and — when [tinted] — the blended colour, as straight-line code inside `main()`.
 *
 * The gradient rides the same blend as everything else, and that is exact rather than an
 * approximation: for the polynomial smooth minimum `mix(b, a, h) - k·h·(1 - h)` the terms the
 * derivative of `h` contributes cancel out, leaving `∇ = h·∇a + (1 - h)·∇b` — the members' own
 * gradients mixed with the very factor the distances were.
 *
 * The centre is whichever member is actually nearest: it costs no extra distance evaluations, only
 * a running min, and it is what lets each member dome about itself instead of about the group.
 *
 * Refraction and colour have to ride on the same walk that produces the distance: they are mixed
 * with the very factor `smoothMin` blends the two fields with, so they follow the geometry of the
 * fusion rather than a direction through the group. Pure inside each member, and 50/50 across the
 * neck of a bridge — a small round member keeps its own gentle bend right up to where it fuses into
 * a wider neighbour. Doing it in a second pass would mean evaluating every member's field twice per
 * pixel, and a function could not return this many floats.
 *
 * Declares `field` (`.x` distance, `.yz` nearest centre), `fieldGradient`, `refraction` (`.x`
 * band, `.y` displacement), `nearestHalfSize` — the nearest member's, which scales the fringe of
 * chromatic aberration as a single pane's own size does — `lightMix` — the light to add, see
 * below — and, for the tinted variant,
 * `tintMix` — a premultiplied colour whose `.rgb` is already scaled by its `.a`, because mixing
 * straight RGBA would drag a colour towards black wherever its neighbour is untinted: "no tint"
 * carries no hue to mix with.
 *
 * The touch light rides the same blend as the colour. A held member's light is its own at full
 * strength inside it, fades across the neck of a bridge into its neighbour's — none, unless that one
 * is held as well — and stops where the bridge does: it never lights a neighbour across a gap, and
 * never ends at a member's own outline in the middle of the body.
 */
private fun fieldSetup(count: Int, tinted: Boolean): String = buildString {
    appendLine("    float3 f0 = sdgElement(p, ${UniformRect[0]}, ${UniformRadii[0]});")
    appendLine("    float e0 = f0.x;")
    appendLine("    float d0 = e0;")
    appendLine("    float2 g0 = f0.yz;")
    appendLine("    float n0 = e0;")
    appendLine("    float2 c0 = ${UniformRect[0]}.xy;")
    appendLine("    float2 s0 = ${UniformRect[0]}.zw;")
    appendLine("    float2 r0 = ${UniformParam[0]}.zw;")
    appendLine("    half3 l0 = ${UniformGlow[0]}.rgb * touchLight(p, ${UniformLight[0]});")
    if (tinted) {
        appendLine(
            "    half4 t0 = absorb(half4(${UniformTint[0]}.rgb * half(${UniformParam[0]}.y), " +
                "half(${UniformParam[0]}.y)), ${UniformFlow[0]}, flowDistance, flowTint);"
        )
    }
    for (i in 1 until count) {
        val p = i - 1
        appendLine("    float3 f$i = sdgElement(p, ${UniformRect[i]}, ${UniformRadii[i]});")
        appendLine("    float e$i = f$i.x;")
        appendLine("    float h$i = blendWeight(d$p, e$i, ${UniformBlend[i]});")
        // smoothMin() written out on the weight already in hand: the same value, without
        // computing the weight a second time.
        appendLine("    float d$i = mix(e$i, d$p, h$i) - ${UniformBlend[i]}.x * h$i * (1.0 - h$i);")
        appendLine("    float2 g$i = mix(f$i.yz, g$p, h$i);")
        appendLine("    float take$i = step(e$i, n$p);")
        appendLine("    float n$i = min(n$p, e$i);")
        appendLine("    float2 c$i = mix(c$p, ${UniformRect[i]}.xy, take$i);")
        appendLine("    float2 s$i = mix(s$p, ${UniformRect[i]}.zw, take$i);")
        appendLine("    float2 r$i = mix(${UniformParam[i]}.zw, r$p, h$i);")
        appendLine(
            "    half3 l$i = mix(${UniformGlow[i]}.rgb * touchLight(p, ${UniformLight[i]}), " +
                "l$p, half(h$i));"
        )
        if (tinted) {
            appendLine(
                "    half4 m$i = absorb(half4(${UniformTint[i]}.rgb * half(${UniformParam[i]}.y), " +
                    "half(${UniformParam[i]}.y)), ${UniformFlow[i]}, flowDistance, flowTint);"
            )
            appendLine("    half4 t$i = mix(m$i, t$p, half(h$i));")
        }
    }
    val last = count - 1
    appendLine("    float3 field = float3(d$last, c$last);")
    appendLine("    float2 fieldGradient = g$last;")
    append("    float2 refraction = r$last;")
    append("\n    float2 nearestHalfSize = s$last;")
    append("\n    half3 lightMix = l$last;")
    if (tinted) append("\n    half4 tintMix = t$last;")
}

/**
 * The merged glass pass: refracts the backdrop through the combined distance field, clips to it
 * and lays a rim light along the merged silhouette.
 *
 * Doing all three in one program is what makes merging look continuous — the refraction and the
 * rim follow the *bridge* between two elements, not the outline of either one.
 */
internal fun mergeGlassShader(count: Int, tinted: Boolean): String =
    glassSources.getOrPut(count * 2 + if (tinted) 1 else 0) { buildGlassShader(count, tinted) }

@Language("AGSL")
private fun buildGlassShader(count: Int, tinted: Boolean): String {
    // Folded in before the rim, so a specular highlight stays white on top of tinted glass. The
    // mix is a plain source-over: the tint arrives premultiplied by its own strength and is scaled
    // by the surface's coverage, so it can never paint outside the merged silhouette.
    val applyTint = if (!tinted) "" else """

    color.rgb = color.rgb * (half(1.0) - tintMix.a) + tintMix.rgb * color.a;
"""
    // Where the flowing colour comes from: the member that carries it — its box, its corners and its
    // colour — and how far each pixel is from it, measured once for every member's `absorb`.
    val flowUniforms = if (!tinted) "" else """
uniform float flowActive;
uniform float4 flowRect;
uniform float4 flowRadii;
layout(color) uniform half4 flowColor;
uniform float flowStrength;
"""
    val flowSetup = if (!tinted) "" else """
    float flowDistance = 0.0;
    if (flowActive > 0.0) {
        flowDistance = max(sdElement(p, flowRect, flowRadii), 0.0);
    }
    half4 flowTint = half4(flowColor.rgb * half(flowStrength), half(flowStrength));
"""
    // Each member's touch light: `lightN` its point, reach and strength in group content space, and
    // `glowN` its colour — opaque, the strength dims it. A member nobody holds has no strength.
    val lightUniforms = (0 until count).joinToString("\n") { i ->
        "uniform float4 ${UniformLight[i]};\nlayout(color) uniform half4 ${UniformGlow[i]};"
    }

    return """
uniform shader content;
$BLUR_TAPS_FUNCTION

uniform float2 offset;
// 1 / aaWidth. The coverage ramp is evaluated on every pixel of the layer, including the ones the
// mask rejects, so the divide it used to do was the most-executed instruction in the program.
uniform float aaInverse;
// The union of the members widened by the reach of the blend, in group content space. See the
// reject at the top of main().
uniform float4 bounds;

// The widest refracting band of any member. Each member bends by its own band and displacement —
// `paramN.zw`, blended into `refraction` — and this only bounds where the gradient is worth taking.
uniform float refractionHeight;
uniform float depthEffect;
// 1 when the material asks for chromatic aberration: the seven taps of dispersed() instead of one.
uniform float dispersion;

layout(color) uniform half4 highlightColor;
uniform float highlightIntensity;
uniform float highlightWidth;
// The light direction itself, not its angle. As an angle this cost a cos() and a sin() on every
// pixel of the merged surface, on every frame, for a vector that is the same across the draw.
uniform float2 highlightNormal;
uniform float highlightFalloff;
// The second half of a two-band rim: a shadow tone drawn *outside* the silhouette, fading away
// from it. Zero width means the style has no outer band, and the rim is the single additive
// specular line every other style asks for.
layout(color) uniform half4 highlightShadeColor;
uniform float highlightShadeIntensity;
uniform float highlightOuterWidth;
// How much of the rim survives where the border faces away from the light.
uniform float highlightAmbient;
// 1 for a rim that is a solid line in its own colour, laid over the glass rather than added to it —
// GlassRimStyle.Outline. Every other style is light, and light adds.
uniform float highlightSolid;
$flowUniforms
$lightUniforms
$COLOR_MATRIX
$DISPERSION_FUNCTION
$TOUCH_LIGHT_FUNCTION

${mergePrelude(count, tinted)}

half4 main(float2 coord) {
    float2 p = coord + offset;

    // Four compares before any field is touched. The layer is deliberately larger than the surface
    // — it carries the blur halo the effect chain reserved — and every pixel out there used to
    // evaluate all twelve distance fields just to be thrown away by the coverage test below.
    // `bounds` is the union of the members widened past anything the blend can reach; see where it
    // is computed for why one blend radius is the bound however many members there are.
    if (p.x < bounds.x || p.y < bounds.y || p.x > bounds.z || p.y > bounds.w) {
        return half4(0.0);
    }
$flowSetup
${fieldSetup(count, tinted)}
    float sd = field.x;
    // |grad| of the blended field: 1 wherever it is a true distance, less where the blend has
    // compressed it — see the confidence below. Taken only inside the band, where something reads
    // it; elsewhere 1 stands in, which is exact for everything that follows: a pixel deeper inside
    // than the band is past every rim anyway, and |grad| never exceeds 1, so it could only have
    // pushed the solid line's distance further out.
    float fieldSlope = 1.0;

    float mask = clamp(0.5 - sd * aaInverse, 0.0, 1.0);
    // A rim with an outer band draws *past* the silhouette, so those pixels have to survive the
    // coverage test — they carry no glass at all, only the line. Everything further out still
    // leaves here, which is the great majority of what the halo-sized layer holds.
    if (mask <= 0.0 && sd >= highlightOuterWidth) {
        return half4(0.0);
    }

    // Interior pixels far from any edge use neither the normal nor its confidence.
    float band = max(refractionHeight, max(highlightWidth, highlightOuterWidth));
    float2 grad = float2(0.0, 0.0);
    float confidence = 0.0;
    if (abs(sd) < band) {
        // A smooth minimum is not a true distance function: inside the fillet the field is
        // compressed, and exactly on the medial axis of a neck the two surfaces' gradients cancel
        // out entirely, leaving a direction that is pure noise. A real distance field has
        // |grad| == 1 everywhere, so the magnitude is a free confidence measure — it collapses
        // towards zero precisely where the direction stops meaning anything.
        //
        // One reciprocal square root for both the magnitude and the direction, as in sdgElement().
        float slopeSquared = dot(fieldGradient, fieldGradient);
        float inverseSlope = inversesqrt(max(slopeSquared, 1e-8));
        fieldSlope = slopeSquared * inverseSlope;
        float gradientLength = fieldSlope;
        if (gradientLength > 0.0001) {
            grad = fieldGradient * inverseSlope;
            // Fade the bend out where the field stops behaving like a distance function. Without
            // this the neck of a merge gets full-strength refraction driven by a meaningless
            // direction, which reads as a dark crease torn across the bridge.
            //
            // The window is deliberately narrow and close to 1: a clean rim sits at |grad| == 1
            // and keeps its full lens, while anywhere the blend has compressed the field — the
            // whole interior of a fillet, not just its centre line — drops out quickly. Widening it
            // lets the crease creep back; lowering it starts eating the rim of ordinary shapes.
            confidence = smoothstep(0.55, 0.97, gradientLength);
        }
    }

    float2 sampleCoord = coord;
    // How far the fringe of chromatic aberration spreads the spectrum here; see dispersed().
    float2 dispersedCoord = float2(0.0);
    // This pixel's own band and displacement, blended from the members around it.
    float bandHeight = refraction.x;
    // `mask > 0` because the outer rim band brings pixels this far that carry no glass: bending a
    // backdrop that is about to be multiplied by zero coverage is a fetch spent on nothing.
    if (mask > 0.0 && bandHeight > 0.0 && -sd < bandHeight && confidence > 0.0) {
        float2 bendDirection = grad;
        if (depthEffect > 0.0) {
            // Dome about the *nearest member's* centre, not the group's. Using the group centre
            // made every separated member bulge outwards away from its neighbours, which read as a
            // lopsided lens rather than a sphere.
            float2 radial = p - field.yz;
            float radialSquared = dot(radial, radial);
            if (radialSquared > 1e-8) {
                bendDirection = normalize(grad + depthEffect * (radial * inversesqrt(radialSquared)));
            }
        }
        // min(sd, 0) is negative, so 1 - (-x)/h is 1 + x/h. The band differs from member to
        // member, so this is a divide per pixel now — but only for pixels inside a band.
        float bandInverse = 1.0 / bandHeight;
        float bend = refraction.y * confidence;
        float t = 1.0 + min(sd, 0.0) * bandInverse;
        float d = circleMap(t) * bend;
        sampleCoord = coord + d * bendDirection;
        if (dispersion > 0.0) {
            // A single pane's fringe, about the nearest member: the bend times x·y over the
            // member's half area, so it vanishes along both of its centre lines — and fades out
            // with the bend wherever the confidence takes the lens away across a neck.
            float2 fromCentre = p - field.yz;
            float halfArea = max(nearestHalfSize.x * nearestHalfSize.y, 1.0);
            dispersedCoord = d * bendDirection * (fromCentre.x * fromCentre.y / halfArea);
        }
    }

    // The backdrop — through the nine taps of a small blur the body's glass asked for, see
    // backdropAt() — or the seven taps of the fringe. Both are uniforms, so this is coherent, and
    // dispersed() itself falls back to one fetch wherever the fringe is too small to see.
    half4 color = dispersion > 0.0
        ? dispersed(sampleCoord, dispersedCoord)
        : filtered(backdropAt(sampleCoord));
    color *= half(mask);
$applyTint
    // The touch light, over the colour and under the rim: the glass lit from within, in its own
    // colour. Scaled by the coverage and held within it, so it never brightens past the silhouette
    // or its anti-aliased edge.
    color.rgb = min(color.rgb + lightMix * color.a, half3(color.a));

    if (highlightWidth > 0.0 && highlightIntensity > 0.0) {
        // A falloff of 1 makes pow() the identity, and 0 makes it a uniform hairline — both are
        // uniforms, so the branch is coherent and the common cases skip the call entirely.
        float aligned = abs(dot(grad, highlightNormal));
        float facing = highlightFalloff == 1.0 ? aligned : pow(aligned, highlightFalloff);
        // Same confidence gate throughout: the rim follows the surface normal, so where the normal
        // is noise the rim would otherwise streak a bright seam across the neck of a bridge.
        float directional = mix(facing, 1.0, highlightAmbient) * confidence;

        if (highlightOuterWidth > 0.0) {
            // Two bands, split at the silhouette and each fading away from it — the same drawn
            // edge a single pane wears, so a merged body is not the one thing in the app with a
            // different border. `highlightOuterWidth` is a uniform, so this branch is coherent.
            float inside = 1.0 - smoothstep(-0.5, 0.5, sd);
            float lit =
                (1.0 - smoothstep(0.0, highlightWidth, max(-sd, 0.0))) * inside;
            float shade =
                (1.0 - smoothstep(0.0, highlightOuterWidth, max(sd, 0.0))) * (1.0 - inside);

            // Premultiplied, and composited source-over rather than added: half of this rim
            // *darkens*, and adding cannot subtract light. The two bands never overlap, so summing
            // them is exactly compositing them against each other.
            half litAlpha = half(lit * directional * highlightIntensity);
            half shadeAlpha = half(shade * directional * highlightShadeIntensity);
            half4 rim = half4(
                highlightColor.rgb * litAlpha + highlightShadeColor.rgb * shadeAlpha,
                litAlpha + shadeAlpha
            );
            color = rim + color * (half(1.0) - rim.a);
        } else if (highlightSolid > 0.0) {
            // A line of one colour, crisp on its inner edge and laid over the glass, so a dark
            // line darkens. Its width is the stroke's own, not doubled.
            //
            // Measured as sd / |grad| rather than sd: where members fuse the blended field is
            // compressed, values near zero reach deep into the body, and a line drawn by sd alone
            // painted a blot into the middle of every junction. Divided by the slope the estimate
            // is a true distance again to first order — unchanged on a clean rim, where the slope
            // is one.
            float lineDistance = -sd / max(fieldSlope, 0.25);
            float line = (1.0 - smoothstep(highlightWidth - 1.0, highlightWidth, lineDistance)) * mask;
            half alpha = half(line * highlightIntensity);
            color = half4(highlightColor.rgb * alpha, alpha) + color * (half(1.0) - alpha);
        } else {
            float rim = 1.0 - smoothstep(0.0, highlightWidth, abs(sd));
            half intensity = half(rim * directional * mask * highlightIntensity);
            color.rgb += highlightColor.rgb * intensity;
            color.a = min(color.a + intensity, half(1.0));
        }
    }

    return color;
}"""
}

/**
 * Flat silhouette of the merged field. Drawn into a layer and blurred to produce a shadow that
 * follows the combined shape rather than each element's own box.
 */
internal fun mergeSilhouetteShader(count: Int): String =
    silhouetteSources.getOrPut(count) { buildSilhouetteShader(count) }

@Language("AGSL")
private fun buildSilhouetteShader(count: Int): String = """
uniform float2 offset;
// 1 / aaWidth, as in the glass pass — and it matters more here, because the shadow layer is two
// blur radii larger than the surface on every side and this runs on all of it.
uniform float aaInverse;
// The union of the members widened by the reach of the blend, in group content space.
uniform float4 bounds;
layout(color) uniform half4 color;
uniform float colorAlpha;

${mergePrelude(count, tinted = false)}

half4 main(float2 coord) {
    float2 p = coord + offset;

    // The same reject as the glass pass, and this is where it earns the most: the shadow layer is
    // two blur radii larger than the surface on every side, so the great majority of its pixels
    // lie outside the silhouette entirely and were evaluating every member's field to prove it.
    if (p.x < bounds.x || p.y < bounds.y || p.x > bounds.z || p.y > bounds.w) {
        return half4(0.0);
    }

    float sd = sceneDistance(p);
    float mask = clamp(0.5 - sd * aaInverse, 0.0, 1.0);
    // Premultiplied: colour is supplied opaque and the coverage carries the alpha.
    return half4(color.rgb, 1.0) * half(mask * colorAlpha);
}"""
