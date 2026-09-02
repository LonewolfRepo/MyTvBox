package com.itv.blockbuster.util

import com.itv.blockbuster.domain.model.PortalVodItem

// A simple global singleton to hold the selected VOD item during navigation.
// This bypasses the need to make PortalVodItem Parcelable for Compose Navigation arguments.
object VodNavigationCache {
    var currentItem: PortalVodItem? = null
}