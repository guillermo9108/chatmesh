package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class P2pVideoCallManager(private val context: Context) {
    companion object {
        private const val TAG = "P2pVideoCallManager"
        const val VIDEO_UDP_PORT = 8990

        // Resolución y calidad ajustadas para que cada frame quepa en
        // UN solo datagrama UDP (< 1400 bytes). Sin fragmentación IP.
        private const val FRAME_WIDTH = 160
        private const val FRAME_HEIGHT = 120
        private const val MAX_PACKET_SIZE = 1350
        private const val JPEG_QUALITY = 30
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _localVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val localVideoBitmap: StateFlow<Bitmap?> = _localVideoBitmap.asStateFlow()

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var sendVideoJob: Job? = null
    private var receiveVideoJob: Job? = null
    private var udpReceiveSocket: DatagramSocket? = null
    private var udpSendSocket: DatagramSocket? = null

    private var currentPeerIp: String = "192.168.49.1"
    private var isFrontFacing: Boolean = true

    @Volatile
    private var isStreamingActive = false

    fun startVideoStream(peerIp: String, frontCamera: Boolean = true) {
        currentPeerIp = peerIp
        isFrontFacing = frontCamera
        isStreamingActive = true

        startCameraBackgroundThread()
        startVideoReceiver()
        startCameraCapture()
    }

    fun stopVideoStream() {
        isStreamingActive = false
        sendVideoJob?.cancel()
        receiveVideoJob?.cancel()

        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        try { udpReceiveSocket?.close() } catch (_: Exception) {}
        udpReceiveSocket = null

        try { udpSendSocket?.close() } catch (_: Exception) {}
        udpSendSocket = null

        stopCameraBackgroundThread()
        _localVideoBitmap.value = null
        _remoteVideoBitmap.value = null
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

            imageReader = ImageReader.newInstance(FRAME_WIDTH, FRAME_HEIGHT, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                if (!isStreamingActive) return@setOnImageAvailableListener
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val planes = image.planes
                    if (planes.isEmpty()) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    val buffer = planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        _localVideoBitmap.value = bitmap
                        val jpeg = encodeFrame(bitmap)
                        if (jpeg != null) sendFrameOverUdp(jpeg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando frame", e)
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
            Log.e(TAG, "Error iniciando captura", e)
        }
    }

    /**
     * Comprime el frame a JPEG y lo reduce iterativamente hasta que
     * quepa en MAX_PACKET_SIZE. Devuelve null si es imposible.
     *
     * Estrategia: 6 intentos degradando calidad Y resolución.
     */
    private fun encodeFrame(bitmap: Bitmap): ByteArray? {
        var quality = JPEG_QUALITY
        var working = bitmap
        var width = bitmap.width
        var height = bitmap.height

        repeat(6) { attempt ->
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_PACKET_SIZE) {
                if (attempt > 0) {
                    Log.d(TAG, "Frame ajustado en intento $attempt: ${bytes.size} bytes, quality=$quality, ${width}x$height")
                }
                return bytes
            }
            quality = (quality - 8).coerceAtLeast(12)
            width = (width * 0.85f).toInt().coerceAtLeast(80)
            height = (height * 0.85f).toInt().coerceAtLeast(60)
            working = Bitmap.createScaledBitmap(working, width, height, true)
        }
        Log.w(TAG, "Frame imposible de comprimir bajo $MAX_PACKET_SIZE bytes, descartado")
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

    private fun sendFrameOverUdp(frameBytes: ByteArray) {
        if (frameBytes.size > MAX_PACKET_SIZE) return
        scope.launch {
            try {
                if (udpSendSocket == null || udpSendSocket?.isClosed == true) {
                    udpSendSocket = DatagramSocket()
                }
                val addr = InetAddress.getByName(currentPeerIp)
                udpSendSocket?.send(DatagramPacket(frameBytes, frameBytes.size, addr, VIDEO_UDP_PORT))
            } catch (_: Exception) {}
        }
    }

    private fun startVideoReceiver() {
        receiveVideoJob?.cancel()
        try { udpReceiveSocket?.close() } catch (_: Exception) {}
        udpReceiveSocket = null

        receiveVideoJob = scope.launch(Dispatchers.IO) {
            try {
                udpReceiveSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(VIDEO_UDP_PORT))
                }
                val buffer = ByteArray(MAX_PACKET_SIZE * 2)
                Log.i(TAG, "Receptor de video escuchando en puerto $VIDEO_UDP_PORT")

                while (isActive && isStreamingActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpReceiveSocket?.receive(packet)
                    if (packet.length > 0) {
                        // Filtrar nuestro propio broadcast
                        val senderIp = packet.address?.hostAddress.orEmpty()
                        if (senderIp == getLocalP2pIp()) continue

                        val bitmap = BitmapFactory.decodeByteArray(packet.data, 0, packet.length)
                        if (bitmap != null) _remoteVideoBitmap.value = bitmap
                    }
                }
            } catch (e: Exception) {
                if (isStreamingActive) Log.e(TAG, "Error receptor video UDP", e)
            } finally {
                try { udpReceiveSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun getLocalP2pIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull { it.name.lowercase().startsWith("p2p") }
                ?.inetAddresses?.toList()
                ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: ""
        } catch (_: Exception) { "" }
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