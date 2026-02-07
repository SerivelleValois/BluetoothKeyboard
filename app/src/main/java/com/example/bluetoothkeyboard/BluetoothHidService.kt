package com.example.bluetoothkeyboard

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Service to manage Bluetooth HID Device profile
 * Handles connection, pairing, and sending keyboard reports
 */
class BluetoothHidService(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothHidService"
        private const val DEVICE_NAME = "Android BT Keyboard"
        private const val DEVICE_DESCRIPTION = "Bluetooth Keyboard"
        private const val DEVICE_PROVIDER = "Android"
        private const val DEVICE_SUBCLASS = 0x40  // Keyboard
        
        // UUID for HID service
        private val HID_UUID = UUID.fromString("00001124-0000-1000-8000-00805f9b34fb")
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var hidDeviceApp: BluetoothHidDeviceAppSdpSettings? = null
    private var callback: HidDeviceCallback? = null
    private var appRegistered = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var connectionListener: ConnectionListener? = null
    
    // Track currently pressed keys for multi-key support
    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private var currentModifier: Int = 0
    
    interface ConnectionListener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected(device: BluetoothDevice)
        fun onError(error: String)
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        ADVERTISING,
        CONNECTING,
        CONNECTED
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return
        }
        
        // Create HID device SDP settings
        hidDeviceApp = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME,
            DEVICE_DESCRIPTION,
            DEVICE_PROVIDER,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidConstants.KEYBOARD_HID_DESCRIPTOR
        )
        
        // Register HID device profile
        registerHidDevice()
    }
    
    private fun registerHidDevice() {
        Log.d(TAG, "========== REGISTER HID DEVICE ==========")
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "Bluetooth adapter is null")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return
        }

        Log.d(TAG, "Getting HID Device profile proxy...")
        // Get HID device proxy
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                Log.d(TAG, "onServiceConnected: profile=$profile, proxy=$proxy")
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    Log.d(TAG, "✓ HID Device service connected: $hidDevice")
                    setupHidCallback()
                } else {
                    Log.w(TAG, "Profile connected but not HID_DEVICE: $profile")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                Log.d(TAG, "onServiceDisconnected: profile=$profile")
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    Log.d(TAG, "✗ HID Device service disconnected")
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }
    
    private fun setupHidCallback() {
        Log.d(TAG, "========== SETUP HID CALLBACK ==========")
        val device = hidDevice ?: run {
            Log.e(TAG, "hidDevice is null")
            return
        }

        Log.d(TAG, "Creating HID device callback...")
        callback = object : HidDeviceCallback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                super.onAppStatusChanged(pluggedDevice, registered)
                Log.d(TAG, "========== APP STATUS CHANGED ==========")
                Log.d(TAG, "Device: $pluggedDevice")
                Log.d(TAG, "Registered: $registered")
                if (registered) {
                    Log.d(TAG, "✓ HID app registered successfully")
                    startAdvertising()
                } else {
                    Log.e(TAG, "✗ HID app registration failed")
                }
            }

            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                super.onConnectionStateChanged(device, state)
                Log.d(TAG, "========== CONNECTION STATE CHANGED ==========")
                Log.d(TAG, "Device: ${device.name} (${device.address})")
                Log.d(TAG, "State: $state (${stateName(state)})")
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "✓ Device connected: ${device.address}")
                        handler.post {
                            connectionListener?.onDeviceConnected(device)
                            connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTED)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "✗ Device disconnected: ${device.address}")
                        handler.post {
                            connectionListener?.onDeviceDisconnected(device)
                            connectionListener?.onConnectionStateChanged(ConnectionState.DISCONNECTED)
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        Log.d(TAG, "→ Connecting to: ${device.address}")
                        handler.post {
                            connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTING)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        Log.d(TAG, "→ Disconnecting from: ${device.address}")
                    }
                }
            }

            override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
                super.onGetReport(device, type, id, bufferSize)
                Log.d(TAG, "Get report: ${device.address}, type=$type, id=$id, size=$bufferSize")
                // Handle GET_REPORT request
                val report = ByteArray(HidConstants.REPORT_SIZE)
                hidDevice?.replyReport(device, type, id, report)
            }

            override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
                super.onSetReport(device, type, id, data)
                Log.d(TAG, "Set report: ${device.address}, type=$type, id=$id, data=${data.size} bytes")
                // Handle SET_REPORT (e.g., LED indicators)
            }

            override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
                super.onSetProtocol(device, protocol)
                Log.d(TAG, "Set protocol: ${device.address}, protocol=$protocol")
            }

            override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
                super.onInterruptData(device, reportId, data)
                Log.d(TAG, "Interrupt data: ${device.address}, reportId=$reportId, data=${data.size} bytes")
            }
        }

        hidDeviceApp?.let { app ->
            callback?.let { cb ->
                Log.d(TAG, "Calling registerApp with app: ${app.name}")
                Log.d(TAG, "App SDP settings: name=${app.name}, desc=${app.description}")
                // registerApp(sdp, inQos, outQos, executor, callback)
                val result = hidDevice?.registerApp(app, null, null, context.mainExecutor, cb)
                Log.d(TAG, "registerApp result: $result")
            } ?: run {
                Log.e(TAG, "callback is null")
            }
        } ?: run {
            Log.e(TAG, "hidDeviceApp is null")
        }
    }

    private fun stateName(state: Int): String {
        return when (state) {
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "UNKNOWN($state)"
        }
    }
    
    fun startAdvertising() {
        Log.d(TAG, "========== START ADVERTISING ==========")
        Log.d(TAG, "Bluetooth adapter: ${bluetoothAdapter?.name} (${bluetoothAdapter?.address})")
        Log.d(TAG, "HID Device available: ${hidDevice != null}")
        
        val device = hidDevice ?: run {
            Log.e(TAG, "HID device not initialized")
            connectionListener?.onError("HID device not initialized")
            return
        }
        
        if (device.connectedDevices.isNotEmpty()) {
            Log.d(TAG, "Already connected to a device")
            return
        }
        
        handler.post {
            connectionListener?.onConnectionStateChanged(ConnectionState.ADVERTISING)
        }
        
        // The device will be discoverable as a HID keyboard
        Log.d(TAG, "Started advertising as HID keyboard")
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "========== CONNECT TO DEVICE ==========")
        Log.d(TAG, "Target device: ${device.name} (${device.address})")
        Log.d(TAG, "Bond state: ${bondStateName(device.bondState)}")
        Log.d(TAG, "HID device initialized: ${hidDevice != null}")
        Log.d(TAG, "App registered: $appRegistered")

        val hid = hidDevice ?: run {
            Log.e(TAG, "✗ HID device not initialized")
            connectionListener?.onError("HID device not initialized")
            return
        }

        if (!appRegistered) {
            Log.e(TAG, "✗ HID app not registered yet")
            connectionListener?.onError("HID app not registered, please wait...")
            return
        }

        try {
            Log.d(TAG, "Calling hid.connect()...")
            hid.connect(device)
            Log.d(TAG, "hid.connect() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to connect", e)
            connectionListener?.onError("Failed to connect: ${e.message}")
        }
    }

    private fun bondStateName(state: Int): String {
        return when (state) {
            BluetoothDevice.BOND_NONE -> "NONE"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_BONDED -> "BONDED"
            else -> "UNKNOWN($state)"
        }
    }
    
    fun disconnect() {
        hidDevice?.connectedDevices?.forEach { device ->
            hidDevice?.disconnect(device)
        }
        pressedKeys.clear()
        currentModifier = 0
    }
    
    /**
     * Send a single key press (press and release)
     */
    fun sendKey(keyCode: Int, modifier: Int = 0) {
        sendKeyPress(keyCode, modifier)
        // Small delay between press and release
        Thread.sleep(10)
        sendKeyRelease()
    }
    
    /**
     * Send key press (down event)
     */
    fun sendKeyPress(keyCode: Int, modifier: Int = 0) {
        val report = ByteArray(HidConstants.REPORT_SIZE)
        report[0] = modifier.toByte()
        report[1] = 0  // Reserved
        report[2] = keyCode.toByte()
        // Bytes 3-7 remain 0 for single key
        
        sendReport(report)
        
        // Track pressed key
        pressedKeys[keyCode] = System.currentTimeMillis()
    }
    
    /**
     * Send multiple keys simultaneously (up to 6 keys)
     */
    fun sendMultiKeyPress(keyCodes: List<Int>, modifier: Int = 0) {
        val report = ByteArray(HidConstants.REPORT_SIZE)
        report[0] = modifier.toByte()
        report[1] = 0  // Reserved
        
        // Fill up to 6 keys
        keyCodes.take(6).forEachIndexed { index, keyCode ->
            report[2 + index] = keyCode.toByte()
        }
        
        sendReport(report)
    }
    
    /**
     * Send key release (all keys up)
     */
    fun sendKeyRelease() {
        val report = ByteArray(HidConstants.REPORT_SIZE) { 0 }
        sendReport(report)
        pressedKeys.clear()
    }
    
    /**
     * Send a text string character by character
     */
    fun sendText(text: String) {
        Thread {
            text.forEach { char ->
                sendChar(char)
                Thread.sleep(20)  // Delay between characters
            }
        }.start()
    }
    
    /**
     * Send a single character
     */
    fun sendChar(char: Char) {
        val shift = if (KeyCodeMap.needsShift(char)) HidConstants.MODIFIER_LEFT_SHIFT else 0
        val baseChar = if (shift != 0) {
            char.lowercaseChar()
        } else {
            char
        }
        
        val hidCode = KeyCodeMap.getHidCode(baseChar)
        if (hidCode != HidConstants.KEY_NONE) {
            sendKey(hidCode, shift)
        }
    }
    
    /**
     * Send special key (arrow keys, function keys, etc.)
     */
    fun sendSpecialKey(keyCode: Int, modifier: Int = 0) {
        sendKey(keyCode, modifier)
    }
    
    private fun sendReport(report: ByteArray) {
        val device = hidDevice ?: return
        
        try {
            device.connectedDevices.firstOrNull()?.let { btDevice ->
                device.sendReport(btDevice, HidConstants.REPORT_ID, report)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send report", e)
        }
    }
    
    /**
     * Check if connected to a host
     */
    fun isConnected(): Boolean {
        return hidDevice?.connectedDevices?.isNotEmpty() ?: false
    }
    
    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }
    
    fun cleanup() {
        disconnect()
        callback = null
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }
}

/**
 * Callback for HID device events
 */
abstract class HidDeviceCallback : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {}
    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {}
    override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {}
    override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {}
    override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {}
    override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {}
}
