package io.github.hdlee73.insu.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import io.github.hdlee73.insu.domain.model.Coverage
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.model.Policy
import io.github.hdlee73.insu.domain.model.ReviewStatus
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val insurer: String,
    val policyNumber: String,
    val contractorType: String,
    val contractorName: String,
    val contractorId: String,
    val insuredType: String,
    val insuredName: String,
    val insuredId: String,
    val sumInsured: Long?,
    val premium: Long?,
    val paymentCycle: String,
    val startDate: String?,
    val endDate: String?,
    val coveragesJson: String,
    val status: String,
    val sourceName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE id = :id")
    fun observe(id: Long): Flow<PolicyEntity?>

    @Query("SELECT * FROM policies WHERE id = :id")
    suspend fun get(id: Long): PolicyEntity?

    @Query("SELECT * FROM policies WHERE id IN (:ids) ORDER BY updatedAt DESC")
    suspend fun getByIds(ids: List<Long>): List<PolicyEntity>

    @Insert
    suspend fun insert(entity: PolicyEntity): Long

    @Update
    suspend fun update(entity: PolicyEntity)

    @Query("DELETE FROM policies WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

@Database(entities = [PolicyEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
}

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)

fun PolicyEntity.toDomain(): Policy = Policy(
    id = id,
    productName = productName,
    insurer = insurer,
    policyNumber = policyNumber,
    contractor = Party(enumOr(contractorType, PartyType.UNKNOWN), contractorName, contractorId),
    insured = Party(enumOr(insuredType, PartyType.UNKNOWN), insuredName, insuredId),
    sumInsured = sumInsured,
    premium = premium,
    paymentCycle = enumOr(paymentCycle, PaymentCycle.UNKNOWN),
    startDate = startDate,
    endDate = endDate,
    coverages = runCatching {
        val arr = JSONArray(coveragesJson)
        List(arr.length()) { i -> arr.getJSONObject(i).let { Coverage(it.getString("n"), it.getLong("a")) } }
    }.getOrDefault(emptyList()),
    status = enumOr(status, ReviewStatus.NEEDS_REVIEW),
    sourceName = sourceName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Policy.toEntity(): PolicyEntity = PolicyEntity(
    id = id,
    productName = productName,
    insurer = insurer,
    policyNumber = policyNumber,
    contractorType = contractor.type.name,
    contractorName = contractor.name,
    contractorId = contractor.idNumber,
    insuredType = insured.type.name,
    insuredName = insured.name,
    insuredId = insured.idNumber,
    sumInsured = sumInsured,
    premium = premium,
    paymentCycle = paymentCycle.name,
    startDate = startDate,
    endDate = endDate,
    coveragesJson = JSONArray().also { arr -> coverages.forEach { arr.put(JSONObject().put("n", it.name).put("a", it.amount)) } }.toString(),
    status = status.name,
    sourceName = sourceName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
