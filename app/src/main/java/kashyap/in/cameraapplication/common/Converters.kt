package kashyap.`in`.cameraapplication.common

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object Converters {

    @JvmStatic
    fun convertBitmapToFile(bitmap: Bitmap, onBitmapConverted: (File) -> Unit): Disposable {
        return Single.fromCallable {
            compressBitmap(
                bitmap
            )
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it != null) {
                    Log.i("convertedPicturePath", it.path)
                    onBitmapConverted(it)
                }
            }, { it.printStackTrace() })
    }

    @JvmStatic
    fun convertBitmapToFile(
        bitmap: File,
        byte: ByteArray,
        onBitmapConverted: (File) -> Unit
    ): Disposable {
        return Single.fromCallable {
            save(
                bitmap, byte
            )
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({

            }, { it.printStackTrace() })
    }

    private fun compressBitmap(bitmap: Bitmap): File? {

        try {
            val myStuff =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Android Custom Camera"
                )
            if (!myStuff.exists())
                myStuff.mkdirs()
            val picture = File(myStuff, "Camera-" + System.currentTimeMillis() + ".jpeg")

            //Convert bitmap to byte array
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*ignored for PNG*/, bos)
            val bitmapData = bos.toByteArray()

            //write the bytes in file
            val fos = FileOutputStream(picture)
            fos.write(bitmapData)
            fos.flush()
            fos.close()
            return picture
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    private fun save(bitmap: File, byte: ByteArray): File {
        val output = FileOutputStream(bitmap)
        try {
            output.write(byte);
        } catch (e: IOException) {
        } finally {
            try {
                output.close();
            } catch (e: Exception) {
            }
        }
        return bitmap
    }


}
