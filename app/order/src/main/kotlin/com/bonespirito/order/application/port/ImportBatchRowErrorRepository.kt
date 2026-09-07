package com.bonespirito.order.application.port

import com.bonespirito.order.domain.ImportBatchRowError
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ImportBatchRowErrorRepository : JpaRepository<ImportBatchRowError, UUID>
