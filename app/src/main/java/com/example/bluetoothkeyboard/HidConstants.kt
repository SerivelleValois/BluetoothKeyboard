package com.example.bluetoothkeyboard

/**
 * HID (Human Interface Device) constants for Bluetooth keyboard
 */
object HidConstants {
    
    // HID Descriptor for a standard keyboard
    // This describes the device capabilities to the host
    val KEYBOARD_HID_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
        0x05.toByte(), 0x07.toByte(),  // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),  // Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(),  // Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(),  // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),  // Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),  // Report Size (1)
        0x95.toByte(), 0x08.toByte(),  // Report Count (8)
        0x81.toByte(), 0x02.toByte(),  // Input (Data, Variable, Absolute) - Modifier keys
        0x95.toByte(), 0x01.toByte(),  // Report Count (1)
        0x75.toByte(), 0x08.toByte(),  // Report Size (8)
        0x81.toByte(), 0x01.toByte(),  // Input (Constant) - Reserved byte
        0x95.toByte(), 0x05.toByte(),  // Report Count (5)
        0x75.toByte(), 0x01.toByte(),  // Report Size (1)
        0x05.toByte(), 0x08.toByte(),  // Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(),  // Usage Minimum (1)
        0x29.toByte(), 0x05.toByte(),  // Usage Maximum (5)
        0x91.toByte(), 0x02.toByte(),  // Output (Data, Variable, Absolute) - LED report
        0x95.toByte(), 0x01.toByte(),  // Report Count (1)
        0x75.toByte(), 0x03.toByte(),  // Report Size (3)
        0x91.toByte(), 0x01.toByte(),  // Output (Constant) - LED padding
        0x95.toByte(), 0x06.toByte(),  // Report Count (6)
        0x75.toByte(), 0x08.toByte(),  // Report Size (8)
        0x15.toByte(), 0x00.toByte(),  // Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),  // Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),  // Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),  // Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),  // Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),  // Input (Data, Array) - Key arrays
        0xC0.toByte()                  // End Collection
    )
    
    // HID Report ID
    const val REPORT_ID = 0
    
    // Report size: 8 bytes
    // Byte 0: Modifier keys (Ctrl, Shift, Alt, GUI)
    // Byte 1: Reserved
    // Bytes 2-7: Key codes (up to 6 simultaneous keys)
    const val REPORT_SIZE = 8
    
    // Modifier key bits
    const val MODIFIER_LEFT_CTRL = 0x01
    const val MODIFIER_LEFT_SHIFT = 0x02
    const val MODIFIER_LEFT_ALT = 0x04
    const val MODIFIER_LEFT_GUI = 0x08
    const val MODIFIER_RIGHT_CTRL = 0x10
    const val MODIFIER_RIGHT_SHIFT = 0x20
    const val MODIFIER_RIGHT_ALT = 0x40
    const val MODIFIER_RIGHT_GUI = 0x80
    
    // Special key codes
    const val KEY_NONE = 0x00
    const val KEY_A = 0x04
    const val KEY_Z = 0x1D
    const val KEY_1 = 0x1E
    const val KEY_0 = 0x27
    const val KEY_ENTER = 0x28
    const val KEY_ESCAPE = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_MINUS = 0x2D
    const val KEY_EQUAL = 0x2E
    const val KEY_LEFT_BRACKET = 0x2F
    const val KEY_RIGHT_BRACKET = 0x30
    const val KEY_BACKSLASH = 0x31
    const val KEY_SEMICOLON = 0x33
    const val KEY_QUOTE = 0x34
    const val KEY_GRAVE = 0x35
    const val KEY_COMMA = 0x36
    const val KEY_PERIOD = 0x37
    const val KEY_SLASH = 0x38
    const val KEY_CAPS_LOCK = 0x39
    const val KEY_F1 = 0x3A
    const val KEY_F12 = 0x45
    const val KEY_PRINT_SCREEN = 0x46
    const val KEY_SCROLL_LOCK = 0x47
    const val KEY_PAUSE = 0x48
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT_ARROW = 0x4F
    const val KEY_LEFT_ARROW = 0x50
    const val KEY_DOWN_ARROW = 0x51
    const val KEY_UP_ARROW = 0x52
    const val KEY_LEFT_CTRL = 0xE0
    const val KEY_LEFT_SHIFT = 0xE1
    const val KEY_LEFT_ALT = 0xE2
    const val KEY_LEFT_GUI = 0xE3
    const val KEY_RIGHT_CTRL = 0xE4
    const val KEY_RIGHT_SHIFT = 0xE5
    const val KEY_RIGHT_ALT = 0xE6
    const val KEY_RIGHT_GUI = 0xE7
}

/**
 * USB HID to Android key code mapping
 */
object KeyCodeMap {
    private val map = mapOf(
        'a' to HidConstants.KEY_A,
        'b' to 0x05,
        'c' to 0x06,
        'd' to 0x07,
        'e' to 0x08,
        'f' to 0x09,
        'g' to 0x0A,
        'h' to 0x0B,
        'i' to 0x0C,
        'j' to 0x0D,
        'k' to 0x0E,
        'l' to 0x0F,
        'm' to 0x10,
        'n' to 0x11,
        'o' to 0x12,
        'p' to 0x13,
        'q' to 0x14,
        'r' to 0x15,
        's' to 0x16,
        't' to 0x17,
        'u' to 0x18,
        'v' to 0x19,
        'w' to 0x1A,
        'x' to 0x1B,
        'y' to 0x1C,
        'z' to HidConstants.KEY_Z,
        '1' to HidConstants.KEY_1,
        '2' to 0x1F,
        '3' to 0x20,
        '4' to 0x21,
        '5' to 0x22,
        '6' to 0x23,
        '7' to 0x24,
        '8' to 0x25,
        '9' to 0x26,
        '0' to HidConstants.KEY_0,
        '\n' to HidConstants.KEY_ENTER,
        '\t' to HidConstants.KEY_TAB,
        ' ' to HidConstants.KEY_SPACE,
        '-' to HidConstants.KEY_MINUS,
        '=' to HidConstants.KEY_EQUAL,
        '[' to HidConstants.KEY_LEFT_BRACKET,
        ']' to HidConstants.KEY_RIGHT_BRACKET,
        '\\' to HidConstants.KEY_BACKSLASH,
        ';' to HidConstants.KEY_SEMICOLON,
        '\'' to HidConstants.KEY_QUOTE,
        '`' to HidConstants.KEY_GRAVE,
        ',' to HidConstants.KEY_COMMA,
        '.' to HidConstants.KEY_PERIOD,
        '/' to HidConstants.KEY_SLASH,
    )
    
    fun getHidCode(char: Char): Int {
        return map[char.lowercaseChar()] ?: HidConstants.KEY_NONE
    }
    
    fun isUpperCase(char: Char): Boolean {
        return char.isUpperCase()
    }
    
    fun needsShift(char: Char): Boolean {
        // Characters that require shift key
        val shiftChars = setOf(
            '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '_', '+', '{', '}', '|', ':', '"', '<', '>', '?',
            '~'
        )
        return char.isUpperCase() || char in shiftChars
    }
    
    fun getShiftedChar(char: Char): Char {
        return when (char) {
            '1' -> '!'
            '2' -> '@'
            '3' -> '#'
            '4' -> '$'
            '5' -> '%'
            '6' -> '^'
            '7' -> '&'
            '8' -> '*'
            '9' -> '('
            '0' -> ')'
            '-' -> '_'
            '=' -> '+'
            '[' -> '{'
            ']' -> '}'
            '\\' -> '|'
            ';' -> ':'
            '\'' -> '"'
            '`' -> '~'
            ',' -> '<'
            '.' -> '>'
            '/' -> '?'
            else -> char
        }
    }
}