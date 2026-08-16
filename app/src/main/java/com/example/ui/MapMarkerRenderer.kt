package com.example.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
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
        isCharging: Boolean = false
    ): Drawable {
        val cacheKey = "$colorHex|$emoji|$isSelected|$photoPath|$weatherEmoji|$isOffline|$relativeTime|$locationDuration|$batteryPercentage|$isCharging"
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
            isCharging = isCharging
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
        val bubblePx = (52 * density).toInt()
        val stemW = (10 * density).toInt()
        val stemH = (13 * density).toInt()
        val shadowRadius = (4 * density)
        val shadowDy = (2 * density)
        val shadowPad = (shadowRadius + shadowDy).toInt() + 2

        val bmpW = bubblePx + shadowPad * 2
        val bmpH = bubblePx + stemH + shadowPad * 2
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val homeColor = Color.parseColor("#2E7D32")
        val cx = bmpW / 2f
        val bubbleTop = shadowPad.toFloat()
        val bubbleBottom = bubbleTop + bubblePx
        val bubbleCy = bubbleTop + bubblePx / 2f
        val bubbleR = bubblePx / 2f
        val stemTipY = bubbleBottom + stemH - shadowPad

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 50
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + 1.5f, bubbleCy + shadowDy + 1.5f, bubbleR - density, shadowPaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

        val ringWidth = 3f * density
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = homeColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

        val stemPath = Path().apply {
            moveTo(cx - stemW / 2f, bubbleBottom - density * 2f)
            lineTo(cx + stemW / 2f, bubbleBottom - density * 2f)
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
            textSize = 24f * density
        }
        val fm = emojiPaint.fontMetrics
        val textY = bubbleCy - (fm.ascent + fm.descent) / 2f
        canvas.drawText("🏠", cx, textY, emojiPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createZoneIcon(context: Context, name: String, iconName: String): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (36 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#00C853")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2f * density, paint)

        val emoji = when (iconName.lowercase()) {
            "home" -> "🏠"
            "school" -> "🏫"
            "gym" -> "💪"
            "work" -> "💼"
            "shop" -> "🛒"
            else -> "📍"
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f * density
        val fm = paint.fontMetrics
        val textY = (sizePx / 2f) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(emoji, sizePx / 2f, textY, paint)

        return BitmapDrawable(context.resources, bitmap)
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
        isCharging: Boolean
    ): Drawable {
        val density = context.resources.displayMetrics.density

        val bubbleDp = if (isSelected) 66 else 54
        val bubblePx = (bubbleDp * density).toInt()
        val stemW = (10 * density).toInt()
        val stemH = (14 * density).toInt()
        val shadowRadius = (4 * density)
        val shadowDy = (2 * density)
        val shadowPad = (shadowRadius + shadowDy).toInt() + 2

        val timePillHeight = if (relativeTime.isNotEmpty()) (16 * density).toInt() else 0
        val timePillMargin = if (relativeTime.isNotEmpty()) (4 * density).toInt() else 0
        val locPillHeight = if (locationDuration.isNotEmpty()) (13 * density).toInt() else 0
        val locPillMargin = if (locationDuration.isNotEmpty()) (3 * density).toInt() else 0

        val bmpW = bubblePx + shadowPad * 2
        val bmpH = bubblePx + stemH + shadowPad * 2 + timePillHeight + timePillMargin + locPillHeight + locPillMargin
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val parsedColor = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.parseColor("#42A5F5")
        }

        val cx = bmpW / 2f
        val bubbleTop = shadowPad.toFloat() + timePillHeight + timePillMargin
        val bubbleBottom = bubbleTop + bubblePx
        val bubbleCy = bubbleTop + bubblePx / 2f
        val bubbleR = bubblePx / 2f
        val stemTipX = cx
        val stemTipY = bubbleBottom + stemH - shadowPad

        // ── 1. Relative Time Pill (Top) ──
        if (relativeTime.isNotEmpty()) {
            drawTimePill(canvas, cx, density, relativeTime, isOffline)
        }

        // ── 2. Location Duration Pill (Bottom) ──
        if (locationDuration.isNotEmpty()) {
            drawLocationDurationPill(canvas, cx, stemTipY, density, locationDuration, isOffline)
        }

        // ── 3. Shadow Circle ──
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = if (isOffline) 20 else 55
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx + 1.5f, bubbleCy + shadowDy + 1.5f, bubbleR - density, shadowPaint)

        // ── 4. White Fill Circle ──
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = if (isOffline) 100 else 255
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - density, fillPaint)

        // ── 5. Selected Outer Glow ──
        if (isSelected) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parsedColor
                alpha = if (isOffline) 30 else 60
                style = Paint.Style.STROKE
                strokeWidth = 5f * density
            }
            canvas.drawCircle(cx, bubbleCy, bubbleR - 0.5f * density, glowPaint)
        }

        // ── 6. Colored Border Ring ──
        val ringWidth = if (isSelected) 3.5f * density else 2.8f * density
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            alpha = if (isOffline) 100 else 255
        }
        canvas.drawCircle(cx, bubbleCy, bubbleR - ringWidth / 2f - density * 0.5f, ringPaint)

        // ── 7. Teardrop Stem ──
        drawStem(canvas, cx, bubbleBottom, stemTipX, stemTipY, stemW, ringWidth, parsedColor, density, isOffline)

        // ── 8. Profile Photo or Emoji ──
        var drawnPhoto = false
        if (photoPath.isNotEmpty()) {
            drawnPhoto = drawPhoto(canvas, photoPath, cx, bubbleCy, bubbleR, ringWidth, density, isOffline)
        }

        if (!drawnPhoto) {
            val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = (if (isSelected) 28f else 22f) * density
                typeface = Typeface.DEFAULT
                alpha = if (isOffline) 100 else 255
            }
            val fm = emojiPaint.fontMetrics
            val textY = bubbleCy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(emoji, cx, textY, emojiPaint)
        }

        // ── 9. Battery % Badge (Bottom-Left) ──
        if (batteryPercentage in 0..100) {
            drawBatteryBadge(canvas, cx, bubbleCy, bubbleR, batteryPercentage, isCharging, density)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawTimePill(canvas: Canvas, cx: Float, density: Float, text: String, isOffline: Boolean) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#B71C1C") else Color.parseColor("#0F172A")
            style = Paint.Style.FILL
            alpha = 230
        }
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#FFCDD2") else Color.parseColor("#38BDF8")
            textSize = 7.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = pillTextPaint.measureText(text)
        val pillW = textWidth + 12f * density
        val pillH = 14f * density
        val pillLeft = cx - pillW / 2f
        val pillTop = 2f * density
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint)
        val pfm = pillTextPaint.fontMetrics
        val pillTextY = pillTop + pillH / 2f - (pfm.ascent + pfm.descent) / 2f
        canvas.drawText(text, cx, pillTextY, pillTextPaint)
    }

    private fun drawLocationDurationPill(canvas: Canvas, cx: Float, stemTipY: Float, density: Float, text: String, isOffline: Boolean) {
        val locPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#1C1917") else Color.parseColor("#0F172A")
            style = Paint.Style.FILL
            alpha = 220
        }
        val locTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffline) Color.parseColor("#A8A29E") else Color.parseColor("#E2E8F0")
            textSize = 6.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = locTextPaint.measureText(text)
        val pillW = textWidth + 10f * density
        val pillH = 12f * density
        val pillLeft = cx - pillW / 2f
        val pillTop = stemTipY + 2f * density
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
            moveTo(cx - stemBaseHalf, bubbleBottom - density * 2f)
            lineTo(cx + stemBaseHalf, bubbleBottom - density * 2f)
            lineTo(stemTipX, stemTipY)
            close()
        }
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = if (isOffline) 100 else 255
        }
        canvas.drawPath(stemPath, stemPaint)
        val stemBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            style = Paint.Style.STROKE
            strokeWidth = ringWidth * 0.85f
            strokeJoin = Paint.Join.ROUND
            alpha = if (isOffline) 100 else 255
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
                val clipSize = (clipR * 2).toInt()
                val photoBmp = loadUprightMarkerBitmap(photoPath, clipSize)
                if (photoBmp != null) {
                    val scaledBmp = Bitmap.createScaledBitmap(photoBmp, clipSize, clipSize, true)
                    val shader = BitmapShader(scaledBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.shader = shader
                        alpha = if (isOffline) 100 else 255
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
        val badgeRadius = 9f * density
        val bx = cx - bubbleR * 0.68f
        val by = bubbleCy + bubbleR * 0.68f
        val battColor = when {
            isCharging -> Color.parseColor("#00E676")
            batteryPercentage <= 20 -> Color.parseColor("#FF1744")
            batteryPercentage <= 50 -> Color.parseColor("#FFB300")
            else -> Color.parseColor("#00E676")
        }
        val battBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A2E")
            style = Paint.Style.FILL
            alpha = 230
        }
        canvas.drawCircle(bx, by, badgeRadius, battBg)
        val battBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = battColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawCircle(bx, by, badgeRadius, battBorder)
        val battText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = battColor
            textSize = 6.5f * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val label = if (isCharging) "⚡" else "${batteryPercentage}%"
        val bfm = battText.fontMetrics
        val battTextY = by - (bfm.ascent + bfm.descent) / 2f
        canvas.drawText(label, bx, battTextY, battText)
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
