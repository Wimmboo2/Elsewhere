package app.elsewhere.ui.map

/**
 * The only place the map provider is named. Same tiles as the prototype (Esri Canvas World
 * Light/Dark Gray Base, no API key). Check the provider's terms before a Play Store release (README).
 */
object MapConfig {
    /** {variant} is Light or Dark; then zoom, row (y), column (x). */
    private const val TILE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_%s_Gray_Base/MapServer/tile/%d/%d/%d"
    const val ZOOM = 12
    const val TILE_DP = 256f
    /** Map chip in the home card. */
    const val ATTRIBUTION_SHORT = "© Esri, OpenStreetMap"
    /** Settings > About > Map preview. */
    const val ATTRIBUTION_LONG = "Tiles © Esri. Data © OpenStreetMap contributors, HERE."

    fun tileUrl(dark: Boolean, z: Int, y: Int, x: Int) = TILE_URL.format(if (dark) "Dark" else "Light", z, y, x)
}
