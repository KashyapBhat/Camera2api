package kashyap.`in`.cameraapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kashyap.`in`.cameraapplication.common.CAMERA_BACK
import kashyap.`in`.cameraapplication.common.CAMERA_FRONT
import kashyap.`in`.cameraapplication.common.CAMERA_ID
import kashyap.`in`.cameraapplication.network.FileUploadService
import kashyap.`in`.cameraapplication.singleton.SharedPrefUtils
import kashyap.`in`.cameraapplication.util.saveImageFile
import java.io.File
import java.util.*

open class MainActivity : AppCompatActivity() {

    companion object {
        fun createInstance(cameraId: String?): Intent {
            val intent = Intent()
            intent.putExtra(CAMERA_ID, cameraId)
            return intent
        }

        private const val TAG = "AndroidCamera2"
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 200

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSessions: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val file: File? = null
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var progressBar: ProgressBar? = null
    private var isCapturing = false
    private var textureView: TextureView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        intentData
        setUpView()
        setUpFolder()
    }

    private val intentData: Unit
        get() {
            cameraId = intent.getStringExtra(CAMERA_ID)
            if (cameraId == null) cameraId =
                SharedPrefUtils.getInstance(this).get(CAMERA_ID, CAMERA_FRONT) as String
        }

    private fun setUpView() {
        textureView = findViewById(R.id.texture)
        textureView?.surfaceTextureListener = textureListener
        val takePicture = findViewById<ImageButton>(R.id.picture)
        takePicture.setOnClickListener {
            if (!isCapturing) takePicture()
        }
        val rotate = findViewById<ImageButton>(R.id.rotate)
        rotate.setOnClickListener { rotateCamera() }
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setUpFolder() {
        val folder = File(
            Environment.getExternalStorageDirectory()
                .toString() + "/" + TAG
        )
        folder.mkdirs()
    }

    var textureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePicture() {
        if (isCapturing) {
            Toast.makeText(this, "Processing... Please wait", Toast.LENGTH_LONG).show()
            return
        }
        if (cameraDevice == null) {
            textureView?.surfaceTextureListener = textureListener
            return
        }
        setCapturing(true)
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var characteristics: CameraCharacteristics? = null
            characteristics = cameraDevice?.id?.let { manager.getCameraCharacteristics(it) }
            var jpegSizes: Array<Size>? = null
            jpegSizes =
                characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
            var width = 640
            var height = 480
            if (jpegSizes != null && jpegSizes.isNotEmpty()) {
                width = jpegSizes[0].width
                height = jpegSizes[0].height
            }
            val reader =
                ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurfaces: MutableList<Surface> =
                ArrayList(2)
            outputSurfaces.add(reader.surface)
            outputSurfaces.add(Surface(textureView?.surfaceTexture))
            val captureBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(reader.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder?.set(
                CaptureRequest.JPEG_ORIENTATION,
                getJpegOrientation(characteristics, rotation)
            )
            val file = File(
                Environment.getExternalStorageDirectory()
                    .toString() + "/" + TAG + "/" + System.currentTimeMillis() + ".jpg"
            )
            val readerListener = OnImageAvailableListener {
                saveImageFile(it, file) { file ->
                    Log.d("Hey", "File:" + file.absolutePath)
                    runOnUiThread { setCapturing(false) }
                    if (!file.exists() || file.absolutePath.toString().isEmpty()) {
                        Toast.makeText(this, "Select file first", Toast.LENGTH_LONG).show()
                        return@saveImageFile
                    }
                    val mIntent = Intent(this, FileUploadService::class.java)
                    mIntent.putExtra("mFilePath", file.absolutePath)
                    FileUploadService.enqueueWork(this, mIntent)
                }
            }
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Toast.makeText(this@MainActivity, "Saved:$file", Toast.LENGTH_SHORT).show()
                    createCameraPreview()
                }
            }
            cameraDevice?.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            captureBuilder?.build()?.let {
                                session.capture(
                                    it,
                                    captureListener,
                                    mBackgroundHandler
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                mBackgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setCapturing(isCapturing: Boolean) {
        this.isCapturing = isCapturing
        progressBar?.visibility = if (isCapturing) View.VISIBLE else View.GONE
    }

    protected fun createCameraPreview() {
        try {
            val texture = textureView?.surfaceTexture
            imageDimension?.width?.let {
                imageDimension?.height?.let { it1 ->
                    texture?.setDefaultBufferSize(
                        it,
                        it1
                    )
                }
            }
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == cameraDevice) {
                            return
                        }
                        cameraCaptureSessions = cameraCaptureSession
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Configuration failed...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.e(TAG, "is camera open")
        try {
//            cameraId = manager.getCameraIdList()[0];
            var characteristics: CameraCharacteristics? = null
            characteristics = cameraId?.let { manager.getCameraCharacteristics(it) }
            var map: StreamConfigurationMap? = null
            if (characteristics != null) {
                map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }
            imageDimension = map?.getOutputSizes(SurfaceTexture::class.java)?.get(0)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }
            cameraId?.let { manager.openCamera(it, stateCallback, null) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return")
        }
        captureRequestBuilder?.set(
            CaptureRequest.CONTROL_MODE,
            CameraMetadata.CONTROL_MODE_AUTO
        )
        try {
            captureRequestBuilder?.build()?.let {
                cameraCaptureSessions?.setRepeatingRequest(
                    it,
                    null,
                    mBackgroundHandler
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateCamera() {
        cameraId = if (cameraId == CAMERA_FRONT) CAMERA_BACK else CAMERA_FRONT
        SharedPrefUtils.getInstance(this).put(CAMERA_ID, cameraId!!)
        recreate()
    }

    private fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                    this@MainActivity,
                    "Sorry, you can't use this app without granting permission",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (textureView?.isAvailable == true) {
            openCamera()
        } else {
            textureView?.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        //closeCamera();
        stopBackgroundThread()
        try {
            Thread.sleep(500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onPause()
    }

    private fun getJpegOrientation(c: CameraCharacteristics?, deviceOrientation: Int): Int {
        var orientation = deviceOrientation
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return 0
        var sensorOrientation = 0
        try {
            sensorOrientation = c?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            orientation = (orientation + 45) / 90 * 90
            val facingFront =
                c?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            if (facingFront) orientation = -orientation
        } catch (ignored: Exception) {
        }
        return (sensorOrientation + orientation + 360) % 360
    }
}