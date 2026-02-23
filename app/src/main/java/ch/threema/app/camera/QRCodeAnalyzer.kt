package ch.threema.app.camera

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import ch.threema.base.utils.getThreemaLogger
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

private val logger = getThreemaLogger("QRCodeAnalyzer")

class QRCodeAnalyzer(private val onDecodeQRCode: (decodeQRCodeState: DecodeQRCodeState) -> Unit) :
    ImageAnalysis.Analyzer {

    private val formats = listOf(ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888)
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    private fun decode(imageProxy: ImageProxy, data: ByteArray) {
        val source = PlanarYUVLuminanceSource(
            data,
            imageProxy.planes[0].rowStride,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false,
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result: Result = reader.decodeWithState(binaryBitmap)
        onDecodeQRCode(DecodeQRCodeState.SUCCESS(result.text))
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (imageProxy.format in formats && imageProxy.planes.size == 3) {
                val data = imageProxy.planes[0].buffer.toByteArray()
                try {
                    decode(imageProxy, data)
                } catch (e: NotFoundException) {
                    for (i in data.indices) data[i] = (255 - (data[i].toInt() and 0xff)).toByte()
                    try {
                        decode(imageProxy, data)
                    } catch (e: Exception) {
                        logger.debug("Decode error for inverted QR Code")
                    }
                } catch (e: Exception) {
                    logger.error("Scanning error", e)
                }
            } else {
                onDecodeQRCode(DecodeQRCodeState.ERROR)
            }
        } catch (e: IllegalStateException) {
            logger.error("QRCode analyzer exception", e)
            onDecodeQRCode(DecodeQRCodeState.ERROR)
        } finally {
            imageProxy.close()
        }
    }
}

sealed class DecodeQRCodeState {
    data class SUCCESS(val qrCode: String?) : DecodeQRCodeState()
    data object ERROR : DecodeQRCodeState()
}
