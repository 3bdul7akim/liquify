plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
    signing
}

val libraryGroupId = "io.github.3bdul7akim"
val libraryArtifactId = "liquify"
val libraryVersion = "1.1.0"

android {
    namespace = "com.hakim.liquify"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            // Maven Central rejects a bundle without both of these.
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.kyant.shapes)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.util)
    compileOnly(libs.jetbrains.annotations)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libraryGroupId
            artifactId = libraryArtifactId
            version = libraryVersion

            afterEvaluate { from(components["release"]) }

            pom {
                name.set("Liquify")
                description.set(
                    "Liquid glass for Jetpack Compose: edge refraction, pointer-driven specular " +
                        "highlights and glass elements that merge and separate like liquid."
                )
                url.set("https://github.com/3bdul7akim/liquify")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("3bdul7akim")
                        name.set("3bdul7akim")
                        url.set("https://github.com/3bdul7akim")
                    }
                }
                scm {
                    url.set("https://github.com/3bdul7akim/liquify")
                    connection.set("scm:git:git://github.com/3bdul7akim/liquify.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/3bdul7akim/liquify.git"
                    )
                }
            }
        }
    }

    repositories {
        // `./gradlew :liquify:publishReleasePublicationToLocalRepository` for a smoke test,
        // and the staging ground the Central Portal bundle is built from.
        //
        // There is deliberately no remote repository here. The Central Portal takes an uploaded
        // bundle rather than a Maven deploy, so `centralBundle` below is the release path;
        // `publishReleasePublicationToMavenLocal` is the one for consuming this from another
        // project on this machine.
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

/**
 * Packs the release into the single zip the Central Portal accepts.
 *
 * The portal expects a Maven repository layout — `io/github/3bdul7akim/liquify/<version>/` holding
 * the pom, the aar, the sources and javadoc jars, a `.asc` signature for each, and their checksums.
 * That is exactly what publishing to the `local` repository above produces, so this only has to
 * archive it.
 *
 * `maven-metadata` is excluded: it describes a repository rather than a release, and the portal
 * rejects a bundle that carries one.
 */
val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Builds the Central Portal upload bundle for the release publication."

    dependsOn(tasks.named("publishReleasePublicationToLocalRepository"))

    // Scoped to the version being released. The staging repository accumulates every version ever
    // built into it, so an unscoped copy would quietly bundle the previous release alongside this
    // one on the second run. This also drops `maven-metadata`, which sits above the version
    // directory and describes a repository rather than a release.
    from(layout.buildDirectory.dir("repo")) {
        include("**/$libraryVersion/**")
    }

    archiveFileName.set("$libraryArtifactId-$libraryVersion-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))

    // Resolved as a provider rather than read off the task inside the action, so the
    // configuration cache (on for this build) can serialise it.
    val bundlePath = archiveFile.map { it.asFile.absolutePath }
    doLast {
        logger.lifecycle("Central Portal bundle: ${bundlePath.get()}")
    }
}

// Central requires signed artifacts, but an unsigned local build must still work, so signing only
// engages once the key material is actually present in gradle.properties or the environment.
signing {
    val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
