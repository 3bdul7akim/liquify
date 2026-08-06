/*
 * The rounded-rectangle signed distance field, its gradient and the refraction/highlight programs
 * in this file are derived from Kyant0's AndroidLiquidGlass:
 *
 *     https://github.com/Kyant0/AndroidLiquidGlass
 *     Copyright 2025 Kyant — Licensed under the Apache License, Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.hakim.liquify.internal

import org.intellij.lang.annotations.Language

/**
 * Signed distance field of a rounded rectangle with four independent corner radii, plus its
 * gradient. `radii` is ordered top-left, top-right, bottom-right, bottom-left.
 */
@Language("AGSL")
internal const val ROUNDED_RECT_SDF: String = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        return coord.y <= 0.0 ? radii.y : radii.z;
    } else {
        return coord.y <= 0.0 ? radii.x : radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}"""

/** Edge refraction: bends the backdrop inside a band of `refractionHeight` around the border. */
@Language("AGSL")
internal const val REFRACTION_SHADER: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );

    return content.eval(coord + d * grad);
}"""

/**
 * Edge refraction with chromatic aberration: the red end of the spectrum is refracted slightly
 * more than the blue end, which is what makes a real lens fringe at its rim.
 */
@Language("AGSL")
internal const val REFRACTION_DISPERSION_SHADER: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity =
        chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""

/**
 * Weights one stop of a progressive blur by how far inside the outline each pixel sits.
 *
 * The element is blurred several times over at increasing radii, and each of those copies runs
 * through this program with a different `stopIndex`. The weight is a hat function centred on the
 * stop, so a pixel is only ever mixed from the two copies whose radii bracket the blur it should
 * have — never from the sharp original and the heaviest blur at once.
 *
 * That is what makes the gradient read as gradual. Cross-fading sharp against fully blurred does
 * not produce an intermediate blur, it produces sharp detail at reduced amplitude laid over a soft
 * base; detail stays legible until the mix is nearly all blur and then disappears at once, so the
 * visible transition collapses into a narrow band however wide the ramp is. Blending between
 * neighbouring radii instead means the *kernel* grows across the band, which is a real progression.
 *
 * The hats sum to exactly one at every position, so the branches recombine into the original image
 * rather than darkening or blowing out where they overlap. Output is premultiplied — scaling the
 * evaluated colour scales rgb and alpha together — which keeps that true for a translucent backdrop
 * too.
 */
@Language("AGSL")
internal const val GRADIENT_BLUR_SHADER: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float clearWidth;
uniform float fadeWidth;
uniform float stopIndex;
uniform float stopCount;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    // How far inside the outline this pixel sits. Negative outside, which clamps away below.
    float depth = -sdRoundedRect(centeredCoord, halfSize, radius);

    float t = clamp((depth - clearWidth) / max(fadeWidth, 0.0001), 0.0, 1.0);

    // Quintic smoothstep: zero first *and* second derivative at both ends, so the ramp neither
    // starts nor stops with a kink that the eye could read as a drawn line.
    float eased = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);

    float s = eased * stopCount;
    float weight = max(0.0, 1.0 - abs(s - stopIndex));

    return content.eval(coord) * half(weight);
}"""

/** Directional rim light: brightest where the border normal faces `angle`. */
@Language("AGSL")
internal const val HIGHLIGHT_DEFAULT_SHADER: String = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float intensity = pow(abs(dot(grad, normal)), falloff);
    return color * intensity;
}"""

/** Rim light that goes white on the lit side and black on the unlit side. */
@Language("AGSL")
internal const val HIGHLIGHT_AMBIENT_SHADER: String = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}"""

/**
 * Rim light that follows a pointer.
 *
 * The border lights up on the side facing `pointer`, and falls off with distance from it, so a
 * finger dragging along a glass control drags the specular hotspot with it. This is the border
 * half of the "energises with light on interaction" behaviour; the interior glow is drawn by
 * `Modifier.interactiveHighlight`.
 */
@Language("AGSL")
internal const val HIGHLIGHT_DYNAMIC_SHADER: String = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float falloff;
uniform float2 pointer;
uniform float pointerFocus;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);

    float2 toPointer = pointer - centeredCoord;
    float len = length(toPointer);
    float2 normal = len > 0.0001 ? toPointer / len : float2(0.0, -1.0);

    float facing = pow(max(dot(grad, normal), 0.0), falloff);

    float reach = max(halfSize.x, halfSize.y) * 2.0;
    float proximity = 1.0 - smoothstep(0.0, reach, distance(coord, pointer + halfSize));
    float intensity = mix(facing, facing * proximity, pointerFocus);

    return color * intensity;
}"""
