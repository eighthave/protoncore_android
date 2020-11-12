import studio.forface.easygradle.dsl.*
import studio.forface.easygradle.dsl.android.*

plugins {
    id("com.android.library")
    kotlin("android")
}

libVersion = Version(0, 1, 2)

android()

dependencies {
    api(
        // project(Module.accountPresentation),
        project(Module.accountDomain),
        project(Module.accountData)
    )
}
