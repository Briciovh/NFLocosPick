package com.softeen.nflocospicks.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class ScoringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params : WorkerParameters,
    private val scoreWeekPicksUseCase: ScoreWeekPicksUseCase
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_GROUP_ID = "groupId"

        // Días de partido en la NFL (constantes de Calendar)
        private val GAME_DAYS = setOf(
            Calendar.SUNDAY,    // partidos principales
            Calendar.MONDAY,    // Monday Night Football
            Calendar.THURSDAY   // Thursday Night Football
        )
    }

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID)
            ?: return Result.failure()

        // No ejecutar en días sin partido — ahorra batería y red en la off-season
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (today !in GAME_DAYS) return Result.success()

        return try {
            scoreWeekPicksUseCase(groupId)
            Result.success()
        } catch (e: Exception) {
            // Reintentar hasta 3 veces con backoff exponencial (comportamiento default de WorkManager)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
