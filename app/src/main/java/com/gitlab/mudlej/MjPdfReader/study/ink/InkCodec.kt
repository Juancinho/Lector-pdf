// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import com.google.gson.Gson
import com.google.gson.JsonParser

object InkCodec {
    fun encode(stroke: InkStroke): String = Gson().toJson(stroke)
    fun decode(text: String): InkStroke? = runCatching {
        if (text.length > 600_000) return null
        val json = JsonParser.parseString(text).asJsonObject
        val points = json["points"].asJsonArray
        if (points.size() !in 1..InkStroke.MAX_POINTS) return null
        InkStroke(
            json["id"].asString, json["page"].asInt,
            points.map { p -> p.asJsonObject.let { InkPoint(it["x"].asFloat, it["y"].asFloat, it["pressure"].asFloat) } },
            json["color"].asInt, json["width"].asFloat, InkTool.valueOf(json["tool"].asString), json["pressureEnabled"].asBoolean,
        )
    }.getOrNull()
}
