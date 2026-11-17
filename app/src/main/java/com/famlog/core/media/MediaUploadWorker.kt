package com.famlog.core.media

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.famlog.core.data.MediaUploader
import com.famlog.core.data.UploadOutcome
import com.famlog.core.data.LocalFileSource
import com.famlog.core.data.UploadScheduler
import com.famlog.core.database.MessageDao
import com.famlog.core.model.SendState
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 사진·동영상 메시지 하나의 업로드 (CHAT-05). 네트워크가 있을 때만 돌고, 실패하면 30초부터 늘려가며 다시 한다 */
@HiltWorker
class MediaUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: MediaUploader,
    private val dao: MessageDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_MESSAGE) ?: return Result.failure()
        return when (uploader.upload(key)) {
            UploadOutcome.DONE -> Result.success()
            UploadOutcome.FAILED -> Result.failure()
            UploadOutcome.RETRY -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                // 오래 실패하면 멈추고 사용자에게 [다시 보내기]를 맡긴다
                dao.updateSendState(key, SendState.FAILED.name)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_MESSAGE = "messageKey"
        private const val MAX_ATTEMPTS = 6
    }
}

@Singleton
class WorkManagerUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : UploadScheduler {

    override fun schedule(messageKey: String) {
        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(workDataOf(MediaUploadWorker.KEY_MESSAGE to messageKey))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // 이미 돌고 있으면 그대로 둔다. 끝난(실패한) 작업은 새로 시작한다
        WorkManager.getInstance(context).enqueueUniqueWork(workName(messageKey), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(messageKey: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(messageKey))
    }

    private fun workName(messageKey: String) = "media-upload-$messageKey"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    abstract fun bindUploadScheduler(impl: WorkManagerUploadScheduler): UploadScheduler

    @Binds
    abstract fun bindLocalFileSource(impl: ContentFileSource): LocalFileSource

    @Binds
    abstract fun bindMediaInspector(impl: ContentMediaInspector): MediaInspector
}
