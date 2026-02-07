package com.example.bluetoothkeyboard

import android.bluetooth.*
import android.content.Context
import android.content.Intent
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
    private var appRegistered = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var connectionListener: ConnectionListener? = null
    private var logListener: LogListener? = null

    fun setLogListener(listener: LogListener?) {
        logListener = listener
    }
    
    // Track currently pressed keys for multi-key support
    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private var currentModifier: Int = 0
    
    interface ConnectionListener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected(device: BluetoothDevice)
        fun onError(error: String)
    }

    interface LogListener {
        fun onLog(message: String)
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
            log("ERROR: Bluetooth not supported")
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
        log("========== REGISTER HID DEVICE ==========")
        val adapter = bluetoothAdapter ?: run {
            log("ERROR: Bluetooth adapter is null")
            return
        }

        if (!adapter.isEnabled) {
            log("ERROR: Bluetooth is disabled")
            return
        }

        log("Getting HID Device profile proxy...")
        // Get HID device proxy
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                log("onServiceConnected: profile=$profile, proxy=$proxy")
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    log("✓ HID Device service connected: $hidDevice")
                    // Callback will be set when startAdvertising is called
                } else {
                    Log.w(TAG, "Profile connected but not HID_DEVICE: $profile")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                log("onServiceDisconnected: profile=$profile")
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    log("✗ HID Device service disconnected")
                }
            }
        }, BluetoothProfile.HID_DEVICE)
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
        // Check if already registered
        if (appRegistered) {
            log("HID app already registered, skipping...")
            return
        }

        log("========== START ADVERTISING ==========")
        log("Bluetooth adapter: ${bluetoothAdapter?.name} (${bluetoothAdapter?.address})")
        log("HID Device available: ${hidDevice != null}")

        val device = hidDevice ?: run {
            log("ERROR: HID device not initialized")
            connectionListener?.onError("HID device not initialized")
            return
        }

        if (device.connectedDevices.isNotEmpty()) {
            log("Already connected to a device")
            return
        }

        handler.post {
            connectionListener?.onConnectionStateChanged(ConnectionState.ADVERTISING)
        }

        // Create SDP settings
        val app = BluetoothHidDeviceAppSdpSettings(
            "Android BT Keyboard",
            "Bluetooth Keyboard",
            "Manufacturer",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidConstants.KEYBOARD_HID_DESCRIPTOR
        )

        log("Created SDP settings: ${app.name}")

        // Create callback
        val cb = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                log("========== APP STATUS CHANGED ==========")
                log("Device: $pluggedDevice")
                log("Registered: $registered")
                if (registered) {
                    appRegistered = true
                    log("✓ HID app registered successfully")
                    // Device is now ready to accept connections
                } else {
                    log("ERROR: ✗ HID app registration failed")
                }
            }

            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                log("========== CONNECTION STATE CHANGED ==========")
                log("Device: ${device.name} (${device.address})")
                log("State: $state (${stateName(state)})")
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        log("✓ Device connected: ${device.address}")
                        handler.post {
                            connectionListener?.onDeviceConnected(device)
                            connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTED)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        log("✗ Device disconnected: ${device.address}")
                        handler.post {
                            connectionListener?.onDeviceDisconnected(device)
                            connectionListener?.onConnectionStateChanged(ConnectionState.DISCONNECTED)
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        log("→ Connecting to: ${device.address}")
                        handler.post {
                            connectionListener?.onConnectionStateChanged(ConnectionState.CONNECTING)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        log("→ Disconnecting from: ${device.address}")
                    }
                }
            }

            override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
                log("Get report: ${device.address}, type=$type, id=$id, size=$bufferSize")
                val report = ByteArray(HidConstants.REPORT_SIZE)
                hidDevice?.replyReport(device, type, id, report)
            }

            override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
                log("Set report: ${device.address}, type=$type, id=$id, data=${data.size} bytes")
            }

            override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
                log("Set protocol: ${device.address}, protocol=$protocol")
            }

            override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
                log("Interrupt data: ${device.address}, reportId=$reportId, data=${data.size} bytes")
            }
        }

        log("Calling registerApp...")
        val result = hidDevice?.registerApp(app, null, null, context.mainExecutor, cb)
        log("registerApp result: $result")
        // Fallback: some devices don't trigger onAppStatusChanged callback
        // If registerApp returns true, consider it registered
        if (result == true) {
            log("Setting appRegistered = true (fallback)")
            appRegistered = true
        }
    }
    
    fun connectToDevice(device: BluetoothDevice?) {
        log("========== WAITING FOR CONNECTION ==========")
        if (device != null) {
            log("Target device: ${device.name} (${device.address})")
            log("Bond state: ${bondStateName(device.bondState)}")
        } else {
            log("Waiting for any device to connect...")
        }
        log("HID device initialized: ${hidDevice != null}")
        log("App registered: $appRegistered")

        val hid = hidDevice ?: run {
            log("ERROR: ✗ HID device not initialized")
            connectionListener?.onError("HID device not initialized")
            return
        }

        if (!appRegistered) {
            log("ERROR: ✗ HID app not registered yet")
            connectionListener?.onError("HID app not registered, please wait...")
            return
        }

        // HID Device mode: wait for remote device to connect to us
        // Don't call hid.connect() - let the other device initiate connection
        log("✓ HID app registered, waiting for remote device to connect...")
        log("请在对方设备上搜索并连接 \"Android BT Keyboard\"")

        // Set device discoverable
        if (bluetoothAdapter?.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            log("Setting device discoverable...")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            log("✓ Device is now discoverable for 5 minutes")
        }

        // Update UI state
        handler.post {
            connectionListener?.onConnectionStateChanged(ConnectionState.ADVERTISING)
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

    private fun log(message: String) {
        Log.d(TAG, message)
        logListener?.onLog(message)
    }
    
    fun cleanup() {
        disconnect()
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
    }
}
