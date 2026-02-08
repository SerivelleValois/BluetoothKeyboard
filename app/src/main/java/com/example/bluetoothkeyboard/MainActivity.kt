package com.example.bluetoothkeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main activity for Bluetooth Keyboard application
 * Manages Bluetooth connection and keyboard input
 */
class MainActivity : AppCompatActivity(), MultiTouchKeyboardView.KeyListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2
        private const val REQUEST_STORAGE_PERMISSIONS = 3
        private const val PAIRING_TIMEOUT_MS = 30000L // 30 seconds pairing timeout
    }

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var keyboardView: MultiTouchKeyboardView

    private var bluetoothHidService: BluetoothHidService? = null
    private var isConnected = false
    private val logBuffer = StringBuilder()
    private val logHandler = Handler(Looper.getMainLooper())
    private var pairingTimeoutRunnable: Runnable? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            updateStatus("蓝牙已关闭", false)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            updateStatus("蓝牙已开启", false)
                            initHidService()
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                    addLog("Bond state changed: ${device?.name} | $bondState (previous: $previousBondState)")

                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            addLog("正在配对: ${device?.name}")
                            // Start pairing timeout timer
                            cancelPairingTimeout()
                            pairingTimeoutRunnable = Runnable {
                                addLog("✗ 配对超时: ${device?.name}")
                                // Note: There's no direct API to cancel ongoing bonding
                                // User needs to cancel from Bluetooth settings
                                updateStatus("配对超时，请取消后重试", false)
                            }
                            logHandler.postDelayed(pairingTimeoutRunnable!!, PAIRING_TIMEOUT_MS)
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            addLog("✓ 配对成功: ${device?.name}")
                            cancelPairingTimeout()
                            // Bonding completed, device is now ready for HID connection
                            bluetoothHidService?.connectToDevice(device)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            cancelPairingTimeout()
                            if (previousBondState == BluetoothDevice.BOND_BONDED) {
                                addLog("✗ 已取消配对: ${device?.name}")
                            } else {
                                addLog("✗ 配对失败: ${device?.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set up global exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            addLog("========== CRASH ==========")
            addLog("Thread: ${thread.name}")
            addLog("Exception: ${throwable.javaClass.name}")
            addLog("Message: ${throwable.message}")
            addLog("Stack trace:")
            throwable.stackTrace.forEach {
                addLog("  at $it")
            }
            addLog("==========================")
            // Save logs before crashing
            forceSaveAllLogs()
            // Call original handler
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }

        setContentView(R.layout.activity_main)

        initViews()
        checkPermissions()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        val logButton = findViewById<Button>(R.id.logButton)
        keyboardView = findViewById(R.id.keyboardView)

        keyboardView.setKeyListener(this)

        connectButton.setOnClickListener {
            checkBluetoothAndConnect()
        }

        disconnectButton.setOnClickListener {
            disconnect()
        }

        logButton.setOnClickListener {
            showLogDialog()
        }

        // Disable keyboard until connected
        updateConnectionState(false)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_ADVERTISE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        // Storage permissions for saving logs to Download directory
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            checkBluetoothSupport()
        }
    }

    /**
     * Check if device supports HID Device profile
     */
    private fun isHidDeviceSupported(): Boolean {
        return try {
            // Try to get HID Device profile
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            val method = bluetoothAdapter.javaClass.getMethod("getProfileProxy",
                Context::class.java,
                Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener"),
                Int::class.javaPrimitiveType
            )
            // HID_DEVICE profile value is 19
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showHidNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle("设备不支持")
            .setMessage("您的设备不支持蓝牙HID设备功能。\n\n" +
                    "部分手机厂商（如小米、OPPO、vivo、一加等）禁用了此功能。\n\n" +
                    "解决方法：\n" +
                    "1. 使用支持HID功能的手机（原生Android或三星）\n" +
                    "2. 刷入支持HID的ROM\n" +
                    "3. 使用ROOT权限启用该功能")
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .show()
    }

    private fun checkBluetoothSupport() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            initHidService()
        }
    }

    private fun initHidService() {
        try {
            // Check if device supports HID Device profile
            if (!isHidDeviceSupported()) {
                showHidNotSupportedDialog()
                return
            }
            bluetoothHidService = BluetoothHidService(this).apply {
                setLogListener(object : BluetoothHidService.LogListener {
                    override fun onLog(message: String) {
                        addLog("[HID] $message")
                    }
                })
                setConnectionListener(object : BluetoothHidService.ConnectionListener {
                    override fun onConnectionStateChanged(state: BluetoothHidService.ConnectionState) {
                        runOnUiThread {
                            when (state) {
                                BluetoothHidService.ConnectionState.DISCONNECTED -> {
                                    updateStatus("未连接", false)
                                    updateConnectionState(false)
                                }
                                BluetoothHidService.ConnectionState.ADVERTISING -> {
                                    updateStatus("等待配对... 请在电脑蓝牙设置中搜索 \"Android BT Keyboard\"", false)
                                    showPairingInstructions()
                                }
                                BluetoothHidService.ConnectionState.CONNECTING -> {
                                    updateStatus("正在连接...", false)
                                }
                                BluetoothHidService.ConnectionState.CONNECTED -> {
                                    updateStatus("已连接", true)
                                    updateConnectionState(true)
                                }
                            }
                        }
                    }

                    override fun onDeviceConnected(device: BluetoothDevice) {
                        Log.d(TAG, "Device connected: ${device.name}")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "已连接到: ${device.name ?: "未知设备"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onDeviceDisconnected(device: BluetoothDevice) {
                        Log.d(TAG, "Device disconnected: ${device.name}")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "设备已断开",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Error: $error")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HID service", e)
            Toast.makeText(this, "初始化蓝牙键盘失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBluetoothAndConnect() {
        if (bluetoothHidService == null) {
            Toast.makeText(this, "蓝牙服务未初始化", Toast.LENGTH_SHORT).show()
            checkBluetoothSupport()
            return
        }
        showConnectionOptions()
    }

    private fun showConnectionOptions() {
        AlertDialog.Builder(this)
            .setTitle("选择连接方式")
            .setItems(arrayOf("开始等待设备连接", "选择已配对设备")) { _, which ->
                when (which) {
                    0 -> startWaitingForConnection()
                    1 -> showDeviceList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startWaitingForConnection() {
        val service = bluetoothHidService ?: return
        addLog("启动HID注册...")
        service.startAdvertising()
        addLog("等待对方设备搜索并连接...")
        Handler(Looper.getMainLooper()).postDelayed({
            addLog("设备已准备就绪")
            service.connectToDevice(null) // null means wait for any device
        }, 500)
    }

    private fun showDeviceList() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return

        // Check permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要蓝牙权限", Toast.LENGTH_SHORT).show()
            return
        }

        // Get paired devices
        val pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()

        if (pairedDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没有配对设备")
                .setMessage("未找到已配对的设备。\n\n可以选择：\n1. 开始等待新设备连接\n2. 先去蓝牙设置中配对设备")
                .setPositiveButton("开始等待") { _, _ ->
                    startWaitingForConnection()
                }
                .setNegativeButton("去配对") { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNeutralButton("取消", null)
                .show()
            return
        }

        // Show device list
        val options = pairedDevices.map { it.name ?: "未知设备 (${it.address})" }.toMutableList()
        options.add("开始等待新设备连接")

        AlertDialog.Builder(this)
            .setTitle("选择要连接的设备")
            .setItems(options.toTypedArray()) { _, which ->
                if (which < pairedDevices.size) {
                    val device = pairedDevices[which]
                    connectToDevice(device)
                } else {
                    startWaitingForConnection()
                }
            }
            .setPositiveButton("刷新") { _, _ ->
                showDeviceList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val service = bluetoothHidService ?: run {
            Toast.makeText(this, "蓝牙服务未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        // Register the HID app and wait for remote device to connect
        addLog("启动HID注册...")
        service.startAdvertising()
        // Short delay for registration to complete
        Handler(Looper.getMainLooper()).postDelayed({
            addLog("等待对方设备连接: ${device.name}")
            service.connectToDevice(device)
        }, 500)
    }

    private fun disconnect() {
        bluetoothHidService?.disconnect()
        keyboardView.releaseAllKeys()
    }

    private fun showPairingInstructions() {
        AlertDialog.Builder(this)
            .setTitle("配对说明")
            .setMessage("重要：请确保删除之前与电脑的所有配对记录！\n\n" +
                    "1. 先在手机蓝牙设置中删除与电脑的配对\n" +
                    "2. 在电脑蓝牙设置中删除与手机的配对\n" +
                    "3. 保持本应用打开\n" +
                    "4. 从电脑蓝牙设置中搜索并发起配对请求\n" +
                    "5. 在手机弹出的配对请求中点击\"配对\"\n\n" +
                    "如果连接失败，请重启蓝牙后重试。")
            .setPositiveButton("打开蓝牙设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateStatus(status: String, connected: Boolean) {
        statusText.text = "状态: $status"
        isConnected = connected

        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
    }

    private fun updateConnectionState(connected: Boolean) {
        keyboardView.isEnabled = connected
        connectButton.visibility = if (connected) View.GONE else View.VISIBLE
        disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
    }

    // MultiTouchKeyboardView.KeyListener implementation

    override fun onKeyDown(keyCode: Int, modifier: Int) {
        if (isConnected) {
            bluetoothHidService?.sendKeyPress(keyCode, modifier)
        }
    }

    override fun onKeyUp(keyCode: Int) {
        if (isConnected) {
            bluetoothHidService?.sendKeyRelease()
        }
    }

    override fun onKeyLongPress(keyCode: Int, modifier: Int) {
        // For long press, we can send the key repeatedly or special handling
        // For now, just send the key again
        if (isConnected) {
            bluetoothHidService?.sendKey(keyCode, modifier)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkBluetoothSupport()
                } else {
                    Toast.makeText(this, "需要蓝牙权限才能使用", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_OK) {
                    initHidService()
                } else {
                    Toast.makeText(this, "需要开启蓝牙", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register Bluetooth state and bond state receivers
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPairingTimeout()
        bluetoothHidService?.cleanup()
        keyboardView.releaseAllKeys()
    }

    private fun cancelPairingTimeout() {
        pairingTimeoutRunnable?.let {
            logHandler.removeCallbacks(it)
            pairingTimeoutRunnable = null
        }
    }

    private fun showLogDialog() {
        val logView = ScrollView(this)
        val textView = TextView(this).apply {
            text = logBuffer.toString()
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        logView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Debug 日志")
            .setView(logView)
            .setPositiveButton("刷新") { _, _ ->
                showLogDialog()
            }
            .setNegativeButton("导出日志") { _, _ ->
                exportLogs()
            }
            .setNeutralButton("清除") { _, _ ->
                logBuffer.clear()
                addLog("=== 日志已清除 ===")
                showLogDialog()
            }
            .show()
    }

    private fun exportLogs() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (logDir != null) {
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val logFile = java.io.File(logDir, "bluetooth_keyboard_${timestamp}.log")
                logFile.writeText(logBuffer.toString())

                // Share the log file
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(logFile))
                    putExtra(Intent.EXTRA_SUBJECT, "蓝牙键盘日志")
                }
                startActivity(Intent.createChooser(shareIntent, "分享日志"))
                Toast.makeText(this, "日志已导出: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun addLog(message: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            logBuffer.append(logEntry)
            // Limit log size
            if (logBuffer.length > 50000) {
                val start = logBuffer.indexOf("\n", 1000)
                if (start > 0) {
                    logBuffer.delete(0, start + 1)
                }
            }
            // Save to file
            saveLogToFile(logEntry)
        }
    
        private fun saveLogToFile(message: String) {
                try {
                    val logDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (logDir != null) {
                        if (!logDir.exists()) {
                            logDir.mkdirs()
                        }
                        val logFile = java.io.File(logDir, "bluetooth_keyboard_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.log")
                        logFile.appendText(message)
                    }
                } catch (e: Exception) {
                    // Silently fail to avoid infinite loop
                }
            }    
        private fun forceSaveAllLogs() {
                try {
                    val logDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (logDir != null) {
                        if (!logDir.exists()) {
                            logDir.mkdirs()
                        }
                        val logFile = java.io.File(logDir, "bluetooth_keyboard_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_crash.log")
                        logFile.writeText(logBuffer.toString())
                    }
                } catch (e: Exception) {
                    // Silently fail
                }
            }    }
