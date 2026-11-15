package app.cairn.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: adds the vitals, hrv and activity_detail tables, and widens fitness.runningLevel from INTEGER to REAL
 * (the server can return a decimal such as 73.3). SQLite cannot alter a column type, so fitness is rebuilt.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `vitals` (`date` TEXT NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `avgHr` INTEGER, `minHr` INTEGER, `maxHr` INTEGER, `restingHr` INTEGER, `avgStress` INTEGER, PRIMARY KEY(`date`, `source`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `hrv` (`date` TEXT NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `avgMs` INTEGER, `assessment` TEXT, `normalLowMs` INTEGER, `normalHighMs` INTEGER, `baselineMs` INTEGER, PRIMARY KEY(`date`, `source`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `activity_detail` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `trainingLoad` INTEGER, `aerobicTe` REAL, `anaerobicTe` REAL, `trainingFocus` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `fitness_new` (`source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `vo2max` INTEGER, `runningLevel` REAL, `thresholdPaceSecPerKm` INTEGER, `prediction5kSec` INTEGER, `prediction10kSec` INTEGER, `predictionHalfSec` INTEGER, `predictionMarathonSec` INTEGER, PRIMARY KEY(`source`))")
        db.execSQL("INSERT INTO `fitness_new` SELECT `source`, `updatedAt`, `vo2max`, `runningLevel`, `thresholdPaceSecPerKm`, `prediction5kSec`, `prediction10kSec`, `predictionHalfSec`, `predictionMarathonSec` FROM `fitness`")
        db.execSQL("DROP TABLE `fitness`")
        db.execSQL("ALTER TABLE `fitness_new` RENAME TO `fitness`")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
