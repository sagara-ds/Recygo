package id.recygo.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

/** Native reference-sized account surface; the parent scrolls when the viewport is smaller. */
internal class AuthCanvas(private val activity: Activity,val ui: Ui,parent: LinearLayout) {
    val largeText=activity.resources.configuration.fontScale>1.1f
    val frame=FrameLayout(activity).apply { minimumHeight=if(largeText)0 else ui.dp(793); clipChildren=true }
    private val flow=if(largeText)ui.column(24) else null
    init {
        parent.setPadding(0,0,0,0)
        parent.addView(frame,LinearLayout.LayoutParams(-1,if(largeText)-2 else -1))
        flow?.let { frame.addView(it,FrameLayout.LayoutParams(-1,-2)) }
    }
    fun place(view: View,x: Int,y: Int,width: Int,height: Int=-2): View {
        if(flow!=null) {
            if(view.isClickable)view.minimumHeight=maxOf(view.minimumHeight,ui.dp(48))
            flow.addView(view,LinearLayout.LayoutParams(if(view is ImageView && width>0)ui.dp(width) else -1,if(view is ImageView && height>0)ui.dp(height) else -2).apply { bottomMargin=ui.dp(12); gravity=Gravity.CENTER_HORIZONTAL })
        } else frame.addView(view,FrameLayout.LayoutParams(if(width<0)width else ui.dp(width),if(height<0)height else ui.dp(height)).apply { marginStart=ui.dp(x); topMargin=ui.dp(y) })
        return view
    }
    fun text(value: String,x: Int,y: Int,width: Int,size: Int=16,weight: Int=400,color: Int=ui.ink,center: Boolean=false): TextView {
        val view=ui.text(value,size,weight>=600,color,weight).apply { if(center)gravity=Gravity.CENTER }
        place(view,x,y,width); return view
    }
    fun image(screen: String,name: String,x: Int,y: Int,width: Int,height: Int,rotation: Float=0f): ImageView {
        val view=asset(screen,name,width,height).apply { this.rotation=rotation }
        // Decorative CTA arrows are redundant in the enlarged, flowing layout.
        if(!largeText || !(height<=30 && (name.contains("Arrow") || name=="imgVector")))place(view,x,y,width,height)
        return view
    }
    fun asset(screen: String,name: String,width: Int,height: Int): ImageView {
        val prefix="figma/${screen.replace(':','_')}_$name"
        val png=runCatching { activity.assets.open("$prefix.png") }.getOrNull()
        return if(png!=null)ImageView(activity).apply {
            png.use { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(it,null)) }
            scaleType=ImageView.ScaleType.FIT_CENTER
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } else ui.icon(screen,name,width,height)
    }
    fun footer(screen: String,name: String,width: Int,height: Int) {
        if(largeText)return
        frame.addView(asset(screen,name,width,height),FrameLayout.LayoutParams(ui.dp(width),ui.dp(height),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }
    fun back(action: ()->Unit,registration: Boolean=false) {
        val control=FrameLayout(activity).apply { contentDescription="Kembali"; isClickable=true; isFocusable=true; setOnClickListener { action() } }
        control.addView(ui.icon("2003:681","imgAtIconsArrowLeft2",28,24),FrameLayout.LayoutParams(ui.dp(28),ui.dp(24),Gravity.START or if(registration)Gravity.TOP else Gravity.CENTER_VERTICAL).apply { if(registration)marginStart=ui.dp(9) })
        place(control,18,0,48,48)
    }
    fun button(value: String,x: Int,y: Int,width: Int=340,height: Int=66,primary: Boolean=true,action: ()->Unit): Button {
        val button=ui.button(value,primary,action).apply { minHeight=ui.dp(height); minimumHeight=ui.dp(height) }
        place(button,x,y,width,height); return button
    }
    fun link(value: String,x: Int,y: Int,width: Int,action: ()->Unit): TextView {
        val link=ui.text(value,16,true,ui.greenText,600).apply {
            gravity=Gravity.CENTER; minHeight=ui.dp(48); isClickable=true; isFocusable=true; setOnClickListener { action() }
        }
        place(link,x,y,width,48); return link
    }
    fun field(label: String,value: String,hint: String,x: Int,y: Int,width: Int=342,password: Boolean=false,input: Int=InputType.TYPE_CLASS_TEXT,height: Int=48,recovery: Boolean=false): EditText {
        text(label,x+8,y-26,width-8,15,if(recovery)500 else 600,if(recovery)Color.BLACK else ui.muted)
        val field=ui.field(hint,if(password)InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else input).apply {
            setText(value); textSize=ui.sp(15); contentDescription=label; setSingleLine()
            // setSingleLine installs its own transformation; restore masking last.
            if(password)transformationMethod=android.text.method.PasswordTransformationMethod.getInstance()
            minHeight=ui.dp(height); minimumHeight=ui.dp(height); background=ui.box(ui.surface,if(recovery)18 else 15,true)
            setPadding(ui.dp(if(password)42 else 16),0,ui.dp(if(password)48 else 12),0)
        }
        val wrapper=if(largeText)FrameLayout(activity).apply { minimumHeight=ui.dp(maxOf(height,56)) } else null
        if(wrapper!=null) {
            wrapper.addView(field,FrameLayout.LayoutParams(-1,ui.dp(maxOf(height,56))))
            place(wrapper,x,y,width)
        } else place(field,x,y,width,height)
        if(password) {
            if(wrapper!=null)wrapper.addView(asset("2003:549","imgUnlock1",19,19),FrameLayout.LayoutParams(ui.dp(19),ui.dp(19),Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart=ui.dp(12) })
            else image("2003:549","imgUnlock1",x+12,y+(height-19)/2,19,19)
            val eye=FrameLayout(activity).apply { contentDescription="Tampilkan atau sembunyikan $label"; isClickable=true; isFocusable=true }
            eye.addView(asset("2003:549","imgView1",24,24),FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            var visible=false
            eye.setOnClickListener { visible=!visible; field.transformationMethod=if(visible)null else android.text.method.PasswordTransformationMethod.getInstance(); field.setSelection(field.length()) }
            if(wrapper!=null)wrapper.addView(eye,FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.END or Gravity.CENTER_VERTICAL))
            else place(eye,x+width-48,y+(height-48)/2,48,48)
        }
        return field
    }
    fun pill(value: String,x: Int,y: Int,width: Int) {
        place(ui.text(value,14,true,ui.cta,800).apply { gravity=Gravity.CENTER; background=ui.box(Color.parseColor("#E5F8F0"),15); if(largeText)setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)) },x,y,width,32)
    }
    fun progress(step: Int,total: Int,role: String) {
        if(largeText) {
            text("Langkah $step dari $total",0,0,354,14,600,ui.cta)
            val bars=ui.row()
            repeat(total) { index -> bars.addView(View(activity).apply { background=ui.box(if(index<step)ui.cta else ui.muted,3) },LinearLayout.LayoutParams(0,ui.dp(3),1f).apply { if(index>0)marginStart=ui.dp(6) }) }
            place(bars,0,0,354)
            pill(if(role=="RESIDENT")"Daftar sebagai warga" else "Daftar sebagai pengepul",0,0,354)
            return
        }
        text("Langkah $step dari $total",34,28,170,14,600,ui.cta)
        text(if(role=="RESIDENT")"Alamat" else "Identitas",300,28,70,14,color=ui.muted)
        val gap=6; val width=(335-gap*(total-1))/total
        repeat(total) { index -> place(View(activity).apply { background=ui.box(if(index<step)ui.cta else ui.muted,3) },34+index*(width+gap),59,width,3) }
        pill(if(role=="RESIDENT")"Daftar sebagai warga" else "Daftar sebagai pengepul",33,80,if(role=="RESIDENT")169 else 196)
    }
    fun photo(bitmap: Bitmap?,x: Int,y: Int,size: Int=80) {
        val view=if(bitmap!=null)ImageView(activity).apply { setImageBitmap(bitmap); scaleType=ImageView.ScaleType.CENTER_CROP } else asset("2003:378","imgSampleUserIcon3",57,57).apply { setPadding(ui.dp(11),ui.dp(11),ui.dp(11),ui.dp(11)) }
        view.background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(ui.pale) }; view.clipToOutline=true
        place(view,x,y,size,size)
    }
}
