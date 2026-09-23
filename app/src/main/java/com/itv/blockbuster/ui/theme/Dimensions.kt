package com.itv.blockbuster.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width of the TV/landscape side rail when collapsed (not focused) - the
 * single source of truth for both the rail's own width (AppShell.kt) AND
 * every content screen's "how much horizontal space does the rail reserve"
 * calculation (the collapsedMenuWidth/railReservedWidth parameters
 * scattered across CarouselRow, PosterGrid, the hub screens, Live TV, etc).
 * Previously each of those independently hardcoded the same 84.dp literal,
 * so changing the rail's actual width required updating every one of them
 * by hand and risked them silently drifting out of sync - this is the one
 * place that number lives now.
 */
val RailCollapsedWidth: Dp = 68.dp
