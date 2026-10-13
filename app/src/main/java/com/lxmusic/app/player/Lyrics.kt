package com.lxmusic.app.player

/** 带时间戳的歌词行 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 解析 LRC 为带时间戳的行：
 * - 过滤 [ti:]/[ar:]/[al:]/[by:]/[offset:] 等元数据
 * - [mm:ss.xx] 时间标签转为毫秒
 * - 一行多个时间标签时拆成多行
 * - 无时间标签的纯文本行保留（timeMs = -1，排在最前）
 */
fun parseLrcTimed(raw: String): List<LyricLine> {
    val metaKeys = setOf("ti", "ar", "al", "by", "offset", "author", "length", "re", "ve")
    val timeRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    val result = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        // 纯元数据行丢弃
        val metaMatch = Regex("""^\[([a-zA-Z]+):.*\]$""").matchEntire(line)
        if (metaMatch != null && metaMatch.groupValues[1].lowercase() in metaKeys) return@forEach

        val times = timeRegex.findAll(line).toList()
        if (times.isEmpty()) {
            // 无时间标签的文本行
            val text = line.trim('[', ']').trim()
            if (text.isNotEmpty() && !text.startsWith("[")) result.add(LyricLine(-1, text))
            return@forEach
        }
        val text = line.substring(times.last().range.last + 1).trim()
        if (text.isEmpty()) return@forEach
        times.forEach { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0
            val sec = m.groupValues[2].toLongOrNull() ?: 0
            val frac = m.groupValues[3]
            val ms = when (frac.length) {
                3 -> frac.toLongOrNull() ?: 0
                2 -> (frac.toLongOrNull() ?: 0) * 10
                1 -> (frac.toLongOrNull() ?: 0) * 100
                else -> 0
            }
            result.add(LyricLine(min * 60_000 + sec * 1000 + ms, text))
        }
    }
    // 无时间戳的行放最前面，其余按时间排序
    return result.sortedWith(compareBy({ it.timeMs < 0 }, { it.timeMs }))
}

/** 根据播放进度找到当前歌词行索引 */
fun List<LyricLine>.currentLyricIndex(positionMs: Long): Int {
    if (isEmpty()) return -1
    var idx = -1
    forEachIndexed { i, l ->
        if (l.timeMs >= 0 && l.timeMs <= positionMs) idx = i
    }
    return idx
}
