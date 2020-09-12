/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

import studio.forface.easygradle.dsl.*
import studio.forface.easygradle.dsl.android.*
import util.libVersion

plugins {
    `android-library`
}

libVersion = Version(0, 1, 2)

android()

dependencies {
    // Base dependencies
    implementation(
        // Kotlin
        `kotlin-jdk7`,
        `coroutines-android`,

        // Android
        `lifecycle-runtime`,
        `lifecycle-liveData`,
        `lifecycle-viewModel`
    )

    // Test dependencies
    api(
        project(Module.androidTest).apply {
            exclude(`mockk`)
            exclude(`robolectric`)
        },

        // MockK
        `mockk-android`,

        // Android
        `espresso`,
        `android-work-testing`,
        `android-test-core`,
        `android-test-runner`,
        `android-test-rules`
    )
}
