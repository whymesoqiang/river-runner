import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Pure Kotlin poker engine, shared by the Android app (jvm) and the web app (js + wasmJs).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(17)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            testLogging { events("passed", "failed") }
        }
    }
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
        commonTest.dependencies { implementation(kotlin("test")) }
        // Exercise the actual platform-neutral persistence code without an Android SDK.
        jvmTest { kotlin.srcDir("../ui/src/commonMain/kotlin/com/riverrunner/data") }
    }
}
