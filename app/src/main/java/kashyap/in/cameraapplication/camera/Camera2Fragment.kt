package kashyap.`in`.cameraapplication.camera

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.Image
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kashyap.`in`.cameraapplication.R
import kashyap.`in`.cameraapplication.common.*
import kashyap.`in`.cameraapplication.common.Converters.convertBitmapToFile
import kashyap.`in`.cameraapplication.customviews.AutoFitTextureView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
open class Camera2Fragment : Fragment(), View.OnClickListener {
    companion object {
        fun newInstance(cameraType: String): Camera2Fragment {
            val fragment =
                Camera2Fragment()
            val args = Bundle()
            args.putString("CameraType", cameraType)
            fragment.arguments = args
            return fragment
        }

        private const val REQUEST_CODE_SIGN_IN = 1
        private const val REQUEST_CODE_OPEN_DOCUMENT = 2
        private var mDriveServiceHelper: DriveServiceHelper? = null

        private val screenName = "Camera Test"

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val FRAGMENT_DIALOG = "dialog"

        /**
         * Camera state: Showing camera preview.
         */
        private const val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        private const val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        private const val STATE_PICTURE_TAKEN = 4

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size,
         * and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output
         * class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(
            choices: Array<Size>, textureViewWidth: Int,
            textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> =
                ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> =
                ArrayList()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w
                ) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(
                    bigEnough,
                    CompareSizesByArea()
                )
            } else if (notBigEnough.size > 0) {
                Collections.max(
                    notBigEnough,
                    CompareSizesByArea()
                )
            } else {
                Log.i("Couldn't find", "any suitable preview size")
                choices[0]
            }
        }

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(
                Surface.ROTATION_180,
                270
            )
            ORIENTATIONS.append(
                Surface.ROTATION_270,
                180
            )
        }
    }


    private var cameraType: String =
        FRONT_CAM

    /**
     * ID of the current [CameraDevice].
     */
    private var mCameraId: String? = null

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private var mTextureView: AutoFitTextureView? = null

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var mCaptureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * The [Size] of camera preview.
     */
    private var mPreviewSize: Size? = null
    private var ibtPicture: ImageButton? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var mImageReader: ImageReader? = null

    /**
     * This is the output file for our picture.
     */
    private var mFile: java.io.File? = null

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val mOnImageAvailableListener =
        OnImageAvailableListener { reader ->
            run {
//                saveImage(reader.acquireNextImage(), mFile)
                Toast.makeText(context, "Saving Image...", Toast.LENGTH_SHORT).show()
                mBackgroundHandler?.post(
                    ImageSaver(
                        reader.acquireNextImage(),
                        mFile
                    )
                );
            }
        }

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * [CaptureRequest] generated by [.mPreviewRequestBuilder]
     */
    private var mPreviewRequest: CaptureRequest? = null

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .mCaptureCallback
     */
    private var mState =
        STATE_PREVIEW

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock =
        Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var mFlashSupported = false

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_PREVIEW -> {
                }
                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == null) {
                        captureStillPicture()
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                    ) {
                        // CONTROL_AE_STATE can be null on some devices
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
                        ) {
                            mState =
                                STATE_PICTURE_TAKEN
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {

                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        mState =
                            STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {

                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState =
                            STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result)
        }
    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity: Activity? = activity
            activity?.finish()
        }
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        val activity: Activity? = activity
        activity?.runOnUiThread {
            Toast.makeText(
                activity,
                text,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            cameraType = arguments?.getString("CameraType") ?: BACK_CAM
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera2, container, false)
        view?.findViewById<View>(R.id.picture)?.setOnClickListener(this)
        val rotate = view.findViewById(R.id.rotate) as ImageButton
        rotate.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(
                    R.id.container,
                    newInstance(
                        if (cameraType == BACK_CAM) FRONT_CAM else BACK_CAM
                    )
                )?.commit()
        }
        return view
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        ibtPicture = view.findViewById(R.id.picture)
        ibtPicture?.tag = "ibtPicture"
        mTextureView = view.findViewById(R.id.texture)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mFile = java.io.File(
            activity?.getExternalFilesDir(null),
            "Camera-" + System.currentTimeMillis() + ".jpg"
        )
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView?.isAvailable ?: false == true) {
            openCamera(mTextureView?.width!!, mTextureView?.height!!)
        } else {
            mTextureView?.surfaceTextureListener = mSurfaceTextureListener
        }
        // CleverTap screen event : camera test
//        analyticsManager.createScreenEvent(screenName, "");
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val activity: Activity? = activity
        val manager =
            activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                Log.i("setUpCameraOutputs: ", "" + cameraId)
                val characteristics =
                    manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraType.equals(FRONT_CAM, ignoreCase = true)) {
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        continue
                    }
                } else if (cameraType.equals(BACK_CAM, ignoreCase = true)) {
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue
                    }
                }
                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                    ?: continue

                // For still image captures, we use the largest available size.
                val largest = Collections.max(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea()
                )
                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG,  /*maxImages*/2
                )
                mImageReader?.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler
                )

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation =
                    activity.windowManager.defaultDisplay.rotation
                val sensorOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.i("Rotation is invalid", "" + displayRotation)
                }
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth =
                    getScreenWidth()
                var maxPreviewHeight =
                    getScreenHeight()
                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth =
                        getScreenHeight()
                    maxPreviewHeight =
                        getScreenWidth()
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth =
                        MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight =
                        MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize =
                    chooseOptimalSize(
                        map.getOutputSizes(
                            SurfaceTexture::class.java
                        ),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest
                    )

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView?.setAspectRatio(
                        mPreviewSize?.width!!, mPreviewSize?.height!!
                    )
                } else {
                    mTextureView?.setAspectRatio(
                        mPreviewSize?.height!!, mPreviewSize?.width!!
                    )
                }

                // Check if the flash is supported.
                val available =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                mFlashSupported = available ?: false
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            Log.d("", e.localizedMessage)
        } catch (e: NullPointerException) {
        } catch (e: IllegalArgumentException) {
        }
    }

    /**
     * Opens the camera specified by [Camera2Fragment.mCameraId].
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        Dexter.withContext(this.activity)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        Log.d("All permissions", "Granted")
                        setUpCameraOutputs(width, height)
                        configureTransform(width, height)
                        val manager =
                            activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        try {
                            if (!mCameraOpenCloseLock.tryAcquire(
                                    2500,
                                    TimeUnit.MILLISECONDS
                                )
                            ) {
                                throw RuntimeException("Time out waiting to lock camera opening.")
                            }
                            mCameraId?.let {
                                manager.openCamera(
                                    it,
                                    mStateCallback,
                                    mBackgroundHandler
                                )
                            }
                        } catch (e: CameraAccessException) {
                        } catch (e: NullPointerException) {
                        } catch (e: IllegalArgumentException) {
                        } catch (e: InterruptedException) {
                            throw RuntimeException(
                                "Interrupted while trying to lock camera opening.",
                                e
                            )
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                }
            }).check()
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession?.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice?.close()
                mCameraDevice = null
            }
            if (null != mImageReader) {
                mImageReader?.close()
                mImageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException(
                "Interrupted while trying to lock camera closing.",
                e
            )
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture: SurfaceTexture? = mTextureView?.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture?.setDefaultBufferSize(mPreviewSize?.width!!, mPreviewSize?.height!!)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder =
                mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder?.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice?.createCaptureSession(
                listOf(surface, mImageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        mCaptureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            mPreviewRequestBuilder?.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            setAutoFlash(mPreviewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            mPreviewRequest = mPreviewRequestBuilder?.build()
                            mPreviewRequest?.let {
                                mCaptureSession?.setRepeatingRequest(
                                    it,
                                    mCaptureCallback, mBackgroundHandler
                                )
                            }
                        } catch (e: CameraAccessException) {
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        showToast("Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
        }
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity: Activity? = activity
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F, mPreviewSize?.height!!.toFloat(), mPreviewSize?.width
            !!.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(
                centerX - bufferRect.centerX(),
                centerY - bufferRect.centerY()
            )
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize?.height!!,
                viewWidth.toFloat() / mPreviewSize?.width!!
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView?.setTransform(matrix)
    }

    /**
     * Initiate a still image capture.
     */
    private fun takePicture() {
        lockFocus()
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            if (cameraType.equals(BACK_CAM, ignoreCase = true)) {

                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                captureStillPicture()
            } else if (cameraType.equals(FRONT_CAM, ignoreCase = true)) {
                captureStillPicture()
            }

            // Tell #mCaptureCallback to wait for the lock.
            mState =
                STATE_WAITING_LOCK
            if (mCaptureSession != null) {
                mPreviewRequestBuilder?.build()?.let {
                    mCaptureSession?.capture(
                        it, mCaptureCallback,
                        mBackgroundHandler
                    )
                }
            }
        } catch (e: CameraAccessException) {
        } catch (e: IllegalStateException) {
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.mCaptureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder?.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState =
                STATE_WAITING_PRECAPTURE
            mPreviewRequestBuilder?.build()?.let {
                mCaptureSession?.capture(
                    it, mCaptureCallback,
                    mBackgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.mCaptureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            val activity: Activity? = activity
            if (null == activity || null == mCameraDevice || null == mCaptureSession) {
                return
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder =
                mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            mImageReader?.surface?.let { captureBuilder?.addTarget(it) }

            // Use the same AE and AF modes as the preview.
            captureBuilder?.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            setAutoFlash(captureBuilder)

            // Orientation
            var rotation = activity.windowManager.defaultDisplay.rotation
            if (cameraType.equals(FRONT_CAM, ignoreCase = true)) {
                val manager =
                    activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val characteristics =
                    mCameraDevice?.id?.let { manager.getCameraCharacteristics(it) }
                val sensorOrientation =
                    characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                if (sensorOrientation == 270 || sensorOrientation == 0) {
                    rotation = 2
                }
            }
            if (cameraType.equals(BACK_CAM, ignoreCase = true)) {
                val manager =
                    activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val characteristics =
                    mCameraDevice?.id?.let { manager.getCameraCharacteristics(it) }
                val sensorOrientation =
                    characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                if (sensorOrientation == 270) {
                    rotation = 2
                } else if (sensorOrientation == 0) {
                    rotation = 0
                }
            }
            captureBuilder?.set(
                CaptureRequest.JPEG_ORIENTATION,
                ORIENTATIONS[rotation]
            )
            val CaptureCallback: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    //showToast("Saved: " + mFile);
                    Log.i("File: ", mFile.toString())
                    unlockFocus()
                }
            }
            mCaptureSession?.stopRepeating()
            captureBuilder?.build()?.let { mCaptureSession?.capture(it, CaptureCallback, null) }
        } catch (e: CameraAccessException) {
        } catch (e: IllegalStateException) {
        } catch (e: NullPointerException) {
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            setAutoFlash(mPreviewRequestBuilder)
            mPreviewRequestBuilder?.build()?.let {
                mCaptureSession?.capture(
                    it, mCaptureCallback,
                    mBackgroundHandler
                )
            }
            // After this, the camera will go back to the normal state of preview.
            mState =
                STATE_PREVIEW
            mPreviewRequest?.let {
                mCaptureSession?.setRepeatingRequest(
                    it, mCaptureCallback,
                    mBackgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
        } catch (e: NullPointerException) {
        } catch (e: IllegalStateException) {
        }
    }

    private fun changeCamAndSetView() {
        cameraType = if (cameraType == BACK_CAM) FRONT_CAM else BACK_CAM
        openCamera(mTextureView?.width!!, mTextureView?.height!!)
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder?) {
        if (mFlashSupported) {
            requestBuilder?.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    /**
     * Saves a JPEG [Image] into the specified [File].
     */
    private class ImageSaver internal constructor(
        /**
         * The JPEG image
         */
        private val mImage: Image,
        /**
         * The file we save the image into.
         */
        private val mFile: File?
    ) :
        Runnable {

        override fun run() {
            saveImage(mImage, mFile);
        }

        private fun saveImage(mImage: Image, mFile: File?) {
            val planes: Array<Plane> = mImage.planes
            val buffer: ByteBuffer = planes[0].buffer
            buffer.rewind()
            val data = ByteArray(buffer.capacity())
            buffer.get(data)
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            saveToInternalStorage(bitmap)

            mFile?.let { it ->
                convertBitmapToFile(it, data) {
                    Log.d("File: ", "" + it.absolutePath);
                }
            }

        }

        private fun saveToInternalStorage(bitmapImage: Bitmap) {
            val mypath = java.io.File(Environment.getExternalStorageDirectory(), "captured.jpg")
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(mypath)
                bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    fos?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

    }


    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                        rhs.width.toLong() * rhs.height
            )
        }
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity: Activity? = activity
            return AlertDialog.Builder(activity)
                .setMessage(arguments?.getString(ARG_MESSAGE))
                .setPositiveButton(
                    android.R.string.ok
                ) { dialogInterface: DialogInterface?, i: Int -> activity?.finish() }
                .create()
        }

        companion object {
            private const val ARG_MESSAGE = "message"
            fun newInstance(message: String?): ErrorDialog {
                val dialog =
                    ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onClick(v: View?) {
        takePicture()
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
//        when (requestCode) {
//            REQUEST_CODE_SIGN_IN -> if (resultCode == Activity.RESULT_OK && resultData != null) {
//                handleSignInResult(resultData)
//            }
//            REQUEST_CODE_OPEN_DOCUMENT -> if (resultCode == Activity.RESULT_OK && resultData != null) {
//                val uri: Uri? = resultData.data
//                if (uri != null) {
//                    openFileFromFilePicker(uri)
//                }
//            }
//        }
//        super.onActivityResult(requestCode, resultCode, resultData)
//    }
//
//    fun openFileFromFilePicker(uri: Uri) {
//        if (mDriveServiceHelper != null) {
//            mDriveServiceHelper!!.openFileUsingStorageAccessFramework(
//                activity?.contentResolver,
//                uri
//            )
//                .addOnSuccessListener { nameAndContent ->
//                    val name = nameAndContent.first
//                    val content = nameAndContent.second
//
//                }
//                .addOnFailureListener { exception ->
//                }
//        }
//    }
//
//    /**
//     * Starts a sign-in activity using [.REQUEST_CODE_SIGN_IN].
//     */
//    fun requestSignIn() {
//        Log.d("", "Requesting sign-in")
//        val signInOptions: GoogleSignInOptions =
//            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                .requestEmail()
//                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
//                .build()
//        val client: GoogleSignInClient? =
//            activity?.let { GoogleSignIn.getClient(it, signInOptions) }
//
//        // The result of the sign-in Intent is handled in onActivityResult.
//        startActivityForResult(client?.getSignInIntent(), REQUEST_CODE_SIGN_IN)
//    }
//
//    /**
//     * Handles the `result` of a completed sign-in activity initiated from [ ][.requestSignIn].
//     */
//    fun handleSignInResult(result: Intent) {
//        GoogleSignIn.getSignedInAccountFromIntent(result)
//            .addOnSuccessListener { googleAccount ->
//                Log.d("", "Signed in as " + googleAccount.getEmail())
//
//                // Use the authenticated account to sign in to the Drive service.
//                val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
//                    activity, Collections.singleton(DriveScopes.DRIVE_FILE)
//                )
//                credential.setSelectedAccount(googleAccount.account)
//                val googleDriveService: Drive = Drive.Builder(
//                    AndroidHttp.newCompatibleTransport(),
//                    GsonFactory(),
//                    credential
//                )
//                    .setApplicationName("Drive API Migration")
//                    .build()
//
//                // The DriveServiceHelper encapsulates all REST API and SAF functionality.
//                // Its instantiation is required before handling any onClick actions.
//                mDriveServiceHelper = DriveServiceHelper(googleDriveService)
//            }
//            .addOnFailureListener { exception ->
//                Log.e(
//                    "",
//                    "Unable to sign in.",
//                    exception
//                )
//            }
//    }
}