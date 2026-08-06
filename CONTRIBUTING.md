# Contributing

Thanks for looking. Issues and pull requests are both welcome — this is a young library and the
most useful thing you can tell me is what it is missing.

## Reporting something

Open an issue. For a bug, the two things that help most are **the device and API level** and
**what the glass looked like versus what you expected** — a screenshot or a screen recording says
more than a paragraph, because almost everything here is visual. If it involves a group, say how
many members and what `merge` value you used.

For a feature request, describe what you are trying to build rather than the API you think you
need. Half the features here exist because someone described a UI they wanted and the shape of the
API fell out of that.

## Building

```bash
./gradlew build              # library + catalog + lint; lint failures fail the build
./gradlew :catalog:installDebug
```

JDK 21 is provisioned by Gradle from `gradle/gradle-daemon-jvm.properties`; you do not need to
install it yourself. CI runs exactly the same `build` task, so a green local build is a green CI.

## Things worth knowing before you change rendering code

**Test on a real device.** The effects here are `RenderEffect` and AGSL shader work, and emulators
lie about both. Refraction in particular looks subtly wrong on an emulator in ways that do not
reproduce on hardware.

**Measure, do not guess, and warm up first.** Shader compilation and thermal throttling move frame
timings enormously — the same build has measured wildly differently minutes apart. A comparison
without a warm-up pass in front of each reading is not a comparison:

```bash
adb shell dumpsys gfxinfo com.hakim.liquify.catalog reset
# …interact to warm up, then discard…
adb shell dumpsys gfxinfo com.hakim.liquify.catalog reset
# …interact again, this is the measurement…
adb shell dumpsys gfxinfo com.hakim.liquify.catalog | grep -E "Janky|percentile"
```

Report negative results honestly. More than one optimisation in here was measured, found to buy
nothing, and reverted — that is a good outcome, not a wasted afternoon.

**Effect order is GPU order.** `blur()` before `lens()` refracts already-blurred content, which is
what reads as thick glass; reversed, it reads as a blurred sticker. `gradientBlur()` is the
exception and goes *after* `lens()`, because the lens samples inwards and would otherwise drag the
soft interior back out over the rim.

**Keep the API-level fallbacks intact.** Nothing may throw on an older device. Refraction, merging
and shader rims need API 33; blur and colour effects need 31. Below that they are skipped, and the
element still renders with its shape, rim and shadow.

## Style

`explicitApi()` is on for the library, so every public declaration needs an explicit visibility and
a return type. Comments explain *why*, not *what* — if a line needs a comment to say what it does,
the line is usually the problem.

## Attribution

Parts of this library are derived from [Kyant0/AndroidLiquidGlass][alg] under the Apache License
2.0, and every derived file carries a notice saying so. If you move or substantially rewrite one of
those files, keep the notice. See [`NOTICE`](NOTICE).

[alg]: https://github.com/Kyant0/AndroidLiquidGlass
