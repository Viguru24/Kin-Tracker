package com.example.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.exifinterface.media.ExifInterface
import android.util.LruCache
import androidx.core.graphics.drawable.toDrawable
import java.io.File

/**
 * High-performance map marker renderer with LRU Bitmap caching,
 * orientation correction, photo shaders, and dynamic status badges.
 */
object MapMarkerRenderer {

    // 16MB LRU Cache for rendered marker drawables to eliminate frame drops & allocations
    private val markerDrawableCache = LruCache<String, Drawable>(100)

    private data class MarkerGeometry(
        val density: Float,
        val bubblePx: Int,
        val bubbleR: Float,
        val stemW: Int,
        val stemH: Int,
        val topBadgePad: Int,
        val timePillHeight: Int,
        val timePillMargin: Int,
        val bmpW: Int,
        val bmpH: Int,
        val cx: Float,
        val bubbleTop: Float,
        val bubbleBottom: Float,
        val bubbleCy: Float,
        val stemTipY: Float,
        val anchorV: Float
    )

    private fun computeMarkerGeometry(
        context: Context,
        isSelected: Boolean,
        relativeTime: String,
        locationDuration: String,
        batteryPercentage: Int,
        weatherEmoji: String,
        showStem: Boolean
    ): MarkerGeometry {
        val density = context.resources.displayMetrics.density
        val bubbleDp = if (isSelected) 48 else 40
        val bubblePx = (bubbleDp * density).toInt()
        val bubbleR = bubblePx / 2f
        val stemW = if (showStem) (6 * density).toInt() else 0
        val stemH = if (showStem) (5 * density).toInt() else 0
        val shadowRadius = 2.5f * density
        val shadowDy = 1.2f * density
        val shadowPad = (shadowRadius + shadowDy).toInt() + 2

        val hasBatteryBadge = batteryPercentage in 0..100
        val hasActivityBadge = locationDuration.startsWith("👣") ||
            locationDuration.startsWith("🚗") ||
            locationDuration.startsWith("🚲") ||
            locationDuration.startsWith("🚆") ||
            weatherEmoji == "👣" || weatherEmoji == "🚗" || weatherEmoji == "🚲" || weatherEmoji == "🚆"

        val battOverhang = if (hasBatteryBadge) {
            kotlin.math.ceil(maxOf(0f, 5.8f * density + 1.1f * density - bubbleR * 0.34f).toDouble()).toInt()
        } else 0
        val actOverhang = if (hasActivityBadge) {
            kotlin.math.ceil(maxOf(0f, 6.5f * density + 1.4f * density - bubbleR * 0.30f).toDouble()).toInt()
        } else 0

        val sidePad = maxOf(shadowPad, battOverhang, actOverhang)
        val topBadgePad = maxOf(shadowPad, actOverhang)

        val timePillHeight = if (relativeTime.isNotEmpty()) kotlin.math.ceil(12.5f * density).toInt() else 0
        val timePillMargin = if (relativeTime.isNotEmpty()) kotlin.math.ceil(2.0f * density).toInt() else 0
        val locPillHeight = if (locationDuration.isNotEmpty()) kotlin.math.ceil(12.5f * density).toInt() else 0
        val locPillMargin = if (locationDuration.isNotEmpty()) kotlin.math.ceil(2.0f * density).toInt() else 0
        val bottomPad = (2 * density).toInt()

        // When rendering a center-anchored clustered marker (showStem == false) that has a top or bottom pill,
        // balance top and bottom pill space so the avatar circle stays dead-center at bmpH / 2f and never shifts UP/DOWN.
        val hasAnyPill = relativeTime.isNotEmpty() || locationDuration.isNotEmpty()
        val rawTopSpace = topBadgePad + timePillHeight + timePillMargin
        val rawBottomSpace = stemH + locPillHeight + locPillMargin + bottomPad
        val effectiveTopSpace = if (!showStem && hasAnyPill) maxOf(rawTopSpace, rawBottomSpace) else rawTopSpace
        val effectiveBottomSpace = if (!showStem && hasAnyPill) maxOf(rawTopSpace, rawBottomSpace) else rawBottomSpace

        val maxPillWidth = maxOf(
            measurePillWidthPx(relativeTime, density),
            measurePillWidthPx(locationDuration, density)
        )
        val bmpW = maxOf(bubblePx + sidePad * 2, maxPillWidth + (4 * density).toInt())
        val bmpH = effectiveTopSpace + bubblePx + effectiveBottomSpace
        val cx = bmpW / 2f
        val bubbleTop = effectiveTopSpace.toFloat()
        val bubbleBottom = bubbleTop + bubblePx
        val bubbleCy = bubbleTop + bubblePx / 2f
        val stemTipY = bubbleBottom + stemH
        val anchorV = if (showStem) (stemTipY / bmpH.toFloat()) else (bubbleCy / bmpH.toFloat())

        return MarkerGeometry(
            density = density,
            bubblePx = bubblePx,
            bubbleR = bubbleR,
            stemW = stemW,
            stemH = stemH,
            topBadgePad = topBadgePad,
            timePillHeight = timePillHeight,
            timePillMargin = timePillMargin,
            bmpW = bmpW,
            bmpH = bmpH,
            cx = cx,
            bubbleTop = bubbleTop,
            bubbleBottom = bubbleBottom,
            bubbleCy = bubbleCy,
            stemTipY = stemTipY,
            anchorV = anchorV.coerceIn(0f, 1f)
        )
    }

    fun getMarkerAnchorV(
        context: Context,
        isSelected: Boolean,
        relativeTime: String = "",
        locationDuration: String = "",
        batteryPercentage: Int = -1,
        weatherEmoji: String = "",
        showStem: Boolean = true
    ): Float {
        return computeMarkerGeometry(
            context = context,
            isSelected = isSelected,
            relativeTime = relativeTime,
            locationDuration = locationDuration,
            batteryPercentage = batteryPercentage,
            weatherEmoji = weatherEmoji,
            showStem = showStem
        ).anchorV
    }

    fun getOrCreateClusterAnchorDot(context: Context): Drawable {
        val cacheKey = "CLUSTER_ANCHOR_DOT"
        markerDrawableCache.get(cacheKey)?.let { return it }
        val density = context.resources.displayMetrics.density
        val sizePx = (11 * density).toInt().coerceAtLeast(6)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = sizePx / 2f
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, c, whitePaint)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, (c - 1.8f * density).coerceAtLeast(1f), corePaint)
        val drawable = BitmapDrawable(context.resources, bitmap)
        markerDrawableCache.put(cacheKey, drawable)
        return drawable
    }

    fun getOrCreateMarkerDrawable(
        context: Context,
        colorHex: String,
        emoji: String,
        isSelected: Boolean,
        photoPath: String = "",
        weatherEmoji: String = "",
        isOffline: Boolean = false,
        relativeTime: String = "",
        locationDuration: String = "",
        batteryPercentage: Int = -1,
        isCharging: Boolean = false,
        animFrame: Int = 0,
        showStem: Boolean = true
    ): Drawable {
        val cacheKey = "$colorHex|$emoji|$isSelected|$photoPath|$weatherEmoji|$isOffline|$relativeTime|$locationDuration|$batteryPercentage|$isCharging|$animFrame|$showStem"
        markerDrawableCache.get(cacheKey)?.let { return it }

        val drawable = createColoredMarkerDrawable(
            context = context,
            colorHex = colorHex,
            emoji = emoji,
            isSelected = isSelected,
            photoPath = photoPath,
            weatherEmoji = weatherEmoji,
            isOffline = isOffline,
            relativeTime = relativeTime,
            locationDuration = locationDuration,
            batteryPercentage = batteryPercentage,
            isCharging = isCharging,
            animFrame = animFrame,
            showStem = showStem
        )
        markerDrawableCache.put(cacheKey, drawable)
        return drawable
    }
    fun getOrCreateHomeMarkerDrawable(context: Context): Drawable {
        val cacheKey = "HOME_MARKER"
        markerDrawableCache.get(cacheKey)?.let { return it }
        val drawable = createHomeMarkerDrawable(context)
        markerDrawableCache.put(cacheKey, drawable)
        return drawable
    }

    fun getOrCreateZoneIcon(context: Context, name: String, iconName: String): Drawable {
        val cacheKey = "ZONE_$name|$iconName"
        markerDrawableCache.get(cacheKey)?.let { return it }
        val drawable = createZoneIcon(context, name, iconName)
        markerDrawableCache.put(cacheKey, drawable)
        return drawable
    }

    private fun createHomeMarkerDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val bubblePx = (34 * density).toInt()
        val stemW = (7 * density).toInt()
        val stemH = (8 * density).toInt()
        val shadowRadius = (3f * density)
        val shadowDy = (1.5f * density)
        val shadowPad = (shadowRadius + shadowDy).toInt() + 2

        val bmpW = bubblePx + shadowPad * 2
        val bottomPad = (2 * density).toInt()
        val bmpH = shadowPad + bubblePx + stemH + bottomPad
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val homeColor = Color.parseColor("#10B981")
        val cx = bmpW / 2f
        val bubbleTop = shadowPad.toFloat()
        val bubbleBottom = bubbleTop + bubblePx
        val bubbleCy = bubbleTop + bubblePx / 2f
        val bubbleR = bubblePx / 2f
        val stemTipY = bubbleBottom + stemH

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 40
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + 1f, bubbleCy + shadowDy + 1f, bubbleR - density, shadowPaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

        val ringWidth = 2.2f * density
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = homeColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

        val stemPath = Path().apply {
            moveTo(cx - stemW / 2f, bubbleBottom - density * 1.5f)
            lineTo(cx + stemW / 2f, bubbleBottom - density * 1.5f)
            lineTo(cx, stemTipY)
            close()
        }
        canvas.drawPath(stemPath, fillPaint)
        val stemBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = homeColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth * 0.85f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(stemPath, stemBorderPaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 15f * density
        }
        val fm = emojiPaint.fontMetrics
        val textY = bubbleCy - (fm.ascent + fm.descent) / 2f
        canvas.drawText("🏠", cx, textY, emojiPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createZoneIcon(context: Context, name: String, iconName: String): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#10B981")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1.8f * density, paint)

        val emoji = when (iconName.lowercase()) {
            "home" -> "🏠"
            "school" -> "🏫"
            "gym" -> "💪"
            "work" -> "💼"
            "shop" -> "🛒"
            else -> "📍"
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 13f * density
        val fm = paint.fontMetrics
        val textY = (sizePx / 2f) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(emoji, sizePx / 2f, textY, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun measurePillWidthPx(text: String, density: Float): Int {
        if (text.isEmpty()) return 0
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return kotlin.math.ceil((paint.measureText(text) + 10f * density).toDouble()).toInt()
    }

    private fun createColoredMarkerDrawable(
        context: Context,
        colorHex: String,
        emoji: String,
        isSelected: Boolean,
        photoPath: String,
        weatherEmoji: String,
        isOffline: Boolean,
        relativeTime: String,
        locationDuration: String,
        batteryPercentage: Int,
        isCharging: Boolean,
        animFrame: Int = 0,
        showStem: Boolean = true
    ): Drawable {
        val geom = computeMarkerGeometry(
            context = context,
            isSelected = isSelected,
            relativeTime = relativeTime,
            locationDuration = locationDuration,
            batteryPercentage = batteryPercentage,
            weatherEmoji = weatherEmoji,
            showStem = showStem
        )
        val density = geom.density
        val bubbleR = geom.bubbleR
        val shadowRadius = 2.5f * density
        val shadowDy = 1.2f * density

        val hasBatteryBadge = batteryPercentage in 0..100
        val activityEmojiToDraw = when {
            locationDuration.startsWith("👣") || weatherEmoji == "👣" -> "👣"
            locationDuration.startsWith("🚗") || weatherEmoji == "🚗" -> "🚗"
            locationDuration.startsWith("🚲") || weatherEmoji == "🚲" -> "🚲"
            locationDuration.startsWith("🚆") || weatherEmoji == "🚆" -> "🚆"
            else -> ""
        }

        val bitmap = Bitmap.createBitmap(geom.bmpW, geom.bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val parsedColor = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.parseColor("#38BDF8")
        }

        val cx = geom.cx
        val bubbleTop = geom.bubbleTop
        val bubbleBottom = geom.bubbleBottom
        val bubbleCy = geom.bubbleCy
        val stemTipY = geom.stemTipY

        // ── 1. Relative Time Pill (Top) ──
        if (relativeTime.isNotEmpty()) {
            drawTimePill(canvas, cx, bubbleTop, density, relativeTime, isOffline)
        }

        // ── 2. Location Duration Pill (Bottom) ──
        if (locationDuration.isNotEmpty()) {
            drawLocationDurationPill(canvas, cx, stemTipY, density, locationDuration, isOffline)
        }

        // ── 3. Shadow Circle ──
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = if (isOffline) 16 else 40
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + 1f, bubbleCy + shadowDy + 1f, bubbleR - density, shadowPaint)

        // ── 4. White Fill Circle ──
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = if (isOffline) 105 else 255
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

        // ── 5. Selected Outer Glow ──
        if (isSelected) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parsedColor
                alpha = if (isOffline) 28 else 55
                style = Paint.Style.STROKE
                strokeWidth = 3.0f * density
            }
            canvas.drawCircle(cx, bubbleCy, bubbleR - 0.5f * density, glowPaint)
        }

        // ── 6. Colored Border Ring ──
        val ringWidth = if (isSelected) 2.5f * density else 1.8f * density
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            alpha = if (isOffline) 105 else 255
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

        // ── 7. Compact Teardrop Stem (only for unclustered pins) ──
        if (showStem && geom.stemH > 0) {
            drawStem(canvas, cx, bubbleBottom, cx, stemTipY, geom.stemW, ringWidth, parsedColor, density, isOffline)
        }

        // ── 8. Profile Photo or Emoji ──
        var drawnPhoto = false
        if (photoPath.isNotEmpty()) {
            drawnPhoto = drawPhoto(canvas, photoPath, cx, bubbleCy, bubbleR, ringWidth, density, isOffline)
        }

        if (!drawnPhoto) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = (if (isSelected) 16.5f else 12.5f) * density
                typeface = Typeface.DEFAULT
                alpha = if (isOffline) 105 else 255
            }
            val fm = emojiPaint.fontMetrics
            val textY = bubbleCy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(emoji, cx, textY, emojiPaint)
        }

        // ── 9. Battery % Badge (Bottom-Left) ──
        if (hasBatteryBadge) {
            drawBatteryBadge(canvas, cx, bubbleCy, bubbleR, batteryPercentage, isCharging, density)
        }

        // ── 10. Transit Mode Activity Badge (Top-Right of avatar) ──
        if (activityEmojiToDraw.isNotEmpty()) {
            drawActivityBadge(canvas, cx, bubbleCy, bubbleR, activityEmojiToDraw, density, animFrame)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawTimePill(canvas: Canvas, cx: Float, bubbleTop: Float, density: Float, text: String, isOffline: Boolean) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#991B1B") else Color.parseColor("#121215")
            style = Paint.Style.FILL
            alpha = 235
        }
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#FECACA") else Color.parseColor("#38BDF8")
            textSize = 6.8f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = pillTextPaint.measureText(text)
        val pillW = textWidth + 10f * density
        val pillH = 12.5f * density
        val pillLeft = cx - pillW / 2f
        val pillTop = maxOf(1f * density, bubbleTop - pillH - 2.5f * density)
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint)
        val pfm = pillTextPaint.fontMetrics
        val pillTextY = pillTop + pillH / 2f - (pfm.ascent + pfm.descent) / 2f
        canvas.drawText(text, cx, pillTextY, pillTextPaint)
    }

    private fun drawLocationDurationPill(canvas: Canvas, cx: Float, stemTipY: Float, density: Float, text: String, isOffline: Boolean) {
        val locPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) {
                Color.parseColor("#1C1917")
            } else if (text.startsWith("👣")) {
                Color.parseColor("#064E3B") // Emerald dark
            } else if (text.startsWith("🚗")) {
                Color.parseColor("#78350F") // Amber dark
            } else if (text.startsWith("🚲")) {
                Color.parseColor("#0C4A6E") // Cyan dark
            } else if (text.startsWith("🚆")) {
                Color.parseColor("#134E4A") // Teal dark
            } else {
                Color.parseColor("#121215") // Obsidian zinc
            }
            style = Paint.Style.FILL
            alpha = 235
        }
        val locTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) {
                Color.parseColor("#A8A29E")
            } else if (text.startsWith("👣")) {
                Color.parseColor("#6EE7B7")
            } else if (text.startsWith("🚗")) {
                Color.parseColor("#FDE68A")
            } else if (text.startsWith("🚲")) {
                Color.parseColor("#7DD3FC")
            } else if (text.startsWith("🚆")) {
                Color.parseColor("#5EEAD4") // Teal accent
            } else {
                Color.parseColor("#E4E4E7") // Zinc-200
            }
            textSize = 6.8f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = locTextPaint.measureText(text)
        val pillW = textWidth + 10f * density
        val pillH = 12.5f * density
        val pillLeft = cx - pillW / 2f
        val pillTop = stemTipY + 1.5f * density
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, locPillPaint)
        val pfm = locTextPaint.fontMetrics
        val pillTextY = pillTop + pillH / 2f - (pfm.ascent + pfm.descent) / 2f
        canvas.drawText(text, cx, pillTextY, locTextPaint)
    }

    private fun drawStem(
        canvas: Canvas, cx: Float, bubbleBottom: Float, stemTipX: Float, stemTipY: Float,
        stemW: Int, ringWidth: Float, parsedColor: Int, density: Float, isOffline: Boolean
    ) {
        val stemPath = Path().apply {
            val stemBaseHalf = stemW / 2f
            moveTo(cx - stemBaseHalf, bubbleBottom - density * 1.5f)
            lineTo(cx + stemBaseHalf, bubbleBottom - density * 1.5f)
            lineTo(stemTipX, stemTipY)
            close()
        }
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = if (isOffline) 105 else 255
        }
        canvas.drawPath(stemPath, stemPaint)
        val stemBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth * 0.85f
            strokeJoin = Paint.Join.ROUND
            alpha = if (isOffline) 105 else 255
        }
        canvas.drawPath(stemPath, stemBorderPaint)
    }

    private fun drawPhoto(
        canvas: Canvas, photoPath: String, cx: Float, bubbleCy: Float,
        bubbleR: Float, ringWidth: Float, density: Float, isOffline: Boolean
    ): Boolean {
        return try {
            val file = File(photoPath)
            if (file.exists()) {
                val clipR = bubbleR - ringWidth - density * 0.5f
                val clipSize = (clipR * 2).toInt().coerceAtLeast(1)
                val photoBmp = loadUprightMarkerBitmap(photoPath, clipSize)
                if (photoBmp != null) {
                    val scaledBmp = Bitmap.createScaledBitmap(photoBmp, clipSize, clipSize, true)
                    val shader = BitmapShader(scaledBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.shader = shader
                        alpha = if (isOffline) 105 else 255
                    }
                    canvas.save()
                    canvas.translate(cx - clipR, bubbleCy - clipR)
                    canvas.drawCircle(clipR, clipR, clipR, shaderPaint)
                    canvas.restore()
                    if (scaledBmp != photoBmp) photoBmp.recycle()
                    true
                } else false
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun drawBatteryBadge(
        canvas: Canvas, cx: Float, bubbleCy: Float, bubbleR: Float,
        batteryPercentage: Int, isCharging: Boolean, density: Float
    ) {
        val badgeRadius = 5.8f * density
        val bx = cx - bubbleR * 0.66f
        val by = bubbleCy + bubbleR * 0.66f
        val battColor = when {
            isCharging -> Color.parseColor("#10B981")
            batteryPercentage <= 20 -> Color.parseColor("#EF4444")
            batteryPercentage <= 50 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#10B981")
        }
        val battBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121215")
            style = Paint.Style.FILL
            alpha = 235
        }
        canvas.drawCircle(bx, by, badgeRadius, battBg)
        val battBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = battColor
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
        }
        canvas.drawCircle(bx, by, badgeRadius, battBorder)
        val battText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = battColor
            textSize = 5.0f * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val label = if (isCharging) "⚡" else "${batteryPercentage}%"
        val bfm = battText.fontMetrics
        val battTextY = by - (bfm.ascent + bfm.descent) / 2f
        canvas.drawText(label, bx, battTextY, battText)
    }

    private fun drawActivityBadge(
        canvas: Canvas,
        cx: Float,
        bubbleCy: Float,
        bubbleR: Float,
        emoji: String,
        density: Float,
        animFrame: Int
    ) {
        val badgeRadius = 6.5f * density
        val ax = cx + bubbleR * 0.70f
        val ay = bubbleCy - bubbleR * 0.70f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121215")
            style = Paint.Style.FILL
            alpha = 240
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (emoji) {
                "👣" -> Color.parseColor("#10B981") // Emerald
                "🚗" -> Color.parseColor("#F59E0B") // Amber
                "🚲" -> Color.parseColor("#06B6D4") // Cyan
                "🚆" -> Color.parseColor("#14B8A6") // Teal
                else -> Color.WHITE
            }
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        canvas.drawCircle(ax, ay, badgeRadius, bgPaint)
        canvas.drawCircle(ax, ay, badgeRadius, borderPaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f * density
            textAlign = Paint.Align.CENTER
        }
        val efm = emojiPaint.fontMetrics
        val textY = ay - (efm.ascent + efm.descent) / 2f

        // Draw transit emoji cleanly and statically without jitter or bouncing
        canvas.drawText(emoji, ax, textY, emojiPaint)
    }

    private fun loadUprightMarkerBitmap(photoPath: String, targetSize: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoPath, boundsOptions)
            var inSample = 1
            while (boundsOptions.outWidth / inSample / 2 >= targetSize &&
                boundsOptions.outHeight / inSample / 2 >= targetSize
            ) {
                inSample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSample }
            val rawBmp = BitmapFactory.decodeFile(photoPath, decodeOptions) ?: return null

            val exif = ExifInterface(photoPath)
            val rotationDegrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                rawBmp.recycle()
                rotated
            } else rawBmp
        } catch (_: Exception) {
            null
        }
    }
}
