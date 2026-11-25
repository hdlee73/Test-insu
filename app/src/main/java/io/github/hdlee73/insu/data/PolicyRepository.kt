package io.github.hdlee73.insu.data

import io.github.hdlee73.insu.data.db.DatabaseProvider
import io.github.hdlee73.insu.data.db.toDomain
import io.github.hdlee73.insu.data.db.toEntity
import io.github.hdlee73.insu.domain.model.Policy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 증권 저장소. DB 열기(Keystore 포함)는 항상 IO 스레드에서 일어나게 한다. */
class PolicyRepository(private val provider: DatabaseProvider) {

    private fun dao() = provider.database().policyDao()

    fun observeAll(): Flow<List<Policy>> =
        flow { emitAll(dao().observeAll()) }.map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observe(id: Long): Flow<Policy?> =
        flow { emitAll(dao().observe(id)) }.map { it?.toDomain() }.flowOn(Dispatchers.IO)

    suspend fun get(id: Long): Policy? = withContext(Dispatchers.IO) { dao().get(id)?.toDomain() }

    suspend fun getByIds(ids: Collection<Long>): List<Policy> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else dao().getByIds(ids.toList()).map { it.toDomain() }
    }

    /** 새 증권이면 id를 새로 받고, 기존 증권이면 덮어쓴다. 저장된 id를 돌려준다. */
    suspend fun save(policy: Policy): Long = withContext(Dispatchers.IO) {
        if (policy.id == 0L) dao().insert(policy.toEntity()) else {
            dao().update(policy.toEntity()); policy.id
        }
    }

    suspend fun delete(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao().delete(ids.toList())
    }
}
