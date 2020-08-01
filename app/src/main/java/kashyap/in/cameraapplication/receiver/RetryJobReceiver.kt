package kashyap.`in`.cameraapplication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kashyap.`in`.cameraapplication.network.FileUploadService
import kashyap.`in`.cameraapplication.network.FileUploadService.Companion.enqueueWork
import kashyap.`in`.cameraapplication.notification.NotificationHelper
import java.util.*

class RetryJobReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RETRY = "com.wave.ACTION_RETRY"
        const val ACTION_CLEAR = "com.wave.ACTION_CLEAR"
    }

    var mNotificationHelper: NotificationHelper? = null
    override fun onReceive(context: Context, intent: Intent) {
        mNotificationHelper =
            NotificationHelper(
                context
            )
        val notificationId = intent.getIntExtra("notificationId", 0)
        val filePath = intent.getStringExtra("mFilePath")
        when (Objects.requireNonNull(intent.action)) {
            ACTION_RETRY -> {
                mNotificationHelper!!.cancelNotification(notificationId)
                val mIntent = Intent(context, FileUploadService::class.java)
                mIntent.putExtra("mFilePath", filePath)
                enqueueWork(context, mIntent)
            }
            ACTION_CLEAR -> mNotificationHelper?.cancelNotification(
                notificationId
            )
            else -> {
            }
        }
    }

}