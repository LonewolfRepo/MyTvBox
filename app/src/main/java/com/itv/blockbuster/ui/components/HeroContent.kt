package com.itv.blockbuster.ui.components

import com.itv.blockbuster.domain.model.PortalChannel
import com.itv.blockbuster.domain.model.PortalVodItem

/**
 * Generalized data HeroBanner renders, so ONE shared composable can serve
 * every screen that wants a persistent hero (Home/Movies/TV Shows today;
 * Live TV, Favorites, and Recents per the ongoing UI-consistency work)
 * instead of duplicating the whole banner per screen. HeroBanner switches
 * its rendering based on which variant it's given:
 *
 * - Vod: the existing dynamic treatment - the poster image is cropped/
 *   zoomed to read as a landscape backdrop (see TopCropTransformation),
 *   title = item name, a Year/Runtime/Genre/IMDb/Country metadata row, and
 *   description = the item's own synopsis.
 * - LiveChannel: a single FIXED generic graphic - deliberately NOT cropped/
 *   zoomed, and deliberately NOT swapped out per channel (same image
 *   regardless of which channel is focused - an explicit product decision,
 *   not a placeholder-for-now shortcut), title = channel name, no VOD-only
 *   metadata row (year/runtime/genre/etc. don't apply to a channel), and
 *   description = whatever's currently playing on it (nowPlaying) instead
 *   of a synopsis.
 */
sealed class HeroContent {
    abstract val id: String
    abstract val title: String
    abstract val description: String

    data class Vod(val item: PortalVodItem) : HeroContent() {
        override val id: String get() = item.id
        override val title: String get() = item.name
        override val description: String get() = item.description
    }

    data class LiveChannel(val channel: PortalChannel) : HeroContent() {
        override val id: String get() = channel.id
        override val title: String get() = channel.name
        override val description: String get() = channel.nowPlaying
    }
}
