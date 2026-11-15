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
}

@Dao
interface RecoveryDao {
    @Query("SELECT * FROM recovery")
    fun observeAll(): Flow<List<RecoveryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RecoveryEntity)
}

@Dao
interface TrainingLoadDao {
    @Query("SELECT * FROM training_load WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<TrainingLoadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<TrainingLoadEntity>)

    @Query("DELETE FROM training_load WHERE date < :before")
    suspend fun deleteBefore(before: String)
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM fitness")
    fun observeAll(): Flow<List<FitnessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FitnessEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE date >= :from ORDER BY date DESC, startMs DESC")
    fun observeSince(from: String): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<ActivityEntity>)

    @Query("DELETE FROM activity WHERE date < :before")
    suspend fun deleteBefore(before: String)
}

@Database(
    entities = [DailyEntity::class, SleepEntity::class, RecoveryEntity::class, TrainingLoadEntity::class, FitnessEntity::class, ActivityEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CairnDatabase : RoomDatabase() {
    abstract fun daily(): DailyDao
    abstract fun sleep(): SleepDao
    abstract fun recovery(): RecoveryDao
    abstract fun trainingLoad(): TrainingLoadDao
    abstract fun fitness(): FitnessDao
    abstract fun activities(): ActivityDao

    companion object {
        const val NAME = "cairn.db"
    }
}
