package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages MapLibre GeoJSON sources, style layers, and bitmap icon registration
 * for Kin-Tracker zones, trails, cluster spokes, and member markers.
 */
object MapLibreOverlayManager {
    const val SOURCE_ZONES_FILL = "kin-zones-fill-source"
    const val SOURCE_ZONES_LINE = "kin-zones-line-source"
    const val SOURCE_LINES_HALO = "kin-lines-halo-source"
    const val SOURCE_LINES_CORE = "kin-lines-core-source"
    const val SOURCE_MARKERS_CENTER = "kin-markers-center-source"
    const val SOURCE_MARKERS_STEM = "kin-markers-stem-source"

    const val LAYER_ZONES_FILL = "kin-zones-fill-layer"
    const val LAYER_ZONES_LINE = "kin-zones-line-layer"
    const val LAYER_LINES_HALO = "kin-lines-halo-layer"
    const val LAYER_LINES_CORE = "kin-lines-core-layer"
    const val LAYER_MARKERS_CENTER = "kin-markers-center-layer"
    const val LAYER_MARKERS_STEM = "kin-markers-stem-layer"

    fun styleUrlForMode(mapTypeMode: String): String = when (mapTypeMode) {
        "hybrid" -> "https://tiles.openfreemap.org/styles/dark"
        "radar" -> "https://tiles.openfreemap.org/styles/positron"
        else -> "https://tiles.openfreemap.org/styles/liberty"
    }

    fun ensureLayers(style: Style) {
        if (style.getSource(SOURCE_ZONES_FILL) == null) {
            style.addSource(GeoJsonSource(SOURCE_ZONES_FILL))
            style.addLayer(
                FillLayer(LAYER_ZONES_FILL, SOURCE_ZONES_FILL).withProperties(
                    fillColor(get("color")),
                    fillOpacity(get("opacity"))
                )
            )
        }
        if (style.getSource(SOURCE_ZONES_LINE) == null) {
            style.addSource(GeoJsonSource(SOURCE_ZONES_LINE))
            style.addLayer(
                LineLayer(LAYER_ZONES_LINE, SOURCE_ZONES_LINE).withProperties(
                    lineColor(get("color")),
                    lineWidth(get("width")),
                    lineOpacity(get("opacity")),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(SOURCE_LINES_HALO) == null) {
            style.addSource(GeoJsonSource(SOURCE_LINES_HALO))
            style.addLayer(
                LineLayer(LAYER_LINES_HALO, SOURCE_LINES_HALO).withProperties(
                    lineColor(get("color")),
                    lineWidth(get("width")),
                    lineOpacity(get("opacity")),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(SOURCE_LINES_CORE) == null) {
            style.addSource(GeoJsonSource(SOURCE_LINES_CORE))
            style.addLayer(
                LineLayer(LAYER_LINES_CORE, SOURCE_LINES_CORE).withProperties(
                    lineColor(get("color")),
                    lineWidth(get("width")),
                    lineOpacity(get("opacity")),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(SOURCE_MARKERS_CENTER) == null) {
            style.addSource(GeoJsonSource(SOURCE_MARKERS_CENTER))
            style.addLayer(
                SymbolLayer(LAYER_MARKERS_CENTER, SOURCE_MARKERS_CENTER).withProperties(
                    iconImage(get("iconId")),
                    iconAnchor(Property.ICON_ANCHOR_CENTER),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }
        if (style.getSource(SOURCE_MARKERS_STEM) == null) {
            style.addSource(GeoJsonSource(SOURCE_MARKERS_STEM))
            style.addLayer(
                SymbolLayer(LAYER_MARKERS_STEM, SOURCE_MARKERS_STEM).withProperties(
                    iconImage(get("iconId")),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }
    }

    fun registerDrawable(style: Style, iconId: String, drawable: Drawable) {
        val bitmap = drawableToBitmap(drawable)
        style.addImage(iconId, bitmap)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun circlePolygonFeature(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        colorHex: String,
        opacity: Float,
        steps: Int = 48
    ): Feature {
        val ring = circlePoints(centerLat, centerLng, radiusMeters, steps)
        val feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        feature.addStringProperty("color", colorHex)
        feature.addNumberProperty("opacity", opacity)
        return feature
    }

    fun circleOutlineFeature(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        colorHex: String,
        widthDp: Float,
        opacity: Float,
        steps: Int = 48
    ): Feature {
        val ring = circlePoints(centerLat, centerLng, radiusMeters, steps)
        val feature = Feature.fromGeometry(LineString.fromLngLats(ring))
        feature.addStringProperty("color", colorHex)
        feature.addNumberProperty("width", widthDp)
        feature.addNumberProperty("opacity", opacity)
        return feature
    }

    fun lineFeature(
        points: List<LatLng>,
        colorHex: String,
        widthDp: Float,
        opacity: Float
    ): Feature {
        val coords = points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val feature = Feature.fromGeometry(LineString.fromLngLats(coords))
        feature.addStringProperty("color", colorHex)
        feature.addNumberProperty("width", widthDp)
        feature.addNumberProperty("opacity", opacity)
        return feature
    }

    fun markerFeature(
        lat: Double,
        lng: Double,
        iconId: String,
        kind: String,
        id: String
    ): Feature {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        feature.addStringProperty("iconId", iconId)
        feature.addStringProperty("kind", kind)
        feature.addStringProperty("id", id)
        return feature
    }

    fun updateSource(style: Style, sourceId: String, features: List<Feature>) {
        val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun circlePoints(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        steps: Int
    ): List<Point> {
        val earthRadius = 6371000.0
        val latRad = Math.toRadians(centerLat)
        val lngRad = Math.toRadians(centerLng)
        val angDist = radiusMeters / earthRadius
        val points = ArrayList<Point>(steps + 1)
        for (i in 0..steps) {
            val bearing = 2.0 * Math.PI * (i % steps) / steps
            val pLat = asin(
                sin(latRad) * cos(angDist) +
                    cos(latRad) * sin(angDist) * cos(bearing)
            )
            val pLng = lngRad + atan2(
                sin(bearing) * sin(angDist) * cos(latRad),
                cos(angDist) - sin(latRad) * sin(pLat)
            )
            points.add(Point.fromLngLat(Math.toDegrees(pLng), Math.toDegrees(pLat)))
        }
        return points
    }

    fun intColorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
