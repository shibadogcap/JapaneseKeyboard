package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickDir

object FlickTypoCandidates {
    fun forCharacter(ch: Char): List<TypoCandidate> {
        val key = KanaFlickLayout.keyOf(ch) ?: return listOf(TypoCandidate(ch, TypoCategory.Exact))

        val out = ArrayList<TypoCandidate>(16)
        out.add(TypoCandidate(ch, TypoCategory.Exact))

        for (dir in FlickDir.entries) {
            if (dir == key.dir) continue
            val v = KanaFlickLayout.charOf(key.group, dir) ?: continue
            out.add(TypoCandidate(v, TypoCategory.TapKeyInFlick))
        }

        for (group in KanaFlickLayout.allGroups()) {
            if (group == key.group) continue
            val v = KanaFlickLayout.charOf(group, key.dir) ?: continue
            val dist = KanaFlickLayout.manhattan(key.group, group)
            val category = when (dist) {
                1 -> TypoCategory.DistanceNear
                2 -> TypoCategory.DistanceMiddle
                else -> TypoCategory.DistanceFar
            }
            out.add(TypoCandidate(v, category))
        }

        return out
            .distinctBy { it.ch to it.category }
            .sortedWith(compareBy<TypoCandidate> { it.penalty }.thenBy { it.ch })
    }
}