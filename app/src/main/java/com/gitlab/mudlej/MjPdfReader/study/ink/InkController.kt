// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.text.InputFilter
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.preference.PreferenceManager
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

/** First pen engine. Standard PDF mutations use the existing fenced writer/journal path. */
class InkController(
    private val activity: AppCompatActivity,
    private val pdf: PDFView,
    private val vm: ReaderViewModel,
    private val scope: CoroutineScope,
    private val canEdit: () -> Boolean,
    private val record: (AnnotationEdit) -> Unit,
    private val save: () -> Unit,
) {
    private val preferences=PreferenceManager.getDefaultSharedPreferences(activity)
    private val overlay=InkOverlay(activity,pdf)
    private val pageInk=linkedMapOf<Int,List<InkStroke>>()
    private val diagnostics=PenDiagnostics()
    private val loading=mutableSetOf<Int>()
    private val revisions=mutableMapOf<Int,Int>()
    private val history get()=vm.studyInkHistory
    private var color=preferences.getInt("study.ink.color",0xFF263E4D.toInt())
    private var nib=preferences.getFloat("study.ink.width",2f).takeIf { it.isFinite() && it in .1f..24f } ?: 2f
    private var markerOpacity=preferences.getInt("study.ink.markerOpacity",40).coerceIn(1,100)
    private var usePressure=preferences.getBoolean("study.ink.pressure",true)
    private var pointer=-1
    private var activePage=-1
    private var suppressFingers=false
    private val samples=mutableListOf<InkPoint>()
    private var activeTool=InkTool.PEN
    private val erased=linkedMapOf<String,InkStroke>()
    private var selected=listOf<InkStroke>()
    private var transformStart: InkPoint?=null
    private var resize=false
    private var transformed=listOf<InkStroke>()
    private lateinit var card: MaterialCardView
    private lateinit var open: MaterialButton
    private lateinit var title: TextView
    private lateinit var undo: MaterialButton
    private lateinit var redo: MaterialButton
    private val toolButtons=mutableMapOf<InkTool,MaterialButton>()
    private var generation=0
    private val writer=object:InkWriter {
        override fun add(stroke:InkStroke)=pdf.addStudyInk(stroke.page,stroke.nativePoints(),stroke.color,stroke.width,stroke.id,InkCodec.encode(stroke),stroke.pressureEnabled)
        override fun remove(stroke:InkStroke)=pdf.removeStudyInk(stroke.page,stroke.id)
    }
    private fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
    private fun message(res:Int)=Snackbar.make(pdf,res,Snackbar.LENGTH_SHORT).show()
    private fun enabled()=vm.studyInkEnabled

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        val parent=pdf.parent as ViewGroup
        parent.addView(overlay,ViewGroup.LayoutParams(-1,-1))
        overlay.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        card=MaterialCardView(activity).apply {
            radius=dp(18).toFloat(); cardElevation=dp(4).toFloat(); strokeWidth=dp(1)
            strokeColor=activity.getColor(R.color.study_outline)
            setCardBackgroundColor(activity.getColor(R.color.study_surface))
            layoutParams=CoordinatorLayout.LayoutParams(minOf(dp(760),activity.resources.displayMetrics.widthPixels-dp(32)),-2).apply {
                gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(dp(16),dp(12),dp(16),0)
            }
            visibility=View.GONE
        }
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(4),dp(8),dp(4)) }
        val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        title=TextView(activity).apply {
            text=activity.getString(R.string.study_tools); textSize=14f
            setTextColor(activity.getColor(R.color.study_ink)); setPadding(dp(12),0,dp(8),0)
        }
        header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button(R.string.study_save_ink,R.drawable.study_save){cancelGesture();save()})
        header.addView(button(R.string.study_hide_tools,R.drawable.study_collapse){collapse()})
        body.addView(header)
        val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        row.addView(button(R.string.study_read,R.drawable.study_hand){vm.studyInkEnabled=false;cancelGesture();clearSelection();refresh()})
        listOf(
            Triple(InkTool.PEN,R.string.study_pen,R.drawable.study_pen),
            Triple(InkTool.PENCIL,R.string.study_pencil,R.drawable.study_pencil),
            Triple(InkTool.HIGHLIGHTER,R.string.study_marker,R.drawable.study_marker),
            Triple(InkTool.ERASER,R.string.study_eraser,R.drawable.study_eraser),
            Triple(InkTool.LASSO,R.string.study_lasso,R.drawable.study_lasso),
            Triple(InkTool.LINE,R.string.study_line,R.drawable.study_line),
            Triple(InkTool.ARROW,R.string.study_arrow,R.drawable.study_arrow),
            Triple(InkTool.RECTANGLE,R.string.study_rectangle,R.drawable.study_rectangle),
            Triple(InkTool.ELLIPSE,R.string.study_ellipse,R.drawable.study_ellipse),
        ).forEach { (tool,label,icon) ->
            val b=button(label,icon){cancelGesture();vm.studyInkEnabled=true;vm.studyInkTool=tool;clearSelection();loadPage(pdf.currentPage);refresh()}
            toolButtons[tool]=b;row.addView(b)
        }
        row.addView(button(R.string.study_ink_options,R.drawable.study_palette){showOptions()})
        undo=button(R.string.study_undo,R.drawable.study_undo){history.peekUndo()?.let { if(apply(it,false)){history.didUndo();refresh()} }}
        redo=button(R.string.study_redo,R.drawable.study_redo){history.peekRedo()?.let { if(apply(it,false)){history.didRedo();refresh()} }}
        row.addView(undo);row.addView(redo)
        body.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false;addView(row) })
        card.addView(body);parent.addView(card)
        open=MaterialButton(activity).apply {
            text=activity.getString(R.string.study_write);setIconResource(R.drawable.study_pen)
            isAllCaps=false;minHeight=dp(48);cornerRadius=dp(24)
            backgroundTintList=ColorStateList.valueOf(activity.getColor(R.color.study_accent))
            setTextColor(activity.getColor(R.color.study_surface));iconTint=ColorStateList.valueOf(activity.getColor(R.color.study_surface))
            layoutParams=CoordinatorLayout.LayoutParams(-2,-2).apply {
                gravity=Gravity.TOP or Gravity.END;setMargins(dp(16),dp(12),dp(16),0)
            }
            setOnClickListener {
                vm.studyToolbarExpanded=true;card.visibility=View.VISIBLE;visibility=View.GONE
                vm.studyInkEnabled=true;loadPage(pdf.currentPage);refresh()
                message(R.string.study_ink_hint)
            }
        }
        parent.addView(open)
        card.visibility=if(vm.studyToolbarExpanded)View.VISIBLE else View.GONE
        open.visibility=if(vm.studyToolbarExpanded)View.GONE else View.VISIBLE
        pdf.setStudyInputHandler { _,event -> touch(event) }
        refresh()
    }
    private fun button(label:Int,icon:Int,action:()->Unit)=MaterialButton(activity).apply {
        backgroundTintList=ColorStateList.valueOf(Color.TRANSPARENT)
        contentDescription=activity.getString(label);TooltipCompat.setTooltipText(this,contentDescription)
        setIconResource(icon);iconSize=dp(22);iconPadding=0;insetTop=0;insetBottom=0
        minWidth=dp(48);minimumWidth=dp(48);minHeight=dp(48);minimumHeight=dp(48)
        setPadding(dp(12),0,dp(12),0);cornerRadius=dp(12)
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
        iconTint=ColorStateList.valueOf(activity.getColor(R.color.study_ink))
        setOnClickListener { if(canEdit())action() }
    }
    private fun collapse() {
        cancelGesture();vm.studyInkEnabled=false;vm.studyToolbarExpanded=false;clearSelection()
        card.visibility=View.GONE;open.visibility=View.VISIBLE;refresh()
    }
    private fun refresh() {
        if(!::undo.isInitialized)return
        undo.isEnabled=history.canUndo;redo.isEnabled=history.canRedo
        undo.alpha=if(history.canUndo)1f else .38f;redo.alpha=if(history.canRedo)1f else .38f
        toolButtons.forEach { (tool,b) ->
            val selected=enabled()&&vm.studyInkTool==tool
            b.isSelected=selected
            b.backgroundTintList=ColorStateList.valueOf(if(selected)activity.getColor(R.color.study_subtle)else Color.TRANSPARENT)
            b.iconTint=ColorStateList.valueOf(if(selected)activity.getColor(R.color.study_accent)else activity.getColor(R.color.study_ink))
        }
        title.text=if(selected.isNotEmpty())activity.getString(R.string.study_ink_selected,selected.size)
            else activity.getString(if(enabled())toolLabel(vm.studyInkTool)else R.string.study_read)
    }
    private fun toolLabel(tool:InkTool)=when(tool){
        InkTool.PEN->R.string.study_pen;InkTool.PENCIL->R.string.study_pencil;InkTool.HIGHLIGHTER->R.string.study_marker
        InkTool.ERASER->R.string.study_eraser;InkTool.LASSO->R.string.study_lasso;InkTool.LINE->R.string.study_line
        InkTool.ARROW->R.string.study_arrow;InkTool.RECTANGLE->R.string.study_rectangle;InkTool.ELLIPSE->R.string.study_ellipse
    }
    fun onDocument(key:String?) {
        generation++;pageInk.clear();loading.clear();revisions.clear();cancelGesture();clearSelection()
        if(key!=null&&vm.studyInkDocument!=key){history.clear();vm.studyInkDocument=key}
        if(::open.isInitialized){
            card.visibility=if(key!=null&&vm.studyToolbarExpanded)View.VISIBLE else View.GONE
            open.visibility=if(key!=null&&!vm.studyToolbarExpanded)View.VISIBLE else View.GONE
        }
        if(key!=null)loadPage(pdf.currentPage)
        refresh()
    }
    fun loadPage(page:Int,force:Boolean=false) {
        if(selected.isNotEmpty()&&selected.first().page!=page)clearSelection()
        if(page<0||(!force&&pageInk.containsKey(page))||!loading.add(page))return
        val token=generation
        val revision=revisions[page]?:0
        scope.launch {
            val strokes=runCatching { withContext(Dispatchers.IO) {
                var count=0
                pdf.getStudyInk(page).mapNotNull(InkCodec::decode).filter { it.page==page }.takeWhile { count+=it.points.size;count<=200_000 }
            } }.getOrElse { emptyList() }
            if(generation==token){
                loading.remove(page)
                if((revisions[page]?:0)!=revision)loadPage(page,true)
                else { pageInk[page]=strokes;while(pageInk.size>1&&(pageInk.size>8||pageInk.values.sumOf { list->list.sumOf { it.points.size } }>200_000))pageInk.remove(pageInk.keys.first()) }
            }
        }
    }
    fun invalidateCache(){generation++;pageInk.clear();loading.clear();clearSelection();loadPage(pdf.currentPage)}
    fun beforeSave():Boolean { if(pointer>=0)finish();cancelGesture();return true }
    fun destroy(){generation++;pdf.setStudyInputHandler(null);cancelGesture()}
    private fun clearSelection(){selected=emptyList();overlay.selected=emptyList();overlay.invalidate()}
    private fun cancelGesture(){pointer=-1;activePage=-1;samples.clear();erased.clear();transformStart=null;transformed=emptyList();overlay.clearPreview();suppressFingers=false}

    private fun touch(event:MotionEvent):Boolean {
        if(!enabled())return false
        if(event.actionMasked==MotionEvent.ACTION_CANCEL){val handled=pointer>=0||suppressFingers;cancelGesture();return handled}
        if(suppressFingers){if(event.actionMasked==MotionEvent.ACTION_UP)suppressFingers=false;return true}
        val actionIndex=event.actionIndex
        val penDown=(event.actionMasked==MotionEvent.ACTION_DOWN||event.actionMasked==MotionEvent.ACTION_POINTER_DOWN)&&
            event.getToolType(actionIndex) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER)
        if(penDown&&pointer<0){
            if(!canEdit())return true
            val page=pdf.studyPageAt(event.getX(actionIndex),event.getY(actionIndex));if(page<0)return true
            pointer=event.getPointerId(actionIndex);activePage=page;samples.clear();erased.clear()
            activeTool=if(event.getToolType(actionIndex)==MotionEvent.TOOL_TYPE_ERASER||event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY!=0)InkTool.ERASER else vm.studyInkTool
            if(activeTool in listOf(InkTool.ERASER,InkTool.LASSO)&&!pageInk.containsKey(page)){loadPage(page);message(R.string.study_ink_loading);pointer=-1;suppressFingers=true;return true}
            val p=point(event,actionIndex)?:return true
            if(activeTool==InkTool.LASSO&&selected.isNotEmpty()){
                val bounds=bounds(selected)
                val handle=pdf.studyPdfToView(page,bounds.right,bounds.top)
                resize=handle!=null&&hypot(event.getX(actionIndex)-handle.x,event.getY(actionIndex)-handle.y)<dp(22)
                if(resize||p.x in bounds.left..bounds.right&&p.y in bounds.bottom..bounds.top)transformStart=p else clearSelection()
            }else clearSelection()
            samples.add(p);preview();return true
        }
        if(pointer<0){if(selected.isNotEmpty())overlay.postInvalidateOnAnimation();return false}
        val index=event.findPointerIndex(pointer)
        if(index<0){cancelGesture();return true}
        if(event.actionMasked==MotionEvent.ACTION_MOVE){
            for(h in 0 until event.historySize)point(event,index,h)?.let(::sample)
            point(event,index)?.let(::sample);preview();return true
        }
        if((event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_POINTER_UP)&&event.getPointerId(actionIndex)==pointer){
            point(event,index)?.let(::sample)
            finish();val palmsRemain=event.pointerCount>1;cancelGesture();suppressFingers=palmsRemain;return true
        }
        return true // Ignore palm/finger pointers until the active pen has lifted.
    }
    private fun point(event:MotionEvent,index:Int,history:Int=-1):InkPoint? {
        val x=if(history<0)event.getX(index)else event.getHistoricalX(index,history)
        val y=if(history<0)event.getY(index)else event.getHistoricalY(index,history)
        if(pdf.studyPageAt(x,y)!=activePage)return null
        val p=pdf.studyViewToPdf(activePage,x,y)?:return null
        val range=event.device?.getMotionRange(MotionEvent.AXIS_PRESSURE,event.source)
        val raw=if(history<0)event.getPressure(index)else event.getHistoricalPressure(index,history)
        diagnostics.record(event,index,raw)
        return InkPoint(p.x,p.y,InkStroke.pressure(raw,range?.min?:0f,range?.max?:1f))
    }
    private fun sample(raw:InkPoint) {
        if(samples.size>=InkStroke.MAX_POINTS)return
        val previous=samples.lastOrNull()
        if(previous!=null&&hypot(raw.x-previous.x,raw.y-previous.y)<.15f)return
        val smoothed=if(previous!=null&&activeTool in listOf(InkTool.PEN,InkTool.PENCIL,InkTool.HIGHLIGHTER))
            InkPoint(previous.x*.2f+raw.x*.8f,previous.y*.2f+raw.y*.8f,previous.pressure*.4f+raw.pressure*.6f)else raw
        samples.add(smoothed)
    }
    private fun preview() {
        if(samples.isEmpty())return
        overlay.page=activePage;overlay.color=if(activeTool==InkTool.LASSO)0xFF5271C4.toInt()else toolColor()
        overlay.width=toolWidth();overlay.pressure=toolPressure();overlay.lasso=activeTool in listOf(InkTool.LASSO,InkTool.ERASER)
        if(activeTool==InkTool.ERASER){
            val p=samples.last();pageInk[activePage].orEmpty().filter { InkGeometry.hits(it,p,toolWidth()*1.5f) }.forEach { erased[it.id]=it }
            overlay.points=emptyList();overlay.selected=erased.values.toList()
        }else if(transformStart!=null){
            val start=transformStart!!;val end=samples.last();val b=bounds(selected)
            val scale=if(resize)(hypot(end.x-b.left,end.y-b.bottom)/hypot(b.width(),b.height()).coerceAtLeast(.1f)).coerceIn(.2f,5f)else 1f
            transformed=selected.map { if(resize)it.transformed(scale=scale,originX=b.left,originY=b.bottom)else it.transformed(end.x-start.x,end.y-start.y) }
            overlay.points=emptyList();overlay.ghosts=transformed
        }else overlay.points=if(activeTool in listOf(InkTool.LINE,InkTool.ARROW,InkTool.RECTANGLE,InkTool.ELLIPSE))InkStroke.shape(activeTool,samples.first(),samples.last())else samples
        overlay.invalidate()
    }
    private fun toolWidth()=if(activeTool==InkTool.HIGHLIGHTER)nib*5 else nib
    private fun toolColor()=when(activeTool){InkTool.HIGHLIGHTER->(color and 0xFFFFFF)or((((markerOpacity*255+50)/100)) shl 24);InkTool.PENCIL->(color and 0xFFFFFF)or(205 shl 24);else->color or 0xFF000000.toInt()}
    private fun toolPressure()=usePressure&&activeTool in listOf(InkTool.PEN,InkTool.PENCIL)
    private fun finish() {
        if(samples.isEmpty())return
        if(activeTool==InkTool.ERASER){if(erased.isNotEmpty())apply(InkChange(erased.values.toList(),emptyList()));clearSelection();return}
        if(activeTool==InkTool.LASSO){
            if(transformStart!=null&&transformed.isNotEmpty()){
                if(apply(InkChange(selected,transformed))){selected=transformed;overlay.selected=selected}
            }else{
                selected=pageInk[activePage].orEmpty().filter { InkGeometry.selected(it,samples) }
                if(selected.sumOf { it.points.size }>50_000){selected=emptyList();message(R.string.study_ink_limit)}
                overlay.selected=selected
            }
            refresh();return
        }
        val points=if(activeTool in listOf(InkTool.LINE,InkTool.ARROW,InkTool.RECTANGLE,InkTool.ELLIPSE))InkStroke.shape(activeTool,samples.first(),samples.last())else samples.toList()
        val stroke=InkStroke(UUID.randomUUID().toString(),activePage,points,toolColor(),toolWidth(),activeTool,toolPressure())
        apply(InkChange(emptyList(),listOf(stroke)))
    }
    private fun apply(change:InkChange,remember:Boolean=true):Boolean {
        if(!canEdit())return false
        if(!InkMutator.apply(writer,change)){message(R.string.study_ink_failed);return false}
        record(AnnotationEdit.InkPatch((change.before+change.after).first().page,change.before.map(InkCodec::encode),change.after.map(InkCodec::encode)))
        val page=(change.before+change.after).first().page
        revisions[page]=(revisions[page]?:0)+1
        pageInk[page]=pageInk[page].orEmpty().filter { old->change.before.none { it.id==old.id } }+change.after
        if(remember)history.record(change)
        clearSelection();refresh();return true
    }
    private fun bounds(strokes:List<InkStroke>):RectF {
        val p=strokes.flatMap { it.points }
        return RectF(p.minOf { it.x },p.maxOf { it.y },p.maxOf { it.x },p.minOf { it.y })
    }
    private fun showDiagnostics() {
        val report=diagnostics.report()+"\n\n"+activity.getString(
            if(diagnostics.observed)R.string.study_pen_information_note else R.string.study_pen_information_empty)
        val text=TextView(activity).apply {
            this.text=report;textSize=14f;setTextIsSelectable(true)
            setPadding(dp(24),dp(12),dp(24),dp(12))
        }
        MaterialAlertDialogBuilder(activity,R.style.StudyColorDialog)
            .setTitle(R.string.study_pen_information)
            .setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton(android.R.string.ok,null)
            .setNeutralButton(R.string.study_copy_report){_,_->
                val clipboard=activity.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(activity.getString(R.string.study_pen_information),report))
            }.show()
    }
    private fun showOptions() {
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),0) }
        val field=TextInputLayout(activity).apply { hint=activity.getString(R.string.study_hex);boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val hex=TextInputEditText(activity).apply { isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(7));setText(HighlightStyle(color and 0xFFFFFF).hex) }
        field.addView(hex);body.addView(field)
        val label=TextView(activity).apply { text=activity.getString(R.string.study_nib_width,nib);setPadding(0,dp(16),0,0) };body.addView(label)
        val width=Slider(activity).apply { valueFrom=.5f;valueTo=12f;stepSize=.5f;value=(nib*2).roundToInt().coerceIn(1,24)/2f;contentDescription=activity.getString(R.string.study_ink_options) }
        width.addOnChangeListener { _,value,_->label.text=activity.getString(R.string.study_nib_width,value) };body.addView(width)
        val opacityLabel=TextView(activity).apply {
            text=activity.getString(R.string.study_marker_opacity,markerOpacity)
        };body.addView(opacityLabel)
        val opacity=Slider(activity).apply {
            valueFrom=1f;valueTo=100f;stepSize=1f;value=markerOpacity.toFloat()
            contentDescription=activity.getString(R.string.study_marker_opacity,markerOpacity)
            addOnChangeListener { _,value,_->opacityLabel.text=activity.getString(R.string.study_marker_opacity,value.roundToInt()) }
        };body.addView(opacity)
        val pressure=MaterialCheckBox(activity).apply { text=activity.getString(R.string.study_pressure);isChecked=usePressure };body.addView(pressure)
        body.addView(MaterialButton(activity).apply {
            text=activity.getString(R.string.study_pen_information);isAllCaps=false;minHeight=dp(48)
            setOnClickListener { showDiagnostics() }
        })
        val palette=LinearLayout(activity)
        listOf(0x263E4D,0x4968B2,0xD76659,0x3C846A,0xC7A12E).forEach { rgb ->
            palette.addView(MaterialButton(activity).apply {
                minWidth=dp(48);minimumWidth=dp(48);minHeight=dp(48);insetTop=dp(8);insetBottom=dp(8);cornerRadius=dp(16)
                backgroundTintList=ColorStateList.valueOf(rgb or 0xFF000000.toInt());contentDescription=HighlightStyle(rgb).hex
                setOnClickListener { hex.setText(HighlightStyle(rgb).hex) }
            },LinearLayout.LayoutParams(0,dp(48),1f))
        };body.addView(palette)
        val dialog=MaterialAlertDialogBuilder(activity,R.style.StudyColorDialog).setTitle(R.string.study_ink_options).setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.apply,null).show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val rgb=HighlightStyle.parseHex(hex.text.toString())
            if(rgb==null){field.error=activity.getString(R.string.study_invalid_hex);return@setOnClickListener}
            color=rgb or 0xFF000000.toInt();nib=width.value;usePressure=pressure.isChecked;markerOpacity=opacity.value.roundToInt()
            preferences.edit().putInt("study.ink.color",color).putInt("study.ink.markerOpacity",markerOpacity).putFloat("study.ink.width",nib).putBoolean("study.ink.pressure",usePressure).apply()
            dialog.dismiss()
        }
    }
}
