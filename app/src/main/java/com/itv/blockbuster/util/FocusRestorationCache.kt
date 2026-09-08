package com.itv.blockbuster.util

/**
 * Exact position of the last focused card inside a screen.
 *
 * @param rowId     ID of the carousel row (vertical position)
 * @param itemId    ID of the focused card (used to match on restore)
 * @param itemIndex Horizontal index of the card inside its row
 */
data class FocusedPosition(
    val rowId: String,
    val itemId: String,
    val itemIndex: Int
)

/**
 * Singleton cache storing the last focused card position per screen key
 * ("home", "vod", "series", ...). Used to restore D-pad focus after
 * navigating back from detail screens.
 */
object FocusRestorationCache {
    private val cache = mutableMapOf<String, FocusedPosition>()

    fun save(screenKey: String, position: FocusedPosition) {
        cache[screenKey] = position
    }

    fun get(screenKey: String): FocusedPosition? = cache[screenKey]

    fun clear(screenKey: String) {
        cache.remove(screenKey)
    }

    fun clearAll() {
        cache.clear()
    }
}