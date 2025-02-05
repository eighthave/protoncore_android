/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.core.label.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.data.room.db.Database
import me.proton.core.data.room.db.migration.DatabaseMigration

interface LabelDatabase : Database {
    fun labelDao(): LabelDao

    companion object {
        val MIGRATION_0 = object : DatabaseMigration {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create LabelEntity table.
                database.execSQL("CREATE TABLE IF NOT EXISTS `LabelEntity` (`userId` TEXT NOT NULL, `labelId` TEXT NOT NULL, `parentId` TEXT, `name` TEXT NOT NULL, `type` INTEGER NOT NULL, `path` TEXT NOT NULL, `color` TEXT NOT NULL, `order` INTEGER NOT NULL, `isNotified` INTEGER, `isExpanded` INTEGER, `isSticky` INTEGER, PRIMARY KEY(`userId`, `labelId`), FOREIGN KEY(`userId`) REFERENCES `UserEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                // Create Indexes.
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_LabelEntity_userId` ON `LabelEntity` (`userId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_LabelEntity_labelId` ON `LabelEntity` (`labelId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_LabelEntity_parentId` ON `LabelEntity` (`parentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_LabelEntity_name` ON `LabelEntity` (`name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_LabelEntity_type` ON `LabelEntity` (`type`)")
            }
        }
    }
}
