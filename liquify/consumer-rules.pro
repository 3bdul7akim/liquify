# Intentionally empty.
#
# Nothing in Liquify is reached reflectively. The AGSL programs are compiled from string constants
# at runtime, but their uniform names address shader uniforms rather than Java fields, so R8 cannot
# break them by renaming anything.
#
# A blanket `-keep public class com.hakim.liquify.** { public *; }` looks harmless and is not: every
# consuming app would then be forced to retain the whole library, including the merge renderer, the
# transitions and the interaction helpers it never calls. Saying nothing here is what lets R8 strip
# the parts an app does not use.
