/*
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.nio.file.Files
import kotlin.collections.associate
import kotlin.collections.ifEmpty
import kotlin.jvm.java

plugins {
    id("kotlinx-io-publish") apply false
    id("kotlinx-io-dokka")
    alias(libs.plugins.kover)
    id("kotlinx-io-multiplatform")
    id("Gradle2SwiftPMConversionPlugin")

}

//kotlin {
//    iosSimulatorArm64()
//}

allprojects {
    properties["DeployVersion"]?.let { version = it }
    repositories {
        mavenCentral()
    }
    tasks.configureEach {
        if (name == "commonizeNativeDistribution") {
            enabled = false
        }
    }
}

dependencies {
    kover(project(":kotlinx-io-core"))
    kover(project(":kotlinx-io-bytestring"))
//    kover(project(":kotlinx-io-okio"))

    dokka(project(":kotlinx-io-bytestring"))
    dokka(project(":kotlinx-io-core"))
//    dokka(project(":kotlinx-io-okio"))
}

kover {
    reports {
        verify {
            rule {
                minBound(95, CoverageUnit.LINE)

                // we allow lower branch coverage, because not all checks in the internal code lead to errors
                minBound(80, CoverageUnit.BRANCH)
            }
        }
    }
}



