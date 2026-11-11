// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import android.os.Build
import android.view.MotionEvent
import java.util.Locale

/** In-memory capability observations only. Never guesses a pen model or writes telemetry. */
class PenDiagnostics {
    private var deviceId:Int?=null
    private var name=""
    private var source=0
    private var range=""
    private var min=Float.POSITIVE_INFINITY
    private var max=Float.NEGATIVE_INFINITY
    private var buttons=0
    private var tools=mutableSetOf<Int>()
    val observed get()=deviceId!=null
    fun record(event:MotionEvent,index:Int,rawPressure:Float) {
        if(deviceId!=event.deviceId){
            deviceId=event.deviceId
            name=event.device?.name.orEmpty()
            source=event.source
            val axis=event.device?.getMotionRange(MotionEvent.AXIS_PRESSURE,event.source)
            range=axis?.let { String.format(Locale.ROOT,"%.4f … %.4f (resolution %.4f)",it.min,it.max,it.resolution) }.orEmpty()
            min=Float.POSITIVE_INFINITY;max=Float.NEGATIVE_INFINITY;buttons=0;tools.clear()
        }
        if(rawPressure.isFinite()){min=kotlin.math.min(min,rawPressure);max=kotlin.math.max(max,rawPressure)}
        buttons=buttons or event.buttonState
        tools.add(event.getToolType(index))
    }
    fun report():String = buildString {
        append("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n")
        if(observed){
            append("\nInputDevice: $name\nSource: 0x${source.toString(16)}\nTool IDs: ${tools.joinToString()}\n")
            append("Pressure axis: ${range.ifEmpty { "—" }}\n")
            append("Pressure observed: ${if(min.isFinite())String.format(Locale.ROOT,"%.4f … %.4f",min,max)else "—"}\n")
            append("Button flags observed: 0x${buttons.toString(16)}")
        }
    }
}
