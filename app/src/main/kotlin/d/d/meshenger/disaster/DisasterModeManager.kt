/*
 * Copyright (C) 2026 Meshenger Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package d.d.meshenger.disaster

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import d.d.meshenger.AddressUtils
import d.d.meshenger.Database
import d.d.meshenger.Log
import d.d.meshenger.bluetooth.BluetoothTransportManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object DisasterModeManager {
    const val DISASTER_PORT = 9876
    
    private val isRunning = AtomicBoolean(false)
    private val isWhistleActive = AtomicBoolean(false)
    private val isStrobeActive = AtomicBoolean(false)

    var currentStatus = DisasterSignal.StatusType.SAFE
    var medicalNotes: String = ""
    
    private val receivedSignalsMap = ConcurrentHashMap<String, DisasterSignal>()
    private val seenMessageIds = ConcurrentHashMap<String, Long>()
    
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var appContext: Context? = null
    
    private var broadcastThread: Thread? = null
    private var listenThread: Thread? = null
    private var whistleThread: Thread? = null
    private var strobeThread: Thread? = null
    
    var currentLocation: Location? = null
    private var locationManager: LocationManager? = null

    private val btListener = object : BluetoothTransportManager.OnBluetoothMessageReceivedListener {
        override fun onMessageReceived(senderAddress: String, rawData: String) {
            try {
                val signal = DisasterSignal.fromJSON(rawData)
                if (signal != null) {
                    handleIncomingSignal(signal, appContext)
                }
            } catch (e: Exception) {
                Log.d(DisasterModeManager, "BT signal parse error: $e")
            }
        }
    }

    fun handleIncomingSignal(signal: DisasterSignal, context: Context?) {
        val now = System.currentTimeMillis()
        // Purge old seen entries (> 10 mins)
        seenMessageIds.entries.removeIf { now - it.value > 600_000 }

        val isNewSignal = !seenMessageIds.containsKey(signal.id)
        if (isNewSignal) {
            seenMessageIds[signal.id] = now
            val key = "${signal.senderName}_${signal.id}"
            receivedSignalsMap[key] = signal
            notifyListeners()

            // Multi-hop Mesh Relay: Relay signal to neighbor nodes if TTL > 1
            if (signal.ttl > 1) {
                val relayedSignal = signal.copy(
                    ttl = signal.ttl - 1,
                    hopCount = signal.hopCount + 1
                )
                relaySignalToNetwork(context, relayedSignal)
            }
        }
    }

    private fun relaySignalToNetwork(context: Context?, relayedSignal: DisasterSignal) {
        Thread {
            try {
                val jsonStr = relayedSignal.toJSON().toString()
                val bytes = jsonStr.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(
                    bytes, bytes.size,
                    InetAddress.getByName("255.255.255.255"),
                    DISASTER_PORT
                )
                udpSocket?.send(packet)
                context?.let { ctx ->
                    BluetoothTransportManager.broadcastMessage(ctx.applicationContext, jsonStr)
                }
            } catch (e: Exception) {
                Log.d(this, "Relay error: $e")
            }
        }.start()
    }

    interface OnSignalReceivedListener {
        fun onSignalsUpdated(signals: List<DisasterSignal>)
    }

    private val listeners = ArrayList<OnSignalReceivedListener>()

    fun registerListener(listener: OnSignalReceivedListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
        listener.onSignalsUpdated(getReceivedSignals())
    }

    fun unregisterListener(listener: OnSignalReceivedListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val list = getReceivedSignals()
        synchronized(listeners) {
            for (l in listeners) {
                try {
                    l.onSignalsUpdated(list)
                } catch (e: Exception) {
                    Log.d(this, "Listener error: $e")
                }
            }
        }
    }

    fun getReceivedSignals(): List<DisasterSignal> {
        val now = System.currentTimeMillis()
        // Remove stale signals older than 5 minutes (300,000 ms)
        receivedSignalsMap.entries.removeIf { now - it.value.timestamp > 300_000 }
        return receivedSignalsMap.values.sortedByDescending { it.timestamp }
    }

    fun isDisasterModeActive(): Boolean = isRunning.get()
    fun isWhistleActive(): Boolean = isWhistleActive.get()
    fun isStrobeActive(): Boolean = isStrobeActive.get()

    @SuppressLint("MissingPermission")
    fun startDisasterMode(context: Context) {
        if (isRunning.getAndSet(true)) return

        Log.d(this, "Starting Disaster Mode")
        val ctx = context.applicationContext
        appContext = ctx

        // Acquire Wi-Fi Multicast lock
        try {
            val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("MeshengerDisasterLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.d(this, "MulticastLock error: $e")
        }

        // Start GPS location updates
        try {
            locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val locListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) { currentLocation = loc }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, locListener)
                    currentLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, locListener)
                    currentLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }
        } catch (e: Exception) {
            Log.d(this, "Location error: $e")
        }

        // Initialize UDP socket
        try {
            udpSocket = DatagramSocket(DISASTER_PORT).apply {
                broadcast = true
                reuseAddress = true
            }
        } catch (e: Exception) {
            Log.d(this, "DatagramSocket init error: $e")
        }

        // Start UDP Listener Thread
        listenThread = Thread {
            val buffer = ByteArray(2048)
            while (isRunning.get()) {
                try {
                    val socket = udpSocket ?: break
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val str = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val signal = DisasterSignal.fromJSON(str)
                    if (signal != null) {
                        handleIncomingSignal(signal, ctx)
                    }
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }.apply { start() }

        // Start Bluetooth Transport (Backup Layer)
        try {
            BluetoothTransportManager.registerListener(btListener)
            BluetoothTransportManager.start(ctx)
        } catch (e: Exception) {
            Log.d(this, "Bluetooth Transport start error: $e")
        }

        // Start UDP Broadcast Thread
        broadcastThread = Thread {
            while (isRunning.get()) {
                try {
                    val userName = Database.getSettings().username.ifEmpty { "Kullanıcı" }
                    val localAddress = AddressUtils.collectAddresses().firstOrNull()?.address ?: "127.0.0.1"
                    val signal = DisasterSignal(
                        senderName = userName,
                        status = currentStatus,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        medicalNotes = medicalNotes,
                        ipAddress = localAddress,
                        timestamp = System.currentTimeMillis()
                    )
                    val jsonStr = signal.toJSON().toString()
                    val bytes = jsonStr.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(
                        bytes, bytes.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISASTER_PORT
                    )
                    udpSocket?.send(packet)

                    // Dual transport broadcast (RFCOMM Bluetooth)
                    BluetoothTransportManager.broadcastMessage(ctx, jsonStr)
                } catch (e: Exception) {
                    Log.d(this, "Broadcast error: $e")
                }
                try { Thread.sleep(4000) } catch (_: InterruptedException) { break }
            }
        }.apply { start() }
    }

    fun stopDisasterMode() {
        if (!isRunning.getAndSet(false)) return
        Log.d(this, "Stopping Disaster Mode")

        stopWhistle()
        stopStrobe(null)

        try {
            BluetoothTransportManager.unregisterListener(btListener)
            BluetoothTransportManager.stop()
        } catch (_: Exception) {}

        try { udpSocket?.close() } catch (_: Exception) {}
        udpSocket = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        multicastLock = null

        broadcastThread?.interrupt()
        listenThread?.interrupt()
        broadcastThread = null
        listenThread = null

        receivedSignalsMap.clear()
        notifyListeners()
    }

    fun startWhistle() {
        if (isWhistleActive.getAndSet(true)) return
        whistleThread = Thread {
            val sampleRate = 44100
            val numSamples = sampleRate / 2 // 0.5 sec tone
            val sample = DoubleArray(numSamples)
            val generatedSnd = ByteArray(2 * numSamples)

            // High frequency 3500 Hz acoustic whistle
            val freqOfTone = 3500.0
            for (i in 0 until numSamples) {
                sample[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
            }
            var idx = 0
            for (dVal in sample) {
                val valShort = (dVal * 32767).toInt().toShort()
                generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
                generatedSnd[idx++] = (valShort.toInt() and 0xff00 ushr 8).toByte()
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            var audioTrack: AudioTrack? = null
            try {
                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        android.media.AudioManager.STREAM_ALARM,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack.play()

                while (isWhistleActive.get()) {
                    audioTrack.write(generatedSnd, 0, generatedSnd.size)
                    Thread.sleep(200) // Pulse pattern
                }
            } catch (e: Exception) {
                Log.d(this, "Whistle error: $e")
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
            }
        }.apply { start() }
    }

    fun stopWhistle() {
        isWhistleActive.set(false)
        whistleThread?.interrupt()
        whistleThread = null
    }

    fun startStrobe(context: Context) {
        if (isStrobeActive.getAndSet(true)) return
        val appContext = context.applicationContext
        strobeThread = Thread {
            try {
                val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraId != null) {
                    var on = false
                    while (isStrobeActive.get()) {
                        on = !on
                        cameraManager.setTorchMode(cameraId, on)
                        Thread.sleep(250)
                    }
                    cameraManager.setTorchMode(cameraId, false)
                }
            } catch (e: Exception) {
                Log.d(this, "Strobe error: $e")
            }
        }.apply { start() }
    }

    fun stopStrobe(context: Context?) {
        isStrobeActive.set(false)
        context?.let { ctx ->
            try {
                val cameraManager = ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraId != null) cameraManager.setTorchMode(cameraId, false)
            } catch (_: Exception) {}
        }
        strobeThread?.interrupt()
        strobeThread = null
    }
}
