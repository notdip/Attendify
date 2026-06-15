package com.dip.attendify.core

import com.dip.attendify.data.entity.SemesterEntity
import com.dip.attendify.data.repository.SemesterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide source of truth for the active semester.
 * Injected into ViewModels that need semester context.
 * Screens never query the semester directly — they go through here.
 */
@Singleton
class SemesterManager @Inject constructor(
    private val repository: SemesterRepository,
) {
    /** Hot flow — emits whenever the active semester changes. */
    val activeSemester: Flow<SemesterEntity?> = repository.observeActiveSemester()

    suspend fun getActiveSemester(): SemesterEntity? = repository.getActiveSemester()

    suspend fun createSemester(
        name: String,
        startDate: Long,
        endDate: Long,
        targetPercent: Int = 75,
        warningBuffer: Int = 5,
    ): Long {
        repository.archiveCurrent()
        return repository.createSemester(
            SemesterEntity(
                name                    = name,
                startDate               = startDate,
                endDate                 = endDate,
                targetAttendancePercent = targetPercent,
                warningBufferPercent    = warningBuffer,
                isActive                = true,
            )
        )
    }

    suspend fun updateSemester(semester: SemesterEntity) =
        repository.updateSemester(semester)

    fun observeArchivedSemesters(): Flow<List<SemesterEntity>> =
        repository.observeArchivedSemesters()
}