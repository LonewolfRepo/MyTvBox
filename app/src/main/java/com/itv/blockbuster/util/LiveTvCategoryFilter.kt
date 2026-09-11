package com.itv.blockbuster.util

import com.itv.blockbuster.domain.model.PortalCategory
import com.itv.blockbuster.domain.model.PortalChannel

/**
 * Single source of truth for how Live TV channel categories are filtered (adult vs
 * normal) and sorted/hidden (per the user's saved Live TV category order from
 * Settings -> Content Settings -> Live TV, stored under the "order_live" key).
 *
 * Shared by LiveTvViewModel and TvGuideViewModel so the TV Guide's category filter
 * and channel list always match Live TV's category sort + show/hide settings
 * exactly, instead of the Guide computing its own independent (unsorted,
 * unfiltered-by-visibility) category list.
 */
object LiveTvCategoryFilter {

    data class Result(
        // Includes the synthetic "All Categories" entry first, followed by the
        // user's categories in their saved order, with hidden ones excluded.
        val categories: List<PortalCategory>,
        val allCategory: PortalCategory,
        val channels: List<PortalChannel>,
        val censoredCategoryIds: Set<String>
    )

    // Ids some portals use for their own built-in "no filter" category. Excluded
    // up front so they can never end up duplicating (or shadowing the order/
    // visibility of) our own synthetic "All Categories" entry below.
    private val ALL_CATEGORY_IDS = setOf("*", "0", "all")

    fun apply(
        allCategories: List<PortalCategory>,
        allChannelsRaw: List<PortalChannel>,
        isAdult: Boolean,
        rawOrder: String
    ): Result {
        val censoredCategoryIds = allCategories.filter { it.isCensored }.map { it.id }.toSet()
        val cats = allCategories.filter { it.isCensored == isAdult && it.id !in ALL_CATEGORY_IDS }
        val channels = allChannelsRaw.filter {
            if (isAdult) it.genreId in censoredCategoryIds else it.genreId !in censoredCategoryIds
        }
        // FIX: applyToCategories already returns exactly the VISIBLE categories, in
        // the user's chosen order - nothing should be appended after it. The
        // previous Live TV-only "missingCats" fallback re-added every category not
        // present in that visible/ordered list, which included categories the user
        // had explicitly hidden, silently defeating the hide feature. Categories
        // the portal adds later that the user has never configured still show,
        // since CategorySortHelper.parse() defaults unseen categories to visible.
        val ordered = CategorySortHelper.applyToCategories(cats, rawOrder)
        val allCategory = PortalCategory(id = "*", title = "All Categories", alias = "all", isCensored = isAdult)
        return Result(
            categories = listOf(allCategory) + ordered,
            allCategory = allCategory,
            channels = channels,
            censoredCategoryIds = censoredCategoryIds
        )
    }
}
