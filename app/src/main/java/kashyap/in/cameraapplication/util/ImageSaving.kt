package kashyap.`in`.cameraapplication.util

import android.media.Image
import android.media.ImageReader
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.*

fun saveImageFile(reader: ImageReader, file: File, onBitmapConverted: (File) -> Unit) {
    Single.fromCallable {
        getImageFromReader(reader, file)
    }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .subscribe({
            if (it != null) {
                onBitmapConverted(it)
            }
        }, { it.localizedMessage }).dispose()
}

fun getImageFromReader(reader: ImageReader, file: File): File {
    var image: Image? = null
    try {
        image = reader.acquireLatestImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer[bytes]
        save(bytes, file)
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        image?.close()
    }
    return file
}

@Throws(IOException::class)
private fun save(bytes: ByteArray, file: File): File {
    var output: OutputStream? = null
    try {
        output = FileOutputStream(file)
        output.write(bytes)
    } finally {
        output?.close()
    }
    return file
}