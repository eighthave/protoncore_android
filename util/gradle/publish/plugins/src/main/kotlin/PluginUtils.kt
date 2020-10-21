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

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import studio.forface.easygradle.dsl.*
import java.util.Locale

var Project.pluginConfig
    get() = if (hasProperty("pluginConfig")) extra["pluginConfig"] as? PluginConfig else null
    set(value) { extra["pluginConfig"] = value }

data class PluginConfig(
    val name: String,
    val version: Version,
    val group: String = "me.proton"
) {
    val id = "$group.$name".toLowerCase(Locale.US)
}
