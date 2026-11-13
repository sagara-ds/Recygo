package id.recygo.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.caverock.androidsvg.SVG
import java.text.NumberFormat
import java.util.Locale

class Ui(val context: Context) {
    // The approved Figma is light. Do not invent a dark identity from device settings.
    private val designScale=(context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density / 402f).coerceIn(0.8f,1.5f)
    val surface=Color.parseColor("#FFFFFF")
    val ink=Color.parseColor("#111827")
    val muted=Color.parseColor("#6B7280")
    val outline=Color.parseColor("#E5E7EB")
    val pale=Color.parseColor("#E3F7EE")
    val green=Color.parseColor("#16C784")
    val cta=Color.parseColor("#16C47F")
    val greenText=Color.parseColor("#16A66A")
    val error=Color.parseColor("#B42318")
    private val font by lazy { runCatching { Typeface.createFromAsset(context.assets,"fonts/InterVariable.ttf") }.getOrElse { Typeface.create("sans-serif",Typeface.NORMAL) } }
    fun dp(value: Int)=(value*designScale*context.resources.displayMetrics.density+0.5f).toInt()
    fun sp(value: Int)=value*designScale
    fun typeface(weight: Int)=if(android.os.Build.VERSION.SDK_INT>=28)Typeface.create(font,weight,false) else Typeface.create(font,if(weight>=600)Typeface.BOLD else Typeface.NORMAL)
    fun lp(w: Int=-1,h: Int=-2)=LinearLayout.LayoutParams(if(w<0)w else dp(w),if(h<0)h else dp(h))
    fun box(color: Int=surface,radius: Int=18,border: Boolean=false)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat(); if(border) setStroke(dp(1),outline)
    }
    fun column(padding: Int=0)=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(padding),dp(padding),dp(padding),dp(padding)); layoutParams=lp() }
    fun row()=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; layoutParams=lp() }
    fun text(value: String,size: Int=16,bold: Boolean=false,color: Int=ink,weight: Int=if(bold)700 else 400)=TextView(context).apply {
        text=value; textSize=sp(size); setTextColor(color)
        typeface=typeface(weight)
        includeFontPadding=false; setLineSpacing(dp(3).toFloat(),1f); layoutParams=lp(); textDirection=View.TEXT_DIRECTION_LOCALE
    }
    fun button(label: String,primary: Boolean=true,action: () -> Unit)=Button(context).apply {
        text=label; isAllCaps=false; textSize=sp(20); typeface=Typeface.create(font,Typeface.BOLD)
        setTextColor(if(primary) Color.WHITE else greenText)
        background=box(if(primary)cta else pale,32)
        backgroundTintList=null; minHeight=dp(64); minimumHeight=dp(64)
        setPadding(dp(16),dp(12),dp(16),dp(12)); layoutParams=lp(); setOnClickListener { action() }
        stateListAnimator=null
    }
    fun field(hintText: String,input: Int=android.text.InputType.TYPE_CLASS_TEXT)=EditText(context).apply {
        hint=hintText; inputType=input; textSize=sp(16); typeface=font; setTextColor(ink); setHintTextColor(muted)
        background=box(surface,16,true); backgroundTintList=null; setPadding(dp(16),dp(14),dp(16),dp(14))
        minHeight=dp(56); layoutParams=lp(); importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
    fun icon(screen: String,name: String,width: Int=24,height: Int=width)=ImageView(context).apply {
        val file="figma/${screen.replace(':','_')}_${name}.svg"
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        context.assets.open(file).use { setImageDrawable(PictureDrawable(SVG.getFromInputStream(it).renderToPicture())) }
        scaleType=ImageView.ScaleType.FIT_CENTER; layoutParams=lp(width,height)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun gap(parent: LinearLayout,size: Int=16) { parent.addView(View(context),lp(1,size)) }
    fun divider(parent: LinearLayout) { parent.addView(View(context).apply { setBackgroundColor(outline) },lp(-1,1)) }
    fun card(padding: Int=16)=column(padding).apply { background=box(surface,24,true) }
    fun add(parent: LinearLayout,view: View,weight: Float=0f) {
        parent.addView(view,if(weight>0)LinearLayout.LayoutParams(0,-2,weight) else view.layoutParams?:lp())
    }
    fun number(value: Int)=NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(value)
    fun kg(grams: Int)=NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply { maximumFractionDigits=3 }.format(grams/1000.0)+" kg"
}
