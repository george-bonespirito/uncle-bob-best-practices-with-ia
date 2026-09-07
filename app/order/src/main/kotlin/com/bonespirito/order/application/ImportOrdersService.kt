package com.bonespirito.order.application

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.domain.ImportBatch
import com.bonespirito.order.domain.ImportProgress
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ExecutorService

@Service
class ImportOrdersService(
    private val importBatches: ImportBatchRepository,
    private val executor: ExecutorService,
) : ImportOrders {

    override fun startImport(fileName: String, content: InputStream): ImportBatchAccepted {
        val rowCount = (content.bufferedReader().readLines().size - 1).coerceAtLeast(0)
        val batch = importBatches.save(
            ImportBatch(fileName = fileName, progress = ImportProgress(totalRows = rowCount)),
        )

        executor.submit { finishProcessing(batch.id) }

        return ImportBatchAccepted(batch.id, batch.status)
    }

    private fun finishProcessing(importBatchId: UUID) {
        val batch = importBatches.findById(importBatchId).orElseThrow()
        batch.markCompleted()
        importBatches.save(batch)
    }
}
