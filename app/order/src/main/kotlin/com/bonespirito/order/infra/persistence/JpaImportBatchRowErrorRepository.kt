package com.bonespirito.order.infra.persistence

import com.bonespirito.order.application.port.ImportBatchRowErrorRepository
import com.bonespirito.order.domain.ImportBatchRowError
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/** Adapter JPA do [ImportBatchRowErrorRepository]: o Spring Data fica aqui dentro, fora de `application`. */
@Repository
class JpaImportBatchRowErrorRepository internal constructor(
    private val springData: SpringDataImportBatchRowErrorRepository,
) : ImportBatchRowErrorRepository {

    override fun save(rowError: ImportBatchRowError): ImportBatchRowError = springData.save(rowError)
}

internal interface SpringDataImportBatchRowErrorRepository : JpaRepository<ImportBatchRowError, UUID>
