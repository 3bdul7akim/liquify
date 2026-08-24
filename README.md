# Liquify

[![build](https://github.com/3bdul7akim/liquify/actions/workflows/build.yml/badge.svg)](https://github.com/3bdul7akim/liquify/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)

**Liquid glass for Jetpack Compose on Android** — real edge refraction, and glass elements that **merge and separate like liquid**.

```kotlin
val backdrop = rememberLayerBackdrop()          // 1. somewhere to read pixels from

Box(Modifier.fillMaxSize()) {
    // 2. mark whatever should show through the glass
    Image(wallpaper, null, Modifier.fillMaxSize().layerBackdrop(backdrop))

    // 3. make something out of glass
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .liquify(Capsule(), backdrop = backdrop, dragging = true, interactiveHighlight = true)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Default.Favorite, null)
    }
}
```

---

## About this project

Liquify began as a **clone** of [Kyant0/AndroidLiquidGlass][alg] — not a fork, which in hindsight
would have been the tidier choice for attribution. Kyant0's work is excellent and solved the hard
part: the rounded-rectangle signed distance field and the refraction that makes glass read as glass
rather than as a blurred rectangle. Credit for that foundation belongs to him.

What I wanted on top of it:

- **Merging.** Glass that fuses into one body when two elements approach and pinches apart as they
  separate, with each member's colour flowing through the bridge into its neighbour's. That was the
  headline feature and it did not exist in the original at all.
- **One-line interactions.** `dragging`, `stretching` and `interactiveHighlight` as plain booleans
  on the modifier. The reference had these behaviours, but only inside its demo app — you had to
  copy them out by hand.
- **App-wide material theming.** Declare the glass recipe once at the root and let every pane in
  the app follow it, live, without touching a single call site.
- **A smaller, faster API.** One modifier instead of two, context-resolved backdrops, sensible
  defaults — so the common case is one line and the uncommon case is still reachable.

### Status

Early, and honest about it. Expect **future updates**. **Feature requests are very welcome** —
open an issue, describe what you're trying to build, and I'll help as soon as I can.

---

## Install
[![Maven Central](https://img.shields.io/maven-central/v/io.github.3bdul7akim/liquify)](https://central.sonatype.com/artifact/io.github.3bdul7akim/liquify)
```kotlin
dependencies {
    implementation("io.github.3bdul7akim:liquify:1.1.0")
}
```

**Requirements:** `minSdk 21`, Compose 1.11+, Kotlin 2.x. Effects light up progressively and
nothing ever throws — an unsupported effect is skipped, not guarded at the call site:

| API level | What you get |
| --- | --- |
| 21–30 | Shape, rim highlight, drop shadow. |
| 31+ (Android 12) | + blur, tint, vibrancy, opacity, inner shadow, `materialize`, `motionBlur`. |
| 33+ (Android 13) | + refraction (`lens`), shader rims, chromatic aberration, **merging**, **gradient blur**, touch illumination. |

---

## The one modifier: `liquify`

There used to be two entry points, `drawBackdrop` and `liquify`. **`drawBackdrop` is gone.**
Everything it could do — custom effect stacks, animatable rim and shadow, all four draw hooks,
backdrop export — now lives in `liquify`, so there is one name to learn and one place to look.

Migrating is mechanical:

```kotlin
// before
Modifier.drawBackdrop(backdrop = b, shape = { Capsule() }, effects = { … })

// after — same arguments, `backdrop` is now optional
Modifier.liquify(shape = { Capsule() }, effects = { … }, backdrop = b)
```

### Three overloads, and when to reach for which

| | **A.** `liquify(shape: Shape, …)` | **B.** `liquify(shape: () -> Shape, …)` | **C.** `liquify(shape, effects, …)` |
| --- | --- | --- | --- |
| Shape | fixed | re-read every frame | re-read every frame |
| Material | a `GlassMaterial` | a `GlassMaterial` | a hand-written effects block |
| Inside a group | merges automatically | merges automatically | only if you call `merge()` |
| Rim / shadow | plain values | plain values | lambdas, so they can animate |
| Draw hooks | `onDrawSurface` | `onDrawSurface` | all four + `exportedBackdrop` |
| Use it for | almost everything | animated corner radius or morphing outlines | anything the material can't express |

**A — the everyday one.** Ninety percent of call sites.

```kotlin
Modifier.liquify(
    shape = RoundedRectangle(28.dp),   // the outline; squircle corners
    material = GlassMaterial.Regular,  // defaults to LocalGlassMaterial.current
    backdrop = null,                   // null = inherit from ProvideBackdrop / the group
    tint = Color.Unspecified,          // overrides GlassMaterial.tint; in a group it is what the
                                       // merge pass blends with the neighbours' colours
    gradientBlur = false,              // frost the middle, keep the rim clear
    highlight = Highlight.Default,     // specular rim, or null for none
    shadow = Shadow.Default,           // drop shadow, or null
    innerShadow = null,                // recessed look
    interaction = null,                // share one gesture across several elements
    dragging = interaction != null,    // lean towards the finger — so: off unless you pass one
    stretching = false,                // …and swell/stretch while doing it (always opt-in)
    interactiveHighlight = interaction != null,  // light up under the fingertip
    layerBlock = null,                 // scale/rotate, inverted for the backdrop
    onDrawSurface = null               // drawn over the glass, under your content
)
```

**B — identical, but the shape is a lambda.** Use it when the outline itself animates. Reading the
shape during draw costs a redraw instead of a recomposition:

```kotlin
val progress by animateFloatAsState(if (open) 1f else 0f)

Modifier.liquify({ RoundedRectangle(lerp(30.dp, 26.dp, progress)) }, dragging = true)
```

**C — the full form.** You write the effect stack yourself, and rim, shadow and inner shadow become
lambdas so they can be driven by a live value:

```kotlin
Modifier.liquify(
    shape = { Capsule() },
    effects = {                                   // the material itself; call order = GPU order
        vibrancy()
        blur(8.dp.toPx() * (1f - press))
        lens(refractionHeight = 24.dp.toPx(), refractionAmount = 32.dp.toPx())
    },
    backdrop = backdrop,                          // still optional
    highlight = { Highlight.Default.copy(alpha = press) },   // re-read every frame
    shadow = { Shadow(alpha = press) },
    innerShadow = { InnerShadow(radius = 8.dp * press) },
    layerBlock = { scaleX = scale; scaleY = scale },
    exportedBackdrop = exported,                  // let another element refract this one
    onDrawBehind = { … },                         // under the glass
    onDrawBackdrop = { draw -> draw() },          // wrap/filter the backdrop draw
    onDrawSurface = { drawRect(containerColor) }, // over the glass, under your content
    onDrawFront = { … },                          // over everything
    interaction = shared,
    dragging = true,
    stretching = true,
    interactiveHighlight = true
)
```

> **One gotcha worth knowing.** Overload C does **not** inherit the group's material or join its
> merged surface automatically. An explicit effect stack means you are in control — call
> `merge()` inside `effects` to opt in, and `mergeTint()` alongside it to give that share of the
> surface a colour. That is exactly what lets a child stay a separate pane of glass inside a group.

---

## Materials

A `GlassMaterial` is a preset for the whole effect stack, so most call sites need one word instead
of five lines.

```kotlin
GlassMaterial(
    blurRadius = 10.dp,          // how far the backdrop is blurred
    refractionHeight = 22.dp,    // thickness of the refracting band at the rim
    refractionAmount = 30.dp,    // how sharply that band bends the backdrop
    saturation = 1.15f,          // vibrancy; 1f for none
    tint = Color.Unspecified,    // blended into the backdrop; alpha is the amount
    opacity = 1f,                // below 1f lets the raw background through
    depthEffect = true,          // dome the pane into a lens instead of a flat sheet
    chromaticAberration = false, // coloured fringe at the rim (7 samples/px instead of 1)
    gradientBlur = false         // frost the middle only, leave the rim clear
)
```

Three presets ship with it:

| Preset | blur | refraction H/A | saturation | tint |
| --- | --- | --- | --- | --- |
| `Regular` | 8.dp | 20 / 35.dp | 1.15 | none |
| `Clear` | 0.dp | 20 / 35.dp | 1.0 | none |
| `Thick` | 22.dp | 32 / 40.dp | 1.6 | white 1 % |

`tint` is deliberately `Color.Unspecified` by default — the material stays colour-neutral so your
app can tint its glass per theme rather than inherit a fixed one.

### App-wide theming

Declare the glass once at the root and everything below follows it — **live**, with no restart and
no call site rewritten:

```kotlin
var material by remember { mutableStateOf(GlassMaterial.Regular) }

ProvideGlassMaterial(material) {
    App(onPick = { material = it })   // every pane re-renders in the new glass next frame
}
```

Every `liquify` call resolves `material` from `LocalGlassMaterial` by default, and so does
`LiquidGlassGroup`. Passing `material` explicitly to a single element still wins.

For a group, which takes an effect *block* rather than a material, use the matching helper — it
returns a stable instance, which matters because a freshly allocated lambda would make the group
rebuild its inherited defaults every recomposition:

```kotlin
LiquidGlassGroup(backdrop, effects = rememberGlassEffects(GlassMaterial.Clear)) { … }
```

---

## Effects

Effects are extension functions on `BackdropEffectScope`, and **call order is GPU order**. The
scope is a `Density`, so `16.dp.toPx()` works directly inside the block.

| Effect | What it does |
| --- | --- |
| `blur(radius, edgeTreatment)` | Gaussian blur of the backdrop. |
| `gradientBlur(radius, fadeWidth, clearWidth, steps, edgeTreatment)` | Blur that ramps up from nothing at the border. |
| `lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` | Bends the backdrop at the rim. The one that makes it glass. |
| `vibrancy(amount)` | Saturation boost so colours survive the blur. |
| `tint(color)` | Blends the backdrop toward a colour, *before* refraction. |
| `colorControls(brightness, contrast, saturation)` | All three in one pass. |
| `opacity(alpha)` | Lets the raw background through. |
| `colorFilter(filter)` | Any `ColorFilter`. |
| `merge(amount)` / `merge(radius: Dp)` | Joins the enclosing group's merged surface. |
| `mergeTint(color)` | Colours this element's share of that surface, blended with its neighbours'. |
| `material(material, mergeAmount)` | Applies a whole `GlassMaterial` in the right order. |
| `effect(renderEffect)` / `runtimeShaderEffect(key, agsl, name) { }` | Escape hatches for your own AGSL. |

**Blur before lens.** The refraction then bends already-blurred content, which is what reads as
thick glass. Reverse them and you get a blurred sticker.

### Gradient blur

A frosted droplet is not evenly cloudy: its rim stays clear and acts as a lens, and only the middle
is frosted. A Gaussian blur has one strength everywhere and cannot express that, so `gradientBlur`
blurs the backdrop several times at increasing radii and weights those copies by how far inside the
outline each pixel sits.

```kotlin
// short form: one boolean, radius comes from the material
Modifier.liquify(Capsule(), gradientBlur = true)

// long form
effects = {
    vibrancy()
    lens(refractionHeight = 22.dp.toPx(), refractionAmount = 30.dp.toPx())
    gradientBlur(
        radius = 20.dp.toPx(),      // how cloudy the middle gets
        fadeWidth = 5.dp.toPx(),    // where the ramp sits — fixed, nothing else moves it
        clearWidth = 0f,            // optional dead band at the very border
        steps = 2                   // how smooth the ramp is (see below)
    )
}
```

Three knobs, one job each: `radius` is *how* blurry, `fadeWidth` is *where*, `steps` is *how
smooth*. Nothing bleeds between them — turning up `refractionHeight` does not eat into the blur.

> **`gradientBlur` goes *after* `lens()`, unlike `blur()`.** `lens` samples *inwards* by up to
> `refractionAmount`, so a rim pixel takes its colour from deep inside the element. Blur that
> interior first and the lens drags the soft version back out over the crisp edge the effect exists
> for. `material()` handles this ordering for you automatically.

`steps` deserves a warning. One step is a plain cross-fade between sharp and fully blurred, and
that never looks gradual however you ease it — detail stays legible until the mix is nearly all
blur and then vanishes at once. More steps means the *kernel* genuinely grows across the band. But
measured on a Galaxy S21 FE, scrolling seven of these at once:

| `steps` | janky frames | median frame |
| --- | --- | --- |
| 1 | 7.9 % | 17 ms |
| **2 (default)** | **9.3 %** | **16 ms** |
| 3 | 43 % | 32 ms |
| 4 | 57 % | 44 ms |

The second stop is free and the third is not — a cliff, not a slope. Two is the default for that
reason. Three is affordable for a single large surface, ruinous for a list of them. **Measure
before raising it.**

Falls back to a plain `blur()` below API 33, for shapes that aren't rounded rectangles, and inside
a group (whose merged field has no single outline to measure against).

---

## Backdrops

A backdrop is the source of pixels the glass refracts. It is **re-recorded every frame**, so glass
tracks a scrolling list or a moving wallpaper rather than freezing a snapshot.

```kotlin
rememberLayerBackdrop()                 // capture real composables via Modifier.layerBackdrop
rememberCanvasBackdrop { /* draw */ }   // procedural, nothing captured
rememberCombinedBackdrop(a, b)          // stack them, b over a
rememberBackdrop(a) { draw -> … }       // wrap one to filter or transform its drawing
emptyBackdrop()                         // nothing behind
```

Set it once per screen and stop passing it around:

```kotlin
ProvideBackdrop(backdrop) {
    Column(Modifier.liquify(RoundedRectangle(28.dp))) { … }   // no backdrop argument
}
```

Order matters: an explicit `backdrop` argument wins, then the enclosing group's, then
`LocalBackdrop`. With none of the three, `liquify` throws rather than silently rendering an empty
pane.

---

## Merging — the gooey effect

The headline feature, and the reason this project exists. Put elements in a `LiquidGlassGroup` and
they fuse:

```kotlin
ProvideBackdrop(backdrop) {          // the group resolves its backdrop from here, like liquify does
    LiquidGlassGroup {
        Box(Modifier.offset { IntOffset(a, 0) }.size(88.dp).liquify(Capsule()))
        Box(Modifier.offset { IntOffset(b, 0) }.size(88.dp).liquify(Capsule()))
    }
}
```

Members declare nothing but their shape. Backdrop, material and merge strength all come from the
group, because every member of one surface is by definition made of the same glass:

```kotlin
LiquidGlassGroup(
    backdrop = backdrop,              // optional — null inherits it from ProvideBackdrop
    modifier = Modifier.fillMaxWidth().height(420.dp),
    effects = rememberGlassEffects(GlassMaterial.Thick),  // shared material
    merge = 0.75f,                    // shared reach; 0f lays out together without fusing
    highlight = { Highlight.Default },// rim of the *combined* silhouette
    shadow = { Shadow.Default },      // shadow of the combined silhouette
    contentAlignment = Alignment.BottomCenter
) { … }
```

Members stop rendering their own glass. The group evaluates every member's signed distance field,
blends them with a **smooth minimum**, and renders one surface. Two elements approaching each other
grow a liquid bridge well before they touch, fuse into a single body, then pinch apart again — with
refraction, rim light and shadow following the combined outline the whole way, so there is never a
seam where two members overlap.

`merge(amount)` is relative to the element's own size; `merge(24.dp)` sets an absolute reach, which
is what you want when differently sized elements have to bridge consistently.

A bridge only appears once the reach is wider than the gap it has to cross — the smooth minimum
pulls the surface in by at most a quarter of `k`, so two elements fuse when **`k` exceeds twice the
distance between them**. With `merge(amount)` that works out to `amount > 4 × gap / short side`: two
48 dp buttons 8 dp apart need roughly `0.7` before anything happens, and look properly gooey around
`1.2`. Below that they simply lean towards one another without ever touching.

### Colours that mix

Members keep their own colour, and the merge blends them. A member's `tint` is not drawn by the
member — that would stop dead at its own bounds and cut a seam across the bridge — but handed to the
group, which mixes the colours with **the very same weights it blends the distance fields with**:

```kotlin
LiquidGlassGroup(backdrop, merge = 1.2f) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Red.copy(alpha = 0.75f)))
        Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Blue.copy(alpha = 0.75f)))
    }
}
```

Apart, the two stay pure red and pure blue. As they approach, the colours flow into one another
exactly as far as their surfaces do, and the neck of a finished bridge carries the mixture of both.
The alpha is the *strength* of the tint, as everywhere else — an untinted neighbour therefore
dilutes a colour towards clear glass rather than dragging it towards black.

The material overload routes `tint` here for you. From a hand-written `effects` block, call
[`mergeTint(color)`](#effects) yourself. Only groups whose members actually declare a colour compile
the tinted program, so this costs nothing when it is not used.

**Up to twelve members per group.** Cost is linear in that count — each pixel evaluates every
member's field five times, once for the distance and four for the gradient — so the group renders
only over the union of its members, never the whole screen. Keep groups tight.

> `LiquidGlassGroup` lays its children out as a **`Box`**, so they stack. Wrap them in a `Row` or
> `Column` to place them apart. (This caught me out more than once.)

Outside a group, `merge()` does nothing, so the same component works standalone.

---

## Touch reactions

Glass flexes and lights up when you touch it. Three independent booleans, nothing to hoist:

```kotlin
Modifier.liquify(
    Capsule(),
    dragging = true,             // leans towards the finger, tanh-damped
    stretching = true,           // …and swells / stretches along the direction of travel
    interactiveHighlight = true  // hotspot under the fingertip, trailing on a spring
)
```

- **`dragging`** — the element follows the finger. `tanh` bounds the translation to the element's
  own short side no matter how far you drag, so it leans but never detaches.
- **`stretching`** — the deformation half. **Off by default and it requires `dragging`**: something
  that stays put has nothing to stretch towards, so `stretching = true` alone does nothing. The
  swell is at least 6 % of the short side, so large surfaces react visibly instead of growing by a
  couple of pixels.
- **`interactiveHighlight`** — a glow rises under the fingertip over a faint overall lift.

**Taps still work.** Nothing is consumed until the finger crosses the platform touch slop, so a
press that never really moves reaches your `clickable` intact — while the glow and the flex still
start on touch-down, because reacting instantly is the whole point of the material.

Pass an explicit `interaction` only when **several elements must share one gesture** — a tab bar
whose whole panel glows from wherever its indicator sits:

```kotlin
val shared = rememberInteractiveHighlight(
    position = { size, offset -> … },   // remap where the light comes from
    glowColor = Color.White,
    glowIntensity = 0.15f,
    dragScaleAmount = 4.dp,             // absolute swell; a 6 % relative floor also applies
    dragResponse = 0.15f,               // how eagerly it follows the finger
    tracksConsumedDrags = false          // keep following after a sibling claims the drag
)

Modifier.liquify(Capsule(), interaction = shared, dragging = false)   // shared glow, no flex
```

Supplying one turns `dragging` and `interactiveHighlight` on by default; `stretching` stays opt-in.

Inside a `LiquidGlassGroup` the drag becomes a property of the **whole merged body**: it is applied
during *layout* rather than as a layer transform, so the group hears about every frame and the
bridge stretches along with the element instead of tearing away from it. The illumination stays
local — it is clipped to the element that was actually touched and does not run across the bridge
onto its neighbours. Nothing extra to write in either case.

### Damped drag — for sliders, toggles and tab bars

Controls need more than a press reaction: they need a *value* with physics.

```kotlin
val drag = rememberDampedDragAnimation(
    initialValue = 0f,
    valueRange = 0f..1f,
    visibilityThreshold = 0.001f,
    initialScale = 1f,
    pressedScale = 1.5f,                    // above 1f makes it swell into the finger
    orientation = Orientation.Horizontal,   // movement across this axis goes to the scroller
    onDragStopped = { onValueChange(targetValue) },
    onDrag = { _, amount -> updateValue(targetValue + amount.x / width) }
)

Modifier
    .then(drag.modifier)
    .liquify(
        shape = { Capsule() },
        effects = {
            // frosted at rest, a lens while held — you can read the value through the thumb
            blur(8.dp.toPx() * (1f - drag.pressProgress))
            lens(10.dp.toPx() * drag.pressProgress, 14.dp.toPx() * drag.pressProgress)
        },
        layerBlock = {
            scaleX = drag.scaleX
            scaleY = drag.scaleY
            val v = drag.velocity / 10f
            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)   // stretch along travel
            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)   // pinch across it
        }
    )
```

It claims the gesture only once it is recognised as belonging to this control, and only along its
own axis — so a vertical swipe that happens to start on a horizontal slider still scrolls the page,
and does not commit a value on release.

Both detectors are built on `inspectDragGestures`, which reports from the very first touch without
a slop threshold and without consuming events.

---

## Rims, shadows and transitions

```kotlin
Highlight(width = 0.5.dp, blurRadius = width / 2, alpha = 1f, style = HighlightStyle.Default)
Shadow(radius = 24.dp, offset = DpOffset(0.dp, radius / 6), color = Color.Black.copy(0.1f))
InnerShadow(radius = 24.dp, offset = DpOffset(0.dp, radius), color = Color.Black.copy(0.15f))
```

| Rim style | Look |
| --- | --- |
| `Plain` | Uniform hairline. Cheapest, and the fallback below API 33. |
| `Default(angle, falloff)` | Directional, lit from a fixed angle. |
| `Ambient(intensity, angle, falloff)` | White on the lit side, dark opposite — reads as a bevel. |
| `Dynamic(color, pointer, focus, falloff)` | Follows the pointer. `interaction.dynamicHighlight()` builds one for you. |

Shadows are punched out under the element, so they never darken the backdrop seen *through* the
glass.

```kotlin
Modifier.materialize({ progress })  // blur + fade content into the material instead of cross-fading
Modifier.motionBlur({ progress })   // blur by how fast something is currently moving
```

`motionBlur` samples the rate of change rather than the value, so a spring blurs hardest mid-travel
and sharpens as it settles — including through an overshoot — and costs nothing at rest.

> **Leave slack around a blurred group.** A render effect rasterises its layer at exactly the
> layer's bounds, so while the blur is live anything a child drew beyond them is cut off. A group
> draws past its own box: half a merge radius for the bridge, and further for the shadow. Grow the
> layer and inset the content by the same amount:
>
> ```kotlin
> Modifier.height(470.dp).motionBlur { progress }.padding(bottom = 48.dp)
> ```

---

## Shapes

Corner geometry matters more here than usual, because refraction is computed from an analytic
distance field of the shape. Liquify depends on [`io.github.kyant0:shapes`][shapes] for
continuous-curvature (squircle) corners:

```kotlin
Capsule()                                                      // fully rounded
RoundedRectangle(28.dp)                                        // squircle corners
RoundedRectangle(28.dp, style = RoundedCornerStyle.Circular)   // plain circular corners
```

Compose's own `RoundedCornerShape` and `CircleShape` work too. Shapes that are not rounded
rectangles fall back to no refraction rather than rendering something wrong.

---

## The catalog app

The `:catalog` module is a **full demo app** built only from Liquify's public API — it doubles as
the reference for how to use the library, and every component in it is meant to be copied.

```bash
./gradlew :catalog:installDebug
```

| Screen | Shows |
| --- | --- |
| Merge & Gooey | Two to four panes fusing and separating, with live `merge(amount)` |
| Morphing | One capsule splitting into three controls, bridged while it travels |
| Menu emergence | A menu growing out of the button that opened it |
| Interactive controls | Buttons, toggles and sliders that flex and light up |
| Materials | `Regular`, `Clear` and `Thick` — **tap one and the whole app switches to it** |
| Playground | Every parameter of the effect stack on a slider |
| Scroll & bars | Floating glass over a list moving underneath it |

It also ships working implementations of `LiquidButton`, `LiquidToggle`, `LiquidSlider` and
`LiquidBottomTabs`. The tab bar is worth reading: the indicator refracts a hidden, accent-tinted
copy of the tab row captured as its own backdrop, so icons genuinely change colour and distort
*through* the lens rather than being swapped for coloured variants.

The app follows the system light/dark setting, and disables Android's overscroll stretch — that
stretch is itself a render effect on the scroll container, and the glass captures its backdrop
separately, so at the end of a list the content would stretch while the glass kept refracting an
unstretched copy of it.

---

## Performance notes

- One glass element is one offscreen layer plus one render-effect chain. Individually cheap, and
  they add up. Prefer one large pane over many small ones.
- A **static backdrop is the single biggest win.** An animating one forces every glass element on
  screen to re-blur and re-refract every frame even when nothing is being touched.
- `chromaticAberration` costs seven backdrop samples per pixel instead of one. Use it on small
  controls, not full-screen surfaces.
- `gradientBlur` composites one branch per step — see the table above before raising `steps`.
- A merged group renders over the union of its members, so keep groups tight.
- A group that is only being *redrawn* — the page scrolled behind it, nothing about it moved — keeps
  the effect chain it already built and reuses its recorded shadow, so that case costs one merge
  pass and nothing else. Moving a member, resizing the group or animating the effects block brings
  the rebuild back, as it must.
- Prefer `LazyColumn` over a scrolling `Column` for lists of glass cards, so offscreen ones are
  neither composed nor drawn.
- **Measure with a warm-up.** Shader compilation and thermals move these numbers enormously; I
  measured the same build at wildly different results minutes apart. Reset `gfxinfo`, interact to
  warm up, reset again, then measure.

---

## Credits

The rounded-rectangle signed distance field, its gradient, the refraction and rim AGSL programs,
and the overall backdrop / effect-scope / shadow-node structure are derived from
**[Kyant0/AndroidLiquidGlass][alg]** (Apache-2.0). Liquify is a clone of that project, not a fork —
which was a **mistake of process** on my part, and I want the lineage stated clearly instead of buried.
Kyant0 built the foundation this stands on.

Used shapes come from **[Kyant0/Shapes][shapes]**, consumed as a Maven artifact rather than vendored.

See [`NOTICE`](NOTICE) for the formal attribution.

What Liquify adds on top: the merged distance-field pass and the whole group renderer, per-member
colour blended through that same field, gradient
blur, the material presets and app-wide material theming, the one-line interaction flags, 
the consolidation of `drawBackdrop` and `liquify` into a single modifier.

---

## Contributing

Issues and feature requests are genuinely welcome — this is a young library and I want to know what
it is missing. Open an issue describing what you're trying to build; I'll get to it as soon as I
can. Pull requests are welcome too.

## Licence

Apache License 2.0 — see [`LICENSE`](LICENSE).

[alg]: https://github.com/Kyant0/AndroidLiquidGlass
[shapes]: https://github.com/Kyant0/Shapes
