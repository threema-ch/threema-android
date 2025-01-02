/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.multidevice.wizard

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import ch.threema.app.R
import ch.threema.app.camera.DecodeQRCodeState
import ch.threema.app.camera.QRCodeAnalyzer
import ch.threema.app.ui.LongToast
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.SoundUtil
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.elevation.ElevationOverlayProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val logger = LoggingUtil.getThreemaLogger("LinkNewDeviceScanQrFragment")

class LinkNewDeviceScanQrFragment: LinkNewDeviceFragment() {
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraPreviewContainer: View

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_link_new_device_scan_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraPreview = view.findViewById(R.id.camera_preview)
        cameraPreviewContainer = view.findViewById(R.id.camera_preview_container)

        // tint viewfinder frame manually as regular views do not support elevation
        val elevationOverlayProvider = ElevationOverlayProvider(requireContext())
        val backgroundColor = elevationOverlayProvider.compositeOverlayIfNeeded(
            ConfigUtils.getColorFromAttribute(activity, R.attr.colorSurface),
            resources.getDimension(R.dimen.bottomsheet_elevation)
        )
        view.findViewById<View>(R.id.border_frame_top).setBackgroundColor(backgroundColor)
        view.findViewById<View>(R.id.border_frame_bottom).setBackgroundColor(backgroundColor)
        view.findViewById<View>(R.id.border_frame_left).setBackgroundColor(backgroundColor)
        view.findViewById<View>(R.id.border_frame_right).setBackgroundColor(backgroundColor)

        view.findViewById<TextView>(R.id.body)?.text = getString(R.string.scan_qr_code_explain, getString(R.string.app_name))

        // Wait for the views to be properly laid out
        cameraPreview.post {
            setUpCamera()
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindCameraUseCases() {
        val lensFacing = if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true) CameraSelector.LENS_FACING_BACK else
            (if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_UNKNOWN)

        if (lensFacing == CameraSelector.LENS_FACING_UNKNOWN) {
            LongToast.makeText(requireContext(), R.string.no_camera_installed, Toast.LENGTH_SHORT).show()
            logger.info("Back and front camera are unavailable")
            viewModel.switchToFragment(null)
            return
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val rotation = cameraPreview.display?.rotation ?: 0
        val resolution = Size(720, 1280)
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { decodeQRCodeState: DecodeQRCodeState ->
                    when (decodeQRCodeState) {
                        is DecodeQRCodeState.SUCCESS -> {
                            logger.debug("Decoder Success")
                            val qrCodeData = decodeQRCodeState.qrCode
                            it.clearAnalyzer()
                            RuntimeUtil.runOnUiThread {
                                returnData(qrCodeData, true)
                            }
                        }

                        else -> {
                            logger.debug("Decoder Error")
                            it.clearAnalyzer()
                            RuntimeUtil.runOnUiThread {
                                viewModel.failureReason = "Decoder error"
                                returnData(null, false)
                            }
                        }
                    }
                })
            }

        try {
            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(cameraPreview.surfaceProvider)

            val point = cameraPreview.meteringPointFactory.createPoint(
                cameraPreviewContainer.left + cameraPreviewContainer.width / 2.0f,
                cameraPreviewContainer.top + cameraPreviewContainer.height / 2.0f)
            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
        } catch (e: Exception) {
            logger.error("Use case binding failed", e)
            returnData(null, false)
        }

        cameraPreviewContainer.setOnTouchListener { _: View, motionEvent: MotionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_UP -> {
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(
                            cameraPreview.meteringPointFactory.createPoint(
                                motionEvent.x, motionEvent.y
                            )
                        ).build()
                    )
                    true
                }
                else -> false
            }
        }
    }

    @UiThread
    private fun returnData(qrCodeData: String?, success: Boolean) {
        cameraProvider?.unbindAll()

        if (success && qrCodeData != null) {
            SoundUtil.play(R.raw.qrscanner_beep)
            viewModel.qrScanResult = qrCodeData
            viewModel.switchToFragment(LinkNewDeviceConnectingFragment::class.java)
        } else {
            viewModel.showFailure(getString(R.string.qr_code_scan_error))
        }
    }
}
