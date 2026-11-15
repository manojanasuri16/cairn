package app.cairn.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Dates are ISO strings, so lexicographic BETWEEN/>= comparisons are chronological.

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DailyEntity>>

    @Query("SELECT * FROM daily WHERE date IN (:dates) AND source = :source")
    suspend fun get(dates: List<String>, source: String): List<DailyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<DailyEntity>)

    @Query("DELETE FROM daily WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM daily")
    suspend fun clear()
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep WHERE date IN (:dates) AND source = :source")
    suspend fun get(dates: List<String>, source: String): List<SleepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<SleepEntity>)

    @Query("DELETE FROM sleep WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM sleep")
    suspend fun clear()
}

@Dao
interface RecoveryDao {
    @Query("SELECT * FROM recovery")
    fun observeAll(): Flow<List<RecoveryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RecoveryEntity)

    @Query("DELETE FROM recovery")
    suspend fun clear()
}

@Dao
interface TrainingLoadDao {
    @Query("SELECT * FROM training_load WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<TrainingLoadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<TrainingLoadEntity>)

    @Query("DELETE FROM training_load WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM training_load")
    suspend fun clear()
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM fitness")
    fun observeAll(): Flow<List<FitnessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FitnessEntity)

    @Query("DELETE FROM fitness")
    suspend fun clear()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE date >= :from ORDER BY date DESC, startMs DESC")
    fun observeSince(from: String): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<ActivityEntity>)

    @Query("DELETE FROM activity WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Dao
interface VitalsDao {
    @Query("SELECT * FROM vitals WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<VitalsEntity>>

    @Query("SELECT * FROM vitals WHERE date IN (:dates) AND source = :source")
    suspend fun get(dates: List<String>, source: String): List<VitalsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<VitalsEntity>)

    @Query("DELETE FROM vitals WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM vitals")
    suspend fun clear()
}

@Dao
interface HrvDao {
    @Query("SELECT * FROM hrv WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<HrvEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<HrvEntity>)

    @Query("DELETE FROM hrv WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM hrv")
    suspend fun clear()
}

@Dao
interface ActivityDetailDao {
    @Query("SELECT * FROM activity_detail")
    fun observeAll(): Flow<List<ActivityDetailEntity>>

    @Query("SELECT id FROM activity_detail")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<ActivityDetailEntity>)

    @Query("DELETE FROM activity_detail")
    suspend fun clear()
}

@Database(
    entities = [
        DailyEntity::class, SleepEntity::class, RecoveryEntity::class, TrainingLoadEntity::class, FitnessEntity::class, ActivityEntity::class,
        VitalsEntity::class, HrvEntity::class, ActivityDetailEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CairnDatabase : RoomDatabase() {
    abstract fun daily(): DailyDao
    abstract fun sleep(): SleepDao
    abstract fun recovery(): RecoveryDao
    abstract fun trainingLoad(): TrainingLoadDao
    abstract fun fitness(): FitnessDao
    abstract fun activities(): ActivityDao
    abstract fun vitals(): VitalsDao
    abstract fun hrv(): HrvDao
    abstract fun activityDetails(): ActivityDetailDao

    companion object {
        const val NAME = "cairn.db"
    }
}
