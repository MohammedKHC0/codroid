/*
 *     Copyright (c) 2022 Zachary. All rights reserved.
 *
 *     This file is part of Codroid.
 *
 *     Codroid is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Codroid is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Codroid.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.codroid.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.codroid.editor.analysis.GrammarRegistration
import org.codroid.editor.analysis.LanguageRegistration
import org.codroid.editor.analysis.registerGrammar
import org.codroid.editor.analysis.registerLanguage
import org.codroid.editor.buffer.linearr.LineArray
import org.codroid.editor.graphics.Cursor
import org.codroid.editor.graphics.RowsRender
import org.codroid.textmate.parseJson
import org.codroid.textmate.parsePLIST
import org.codroid.textmate.parseRawGrammar
import org.codroid.textmate.theme.RawTheme
import java.io.InputStream
import java.nio.file.Path
import kotlin.math.ceil

class CodroidEditor : View, LifecycleOwner {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
//        this.setOnClickListener(this)
//        showInput()
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val mLifecycleRegistry = LifecycleRegistry(this)

    private val mInputMethodManager =
        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private var mEditContent: EditContent? = null
    private val mRowsRender by lazy {
        RowsRender(this)
    }

    private val mCursor by lazy {
        Cursor(this)
    }

    private var mVisibleRows = 0

    companion object {
        var DefaultRawTheme: RawTheme? = null
        var DefaultTypeface: Typeface? = null
    }

    val theme: RawTheme?
        get() {
            return DefaultRawTheme
        }

    var typeface: Typeface?
        get() {
            return DefaultTypeface
        }

    init {
        lifecycleScope.launch {
            if (DefaultRawTheme == null) {
                val file = "Solarized-light.tmTheme" // Default Theme
                context.assets.open(file).use { input ->
                    if (Regex("\\.json$").containsMatchIn(file)) {
                        DefaultRawTheme = parseJson(input)
                    }
                    DefaultRawTheme = parsePLIST(input)
                }
            }
        }

        lifecycleScope.launch {
            val file = "Kotlin.tmLanguage" // kotlin grammar
            context.assets.open(file).use {
                registerGrammar(
                    GrammarRegistration(
                        language = "kotlin",
                        scopeName = "source.kotlin",
                        grammar = parseRawGrammar(it, file)
                    )
                )
            }

            val c = "c.json"
            context.assets.open(c).use {
                registerGrammar(
                    GrammarRegistration(
                        language = "c",
                        scopeName = "source.c",
                        grammar = parseRawGrammar(it, c)
                    )
                )
            }

            val cpp = "c++.json"
            context.assets.open(cpp).use {
                registerGrammar(
                    GrammarRegistration(
                        language = "c++",
                        scopeName = "source.cpp",
                        grammar = parseRawGrammar(it, cpp)
                    )
                )
            }
        }

        lifecycleScope.launch {
            registerLanguage(
                LanguageRegistration(
                    id = "kotlin",
                    extensions = listOf(".kt")
                )
            )
            registerLanguage(
                LanguageRegistration(
                    id = "c++",
                    extensions = listOf(".cpp", ".cc")
                )
            )
            registerLanguage(
                LanguageRegistration(
                    id = "c",
                    extensions = listOf(".c")
                )
            )
        }

        if (DefaultTypeface == null) {
            DefaultTypeface = Typeface.createFromAsset(context.assets, "CascadiaCodePL-Regular.ttf")
        }
        typeface = DefaultTypeface
        lifecycleScope.launchWhenCreated {
            getParentAsUnrestrainedScroll()?.run {
                setOnScrollWithRowListener { start, old ->
                    mEditContent?.getRange()?.let {
                        it.bindScroll(start, old)
                        if (mCursor.getCurrentRow() in it.getBegin()..it.getEnd()) {
                            mCursor.start()
                        } else {
                            mCursor.stop()
                        }
                    }


                }
            }
            mCursor.addCursorChangedListener { row, col ->
                println("ROW: $row, COL: $col")
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mRowsRender.measure().run {
            setMeasuredDimension(first(), second())
        }
        mVisibleRows =
            ceil(MeasureSpec.getSize(heightMeasureSpec) / mRowsRender.getLineHeight()).toInt()
        println(mVisibleRows)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let {
            mRowsRender.drawRows(canvas)
            mCursor.drawCursor(canvas)
        }
    }

    private var mActionDownStartTime = 0L
    private var longPressJob: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.run {
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mActionDownStartTime = System.currentTimeMillis()
                    if (longPressJob == null) {
                        longPressJob = lifecycleScope.launch {
                            delay(500)
                            onLongPressed(makePair(event.y.toInt(), event.x.toInt()))
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (System.currentTimeMillis() - mActionDownStartTime < 300) {
                        onClicked(makePair(event.y.toInt(), event.x.toInt()))
                    }
                    true
                }
                else -> {
                    false
                }
            }
        }
        return false
    }

    private fun onClicked(position: IntPair) {
        if (position.second() >= mRowsRender.lineNumberOffset()) {
            mRowsRender.computeRowCol(position).run {
                mRowsRender.focusRow(this.first(), position)
                mCursor.moveCursor(this.first(), this.second())
                mCursor.start()
            }
        }
    }

    private fun onLongPressed(position: IntPair) {
        Log.i("Zac", "OnLongClicked")
        Toast.makeText(this.context, "onLongClick", Toast.LENGTH_SHORT).show()
    }

    fun load(input: InputStream, path: Path) {
        mEditContent =
            EditContent(LineArray(input), path, this, mVisibleRows)
        mRowsRender.loadContent(mEditContent!!)
        lifecycleScope.launch(Dispatchers.Main) {
            requestLayout()
            invalidate()
        }
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection {
        outAttrs?.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs?.inputType = InputType.TYPE_NULL
        return InputConnection(this, true)
    }

    fun showInput() {
        mInputMethodManager.showSoftInput(this@CodroidEditor, InputMethodManager.SHOW_FORCED)
    }

    fun closeInput() {
        mInputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
    }

    inner class InputConnection(targetView: View, fullEditor: Boolean) :
        BaseInputConnection(targetView, fullEditor) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            this@CodroidEditor.postInvalidate()
            return true
        }

        override fun closeConnection() {
            super.closeConnection()
            Log.i("Zac", "CloseConnection")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mLifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            requestLayout()
            invalidate()
        } else if (visibility == GONE || visibility == INVISIBLE) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun getRowsRender(): RowsRender = this.mRowsRender

    fun getLineHeight(): Float = mRowsRender.getLineHeight()

    fun getSingleCharWidth(): Float = mRowsRender.getSingleCharWidth()

    private fun getParentAsUnrestrainedScroll(): UnrestrainedScrollView? {
        if (parent is UnrestrainedScrollView) {
            return parent as UnrestrainedScrollView
        }
        return null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mLifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun getLifecycle(): Lifecycle = this.mLifecycleRegistry
}
