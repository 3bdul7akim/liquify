package com.hakim.liquify.internal

import org.intellij.lang.annotations.Language

/**
 * Upper bound on how many elements can take part in one merged surface.
 *
 * The merge pass evaluates every member's distance field per pixel, five times over (once for the
 * distance, four more for the central-difference gradient), so cost grows linearly with this.
 * Twelve is generous for the interactions this is meant for — a toolbar splitting apart, a menu
 * growing out of a button.
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
 * - `paramN` — `x` smoothing radius `k` in pixels, `y` tint strength, `zw` reserved
 * - `tintN`  — the member's colour, only when [tinted]
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
            if (tinted) append("\nlayout(color) uniform half4 ${UniformTint[i]};")
        }
    }

    val fold = buildString {
        appendLine("    float d0 = sdElement(coord, ${UniformRect[0]}, ${UniformRadii[0]});")
        appendLine("    float k0 = ${UniformParam[0]}.x;")
        for (i in 1 until count) {
            val p = i - 1
            appendLine("    float d$i = sdElement(coord, ${UniformRect[i]}, ${UniformRadii[i]});")
            // The pair blends over the wider of the two reaches, so raising one member's
            // merge() also thickens its bridges to quieter neighbours.
            appendLine("    float k$i = max(k$p, ${UniformParam[i]}.x);")
            appendLine("    d$i = smoothMin(d$p, d$i, k$i);")
        }
        appendLine("    return d${count - 1};")
    }

    // Same walk, but it also carries the centre of whichever member is actually nearest. Costs no
    // extra distance evaluations — only a running min — and it is what lets each member dome about
    // itself instead of about the group.
    //
    // Only the plain variant needs it as a function: the tinted one runs the same walk inline in
    // main(), because it has to come away with the colour as well and a function cannot return
    // seven floats.
    val fieldFunction = if (tinted) "" else buildString {
        appendLine("// .x = blended distance, .yz = centre of the nearest member.")
        appendLine("float3 sceneField(float2 coord) {")
        appendLine("    float e0 = sdElement(coord, ${UniformRect[0]}, ${UniformRadii[0]});")
        appendLine("    float d0 = e0;")
        appendLine("    float n0 = e0;")
        appendLine("    float2 c0 = ${UniformRect[0]}.xy;")
        appendLine("    float k0 = ${UniformParam[0]}.x;")
        for (i in 1 until count) {
            val p = i - 1
            appendLine("    float e$i = sdElement(coord, ${UniformRect[i]}, ${UniformRadii[i]});")
            appendLine("    float k$i = max(k$p, ${UniformParam[i]}.x);")
            appendLine("    float d$i = smoothMin(d$p, e$i, k$i);")
            appendLine("    float take$i = step(e$i, n$p);")
            appendLine("    float n$i = min(n$p, e$i);")
            appendLine("    float2 c$i = mix(c$p, ${UniformRect[i]}.xy, take$i);")
        }
        appendLine("    return float3(d${count - 1}, c${count - 1});")
        appendLine("}")
    }

    val weightFunction = if (!tinted) "" else """
// The factor smoothMin() blends two fields with: 1 keeps `a`, 0 keeps `b`. Pulling it out of
// smoothMin is what lets colour ride on exactly the blend the surface already uses.
float blendWeight(float a, float b, float k) {
    if (k <= 0.0) {
        return step(a, b);
    }
    return clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
}
"""

    return """
$uniforms

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        return coord.y <= 0.0 ? radii.y : radii.z;
    } else {
        return coord.y <= 0.0 ? radii.x : radii.w;
    }
}

float sdElement(float2 coord, float4 rect, float4 radii) {
    float2 local = coord - rect.xy;
    float2 halfSize = rect.zw;
    float radius = radiusAt(local, radii);
    float2 cornerCoord = abs(local) - (halfSize - float2(radius));
    return length(max(cornerCoord, 0.0)) - radius + min(max(cornerCoord.x, cornerCoord.y), 0.0);
}

// Quadratic polynomial smooth minimum. Below a separation of k the two fields blend into a single
// surface with a concave fillet instead of a hard crease -- the "gooey" bridge.
float smoothMin(float a, float b, float k) {
    if (k <= 0.0) {
        return min(a, b);
    }
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sceneDistance(float2 coord) {
$fold}

$fieldFunction
// Returns the surface normal in .xy and how trustworthy it is in .z.
//
// A smooth minimum is not a true distance function: inside the fillet the field is compressed, and
// exactly on the medial axis of a neck the two surfaces' gradients cancel out entirely, leaving a
// direction that is pure noise. A real distance field has |grad| == 1 everywhere, so the magnitude
// is a free confidence measure — it collapses towards zero precisely where the direction stops
// meaning anything.
float3 sceneGradient(float2 coord) {
    float e = 1.0;
    float dx = sceneDistance(coord + float2(e, 0.0)) - sceneDistance(coord - float2(e, 0.0));
    float dy = sceneDistance(coord + float2(0.0, e)) - sceneDistance(coord - float2(0.0, e));
    float2 g = float2(dx, dy) / (2.0 * e);
    float len = length(g);
    if (len < 0.0001) {
        return float3(0.0, 0.0, 0.0);
    }
    return float3(g / len, len);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
$weightFunction"""
}

/**
 * Resolves the blended distance, the nearest member's centre and — when [tinted] — the blended
 * colour, as straight-line code inside `main()`.
 *
 * The colour has to ride on the same walk that produces the distance: it is mixed with the very
 * factor `smoothMin` blends the two fields with, so it follows the geometry of the fusion rather
 * than a direction through the group. Pure inside each member, and 50/50 across the neck of a
 * bridge. Doing it in a second pass would mean evaluating every member's field twice per pixel.
 *
 * Declares `field` and, for the tinted variant, `tintMix` — a premultiplied colour whose `.rgb` is
 * already scaled by its `.a`, because mixing straight RGBA would drag a colour towards black
 * wherever its neighbour is untinted: "no tint" carries no hue to mix with.
 */
private fun fieldSetup(count: Int, tinted: Boolean): String {
    if (!tinted) return "    float3 field = sceneField(p);"

    return buildString {
        appendLine("    float e0 = sdElement(p, ${UniformRect[0]}, ${UniformRadii[0]});")
        appendLine("    float d0 = e0;")
        appendLine("    float n0 = e0;")
        appendLine("    float2 c0 = ${UniformRect[0]}.xy;")
        appendLine("    float k0 = ${UniformParam[0]}.x;")
        appendLine(
            "    half4 t0 = half4(${UniformTint[0]}.rgb * half(${UniformParam[0]}.y), " +
                "half(${UniformParam[0]}.y));"
        )
        for (i in 1 until count) {
            val p = i - 1
            appendLine("    float e$i = sdElement(p, ${UniformRect[i]}, ${UniformRadii[i]});")
            appendLine("    float k$i = max(k$p, ${UniformParam[i]}.x);")
            appendLine("    float h$i = blendWeight(d$p, e$i, k$i);")
            appendLine("    float d$i = smoothMin(d$p, e$i, k$i);")
            appendLine("    float take$i = step(e$i, n$p);")
            appendLine("    float n$i = min(n$p, e$i);")
            appendLine("    float2 c$i = mix(c$p, ${UniformRect[i]}.xy, take$i);")
            appendLine(
                "    half4 m$i = half4(${UniformTint[i]}.rgb * half(${UniformParam[i]}.y), " +
                    "half(${UniformParam[i]}.y));"
            )
            appendLine("    half4 t$i = mix(m$i, t$p, half(h$i));")
        }
        val last = count - 1
        appendLine("    float3 field = float3(d$last, c$last);")
        append("    half4 tintMix = t$last;")
    }
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

    return """
uniform shader content;

uniform float2 offset;
uniform float aaWidth;

uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

layout(color) uniform half4 highlightColor;
uniform float highlightIntensity;
uniform float highlightWidth;
uniform float highlightAngle;
uniform float highlightFalloff;

${mergePrelude(count, tinted)}

half4 main(float2 coord) {
    float2 p = coord + offset;
${fieldSetup(count, tinted)}
    float sd = field.x;

    float mask = clamp(0.5 - sd / aaWidth, 0.0, 1.0);
    if (mask <= 0.0) {
        return half4(0.0);
    }

    // Interior pixels far from any edge need neither the gradient nor its four extra field
    // evaluations, which is where most of the saving on a large merged surface comes from.
    float band = max(refractionHeight, highlightWidth);
    float2 grad = float2(0.0, 0.0);
    float confidence = 0.0;
    if (abs(sd) < band) {
        float3 gradient = sceneGradient(p);
        grad = gradient.xy;
        // Fade the bend out where the field stops behaving like a distance function. Without this
        // the neck of a merge gets full-strength refraction driven by a meaningless direction,
        // which reads as a dark crease torn across the bridge.
        //
        // The window is deliberately narrow and close to 1: a clean rim sits at |grad| == 1 and
        // keeps its full lens, while anywhere the blend has compressed the field — the whole
        // interior of a fillet, not just its centre line — drops out quickly. Widening it lets the
        // crease creep back; lowering it starts eating the rim of ordinary shapes.
        confidence = smoothstep(0.55, 0.97, gradient.z);
    }

    float2 sampleCoord = coord;
    if (refractionHeight > 0.0 && -sd < refractionHeight && confidence > 0.0) {
        float2 bendDirection = grad;
        if (depthEffect > 0.0) {
            // Dome about the *nearest member's* centre, not the group's. Using the group centre
            // made every separated member bulge outwards away from its neighbours, which read as a
            // lopsided lens rather than a sphere.
            float2 radial = p - field.yz;
            float radialLength = length(radial);
            if (radialLength > 0.0001) {
                bendDirection = normalize(grad + depthEffect * (radial / radialLength));
            }
        }
        float d = circleMap(1.0 - -min(sd, 0.0) / refractionHeight) * refractionAmount * confidence;
        sampleCoord = coord + d * bendDirection;
    }

    half4 color = content.eval(sampleCoord) * half(mask);
$applyTint
    if (highlightWidth > 0.0 && highlightIntensity > 0.0) {
        float rim = 1.0 - smoothstep(0.0, highlightWidth, abs(sd));
        float2 lightDirection = float2(cos(highlightAngle), sin(highlightAngle));
        float facing = pow(abs(dot(grad, lightDirection)), highlightFalloff);
        // Same confidence gate: the rim follows the surface normal, so where the normal is noise
        // the rim would otherwise streak a bright seam across the neck.
        half intensity = half(rim * facing * mask * highlightIntensity * confidence);
        color.rgb += highlightColor.rgb * intensity;
        color.a = min(color.a + intensity, half(1.0));
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
uniform float aaWidth;
layout(color) uniform half4 color;
uniform float colorAlpha;

${mergePrelude(count, tinted = false)}

half4 main(float2 coord) {
    float sd = sceneDistance(coord + offset);
    float mask = clamp(0.5 - sd / aaWidth, 0.0, 1.0);
    // Premultiplied: colour is supplied opaque and the coverage carries the alpha.
    return half4(color.rgb, 1.0) * half(mask * colorAlpha);
}"""
