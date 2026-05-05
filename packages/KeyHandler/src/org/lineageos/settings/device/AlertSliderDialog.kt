/*
 * Copyright (C) 2019 CypherOS
 * Copyright (C) 2014-2020 Paranoid Android
 * Copyright (C) 2023 The LineageOS Project
 * Copyright (C) 2023 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.animation.Animator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Surface
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** View with some logging to show that its being run. */
class AlertSliderDialog(private val context: Context) :
    Dialog(context, R.style.alert_slider_theme) {
    
    // Applying the !! inside the lazy block makes these non-nullable below
    private val dialogView by lazy { findViewById<LinearLayout>(R.id.alert_slider_dialog)!! }
    private val frameView by lazy { findViewById<ViewGroup>(R.id.alert_slider_view)!! }
    private val iconView by lazy { findViewById<ImageView>(R.id.alert_slider_icon)!! }
    private val textView by lazy { findViewById<TextView>(R.id.alert_slider_text)!! }

    private val rotation: Int = context.display.rotation
    // Fixed landscape detection to ignore reverse portrait (ROTATION_180)
    private val isLand: Boolean = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    private val isLeft = context.resources.getBoolean(R.bool.alert_slider_dialog_left)
    
    private val length: Int
    private val xPos: Int
    private val yPos: Int

    private var isAnimating = false
    private var animator = ValueAnimator()

    init {
        // window init using property access
        window?.let {
            it.requestFeature(Window.FEATURE_NO_TITLE)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            it.addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            it.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY)

            it.attributes =
                it.attributes.apply {
                    format = PixelFormat.TRANSLUCENT
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    title = TAG
                }
        }

        setCanceledOnTouchOutside(false)
        setContentView(R.layout.alert_slider_dialog)

        // position calculations
        val fraction = context.resources.getFraction(R.fraction.alert_slider_dialog_y, 1, 1)
        val widthPixels = context.resources.displayMetrics.widthPixels
        val heightPixels = context.resources.displayMetrics.heightPixels
        val pads = dialogView.paddingTop * 2 // equal paddings in all 4 directions
        
        length =
            if (isLand) context.resources.getDimension(R.dimen.alert_slider_dialog_width).toInt()
            else context.resources.getDimension(R.dimen.alert_slider_dialog_height).toInt()
            
        val hv = (length + pads) * 0.5

        xPos =
            if (isLand) (widthPixels * fraction - hv).toInt()
            else if (isLeft) 0 else widthPixels / 100
        yPos =
            if (isLand) (if (isLeft) (widthPixels / 100) else 0)
            else (heightPixels * fraction - hv).toInt()

        window?.let {
            it.attributes =
                it.attributes.apply {
                    gravity =
                        when (rotation) {
                            Surface.ROTATION_0 ->
                                if (isLeft) (Gravity.TOP or Gravity.LEFT)
                                else (Gravity.TOP or Gravity.RIGHT)
                            Surface.ROTATION_90 ->
                                if (isLeft) (Gravity.BOTTOM or Gravity.LEFT)
                                else (Gravity.TOP or Gravity.LEFT)
                            Surface.ROTATION_270 ->
                                if (isLeft) (Gravity.TOP or Gravity.RIGHT)
                                else (Gravity.BOTTOM or Gravity.RIGHT)
                            else ->
                                if (isLeft) (Gravity.BOTTOM or Gravity.LEFT)
                                else (Gravity.TOP or Gravity.LEFT)
                        }

                    x = xPos
                    y = yPos
                }
        }
    }

    @Synchronized
    fun setState(position: Int, ringerMode: Int) {
        val delta =
            length *
                when (position) {
                    KeyHandler.POSITION_TOP -> -1
                    KeyHandler.POSITION_BOTTOM -> 1
                    else -> 0 // KeyHandler.POSITION_MIDDLE
                }
                
        var endX = xPos
        var endY = yPos
        if (isLand) endX += delta else endY += delta
        
        if (isShowing) { // using property access
            animatePosition(endX, endY, position, ringerMode)
        } else {
            applyOnStart(ringerMode)
            applyOnEnd(endX, endY, position)
        }
    }

    @Synchronized
    private fun animatePosition(endX: Int, endY: Int, position: Int, ringerMode: Int) {
        if (isAnimating) animator.cancel()
        
        animator = ValueAnimator()
        animator.duration = 100
        animator.interpolator = OvershootInterpolator()
        
        window?.let {
            animator.setValues(
                PropertyValuesHolder.ofInt("x", it.attributes.x, endX),
                PropertyValuesHolder.ofInt("y", it.attributes.y, endY)
            )
        }
        
        // Converted to concise lambda syntax
        animator.addUpdateListener { animation ->
            window?.let {
                it.attributes =
                    it.attributes.apply {
                        x = animation.getAnimatedValue("x") as Int
                        y = animation.getAnimatedValue("y") as Int
                    }
            }
        }
        
        animator.addListener(
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    isAnimating = true
                    applyOnStart(ringerMode)
                }

                override fun onAnimationEnd(animation: Animator) {
                    applyOnEnd(endX, endY, position)
                    isAnimating = false
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            }
        )
        animator.start()
    }

    private fun applyOnStart(ringerMode: Int) {
        // Replaced Map with when expression
        iconView.setImageResource(
            when (ringerMode) {
                KeyHandler.KEY_VALUE_SILENT -> R.drawable.ic_volume_ringer_mute
                KeyHandler.KEY_VALUE_VIBRATE -> R.drawable.ic_volume_ringer_vibrate
                KeyHandler.KEY_VALUE_NORMAL -> R.drawable.ic_volume_ringer
                KeyHandler.KEY_VALUE_PRIORITY_ONLY -> R.drawable.ic_notifications_alert
                KeyHandler.KEY_VALUE_TOTAL_SILENCE -> R.drawable.ic_notifications_silence
                else -> R.drawable.ic_info
            }
        )

        // Replaced Map with when expression
        textView.setText(
            when (ringerMode) {
                KeyHandler.KEY_VALUE_SILENT -> R.string.alert_slider_mode_silent
                KeyHandler.KEY_VALUE_VIBRATE -> R.string.alert_slider_mode_vibration
                KeyHandler.KEY_VALUE_NORMAL -> R.string.alert_slider_mode_normal
                KeyHandler.KEY_VALUE_PRIORITY_ONLY -> R.string.alert_slider_mode_dnd_priority_only
                KeyHandler.KEY_VALUE_TOTAL_SILENCE -> R.string.alert_slider_mode_dnd_total_silence
                else -> R.string.alert_slider_mode_normal
            }
        )
        
        // Enforcing text color
        textView.setTextColor(context.getColor(R.color.alert_slider_text_color))
    }

    private fun applyOnEnd(endX: Int, endY: Int, position: Int) {
        frameView.setBackgroundResource(backgroundFor(rotation, position, isLeft))
        
        window?.let {
            it.attributes =
                it.attributes.apply {
                    x = endX
                    y = endY
                }
        }
    }

    private fun backgroundFor(rotation: Int, position: Int, flip: Boolean): Int {
        fun base(position: Int): Int =
            when (position) {
                KeyHandler.POSITION_TOP ->
                    if (flip) R.drawable.alert_slider_top_flip else R.drawable.alert_slider_top
                KeyHandler.POSITION_MIDDLE -> R.drawable.alert_slider_middle
                KeyHandler.POSITION_BOTTOM ->
                    if (flip) R.drawable.alert_slider_bottom_flip
                    else R.drawable.alert_slider_bottom
                else -> R.drawable.alert_slider_middle
            }

        return when (rotation) {
            Surface.ROTATION_90 ->
                when (position) {
                    KeyHandler.POSITION_TOP ->
                        if (flip) R.drawable.alert_slider_top_90_flip
                        else R.drawable.alert_slider_top_90
                    KeyHandler.POSITION_BOTTOM ->
                        if (flip) R.drawable.alert_slider_bottom_90_flip
                        else R.drawable.alert_slider_bottom_90
                    else -> R.drawable.alert_slider_middle
                }
            Surface.ROTATION_270 ->
                when (position) {
                    KeyHandler.POSITION_TOP ->
                        if (flip) R.drawable.alert_slider_top_270_flip
                        else R.drawable.alert_slider_top_270
                    KeyHandler.POSITION_BOTTOM ->
                        if (flip) R.drawable.alert_slider_bottom_270_flip
                        else R.drawable.alert_slider_bottom_270
                    else -> R.drawable.alert_slider_middle
                }
            else -> base(position) // ROTATION_0 / ROTATION_180
        }
    }

    companion object {
        private const val TAG = "AlertSliderDialog"
        // Maps removed to optimize memory and class loading speed
    }
}