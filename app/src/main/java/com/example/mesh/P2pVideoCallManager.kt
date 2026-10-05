package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class P2pVideoCallManager(
    private val context: Context,
    private val onFrameReady: (base64Frame: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "P2pVideoCallManager"
    }

    private var currentQuality: VideoQuality = VideoQuality.HIGH

    fun setVideoQuality(quality: VideoQuality) {
        if (currentQuality == quality) return
        currentQuality = quality
        Log.i(TAG, "Calidad de video cambiada a: ${quality.title} (${quality.width}x${quality.height})")
        if (isStreamingActive) {
            try {
                captureSession?.close()
                cameraDevice?.close()
                imageReader?.close()
            } catch (_: Exception) {}
            startCameraCapture()
        }
    }

    fun getVideoQuality(): VideoQuality = currentQuality

    private val _localVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val localVideoBitmap: StateFlow<Bitmap?> = _localVideoBitmap.asStateFlow()

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var isFrontFacing: Boolean = true

    @Volatile
    private var isStreamingActive = false

    @Volatile
    private var lastFrameTimestamp = 0L

    fun startVideoStream(frontCamera: Boolean = true) {
        isFrontFacing = frontCamera
        isStreamingActive = true
        lastFrameTimestamp = 0L

        startCameraBackgroundThread()
        startCameraCapture()
        Log.i(TAG, "Video streaming iniciado (TCP MeshPacket, max 8 FPS)")
    }

    fun stopVideoStream() {
        isStreamingActive = false

        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        stopCameraBackgroundThread()
        _localVideoBitmap.value = null
        _remoteVideoBitmap.value = null
        Log.i(TAG, "Video streaming detenido")
    }

    fun switchCamera() {
        if (!isStreamingActive) return
        isFrontFacing = !isFrontFacing
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (_: Exception) {}
        startCameraCapture()
    }

    fun onRemoteFrameReceived(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                _remoteVideoBitmap.value = bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decodificando frame remoto", e)
        }
    }

    private fun startCameraBackgroundThread() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("CameraBackground").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }
    }

    private fun stopCameraBackgroundThread() {
        cameraThread?.quitSafely()
        try { cameraThread?.join(500) } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    @SuppressLint("MissingPermission")
    private fun startCameraCapture() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = findCameraId(cameraManager, isFrontFacing)
                ?: cameraManager.cameraIdList.firstOrNull() ?: return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            val targetW = currentQuality.width
            val targetH = currentQuality.height
            // Elegir el tamaño soportado por hardware más cercano a la calidad solicitada
            val chosenSize = jpegSizes.minByOrNull { size ->
                val dw = Math.abs(size.width - targetW)
                val dh = Math.abs(size.height - targetH)
                dw + dh
            } ?: android.util.Size(targetW, targetH)

            imageReader = ImageReader.newInstance(chosenSize.width, chosenSize.height, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                if (!isStreamingActive) return@setOnImageAvailableListener
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val planes = image.planes
                    if (planes.isEmpty()) {
                        image.close()
                        return@setOnImageAvailableListener
                    }

                    // Limitar según intervalo de FPS configurado
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTimestamp < currentQuality.frameIntervalMs) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    lastFrameTimestamp = now

                    val buffer = planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        _localVideoBitmap.value = bitmap
                        val jpeg = encodeFrame(bitmap)
                        if (jpeg != null) {
                            val base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
                            onFrameReady(base64)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando frame de cámara", e)
                }
            }, cameraHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Error cámara: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando captura de cámara", e)
        }
    }

    private fun encodeFrame(bitmap: Bitmap): ByteArray? {
        var quality = currentQuality.jpegQuality
        val maxBytes = currentQuality.maxFrameSize
        var working = bitmap
        var width = bitmap.width
        var height = bitmap.height

        repeat(5) { attempt ->
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxBytes) {
                if (attempt > 0) {
                    Log.d(TAG, "Frame adaptado intento $attempt: ${bytes.size} bytes, ${width}x$height, q=$quality")
                }
                return bytes
            }
            // Reducir compresión y escala suavemente si excede el límite de paquete
            quality = (quality - 6).coerceAtLeast(35)
            width = (width * 0.92f).toInt().coerceAtLeast(160)
            height = (height * 0.92f).toInt().coerceAtLeast(120)
            working = Bitmap.createScaledBitmap(working, width, height, true)
        }
        Log.w(TAG, "Frame descartado por superar $maxBytes bytes")
        return null
    }

    private fun createCaptureSession(camera: CameraDevice) {
        val surface = imageReader?.surface ?: return
        try {
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en preview repetitivo", e)
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Fallo configurando CaptureSession")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando CaptureSession", e)
        }
    }

    private fun findCameraId(manager: CameraManager, front: Boolean): String? {
        val targetFacing = if (front)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing) return id
        }
        return null
    }
}
