package com.itv.blockbuster.util

import java.util.Locale

object MacSerialUtils {

    fun generateSerial(mac: String): String {
        val cleanMac = mac.replace(":", "").uppercase(Locale.US)

        if (cleanMac.length < 12) return "102014J000000"

        val macBytes = cleanMac.chunked(2)
        if (macBytes.size < 6) return "102014J000000"

        val i = macBytes[3].toIntOrNull(16) ?: 0
        val i2 = macBytes[4].toIntOrNull(16) ?: 0
        val i3 = macBytes[5].toIntOrNull(16) ?: 0

        val months = arrayOf(
            "102014", "112014", "122014",
            "012015", "022015", "032015",
            "042015", "052015", "062015",
            "072015", "082015", "092015",
            "102015", "112015", "122015"
        )
        val letters = arrayOf("J", "K", "L", "M", "N")

        val part1 = months[i % months.size]
        val part2 = letters[i2 % letters.size]
        val part3 = String.format(Locale.US, "%06d", i3 or (i2 shl 8)).take(6)

        return part1 + part2 + part3
    }
}