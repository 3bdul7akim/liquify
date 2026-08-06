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
 * - `paramN` — `x` smoothing radius `k` in pixels, `yzw` reserved
 */
@Language("AGSL")
private fun mergePrelude(count: Int): String {
    val uniforms = (0 until count).joinToString("\n") { index ->
        "uniform float4 rect$index;\nuniform float4 radii$index;\nuniform float4 param$index;"
    }

    val fold = buildString {
        appendLine("    float d0 = sdElement(coord, rect0, radii0);")
        appendLine("    float k0 = param0.x;")
        for (index in 1 until count) {
            val previous = index - 1
            appendLine("    float d$index = sdElement(coord, rect$index, radii$index);")
            // The pair blends over the wider of the two reaches, so raising one member's
            // merge() also thickens its bridges to quieter neighbours.
            appendLine("    float k$index = max(k$previous, param$index.x);")
            appendLine("    d$index = smoothMin(d$previous, d$index, k$index);")
        }
        appendLine("    return d${count - 1};")
    }

    // Same walk, but it also carries the centre of whichever member is actually nearest. Costs no
    // extra distance evaluations — only a running min — and it is what lets each member dome about
    // itself instead of about the group.
    val fieldFold = buildString {
        appendLine("    float e0 = sdElement(coord, rect0, radii0);")
        appendLine("    float d0 = e0;")
        appendLine("    float n0 = e0;")
        appendLine("    float2 c0 = rect0.xy;")
        appendLine("    float k0 = param0.x;")
        for (index in 1 until count) {
            val previous = index - 1
            appendLine("    float e$index = sdElement(coord, rect$index, radii$index);")
            appendLine("    float k$index = max(k$previous, param$index.x);")
            appendLine("    float d$index = smoothMin(d$previous, e$index, k$index);")
            appendLine("    float take$index = step(e$index, n$previous);")
            appendLine("    float n$index = min(n$previous, e$index);")
            appendLine("    float2 c$index = mix(c$previous, rect$index.xy, take$index);")
        }
        val last = count - 1
        appendLine("    return float3(d$last, c$last);")
    }

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

// .x = blended distance, .yz = centre of the nearest member.
float3 sceneField(float2 coord) {
$fieldFold}

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
"""
}

/**
 * The merged glass pass: refracts the backdrop through the combined distance field, clips to it
 * and lays a rim light along the merged silhouette.
 *
 * Doing all three in one program is what makes merging look continuous — the refraction and the
 * rim follow the *bridge* between two elements, not the outline of either one.
 */
@Language("AGSL")
internal fun mergeGlassShader(count: Int): String = """
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

${mergePrelude(count)}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float3 field = sceneField(p);
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

/**
 * Flat silhouette of the merged field. Drawn into a layer and blurred to produce a shadow that
 * follows the combined shape rather than each element's own box.
 */
@Language("AGSL")
internal fun mergeSilhouetteShader(count: Int): String = """
uniform float2 offset;
uniform float aaWidth;
layout(color) uniform half4 color;
uniform float colorAlpha;

${mergePrelude(count)}

half4 main(float2 coord) {
    float sd = sceneDistance(coord + offset);
    float mask = clamp(0.5 - sd / aaWidth, 0.0, 1.0);
    // Premultiplied: colour is supplied opaque and the coverage carries the alpha.
    return half4(color.rgb, 1.0) * half(mask * colorAlpha);
}"""
