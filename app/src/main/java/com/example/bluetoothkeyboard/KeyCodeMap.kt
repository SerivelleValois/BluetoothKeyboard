package com.example.bluetoothkeyboard

/**
 * Maps characters to HID key codes
 * Based on USB HID Usage Tables for keyboards
 */
object KeyCodeMap {

    // Standard key mappings (char to HID keycode)
    private val charToKeyCode = mapOf(
        // Numbers row
        '`' to 0x35, '1' to 0x1E, '2' to 0x1F, '3' to 0x20, '4' to 0x21,
        '5' to 0x22, '6' to 0x23, '7' to 0x24, '8' to 0x25, '9' to 0x26,
        '0' to 0x27, '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30,
        '\\' to 0x31, ';' to 0x33, '\'' to 0x34, ',' to 0x36, '.' to 0x37,
        '/' to 0x38,

        // Letters (lowercase)
        'a' to 0x04, 'b' to 0x05, 'c' to 0x06, 'd' to 0x07, 'e' to 0x08,
        'f' to 0x09, 'g' to 0x0A, 'h' to 0x0B, 'i' to 0x0C, 'j' to 0x0D,
        'k' to 0x0E, 'l' to 0x0F, 'm' to 0x10, 'n' to 0x11, 'o' to 0x12,
        'p' to 0x13, 'q' to 0x14, 'r' to 0x15, 's' to 0x16, 't' to 0x17,
        'u' to 0x18, 'v' to 0x19, 'w' to 0x1A, 'x' to 0x1B, 'y' to 0x1C,
        'z' to 0x1D,

        // Letters (uppercase) - same keycodes, used with shift modifier
        'A' to 0x04, 'B' to 0x05, 'C' to 0x06, 'D' to 0x07, 'E' to 0x08,
        'F' to 0x09, 'G' to 0x0A, 'H' to 0x0B, 'I' to 0x0C, 'J' to 0x0D,
        'K' to 0x0E, 'L' to 0x0F, 'M' to 0x10, 'N' to 0x11, 'O' to 0x12,
        'P' to 0x13, 'Q' to 0x14, 'R' to 0x15, 'S' to 0x16, 'T' to 0x17,
        'U' to 0x18, 'V' to 0x19, 'W' to 0x1A, 'X' to 0x1B, 'Y' to 0x1C,
        'Z' to 0x1D,

        // Special characters (shifted numbers and punctuation)
        '!' to 0x1E, '@' to 0x1F, '#' to 0x20, '$' to 0x21, '%' to 0x22,
        '^' to 0x23, '&' to 0x24, '*' to 0x25, '(' to 0x26, ')' to 0x27,
        '_' to 0x2D, '+' to 0x2E, '{' to 0x2F, '}' to 0x30, '|' to 0x31,
        ':' to 0x33, '"' to 0x34, '<' to 0x36, '>' to 0x37, '?' to 0x38,
        '~' to 0x35
    )

    // Special keys that don't have character representations
    private val specialKeys = mapOf(
        "Backspace" to HidConstants.KEY_BACKSPACE,
        "Tab" to HidConstants.KEY_TAB,
        "Enter" to HidConstants.KEY_ENTER,
        "Caps" to HidConstants.KEY_CAPS_LOCK,
        "Shift" to HidConstants.KEY_LEFT_SHIFT,
        "Ctrl" to HidConstants.KEY_LEFT_CTRL,
        "Alt" to HidConstants.KEY_LEFT_ALT,
        "Win" to HidConstants.KEY_LEFT_GUI,
        "Menu" to 0x76,
        "Esc" to HidConstants.KEY_ESCAPE,
        "Space" to HidConstants.KEY_SPACE,
        "←" to HidConstants.KEY_LEFT_ARROW,
        "↑" to HidConstants.KEY_UP_ARROW,
        "↓" to HidConstants.KEY_DOWN_ARROW,
        "→" to HidConstants.KEY_RIGHT_ARROW,
        "Home" to HidConstants.KEY_HOME,
        "End" to HidConstants.KEY_END,
        "PgUp" to HidConstants.KEY_PAGE_UP,
        "PgDn" to HidConstants.KEY_PAGE_DOWN,
        "Insert" to HidConstants.KEY_INSERT,
        "Delete" to HidConstants.KEY_DELETE
    )

    /**
     * Get HID keycode for a character
     */
    fun getHidCode(char: Char): Int {
        return charToKeyCode[char] ?: 0
    }

    /**
     * Get HID keycode from a string (for special keys)
     */
    fun getSpecialKeyCode(keyName: String): Int? {
        return specialKeys[keyName]
    }

    /**
     * Check if a character requires shift modifier
     */
    fun needsShift(char: Char): Boolean {
        return when (char) {
            in 'A'..'Z' -> true
            '!' -> true
            '@' -> true
            '#' -> true
            '$' -> true
            '%' -> true
            '^' -> true
            '&' -> true
            '*' -> true
            '(' -> true
            ')' -> true
            '_' -> true
            '+' -> true
            '{' -> true
            '}' -> true
            '|' -> true
            ':' -> true
            '"' -> true
            '<' -> true
            '>' -> true
            '?' -> true
            '~' -> true
            else -> false
        }
    }
}