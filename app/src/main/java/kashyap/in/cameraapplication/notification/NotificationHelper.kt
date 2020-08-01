package kashyap.`in`.cameraapplication.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kashyap.`in`.cameraapplication.R

class NotificationHelper(mContext: Context?) : ContextWrapper(mContext) {

    companion object {
        const val NOT_CHANNEL = "default"
    }

    private var manager: NotificationManager? = null
        get() {
            if (field == null) {
                field =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            return field
        }

    init {
        var mChannel: NotificationChannel? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mChannel = NotificationChannel(
                NOT_CHANNEL,
                getString(R.string.noti_channel_default), NotificationManager.IMPORTANCE_DEFAULT
            )
            mChannel.setSound(null, null);
            mChannel.lightColor = Color.GREEN
            mChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager?.createNotificationChannel(mChannel)
        }
    }

    fun getNotification(
        title: String?,
        body: String?,
        progress: Int
    ): NotificationCompat.Builder {
        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            applicationContext,
            NOT_CHANNEL
        )
        mBuilder.setSmallIcon(smallIcon)
        mBuilder.setSound(null)
        mBuilder.color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
        mBuilder.setContentTitle(title)
            .setContentText(body)
            .setOngoing(true) //.setContentIntent(resultPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL).priority = NotificationCompat.PRIORITY_HIGH
        mBuilder.setVibrate(longArrayOf(0L))
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        mBuilder.setProgress(100, progress, false)
        if (progress == 100) {
            mBuilder.setProgress(0, 0, false)
            mBuilder.setContentText(body)
        }
        return mBuilder
    }

    fun getNotification(
        title: String?, body: String?,
        resultPendingIntent: PendingIntent?
    ): NotificationCompat.Builder {
        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            applicationContext,
            NOT_CHANNEL
        )
        mBuilder.setSound(null)
        mBuilder.setSmallIcon(smallIcon)
        mBuilder.color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
        mBuilder.setContentTitle(title)
            .setContentText(body)
            .setContentIntent(resultPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL).priority = NotificationCompat.PRIORITY_HIGH
        mBuilder.setVibrate(longArrayOf(0L))
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return mBuilder
    }

    fun notify(id: Int, notification: NotificationCompat.Builder) {
        manager?.notify(id, notification.setSound(null).build())
    }

    private val smallIcon: Int
        get() = R.drawable.ic_camera

    fun cancelNotification(notificationId: Int) {
        manager?.cancel(notificationId)
    }

}