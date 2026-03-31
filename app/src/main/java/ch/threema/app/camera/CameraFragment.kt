package ch.threema.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FlashMode
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toDrawable
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ch.threema.android.postDelayed
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.camera.CameraActivity.KEY_EVENT_ACTION
import ch.threema.app.camera.CameraActivity.KEY_EVENT_EXTRA
import ch.threema.app.camera.ShutterButtonView.ShutterButtonListener
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.ui.LessObnoxiousMediaActionSound
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = getThreemaLogger("CameraFragment")

class CameraFragment : Fragment() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private val viewModel: CameraViewModel by viewModel()

    private var displayId: Int = -1
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var videoRecording: Recording? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var cameraCallback: CameraCallback? = null
    private var cameraConfiguration: CameraConfiguration? = null

    private var container: ConstraintLayout? = null
    private var controlsContainer: View? = null
    private var previewView: PreviewView? = null
    private var progressBar: CircularProgressIndicator? = null
    private var timerView: TimerView? = null
    private var windowInsets: WindowInsetsCompat? = null
    private var mediaActionSound: LessObnoxiousMediaActionSound? = null
    private var recordingMode: Int = RECORDING_MODE_IMAGE

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var broadcastManager: LocalBroadcastManager

    // Configuration options
    private var targetWidth = CameraConfig.getDefaultImageSize()
    private var targetHeight = CameraConfig.getDefaultImageSize()

    private val displayManager by lazy {
        requireContext().getSystemService<DisplayManager>()
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Volume down button receiver used to trigger shutter */
    private val volumeButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyCode = intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                val shutter: ShutterButtonView =
                    container!!.findViewById(R.id.camera_capture_button)
                shutter.simulateClick()
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                logger.debug("Rotation changed: {}", view.display.rotation)
                imageCapture?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    /**
     * Listener for pinch to zoom gesture
     */
    private val scaleGestureListener =
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector) = consume {
                val zoomState: ZoomState = camera?.cameraInfo?.zoomState?.value ?: return false
                var clampedRatio: Float = zoomState.zoomRatio * speedUpZoomBy2X(detector.scaleFactor)
                // Clamp the ratio with the zoom range.
                clampedRatio = clampedRatio.coerceIn(minimumValue = zoomState.minZoomRatio, maximumValue = zoomState.maxZoomRatio)
                camera?.cameraControl?.setZoomRatio(clampedRatio)
            }
        }

    private val videoEventConsumer: Consumer<VideoRecordEvent> = Consumer {
        when (it) {
            is VideoRecordEvent.Start -> logger.debug("Starting recording")
            is Finalize -> {
                if (it.hasError()) {
                    cameraCallback?.onError(
                        when (it.error) {
                            Finalize.ERROR_ENCODING_FAILED -> "Encoding failed"
                            Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "File size limit reached"
                            Finalize.ERROR_INSUFFICIENT_STORAGE -> "Insufficient storage"
                            Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "Invalid output options"
                            Finalize.ERROR_NO_VALID_DATA -> "No valid data"
                            Finalize.ERROR_RECORDER_ERROR -> "Recorder error"
                            Finalize.ERROR_SOURCE_INACTIVE -> "Source inactive"
                            Finalize.ERROR_UNKNOWN -> "Unknown"
                            Finalize.ERROR_NONE -> "None"
                            else -> "Unknown"
                        },
                    )
                } else {
                    previewView?.isVisible = false
                    container?.findViewById<ConstraintLayout?>(R.id.camera_ui_container)
                        ?.isVisible = false
                    cameraCallback?.onVideoReady()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        ConfigUtils.requestCameraPermissions(
            requireActivity(),
            this@CameraFragment,
            PERMISSION_REQUEST_CODE_CAMERA,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeButtonReceiver)
        displayManager?.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        inflater.inflate(R.layout.camerax_fragment_camera, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeButtonReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager?.registerDisplayListener(displayListener, null)

        container = view as ConstraintLayout
        ViewCompat.setOnApplyWindowInsetsListener(container!!) { _, insets ->
            windowInsets = insets
            logger.debug(
                "updateCameraUI: top = {} bottom = {}",
                insets.systemWindowInsetTop,
                insets.systemWindowInsetBottom,
            )
            insets
        }

        previewView = container!!.findViewById(R.id.camera_view)!!
        if (previewView == null) {
            activity?.finish()
            return
        }

        previewView!!.setOnTouchListener { v, event ->
            v.performClick()
            scaleGestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> return@setOnTouchListener true
                MotionEvent.ACTION_UP -> {
                    val factory = previewView!!.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                    return@setOnTouchListener true
                }

                else -> return@setOnTouchListener false
            }
        }

        progressBar = container!!.findViewById(R.id.progress)
        timerView = container!!.findViewById(R.id.timer_view)

        // Wait for the views to be properly laid out
        previewView!!.post {
            // Keep track of the display in which this view is attached
            displayId = previewView?.display?.displayId ?: -1

            if (displayId != -1) {
                // Build UI controls
                updateCameraUi()

                // Set up the camera and its use cases
                setUpCamera()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaActionSound = LessObnoxiousMediaActionSound()
        mediaActionSound?.load(LessObnoxiousMediaActionSound.SHUTTER_CLICK)
        scaleGestureDetector = ScaleGestureDetector(requireContext(), scaleGestureListener)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        check(activity is CameraCallback) { "Activity does not implement CameraCallback." }
        cameraCallback = activity as CameraCallback?
        cameraConfiguration = activity as CameraConfiguration?
    }

    override fun onDetach() {
        cameraCallback = null
        cameraConfiguration = null

        super.onDetach()
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        try {
            bindCameraUseCases()

            // Enable or disable switching between cameras
            updateCameraSwitchButton()
        } catch (exc: Exception) {
            logger.error("Use case binding failed", exc)
            activity?.finish()
        }
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // CameraProvider
            cameraProvider = cameraProviderFuture.get()
            if (cameraProvider == null) {
                logger.warn("Camera provider is null")
            }

            // Select lensFacing depending on the available cameras
            if (viewModel.lensFacing == CameraSelector.LENS_FACING_BACK && !hasBackCamera()) {
                // try front camera
                viewModel.lensFacing = CameraSelector.LENS_FACING_FRONT
            }

            if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFrontCamera()) {
                if (hasBackCamera()) {
                    viewModel.lensFacing = CameraSelector.LENS_FACING_BACK
                } else {
                    showToast(R.string.no_camera_installed)
                    logger.info("Back and front camera are unavailable")
                    activity?.finish()
                }
            }

            // Build and bind the camera use cases
            if (bindCameraUseCases()) {
                // Enable or disable switching between cameras
                updateCameraSwitchButton()

                // Enable flash mode switching
                restoreFlashMode()
                updateFlashButton()
            } else {
                logger.info("Unable to bind camera use cases")
                activity?.finish()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases(): Boolean {
        if (previewView == null || previewView!!.display == null) {
            return false
        }

        // Set the preferred aspect ratio as 4:3 if it is IMAGE only mode. Set the preferred aspect
        // ratio as 16:9 if it is VIDEO or MIXED mode. Then, it will be WYSIWYG when the view finder
        // is in CENTER_INSIDE mode.
        val rotation = previewView!!.display.rotation
        val isDisplayPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
        val targetAspectRatio = if (isDisplayPortrait) ASPECT_RATIO_9_16 else ASPECT_RATIO_16_9

        val cameraProvider = cameraProvider
            ?: error("Camera initialization failed.")

        val previewHeight = (previewView!!.measuredWidth / targetAspectRatio.toFloat()).toInt()

        // Preview
        preview = Preview.Builder()
            .setTargetName("Preview")
            .setTargetResolution(Size(previewView!!.measuredWidth, previewHeight))
            .setTargetRotation(rotation)
            .build()
        logger.debug(
            "Preview size: {} * {} isDisplayPortrait {}",
            previewView!!.measuredWidth,
            previewHeight,
            isDisplayPortrait,
        )

        if (recordingMode == RECORDING_MODE_VIDEO) {
            prepareVideoRecording(cameraProvider)
            imageCapture = null
        } else {
            if (!prepareImageCapture(
                    cameraProvider,
                    isDisplayPortrait,
                    targetAspectRatio,
                    rotation,
                )
            ) {
                return false
            }
            videoCapture = null
        }

        // Attach the viewfinder's surface provider to preview use case
        preview?.surfaceProvider = previewView!!.surfaceProvider
        observeCameraState(camera?.cameraInfo!!)

        return true
    }

    private fun prepareVideoRecording(cameraProvider: ProcessCameraProvider) {
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.FHD, Quality.SD, Quality.UHD),
            FallbackStrategy.higherQualityOrLowerThan(Quality.HD),
        )

        // Note that the video bit rate varies depending on the device. However, we still set the
        // bitrate to keep the bitrate close to the value we use for calculating the maximum
        // duration of a video.
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setTargetVideoEncodingBitRate(CameraConfig.getDefaultVideoBitrate())
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.Builder().requireLensFacing(viewModel.lensFacing).build(),
                preview,
                videoCapture,
            )
        } catch (e: Exception) {
            logger.error("Error binding camera to lifecycle", e)
        }
    }

    private fun prepareImageCapture(
        cameraProvider: ProcessCameraProvider,
        isDisplayPortrait: Boolean,
        targetAspectRatio: Rational,
        rotation: Int,
    ): Boolean {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.lensFacing)
            .build()

        // ImageCapture
        // Adjust the captured image resolution according to the view size and the target width.
        val width: Int
        val height: Int

        if (targetWidth > targetHeight) {
            width = (targetHeight.toFloat() * targetAspectRatio.toFloat()).toInt()
            height = targetHeight
        } else if (targetWidth < targetHeight) {
            width = targetWidth
            height = (targetWidth.toFloat() / targetAspectRatio.toFloat()).toInt()
        } else if (isDisplayPortrait) {
            width = (targetHeight.toFloat() * targetAspectRatio.toFloat()).toInt()
            height = targetHeight
        } else {
            width = targetWidth
            height = (targetWidth.toFloat() / targetAspectRatio.toFloat()).toInt()
        }

        imageCapture = ImageCapture.Builder()
            .setTargetName("ImageCapture")
            .setCaptureMode(CameraUtil.getCaptureMode())
            .setTargetResolution(Size(width, height))
            .setTargetRotation(rotation)
            .build()
        logger.debug("Image capture size: {} * {}", width, height)

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
        } catch (e: IllegalArgumentException) {
            logger.error("Unable to resolve camera", e)
            return false
        }
        return true
    }

    private fun restoreFlashMode() {
        var flashModeOrdinal = preferenceService.getCameraFlashMode()
        if (flashModeOrdinal > 0) {
            flashModeOrdinal--
            if (hasFlash()) {
                imageCapture?.flashMode = flashModeOrdinal
            }
        }
    }

    private fun updateFlashButton() {
        val flashButton = controlsContainer?.findViewById<ImageView>(R.id.flash_switch_button)
        flashButton?.let {
            try {
                if (hasFlash()) {
                    @FlashMode val flashMode: Int = imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_AUTO
                    it.post {
                        it.isVisible = true
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> {
                                it.setImageResource(R.drawable.ic_flash_auto_outline)
                            }
                            ImageCapture.FLASH_MODE_ON -> {
                                it.setImageResource(R.drawable.ic_flash_on_outline)
                            }
                            ImageCapture.FLASH_MODE_OFF -> {
                                it.setImageResource(R.drawable.ic_flash_off_outline)
                            }
                        }
                    }
                    return
                }
            } catch (exc: java.lang.IllegalStateException) {
                logger.error("Unable to get flash state", exc)
            }
            it.isVisible = false
        }
    }

    /**
     * Returns true if the CameraView's current camera has a flash unit
     * Note: The camera will be initialized in CameraView's onMeasure(). If this method is called before tha camera is set up, it will return false.
     * @return true if current camera has a flash unit, false otherwise or in case of error
     */
    private fun hasFlash(): Boolean =
        camera?.cameraInfo?.hasFlashUnit() ?: false

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        logger.debug("CameraState: Pending Open")
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        logger.debug("CameraState: Opening")
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        logger.debug("CameraState: Open")
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        logger.debug("CameraState: Closing")
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        logger.debug("CameraState: Closed")
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Stream config error")
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Camera in use")
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Max cameras in use")
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Other recoverable error")
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Camera disabled")
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Fatal error")
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        // TODO(ANDR-4447): Toast messages should be localized
                        showToast("Do not disturb mode enabled")
                    }
                }
            }
        }
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {
        if (previewView == null) {
            return
        }

        // Remove previous UI if any
        val constraintLayout: ConstraintLayout? = container?.findViewById(R.id.camera_ui_container)
        constraintLayout.let {
            container?.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val curOrientation = previewView!!.display?.rotation ?: Surface.ROTATION_0

        controlsContainer =
            if (curOrientation == Surface.ROTATION_180 || curOrientation == Surface.ROTATION_270) {
                View.inflate(requireContext(), R.layout.camerax_ui_container_reverse, container)
            } else {
                View.inflate(requireContext(), R.layout.camerax_ui_container, container)
            }

        val controls = controlsContainer!!.findViewById<View>(R.id.controls)

        windowInsets?.let {
            controls.setPadding(
                it.systemWindowInsetLeft,
                it.systemWindowInsetTop,
                it.systemWindowInsetRight,
                it.systemWindowInsetBottom,
            )
        }

        // Listener for button used to capture photo
        val shutterButton: ShutterButtonView = controls!!.findViewById(R.id.camera_capture_button)
        shutterButton.setVideoEnable(cameraConfiguration?.videoEnable ?: false)
        shutterButton.setShutterButtonListener(object : ShutterButtonListener {
            /**
             * Some android devices, e.g. pixel devices, support wide angle recordings with their camera. This is controlled with the zoom level. A
             * linear zoom of 0.0 indicates the maximum angle, whereas the value 1.0 indicates maximum zoom. The camera is initialized with a zoom
             * value between 0.0 and 1.0. If there is a wide angle available, the initial zoom level is higher than 0.0.
             *
             * As for most recordings we just want the default zoom level, we start with the default zoom level. However, if the user starts zooming
             * in now, we only start applying the zoom once the initial zoom level is reached. Otherwise it jumps to 0.0 immediately after the user
             * moves the zoom slider up.
             */
            private var initialZoomLevelReached = false

            override fun onRecordStart() {
                if (ConfigUtils.requestAudioPermissions(
                        requireActivity(),
                        this@CameraFragment,
                        PERMISSION_REQUEST_CODE_AUDIO,
                    )
                ) {
                    preStartVideoRecording()
                } else {
                    shutterButton.reset()
                }
            }

            override fun onRecordEnd() {
                stopVideoRecording()
            }

            override fun onZoomChanged(zoomFactor: Float) {
                // In case the initial zoom level has not been reached, we skip applying the zoom factor. Otherwise the zoom value would jump
                // initially.
                if (!initialZoomLevelReached) {
                    val initialZoomLevel = camera?.cameraInfo?.zoomState?.value?.linearZoom ?: return
                    if (zoomFactor < initialZoomLevel) {
                        return
                    }
                    initialZoomLevelReached = true
                }

                camera?.cameraControl?.setLinearZoom(zoomFactor)
                val zoomView: ZoomView = controls.findViewById(R.id.zoom_view)
                zoomView.setZoomFactor(zoomFactor)
            }

            override fun onClick() {
                rebindUseCases(RECORDING_MODE_IMAGE)

                // Get a stable reference of the modifiable image capture use case
                imageCapture?.let { imageCapture ->
                    if (cameraCallback?.cameraFilePath == null) {
                        return
                    }

                    val metadata = ImageCapture.Metadata().apply {
                        // Mirror image when using the front camera
                        isReversedHorizontal = viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT
                    }

                    // Create output options object which contains file + metadata
                    val outputOptions =
                        ImageCapture.OutputFileOptions.Builder(File(cameraCallback?.cameraFilePath!!))
                            .setMetadata(metadata)
                            .build()

                    // Setup image capture listener which is triggered after photo has been taken
                    imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                if (exc.message != null) {
                                    logger.debug("Capture error {}", exc.message)
                                    cameraCallback?.onError(exc.message)
                                } else {
                                    cameraCallback?.onError("Capture error")
                                }
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                // Display flash animation to indicate that photo was captured
                                previewView?.let {
                                    it.post {
                                        if (isAdded && !isDetached) {
                                            it.foreground = Color.WHITE.toDrawable()
                                            it.postDelayed(50.milliseconds) {
                                                if (isAdded && !isDetached) {
                                                    it.foreground = null
                                                    progressBar?.isVisible = true
                                                }
                                            }
                                            mediaActionSound?.play(LessObnoxiousMediaActionSound.SHUTTER_CLICK)
                                        }
                                    }
                                }

                                val savedUri = output.savedUri
                                    ?: Uri.fromFile(File(cameraCallback?.cameraFilePath!!))
                                logger.debug("Photo capture succeeded: {}", savedUri)

                                cameraCallback?.onImageReady()
                            }
                        },
                    )
                }
            }
        })

        val shutterExplainText = controls.findViewById<TextView>(R.id.shutter_explain)
        shutterExplainText?.isVisible = cameraConfiguration?.videoEnable == true

        controls.findViewById<View>(R.id.flash_switch_button).setOnClickListener { switchFlash() }

        // Setup for button used to switch cameras
        controls.findViewById<ImageView>(R.id.camera_switch_button)?.let { cameraSwitchButton ->
            // Disable the button until the camera is set up
            cameraSwitchButton.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            cameraSwitchButton.setOnClickListener {
                viewModel.lensFacing = if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }

                // Re-bind use cases to update selected camera
                bindCameraUseCases()
                restoreFlashMode()
                updateFlashButton()
            }
        }
    }

    private fun rebindUseCases(newRecordingMode: Int) {
        if (recordingMode != newRecordingMode) {
            recordingMode = newRecordingMode
            bindCameraUseCases()
        }
    }

    private fun preStartVideoRecording() {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        rebindUseCases(RECORDING_MODE_VIDEO)

        startVideoRecording()
        container!!.findViewById<View>(R.id.shutter_explain)?.isVisible = false

        val controls = controlsContainer!!.findViewById<View>(R.id.controls)

        controls.findViewById<View>(R.id.camera_switch_button).isVisible = false
        controls.findViewById<View>(R.id.flash_switch_button).isVisible = false
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val cameraSwitchButton =
            controlsContainer!!.findViewById<ImageView>(R.id.camera_switch_button)
        try {
            cameraSwitchButton?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (_: CameraInfoUnavailableException) {
            cameraSwitchButton?.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean =
        try {
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
        } catch (e: Exception) {
            logger.error("Failed to check for presence of back camera", e)
            false
        }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean =
        try {
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
        } catch (e: Exception) {
            logger.error("Failed to check for presence of front camera", e)
            false
        }

    private fun switchFlash() {
        @FlashMode var flashMode: Int = imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_OFF
        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                flashMode = ImageCapture.FLASH_MODE_ON
            }

            ImageCapture.FLASH_MODE_ON -> {
                flashMode = ImageCapture.FLASH_MODE_OFF
            }

            ImageCapture.FLASH_MODE_OFF -> {
                flashMode = ImageCapture.FLASH_MODE_AUTO
            }
        }
        imageCapture?.flashMode = flashMode
        preferenceService.setCameraFlashMode(flashMode + 1)
        updateFlashButton()
    }

    private fun startVideoRecording() {
        val context = context
        if (cameraCallback?.videoFilePath == null || context == null) {
            return
        }

        // Play shutter sound
        mediaActionSound?.play(LessObnoxiousMediaActionSound.START_VIDEO_RECORDING)
        // Use default video and audio bitrate to calculate video duration. Note that the actual
        // bitrate depends on the device. If the video gets larger than the maximum blob size, then
        // it will be transcoded to reduce file size.
        val bytesPerSecond = CameraConfig.getDefaultVideoBitrate()
            .toFloat() / 8f + CameraConfig.getDefaultAudioBitrate().toFloat() / 8f
        val durationSeconds =
            floor(((AppConstants.MAX_BLOB_SIZE - 1000000).toFloat() / bytesPerSecond).toDouble()).toLong() // we assume a MP4 overhead of 1 MB
        logger.debug(
            "Calculated video duration: " + LocaleUtil.formatTimerText(
                durationSeconds * DateUtils.SECOND_IN_MILLIS,
                true,
            ),
        )

        if (hasFlash() && imageCapture?.flashMode == ImageCapture.FLASH_MODE_ON) {
            camera?.cameraControl?.enableTorch(true)
        }

        // Create output options object which contains file + metadata
        val pendingRecording = videoCapture?.output?.prepareRecording(
            context,
            FileOutputOptions.Builder(File(cameraCallback?.videoFilePath!!)).build(),
        )
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pendingRecording?.withAudioEnabled()
        }

        lifecycleScope.launch(dispatcherProvider.worker) {
            // Wait until camera is open
            camera?.cameraInfo?.cameraState?.asFlow()
                ?.first { it.type == CameraState.Type.OPEN }

            // Add some delay so that the camera can adjust the brightness
            delay(400)

            // Start the timer and the recording
            launch(dispatcherProvider.main) {
                timerView?.start(durationSeconds * DateUtils.SECOND_IN_MILLIS) { _: Long -> stopVideoRecording() }
            }
            try {
                videoRecording =
                    pendingRecording?.start(RuntimeUtil.MainThreadExecutor(), videoEventConsumer)
            } catch (e: IllegalStateException) {
                logger.error("Unable to start recording", e)
            }
        }
    }

    private fun stopVideoRecording() {
        timerView?.stop()

        // play shutter sound
        mediaActionSound?.play(LessObnoxiousMediaActionSound.STOP_VIDEO_RECORDING)

        try {
            // enableTorch() may crash with IllegalStateException
            camera?.cameraControl?.enableTorch(false)
        } catch (_: Exception) {
            // ignore this
        }
        try {
            videoRecording?.stop()
        } catch (e: Exception) {
            logger.error("Failed to stop video recording", e)
        }
    }

    private fun speedUpZoomBy2X(scaleFactor: Float): Float =
        if (scaleFactor > 1f) {
            1.0f + (scaleFactor - 1.0f) * 2
        } else {
            1.0f - (1.0f - scaleFactor) * 2
        }

    private fun restart() {
        requireActivity().recreate()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                PERMISSION_REQUEST_CODE_AUDIO -> restart()
            }
        } else {
            when (requestCode) {
                PERMISSION_REQUEST_CODE_CAMERA -> requireActivity().finish()
            }
        }
    }

    internal interface CameraCallback {
        fun onImageReady()
        fun onVideoReady()
        fun onError(message: String?)
        val videoFilePath: String?
        val cameraFilePath: String?
    }

    internal interface CameraConfiguration {
        val videoEnable: Boolean
    }

    companion object {
        private val ASPECT_RATIO_16_9 = Rational(16, 9)
        private val ASPECT_RATIO_9_16 = Rational(9, 16)
        private const val PERMISSION_REQUEST_CODE_AUDIO = 869
        private const val PERMISSION_REQUEST_CODE_CAMERA = 868
        private const val RECORDING_MODE_IMAGE = 0
        private const val RECORDING_MODE_VIDEO = 1
    }
}
