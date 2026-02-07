# ProGuard rules for Bluetooth Keyboard app

# Keep Bluetooth HID classes
-keep class android.bluetooth.** { *; }
-keep class android.bluetooth.BluetoothHidDevice { *; }
-keep class android.bluetooth.BluetoothHidDeviceAppQosSettings { *; }
-keep class android.bluetooth.BluetoothHidDeviceAppSdpSettings { *; }

# Keep Kotlin data classes
-keep class com.example.bluetoothkeyboard.** { *; }

# Keep for debugging
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
