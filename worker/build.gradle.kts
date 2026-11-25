plugins { id("org.jetbrains.kotlin.multiplatform") }
kotlin {
    js {
        browser { commonWebpackConfig { outputFileName = "strategyWorker.js" } }
        binaries.executable()
    }
    sourceSets { commonMain.dependencies { implementation(project(":engine")) } }
}
