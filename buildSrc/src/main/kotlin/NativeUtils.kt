/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.publish.maven.tasks.*
import org.gradle.api.publish.tasks.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

fun KotlinMultiplatformExtension.posixTargets(): Set<KotlinNativeTarget> =
    nixTargets() + mingwX64()

fun KotlinMultiplatformExtension.nixTargets(): Set<KotlinNativeTarget> =
    darwinTargets() + linuxX64()

fun KotlinMultiplatformExtension.darwinTargets(): Set<KotlinNativeTarget> = setOf(
    iosX64(),
    iosArm64(),
    iosArm32(),
    iosSimulatorArm64(),

    watchosX86(),
    watchosX64(),
    watchosArm32(),
    watchosArm64(),
    watchosSimulatorArm64(),

    tvosX64(),
    tvosArm64(),
    tvosSimulatorArm64(),

    macosX64(),
    macosArm64()
)

fun KotlinMultiplatformExtension.desktopTargets(): Set<KotlinNativeTarget> = setOf(
    macosX64(),
    linuxX64(),
    mingwX64()
)

fun KotlinMultiplatformExtension.nixX86Targets(): Set<KotlinNativeTarget> = setOf(
    iosArm32(),
    watchosX86(),
    watchosArm32()
)

fun KotlinMultiplatformExtension.nixX64Targets(): Set<KotlinNativeTarget> = setOf(
    linuxX64(),
    iosSimulatorArm64(),
    watchosX64(),
    watchosArm64(),
    watchosSimulatorArm64(),
    tvosX64(),
    tvosArm64(),
    tvosSimulatorArm64(),
    macosX64(),
    macosArm64()
)

fun Project.disableCompilation(target: KotlinNativeTarget) {
    target.apply {

        compilations.all {
            cinterops.all {
                tasks.named(interopProcessingTaskName) { enabled = false }
            }
            compileKotlinTaskProvider.configure { enabled = false }
        }

        binaries.all {
            linkTaskProvider.configure { enabled = false }
        }

        mavenPublication {
            tasks.withType<AbstractPublishToMaven>().configureEach {
                onlyIf { publication != this@mavenPublication }
            }
            tasks.withType<GenerateModuleMetadata>().configureEach {
                onlyIf { publication.get() != this@mavenPublication }
            }
        }
    }
}
