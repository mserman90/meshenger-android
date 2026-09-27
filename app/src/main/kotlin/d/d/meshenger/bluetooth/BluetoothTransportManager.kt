/*
 * Copyright (C) 2026 Meshenger Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package d.d.meshenger.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import d.d.meshenger.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BluetoothTransportManager {
    private const val SERVICE_NAME = "MeshengerBluetoothTransport"
    // Custom UUID for Meshenger Bluetooth P2P transport
    private val MESHENGER_UUID: UUID = UUID.fromString("e8a939f4-7e18-4d56-a944-9337efb32e12")

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null

    private val activeSockets = ConcurrentHashMap<String, BluetoothSocket>()

    interface OnBluetoothMessageReceivedListener {
        fun onMessageReceived(senderAddress: String, rawData: String)
    }

    private val listeners = ArrayList<OnBluetoothMessageReceivedListener>()

    fun registerListener(listener: OnBluetoothMessageReceivedListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun unregisterListener(listener: OnBluetoothMessageReceivedListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyMessageReceived(senderAddress: String, rawData: String) {
        synchronized(listeners) {
            for (l in listeners) {
                try {
                    l.onMessageReceived(senderAddress, rawData)
                } catch (e: Exception) {
                    Log.d(this, "Listener notify error: $e")
                }
            }
        }
    }

    fun isBluetoothSupported(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context)
        return adapter != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context)
        return adapter != null && adapter.isEnabled
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private fun hasBluetoothPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return connectGranted
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (isRunning.getAndSet(true)) return

        val appContext = context.applicationContext
        val adapter = getBluetoothAdapter(appContext)

        if (adapter == null || !adapter.isEnabled || !hasBluetoothPermissions(appContext)) {
            Log.d(this, "Bluetooth not available, disabled, or missing permissions")
            isRunning.set(false)
            return
        }

        Log.d(this, "Starting Bluetooth Transport Server")

        // Start ServerSocket Accept Thread
        acceptThread = Thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, MESHENGER_UUID)
                while (isRunning.get()) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        null
                    }

                    if (socket != null) {
                        val deviceAddress = socket.remoteDevice.address
                        Log.d(this, "Accepted incoming Bluetooth connection from $deviceAddress")
                        activeSockets[deviceAddress] = socket
                        listenToSocket(socket, deviceAddress)
                    }
                }
            } catch (e: Exception) {
                Log.d(this, "Bluetooth ServerSocket error: $e")
            } finally {
                closeServerSocket()
            }
        }.apply { start() }

        // Connect to paired devices in background
        connectToPairedDevices(appContext)
    }

    @SuppressLint("MissingPermission")
    private fun connectToPairedDevices(context: Context) {
        val adapter = getBluetoothAdapter(context) ?: return
        if (!hasBluetoothPermissions(context)) return

        Thread {
            try {
                val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
                pairedDevices?.forEach { device ->
                    if (!isRunning.get()) return@forEach
                    val address = device.address
                    if (!activeSockets.containsKey(address)) {
                        tryConnectDevice(device)
                    }
                }
            } catch (e: Exception) {
                Log.d(this, "Error connecting to paired devices: $e")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectDevice(device: BluetoothDevice) {
        try {
            Log.d(this, "Attempting Bluetooth connection to ${device.name} (${device.address})")
            val socket = device.createRfcommSocketToServiceRecord(MESHENGER_UUID)
            socket.connect()
            val address = device.address
            activeSockets[address] = socket
            Log.d(this, "Successfully connected to Bluetooth device $address")
            listenToSocket(socket, address)
        } catch (e: Exception) {
            Log.d(this, "Could not connect to Bluetooth device ${device.address}: $e")
        }
    }

    private fun listenToSocket(socket: BluetoothSocket, address: String) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                while (isRunning.get() && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        notifyMessageReceived(address, line)
                    }
                }
            } catch (e: Exception) {
                Log.d(this, "Socket read loop ended for $address: $e")
            } finally {
                activeSockets.remove(address)
                try { socket.close() } catch (_: Exception) {}
            }
        }.start()
    }

    fun broadcastMessage(context: Context, payloadJson: String) {
        if (!isRunning.get()) return

        val appContext = context.applicationContext
        val line = payloadJson.replace("\n", "") + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)

        // Broadcast to all active Bluetooth sockets
        activeSockets.forEach { (address, socket) ->
            try {
                val out: OutputStream = socket.outputStream
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                Log.d(this, "Failed to send data to Bluetooth device $address: $e")
                activeSockets.remove(address)
                try { socket.close() } catch (_: Exception) {}
            }
        }

        // Try reconnecting/sending to paired devices if active sockets list is small
        if (activeSockets.isEmpty()) {
            connectToPairedDevices(appContext)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.d(this, "Stopping Bluetooth Transport Manager")

        closeServerSocket()

        acceptThread?.interrupt()
        acceptThread = null

        activeSockets.forEach { (_, socket) ->
            try { socket.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
