package app.cairn.source.healthconnect

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

const val COROS_PACKAGE = "com.yf.smart.coros.dist"

private class Spec(val info: RecordTypeInfo, val kclass: KClass<out Record>)

private val specs = listOf(
    Spec(RecordTypeInfo("steps", "Steps"), StepsRecord::class),
    Spec(RecordTypeInfo("hr", "Heart rate"), HeartRateRecord::class),
    Spec(RecordTypeInfo("rhr", "Resting heart rate"), RestingHeartRateRecord::class),
    Spec(RecordTypeInfo("sleep", "Sleep"), SleepSessionRecord::class),
    Spec(RecordTypeInfo("exercise", "Workouts"), ExerciseSessionRecord::class),
    Spec(RecordTypeInfo("hrv", "HRV (RMSSD)"), HeartRateVariabilityRmssdRecord::class),
    Spec(RecordTypeInfo("distance", "Distance"), DistanceRecord::class),
    Spec(RecordTypeInfo("totalcal", "Total calories"), TotalCaloriesBurnedRecord::class),
    Spec(RecordTypeInfo("activecal", "Active calories"), ActiveCaloriesBurnedRecord::class),
    Spec(RecordTypeInfo("spo2", "Oxygen saturation"), OxygenSaturationRecord::class),
    Spec(RecordTypeInfo("vo2", "VO2 max"), Vo2MaxRecord::class),
    Spec(RecordTypeInfo("resp", "Respiratory rate"), RespiratoryRateRecord::class),
    Spec(RecordTypeInfo("weight", "Weight"), WeightRecord::class),
)

/** Read permissions for every inspected type. */
val inspectedReadPermissions: Set<String> =
    specs.mapTo(linkedSetOf()) { HealthPermission.getReadPermission(it.kclass) }

/** Types the periodic sync reads. */
val syncReadPermissions: Set<String> = setOf(
    StepsRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, SleepSessionRecord::class, ExerciseSessionRecord::class,
).mapTo(linkedSetOf()) { HealthPermission.getReadPermission(it) }

const val BACKGROUND_READ_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

enum class HcAvailability { Available, UpdateRequired, Unavailable }

data class HcFeatures(val backgroundRead: Boolean, val history: Boolean)

data class CorosStatus(val installed: Boolean, val batteryUnrestricted: Boolean?)

class HealthConnectReader(private val context: Context) : HealthRecordReader {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override val types: List<RecordTypeInfo> = specs.map { it.info }

    fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UpdateRequired
            else -> HcAvailability.Unavailable
        }

    fun features(): HcFeatures {
        val f = client.features
        return HcFeatures(
            backgroundRead = f.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
            history = f.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
        )
    }

    override suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    override suspend fun readMeta(type: RecordTypeInfo, from: Instant, to: Instant): List<RecordMeta> {
        val kclass = specs.first { it.info.key == type.key }.kclass
        val out = ArrayList<RecordMeta>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = kclass,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageSize = 1000,
                    pageToken = token,
                ),
            )
            resp.records.mapTo(out) { RecordMeta(it.metadata.lastModifiedTime, it.metadata.dataOrigin.packageName) }
            token = resp.pageToken
        } while (token != null)
        return out
    }

    /** Read permissions Cairn's sync needs; sync runs when at least one is granted. */
    suspend fun hasSyncPermission(): Boolean = grantedPermissions().any { it in syncReadPermissions }

    /**
     * Reads every sync record type in [from]..[to] (all origins; a type whose permission is not granted yields an
     * empty list). Pages through results. No changes-token is used: callers re-read a short recent window.
     */
    suspend fun readInputs(from: Instant, to: Instant): HcInputs {
        val granted = grantedPermissions()
        suspend fun <T : Record> read(kclass: KClass<T>): List<T> {
            if (HealthPermission.getReadPermission(kclass) !in granted) return emptyList()
            val out = ArrayList<T>()
            var token: String? = null
            do {
                val resp = client.readRecords(
                    ReadRecordsRequest(kclass, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken = token),
                )
                out += resp.records
                token = resp.pageToken
            } while (token != null)
            return out
        }
        return HcInputs(
            steps = read(StepsRecord::class).map { it.toInput() },
            heartRate = read(HeartRateRecord::class).map { it.toInput() },
            restingHr = read(RestingHeartRateRecord::class).map { it.toInput() },
            distance = read(DistanceRecord::class).map { it.toInput() },
            calories = read(TotalCaloriesBurnedRecord::class).map { it.toInput() },
            sleep = read(SleepSessionRecord::class).map { it.toInput() },
            exercise = read(ExerciseSessionRecord::class).map { it.toInput() },
        )
    }

    fun corosStatus(): CorosStatus {
        val installed = try {
            context.packageManager.getPackageInfo(COROS_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val battery = if (installed) {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(COROS_PACKAGE)
        } else null
        return CorosStatus(installed, battery)
    }

    companion object {
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }
}
