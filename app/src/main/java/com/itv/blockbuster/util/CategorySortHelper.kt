package com.itv.blockbuster.util

import com.itv.blockbuster.domain.model.PortalCategory

data class CategorySortItem(val id: String, val title: String, val isVisible: Boolean)

object CategorySortHelper {
    fun parse(cats: List<PortalCategory>, raw: String): List<CategorySortItem> {
        if (raw.isBlank()) return cats.map { CategorySortItem(it.id, it.title, true) }
        val map = cats.associateBy { it.id }
        val parsed = raw.split(",").mapNotNull { part ->
            val pieces = part.split("|")
            if (pieces.size == 2) {
                val id = pieces[0]
                val visible = pieces[1] == "1"
                map[id]?.let { CategorySortItem(it.id, it.title, visible) }
            } else null
        }
        val existingIds = parsed.map { it.id }.toSet()
        val newItems = cats.filter { it.id !in existingIds }.map { CategorySortItem(it.id, it.title, true) }
        return parsed + newItems
    }

    fun serialize(items: List<CategorySortItem>): String {
        return items.joinToString(",") { "${it.id}|${if (it.isVisible) "1" else "0"}" }
    }

    fun applyToCategories(cats: List<PortalCategory>, raw: String): List<PortalCategory> {
        val sortItems = parse(cats, raw)
        val visibleIds = sortItems.filter { it.isVisible }.map { it.id }
        val byId = cats.associateBy { it.id }
        return visibleIds.mapNotNull { byId[it] }
    }
}