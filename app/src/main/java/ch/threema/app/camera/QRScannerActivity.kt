/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.camera

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.services.QRCodeServiceImpl.QRCodeColor
import ch.threema.app.services.QRCodeServiceImpl.QR_TYPE_ANY
import ch.threema.app.utils.SoundUtil
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ThreemaActivity() {
    companion object {
        const val KEY_HINT_TEXT: String = "hint"
        const val KEY_QR_TYPE: String = "qrType"
    }

    private val logger = LoggingUtil.getThreemaLogger("QRScannerActivity")

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraPreviewContainer: View

    private var hint: String = ""
    @QRCodeColor private var qrColor = QR_TYPE_ANY

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContentView(R.layout.activity_qrscanner)

        cameraPreview = findViewById(R.id.camera_preview)
        cameraPreviewContainer = findViewById(R.id.camera_preview_container)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // we want dark icons, i.e. a light status bar
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        qrColor = intent.getIntExtra(KEY_QR_TYPE, QR_TYPE_ANY)

        if (hint.isEmpty()) {
            hint = if (intent.hasExtra(KEY_HINT_TEXT)) {
                intent.getStringExtra(KEY_HINT_TEXT)!!
            } else {
                getString(R.string.msg_default_status)
            }
        }

        // set hint text
        findViewById<TextView>(R.id.hint_view)?.let {
            it.text = hint
        }

        // set viewfinder color
        findViewById<ImageView>(R.id.camera_viewfinder)?.let {
            if (qrColor == QR_TYPE_ANY) {
                it.visibility = View.GONE
            } else {
                it.setColorFilter(qrColor)
            }
        }

        // Wait for the views to be properly laid out
        cameraPreview.post {
            setUpCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("ClickableViewAccessibility", "WrongConstant")
    private fun bindCameraUseCases() {
        val lensFacing = if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true) CameraSelector.LENS_FACING_BACK else
                    (if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) CameraSelector.LENS_FACING_FRONT else -1)

        if (lensFacing == -1) {
            Toast.makeText(this, R.string.no_camera_installed, Toast.LENGTH_SHORT).show()
            logger.info("Back and front camera are unavailable")
            finish()
            return
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val rotation = cameraPreview.display?.rotation ?: 0
        val resolution = Size(720, 1280)

        val cameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        preview = Preview.Builder()
                .setTargetResolution(resolution)
                .setTargetRotation(rotation)
                .build()

        imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(resolution)
                .setTargetRotation(rotation)
                .build()

        imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(resolution)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { decodeQRCodeState: DecodeQRCodeState ->
                        when (decodeQRCodeState) {
                            is DecodeQRCodeState.ERROR -> {
                                logger.debug("Decoder Error")
                                it.clearAnalyzer()
                                runOnUiThread {
                                    Toast.makeText(this, R.string.qr_code, Toast.LENGTH_LONG).show()
                                    returnData(null, false)
                                }
                            }
                            is DecodeQRCodeState.SUCCESS -> {
                                logger.debug("Decoder Success")
                                val qrCodeData = decodeQRCodeState.qrCode
                                it.clearAnalyzer()
                                qrCodeData?.let {
                                    returnData(qrCodeData, true)
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

    private fun returnData(qrCodeData: String?, success: Boolean) {
        if (success) {
            SoundUtil.play(R.raw.qrscanner_beep)

            val intent = Intent()
            intent.putExtra(ThreemaApplication.INTENT_DATA_QRCODE, qrCodeData)
            setResult(RESULT_OK, intent)
        } else  {
            setResult(RESULT_CANCELED)
        }

        finish()
    }
}
