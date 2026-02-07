package com.example.bluetoothkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Custom keyboard view with multi-touch and long-press support
 */
class MultiTouchKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface KeyListener {
        fun onKeyDown(keyCode: Int, modifier: Int)
        fun onKeyUp(keyCode: Int)
        fun onKeyLongPress(keyCode: Int, modifier: Int)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyLongPressPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var keyListener: KeyListener? = null

    // Track active pointers and their associated keys
    private val pointerMap = mutableMapOf<Int, Key>()
    private val longPressHandlers = mutableMapOf<Int, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    // Long press delay
    private val LONG_PRESS_DELAY = 400L

    // Keyboard layout
    private var keys: List<Key> = createKeyboardLayout()
    private var keyRects: Map<Key, RectF> = emptyMap()

    // Visual properties
    private val keyMargin = 4f
    private val keyCornerRadius = 8f

    data class Key(
        val label: String,
        val shiftLabel: String? = null,
        val keyCode: Int,
        val width: Float = 1.0f,  // Relative width (1.0 = standard key)
        val isModifier: Boolean = false,
        val modifierBit: Int = 0,
        val row: Int = 0
    )

    private var shiftPressed = false
    private var ctrlPressed = false
    private var altPressed = false

    init {
        setupPaints()
    }

    private fun setupPaints() {
        textPaint.apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }

        keyBgPaint.apply {
            color = Color.parseColor("#333333")
            style = Paint.Style.FILL
        }

        keyPressedPaint.apply {
            color = Color.parseColor("#666666")
            style = Paint.Style.FILL
        }

        keyLongPressPaint.apply {
            color = Color.parseColor("#888888")
            style = Paint.Style.FILL
        }

        paint.apply {
            color = Color.parseColor("#222222")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
    }

    fun setKeyListener(listener: KeyListener) {
        this.keyListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyRects()
    }

    private fun calculateKeyRects() {
        val rects = mutableMapOf<Key, RectF>()

        val rows = keys.groupBy { it.row }
        val rowHeight = height.toFloat() / rows.size

        rows.forEach { (rowIndex, rowKeys) ->
            val totalWidth = rowKeys.sumOf { it.width.toDouble() }.toFloat()
            val keyWidthUnit = (width - (rowKeys.size + 1) * keyMargin) / totalWidth

            var x = keyMargin
            val y = rowIndex * rowHeight + keyMargin
            val keyHeight = rowHeight - 2 * keyMargin

            rowKeys.forEach { key ->
                val keyWidth = key.width * keyWidthUnit
                rects[key] = RectF(x, y, x + keyWidth, y + keyHeight)
                x += keyWidth + keyMargin
            }
        }

        keyRects = rects
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        keyRects.forEach { (key, rect) ->
            // Determine paint based on key state
            val bgPaint = when {
                isModifierActive(key) -> keyLongPressPaint
                pointerMap.values.contains(key) -> keyPressedPaint
                else -> keyBgPaint
            }

            // Draw key background
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, bgPaint)
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, paint)

            // Draw label
            val label = if (shiftPressed && key.shiftLabel != null) {
                key.shiftLabel
            } else {
                key.label
            }

            val centerX = rect.centerX()
            val centerY = rect.centerY() + textPaint.textSize / 3

            canvas.drawText(label, centerX, centerY, textPaint)
        }
    }

    private fun isModifierActive(key: Key): Boolean {
        return when (key.modifierBit) {
            HidConstants.MODIFIER_LEFT_SHIFT -> shiftPressed
            HidConstants.MODIFIER_LEFT_CTRL -> ctrlPressed
            HidConstants.MODIFIER_LEFT_ALT -> altPressed
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                handlePointerDown(pointerId, x, y)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                handlePointerUp(pointerId)
            }

            MotionEvent.ACTION_MOVE -> {
                // Handle move if needed (e.g., for swipe gestures)
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)

                    // Check if pointer moved outside original key
                    pointerMap[pointerId]?.let { currentKey ->
                        val rect = keyRects[currentKey]
                        if (rect != null && !rect.contains(x, y)) {
                            // Pointer moved outside - release key
                            handlePointerUp(pointerId)

                            // Check if moved to another key
                            findKeyAt(x, y)?.let { newKey ->
                                handlePointerDown(pointerId, x, y)
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                // Release all keys
                pointerMap.keys.toList().forEach { handlePointerUp(it) }
            }
        }

        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        val key = findKeyAt(x, y) ?: return

        pointerMap[pointerId] = key

        // Handle modifier keys
        if (key.isModifier) {
            toggleModifier(key)
        }

        // Calculate modifier state
        val modifier = getCurrentModifier()

        // Send key down
        keyListener?.onKeyDown(key.keyCode, modifier)

        // Post long press runnable
        val longPressRunnable = Runnable {
            if (pointerMap[pointerId] == key && !key.isModifier) {
                keyListener?.onKeyLongPress(key.keyCode, modifier)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        longPressHandlers[pointerId] = longPressRunnable
        handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)

        invalidate()
    }

    private fun handlePointerUp(pointerId: Int) {
        // Remove long press callback
        longPressHandlers.remove(pointerId)?.let {
            handler.removeCallbacks(it)
        }

        pointerMap.remove(pointerId)?.let { key ->
            if (!key.isModifier) {
                keyListener?.onKeyUp(key.keyCode)
            }
        }

        invalidate()
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        return keyRects.entries.find { it.value.contains(x, y) }?.key
    }

    private fun toggleModifier(key: Key) {
        when (key.modifierBit) {
            HidConstants.MODIFIER_LEFT_SHIFT -> shiftPressed = !shiftPressed
            HidConstants.MODIFIER_LEFT_CTRL -> ctrlPressed = !ctrlPressed
            HidConstants.MODIFIER_LEFT_ALT -> altPressed = !altPressed
        }
    }

    private fun getCurrentModifier(): Int {
        var modifier = 0
        if (shiftPressed) modifier = modifier or HidConstants.MODIFIER_LEFT_SHIFT
        if (ctrlPressed) modifier = modifier or HidConstants.MODIFIER_LEFT_CTRL
        if (altPressed) modifier = modifier or HidConstants.MODIFIER_LEFT_ALT
        return modifier
    }

    /**
     * Release all currently pressed keys
     */
    fun releaseAllKeys() {
        pointerMap.keys.toList().forEach { handlePointerUp(it) }
        shiftPressed = false
        ctrlPressed = false
        altPressed = false
        invalidate()
    }

    /**
     * Create standard QWERTY keyboard layout with row information
     */
    private fun createKeyboardLayout(): List<Key> {
        val layout = mutableListOf<Key>()
        var currentRow = 0

        // Row 1: Number keys
        val numbers = listOf(
            "`" to "~", "1" to "!", "2" to "@", "3" to "#", "4" to "$",
            "5" to "%", "6" to "^", "7" to "&", "8" to "*", "9" to "(",
            "0" to ")", "-" to "_", "=" to "+"
        )
        numbers.forEach { (base, shift) ->
            layout.add(Key(base, shift, KeyCodeMap.getHidCode(base[0]), row = currentRow))
        }
        layout.add(Key("Backspace", null, HidConstants.KEY_BACKSPACE, 2.0f, row = currentRow))
        currentRow++

        // Row 2: QWERTY row
        layout.add(Key("Tab", null, HidConstants.KEY_TAB, 1.5f, row = currentRow))
        val qwerty = listOf(
            "q" to "Q", "w" to "W", "e" to "E", "r" to "R", "t" to "T",
            "y" to "Y", "u" to "U", "i" to "I", "o" to "O", "p" to "P",
            "[" to "{", "]" to "}", "\\" to "|"
        )
        qwerty.forEach { (base, shift) ->
            layout.add(Key(base, shift, KeyCodeMap.getHidCode(base[0]), row = currentRow))
        }
        currentRow++

        // Row 3: ASDF row
        layout.add(Key("Caps", null, HidConstants.KEY_CAPS_LOCK, 1.75f, row = currentRow))
        val asdf = listOf(
            "a" to "A", "s" to "S", "d" to "D", "f" to "F", "g" to "G",
            "h" to "H", "j" to "J", "k" to "K", "l" to "L", ";" to ":",
            "'" to "\""
        )
        asdf.forEach { (base, shift) ->
            layout.add(Key(base, shift, KeyCodeMap.getHidCode(base[0]), row = currentRow))
        }
        layout.add(Key("Enter", null, HidConstants.KEY_ENTER, 2.25f, row = currentRow))
        currentRow++

        // Row 4: ZXCV row
        layout.add(Key("Shift", null, HidConstants.KEY_LEFT_SHIFT, 2.25f, true, HidConstants.MODIFIER_LEFT_SHIFT, currentRow))
        val zxcv = listOf(
            "z" to "Z", "x" to "X", "c" to "C", "v" to "V", "b" to "B",
            "n" to "N", "m" to "M", "," to "<", "." to ">", "/" to "?"
        )
        zxcv.forEach { (base, shift) ->
            layout.add(Key(base, shift, KeyCodeMap.getHidCode(base[0]), row = currentRow))
        }
        layout.add(Key("Shift", null, HidConstants.KEY_RIGHT_SHIFT, 2.75f, true, HidConstants.MODIFIER_RIGHT_SHIFT, currentRow))
        currentRow++

        // Row 5: Control row
        layout.add(Key("Ctrl", null, HidConstants.KEY_LEFT_CTRL, 1.5f, true, HidConstants.MODIFIER_LEFT_CTRL, currentRow))
        layout.add(Key("Win", null, HidConstants.KEY_LEFT_GUI, 1.25f, row = currentRow))
        layout.add(Key("Alt", null, HidConstants.KEY_LEFT_ALT, 1.25f, true, HidConstants.MODIFIER_LEFT_ALT, currentRow))
        layout.add(Key("Space", null, HidConstants.KEY_SPACE, 6.0f, row = currentRow))
        layout.add(Key("Alt", null, HidConstants.KEY_RIGHT_ALT, 1.25f, true, HidConstants.MODIFIER_RIGHT_ALT, currentRow))
        layout.add(Key("Win", null, HidConstants.KEY_RIGHT_GUI, 1.25f, row = currentRow))
        layout.add(Key("Menu", null, 0x76, 1.25f, row = currentRow))
        layout.add(Key("Ctrl", null, HidConstants.KEY_RIGHT_CTRL, 1.5f, true, HidConstants.MODIFIER_RIGHT_CTRL, currentRow))
        currentRow++

        // Row 6: Arrow keys and function keys
        layout.add(Key("Esc", null, HidConstants.KEY_ESCAPE, 1.5f, row = currentRow))
        layout.add(Key("←", null, HidConstants.KEY_LEFT_ARROW, row = currentRow))
        layout.add(Key("↑", null, HidConstants.KEY_UP_ARROW, row = currentRow))
        layout.add(Key("↓", null, HidConstants.KEY_DOWN_ARROW, row = currentRow))
        layout.add(Key("→", null, HidConstants.KEY_RIGHT_ARROW, row = currentRow))
        layout.add(Key("Home", null, HidConstants.KEY_HOME, row = currentRow))
        layout.add(Key("End", null, HidConstants.KEY_END, row = currentRow))
        layout.add(Key("PgUp", null, HidConstants.KEY_PAGE_UP, row = currentRow))
        layout.add(Key("PgDn", null, HidConstants.KEY_PAGE_DOWN, row = currentRow))

        return layout
    }
}
