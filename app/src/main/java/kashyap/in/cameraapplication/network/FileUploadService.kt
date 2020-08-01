package kashyap.`in`.cameraapplication.network

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kashyap.`in`.cameraapplication.MainActivity
import kashyap.`in`.cameraapplication.R
import kashyap.`in`.cameraapplication.common.EMAIL_ID
import kashyap.`in`.cameraapplication.notification.NotificationHelper
import kashyap.`in`.cameraapplication.receiver.FileProgressReceiver
import kashyap.`in`.cameraapplication.receiver.RetryJobReceiver
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class FileUploadService : JobIntentService() {

    var apiService: ApiService? = null
    var mDisposable: Disposable? = null
    var mFilePath: String? = null
    var mNotificationHelper: NotificationHelper? = null

    companion object {
        private const val TAG = "FileUploadService"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_RETRY_ID = 2
        private const val JOB_ID = 102

        @JvmStatic
        fun enqueueWork(context: Context?, intent: Intent?) {
            context?.let {
                intent?.let { it1 ->
                    enqueueWork(
                        it,
                        FileUploadService::class.java,
                        JOB_ID,
                        it1
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mNotificationHelper =
            NotificationHelper(this)
    }

    override fun onHandleWork(intent: Intent) {
        Log.d(TAG, "onHandleWork: ")
        mFilePath = intent.getStringExtra("mFilePath")
        if (mFilePath == null) {
            Log.e(TAG, "onHandleWork: Invalid file URI")
            return
        }
        apiService = RetrofitInstance.apiService
        val fileObservable =
            Flowable.create(
                { emitter: FlowableEmitter<Double> ->
                    apiService?.onFileUpload(
                        createRequestBodyFromText(EMAIL_ID),
                        createMultipartBody(mFilePath!!, emitter)
                    )?.blockingGet()
                    emitter.onComplete()
                }, BackpressureStrategy.LATEST
            )
        mDisposable = fileObservable.subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { progress: Double ->
                    onProgress(progress)
                },
                { throwable: Throwable ->
                    onErrors(throwable)
                }
            ) { this@FileUploadService.onSuccess() }
    }

    private fun onErrors(throwable: Throwable) {
        val successIntent = Intent("kashyap.in.ACTION_CLEAR_NOTIFICATION")
        successIntent.putExtra("notificationId", NOTIFICATION_ID)
        sendBroadcast(successIntent)
        val resultPendingIntent = PendingIntent.getActivity(
            this,
            0 /* Request code */, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val retryIntent = Intent(this, RetryJobReceiver::class.java)
        retryIntent.putExtra("notificationId", NOTIFICATION_RETRY_ID)
        retryIntent.putExtra("mFilePath", mFilePath)
        retryIntent.action = RetryJobReceiver.ACTION_RETRY
        val clearIntent = Intent(this, RetryJobReceiver::class.java)
        clearIntent.putExtra("notificationId", NOTIFICATION_RETRY_ID)
        clearIntent.putExtra("mFilePath", mFilePath)
        clearIntent.action = RetryJobReceiver.ACTION_CLEAR
        val retryPendingIntent = PendingIntent.getBroadcast(this, 0, retryIntent, 0)
        val clearPendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, 0)
        val mBuilder = mNotificationHelper?.getNotification(
            getString(R.string.error_upload_failed),
            getString(R.string.message_upload_failed), resultPendingIntent
        )
        mBuilder?.addAction(
            R.drawable.ic_camera_rotation, getString(R.string.btn_retry_not),
            retryPendingIntent
        )
        mBuilder?.addAction(
            R.drawable.ic_camera_rotation, getString(R.string.btn_cancel_not),
            clearPendingIntent
        )
        mBuilder?.let { mNotificationHelper?.notify(NOTIFICATION_RETRY_ID, it) }
    }

    private fun onProgress(progress: Double) {
        val progressIntent = Intent(this, FileProgressReceiver::class.java)
        progressIntent.action = "kashyap.in.ACTION_PROGRESS_NOTIFICATION"
        progressIntent.putExtra("notificationId", NOTIFICATION_ID)
        progressIntent.putExtra("progress", (100 * progress).toInt())
        sendBroadcast(progressIntent)
    }

    private fun onSuccess() {
        val successIntent = Intent(this, FileProgressReceiver::class.java)
        successIntent.action = "kashyap.in.ACTION_UPLOADED"
        successIntent.putExtra("notificationId", NOTIFICATION_ID)
        successIntent.putExtra("progress", 100)
        sendBroadcast(successIntent)
    }

    private fun createRequestBodyFromFile(
        file: File,
        mimeType: String
    ): RequestBody {
        return file.asRequestBody(mimeType.toMediaTypeOrNull())
    }

    private fun createRequestBodyFromText(mText: String): RequestBody {
        return mText.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private fun createMultipartBody(
        filePath: String,
        emitter: FlowableEmitter<Double>
    ): MultipartBody.Part {
        val file = File(filePath)
        return MultipartBody.Part.createFormData(
            "filesUploaded", file.name,
            createCountingRequestBody(file, "image/jpeg", emitter)
        )
    }

    private fun createCountingRequestBody(
        file: File, mimeType: String,
        emitter: FlowableEmitter<Double>
    ): RequestBody {
        val requestBody = createRequestBodyFromFile(file, mimeType)
        return CountingRequestBody(
            requestBody,
            object : CountingRequestBody.Listener {
                override fun onRequestProgress(bytesWritten: Long, contentLength: Long) {
                    val progress = 1.0 * bytesWritten / contentLength
                    emitter.onNext(progress)
                }
            }
        )
    }
}