package kashyap.`in`.cameraapplication.util

import android.media.Image
import android.media.ImageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

fun saveImageFile(reader: ImageReader, file: File, onBitmapConverted: (File) -> Unit) {
    GlobalScope.launch {
        withContext(Dispatchers.IO) {
            onBitmapConverted(getImageFromReader(reader, file))
        }
    }
}

suspend fun getImageFromReader(reader: ImageReader, file: File): File {
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
private suspend fun save(bytes: ByteArray, file: File): File {
    var output: OutputStream? = null
    try {
        output = FileOutputStream(file)
        output.write(bytes)
    } finally {
        output?.close()
    }
    return file
}