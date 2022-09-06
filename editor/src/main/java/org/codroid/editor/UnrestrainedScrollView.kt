/*
 *      Copyright (c) 2022 Zachary. All rights reserved.
 *
 *      This file is part of Codroid.
 *
 *      Codroid is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      (at your option) any later version.
 *
 *      Codroid is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with Codroid.  If not, see <https://www.gnu.org/licenses/>.
 *
 */


package org.codroid.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.appcompat.widget.DrawableUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class UnrestrainedScrollView : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        setWillNotDraw(false)
    }

    private val mMaximumVelocity: Float = 8000F
    private var mIsScrolling = false
    private var mScrollThreshold = 0
    private var mLastMotion = Vector()
    private val mScroller = OverScroller(context)
    private var mVelocityTracker: VelocityTracker? = null

    private var scrollCurrent: IntPair = 0u
    private val barPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }
    private var barHeight = 0
    private var barWidth = 0
    private var barLeft = 0
    private var barTop = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount > 0) {
            getChildAt(0)?.let {
                measureChild(it, widthMeasureSpec, heightMeasureSpec)
                barHeight = height * height / it.measuredHeight
                barWidth = if (it.measuredWidth == 0) {
                    0
                } else {
                    width * width / it.measuredWidth
                }
            }
        }
    }

    override fun measureChild(
        child: View?,
        parentWidthMeasureSpec: Int,
        parentHeightMeasureSpec: Int
    ) {
        child?.let {
            val width = MeasureSpec.getSize(parentWidthMeasureSpec)
            val height = MeasureSpec.getSize(parentHeightMeasureSpec)
            child.measure(
                MeasureSpec.makeMeasureSpec(
                    max(0, width - paddingLeft - paddingRight),
                    MeasureSpec.UNSPECIFIED
                ),
                MeasureSpec.makeMeasureSpec(
                    max(0, height - paddingTop - paddingBottom),
                    MeasureSpec.UNSPECIFIED
                )
            )
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (ev.action == MotionEvent.ACTION_MOVE && mIsScrolling) {
                return true
            }
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    mLastMotion.reset(it.x.roundToInt(), it.y.roundToInt())
                    mScroller.computeScrollOffset()
                    mIsScrolling = !mScroller.isFinished
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = mLastMotion.deltaFrom(it.x.roundToInt(), it.y.roundToInt())
                    if (abs(delta.x) >= mScrollThreshold || abs(delta.y) >= mScrollThreshold) {
                        parent?.run {
                            requestDisallowInterceptTouchEvent(true)
                        }
                        mIsScrolling = true
                        mLastMotion.reset(it.x.roundToInt(), it.y.roundToInt())
                    }
                }

                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    mIsScrolling = false;
                }
            }
        }
        return mIsScrolling
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            initVelocityTrackerIfNotExists()
            mVelocityTracker!!.addMovement(event)
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    mIsScrolling = !mScroller.isFinished
                    if (!mScroller.isFinished) {
                        parent?.run {
                            requestDisallowInterceptTouchEvent(true)
                        }
                        mScroller.abortAnimation()
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mIsScrolling) {
                        val delta =
                            -mLastMotion.deltaFrom(event.x.roundToInt(), event.y.roundToInt())
                        val range = getScrollRange()
                        mLastMotion.reset(event.x.roundToInt(), event.y.roundToInt())
                        overScrollBy(
                            delta.x, delta.y,
                            scrollX, scrollY,
                            range.x, range.y,
                            0, 0,
                            true
                        )
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (mIsScrolling) {
                        mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity)
                        mIsScrolling = false
                        fling(
                            Vector(
                                mVelocityTracker!!.xVelocity.roundToInt(),
                                mVelocityTracker!!.yVelocity.roundToInt()
                            )
                        )
                        recycleVelocityTracker()
                    }
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    override fun onDrawForeground(canvas: Canvas?) {
        canvas?.run {
            barTop = height * scrollCurrent.second() / getScrollableSize().second()
            barLeft = if (getScrollableSize().first() == 0) {
                0
            } else {
                width * scrollCurrent.first() / getScrollableSize().first()
            }
            drawRect(
                Rect(
                    scrollCurrent.first() + width - 20,
                    scrollCurrent.second() + barTop,
                    scrollCurrent.first() + width,
                    scrollCurrent.second() + barTop + barHeight
                ),
                barPaint
            )
            drawRect(
                Rect(
                    scrollCurrent.first() + barLeft,
                    scrollCurrent.second() + height - 20,
                    scrollCurrent.first() + barLeft + barWidth,
                    scrollCurrent.second() + height
                ),
                barPaint
            )
        }
    }

    private fun getScrollRange(): Vector {
        val result = Vector()
        if (childCount > 0) {
            getChildAt(0)?.let {
                result.reset(
                    it.width - (width - paddingLeft - paddingRight),
                    it.height - (height - paddingTop - paddingBottom)
                )
            }
        }
        return result
    }

    private fun getScrollableSize(): IntPair {
        getChildAt(0)?.let {
            return makePair(it.width, it.height)
        }
        return 0U
    }

    private fun fling(velocity: Vector) {
        val range = getScrollRange()
        mScroller.fling(
            scrollX,
            scrollY,
            -velocity.x,
            -velocity.y,
            0,
            range.x,
            0,
            range.y
        )
        postInvalidateOnAnimation()
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (!mScroller.isFinished) {
            val oldX = this.scrollX
            val oldY = this.scrollY
            this.scrollX = scrollX
            this.scrollY = scrollY
            onScrollChanged(this.scrollX, this.scrollY, oldX, oldY)
        }
        super.scrollTo(scrollX, scrollY)
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            val old = Vector(scrollX, scrollY)
            val now = Vector(mScroller.currX, mScroller.currY)
            if (now != old) {
                val range = getScrollRange()
                val delta = old.deltaFrom(now)
                overScrollBy(delta.x, delta.y, scrollX, scrollY, range.x, range.y, 0, 0, false)
                onScrollChanged(scrollX, scrollY, old.x, old.y)
            }
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        scrollCurrent = makePair(l, t)
    }

    private fun initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }
}