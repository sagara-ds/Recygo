package id.recygo.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity: Activity() {
    private lateinit var ui: Ui
    private lateinit var vault: SessionVault
    private lateinit var accountFlow: AccountFlow
    private var accountProfile=JSONObject()
    private val api=Api()
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var feedback: TextView
    private var user: User?=null
    private var materials=emptyList<Waste>()
    private var slots=emptyList<String>()
    private var orders=emptyList<Order>()
    private var page="home"
    private var selectedId=""
    private var busy=false
    private var generation=0
    private var draft=JSONObject()
    private var draftKey=UUID.randomUUID().toString()
    private val selected=linkedSetOf<String>()
    private val formFields=linkedMapOf<String,EditText>()
    private val skippedQueue=mutableSetOf<String>()
    private var profileSection="all"
    private var historyPeriod="today"
    private var weightDraft=JSONObject()
    private val pollHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var pollRunning=false
    private val pollIntervalMs=20_000L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ui=Ui(this); vault=SessionVault(this); api.token=vault.read()
        accountFlow=AccountFlow(this,ui,api)
        page=state?.getString("page")?:"home"; selectedId=state?.getString("order").orEmpty()
        draft=runCatching { JSONObject(state?.getString("draft")?:"{}") }.getOrDefault(JSONObject())
        draftKey=state?.getString("draftKey")?:UUID.randomUUID().toString()
        weightDraft=runCatching { JSONObject(state?.getString("weightDraft")?:"{}") }.getOrDefault(JSONObject())
        state?.getStringArrayList("selected")?.let(selected::addAll)
        if(Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { back() }
        fun resume() {
            if(api.token.isEmpty()) {
                val snapshot=state?.getString("accountFlow")
                if(snapshot!=null)accountFlow.restore(snapshot) else accountFlow.start()
            } else {
                shell(false); content.addView(ui.text("Menghubungkan ke Recygo…",20,true)); ui.gap(content,20)
                content.addView(ui.button("Coba hubungkan lagi",false) { loadSession() }); loadSession()
            }
        }
        if(state==null)accountFlow.splash(::resume) else resume()
    }

    private fun shell(nav: Boolean=true) {
        generation++; formFields.clear()
        root=ui.column().apply { setBackgroundColor(ui.surface); fitsSystemWindows=false }
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else {
                @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        @Suppress("DEPRECATION")
        val barVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=barVisibility
        @Suppress("DEPRECATION")
        window.statusBarColor=Color.WHITE
        @Suppress("DEPRECATION")
        window.navigationBarColor=Color.WHITE
        if(Build.VERSION.SDK_INT>=29)root.isForceDarkAllowed=false
        feedback=ui.text("",14,color=ui.error).apply {
            visibility=View.GONE; setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12)); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(feedback)
        val scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
        content=ui.column(24); scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(nav && user!=null) navigation()
        root.requestApplyInsets()
    }

    private fun navigation() {
        ui.divider(root)
        val row=ui.row().apply { setPadding(ui.dp(4),ui.dp(6),ui.dp(4),ui.dp(6)) }
        val tabs=listOf("home" to "Beranda","orders" to "Pesanan","history" to "Riwayat","profile" to "Profil")
        tabs.forEach { (id,label) ->
            val active=page==id || (id=="orders" && page in listOf("detail","arrival","weigh","summary"))
            val button=ui.button(label,false) { go(id,true) }.apply {
                textSize=ui.sp(15); setPadding(0,ui.dp(12),0,ui.dp(12)); background=ui.box(ui.surface,0)
                typeface=ui.typeface(600)
                minHeight=ui.dp(48); minimumHeight=ui.dp(48)
                setTextColor(if(active)ui.greenText else ui.muted); isSelected=active
            }
            row.addView(button,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(row)
    }

    private fun title(value: String,back: Boolean=false) {
        val row=ui.row()
        if(back) {
            val control=FrameLayout(this).apply {
                contentDescription="Kembali"; isClickable=true; isFocusable=true
                setOnClickListener { this@MainActivity.back() }
            }
            control.addView(ui.icon("2003:681","imgAtIconsArrowLeft2",28,24),FrameLayout.LayoutParams(ui.dp(28),ui.dp(24),Gravity.START or Gravity.CENTER_VERTICAL))
            row.addView(control,ui.lp(48,48))
        }
        row.addView(ui.text(value,26,true),LinearLayout.LayoutParams(0,-2,1f))
        content.addView(row); ui.gap(content,20)
    }

    private fun notice(value: String) {
        content.addView(ui.text(value,13,color=ui.muted).apply { setPadding(0,ui.dp(8),0,ui.dp(8)) })
    }
    private fun error(message: String) { feedback.text=message; feedback.visibility=View.VISIBLE }
    private fun go(destination: String,refresh: Boolean=false) {
        captureDraft(); page=destination; render()
        if(refresh) refresh()
    }
    private fun render() {
        shell(page !in setOf("select","request","guide","edit-profile"))
        when(page) {
            "home" -> home(); "orders" -> orderList(false); "history" -> orderList(true)
            "select" -> selection(); "request" -> requestForm(); "detail" -> detail()
            "weigh" -> weigh(); "profile" -> profile(); "guide" -> guide()
            "edit-profile" -> editProfile()
            "arrival" -> arrival(); "summary" -> transactionSummary()
            else -> { page="home"; home() }
        }
    }

    private fun <T> perform(button: View?=null,work: ()->T,success: (T)->Unit) {
        if(busy)return
        busy=true; button?.isEnabled=false
        feedback.text="Memproses…"; feedback.setTextColor(ui.muted); feedback.visibility=View.VISIBLE
        val current=generation
        worker.execute {
            val result=runCatching(work)
            runOnUiThread {
                if(isDestroyed || isFinishing)return@runOnUiThread
                busy=false; button?.isEnabled=true
                if(generation!=current) { if(user!=null)refresh(); return@runOnUiThread }
                feedback.visibility=View.GONE; feedback.setTextColor(ui.error)
                result.fold(success) { ex ->
                    if(ex is ApiFailure && ex.status==401 && api.token.isNotEmpty()) {
                        vault.clear(); api.token=""; user=null; login(); error("Sesi berakhir. Silakan masuk lagi.")
                    } else error(if(ex is ApiFailure)ex.message else "Tidak dapat terhubung. Periksa koneksi dan pastikan server demo aktif. Coba lagi.")
                }
            }
        }
    }

    private fun loadSession()=perform(work={
        val me=User.from(api.request("/api/me").getJSONObject("user"))
        val cat=api.request("/api/catalog")
        val list=api.request("/api/orders").getJSONArray("orders")
        val profile=api.request("/api/profile").getJSONObject("profile")
        Bootstrap(me,cat,list,profile)
    }) { (me,cat,list,profile) ->
        user=me; accountProfile=profile
        val m=cat.getJSONArray("materials")
        materials=List(m.length()) { i -> m.getJSONObject(i).let { Waste(it.getString("id"),it.getString("name"),it.getInt("pointsPerKg"),it.getString("guide")) } }
        val s=cat.getJSONArray("slots"); slots=List(s.length()) { s.getString(it) }
        orders=parseOrders(list); render()
        if(me.collector)startPolling()
    }
    private fun parseOrders(a: JSONArray)=List(a.length()) { Order.from(a.getJSONObject(it)) }
    private fun refresh() {
        if(user==null || busy)return
        perform(work={parseOrders(api.request("/api/orders").getJSONArray("orders"))}) { updated -> notifyChanges(orders,updated); orders=updated; render() }
    }

    private fun startPolling() {
        if(pollRunning)return
        pollRunning=true
        pollHandler.postDelayed(pollTick,pollIntervalMs)
    }
    private fun stopPolling() {
        pollRunning=false
        pollHandler.removeCallbacks(pollTick)
    }
    private val pollTick=object: Runnable {
        override fun run() {
            if(pollRunning && user?.collector==true && page=="home" && !busy)silentPoll()
            if(pollRunning)pollHandler.postDelayed(this,pollIntervalMs)
        }
    }
    private fun silentPoll() {
        val previous=orders; val gen=generation
        worker.execute {
            val result=runCatching { parseOrders(api.request("/api/orders").getJSONArray("orders")) }
            runOnUiThread {
                if(isDestroyed || isFinishing || generation!=gen)return@runOnUiThread
                if(!pollRunning || busy || user?.collector!=true || page!="home")return@runOnUiThread
                result.getOrNull()?.let { updated ->
                    if(orderKeys(previous)!=orderKeys(updated)) {
                        val fresh=updated.count { order -> order.status=="REQUESTED" && previous.none { it.id==order.id } }
                        if(fresh>0)Toast.makeText(this,"$fresh pesanan baru masuk dari warga",Toast.LENGTH_SHORT).show()
                        orders=updated; render()
                    }
                }
            }
        }
    }
    private fun orderKeys(list: List<Order>)=list.map { "${it.id}:${it.status}:${it.updatedAt}" }.toSet()

    private fun login() {
        accountFlow.showFind()
    }

    internal fun accountCanvas(): AuthCanvas { shell(false); return AuthCanvas(this,ui,content) }
    internal fun accountError(value: String)=error(value)
    internal fun accountRequest(button: View?,work: ()->JSONObject,done: (JSONObject)->Unit)=perform(button,work,done)
    internal fun accountBarColor(color: Int) {
        @Suppress("DEPRECATION")
        window.statusBarColor=color
        @Suppress("DEPRECATION")
        window.navigationBarColor=color
    }
    internal fun completeAccountAuth(result: JSONObject) {
        val token=result.getString("token")
        try { vault.save(token) } catch (_: Exception) { error("Sesi tidak dapat disimpan dengan aman. Coba lagi."); return }
        api.token=token; page="home"; loadSession()
    }
    internal fun prepareNotificationChannel() {
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(android.app.NotificationChannel("orders","Status pesanan Recygo",android.app.NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun notifyChanges(previous: List<Order>,updated: List<Order>) {
        if(user?.collector==true)return
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        prepareNotificationChannel()
        val manager=getSystemService(android.app.NotificationManager::class.java)
        updated.filter { current -> previous.any { it.id==current.id && it.status!=current.status } }.forEach { current ->
            val intent=android.app.PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            manager.notify(current.id.hashCode(),android.app.Notification.Builder(this,"orders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Pesanan #${current.shortId}").setContentText(statusLabel(current.status)).setContentIntent(intent).setAutoCancel(true).setVisibility(android.app.Notification.VISIBILITY_PRIVATE).build())
        }
    }

    private data class Bootstrap(val user: User,val catalog: JSONObject,val orders: JSONArray,val profile: JSONObject)

    private fun home() {
        val account=user?:return
        if(account.collector) { collectorHome(account); return }
        val outer=content
        outer.setPadding(0,0,0,0)
        val sourceHeaderGreen=Color.rgb(45,204,144)
        @Suppress("DEPRECATION")
        window.statusBarColor=sourceHeaderGreen
        val header=FrameLayout(this).apply {
            background=ui.box(sourceHeaderGreen,40).apply { cornerRadii=floatArrayOf(0f,0f,0f,0f,ui.dp(40).toFloat(),ui.dp(40).toFloat(),ui.dp(40).toFloat(),ui.dp(40).toFloat()) }
            clipChildren=true; clipToOutline=true
        }
        header.addView(ui.icon("2003:631","imgEllipse10",209,204),FrameLayout.LayoutParams(ui.dp(209),ui.dp(204),Gravity.END or Gravity.TOP).apply { topMargin=ui.dp(-35); marginEnd=ui.dp(-35) })
        header.addView(ui.icon("2003:631","imgEllipse8",132,147),FrameLayout.LayoutParams(ui.dp(132),ui.dp(147),Gravity.START or Gravity.BOTTOM).apply { marginStart=ui.dp(-38) })
        val headerContent=ui.column().apply { setPadding(ui.dp(35),ui.dp(6),ui.dp(27),0) }
        val greeting=ui.row()
        val headerInk=Color.WHITE
        val texts=ui.column().apply { setPadding(ui.dp(14),0,0,0) }; texts.addView(ui.text("Selamat pagi",15,true,headerInk,600)); ui.gap(texts,4); texts.addView(ui.text("Hi, ${account.name.substringBefore(' ')}",20,true,headerInk))
        greeting.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
        val bell=FrameLayout(this).apply { isClickable=true; isFocusable=true; contentDescription="Lihat pembaruan pesanan"; setOnClickListener { go("orders",true) } }
        bell.addView(ui.icon("2003:631","imgEllipse11",48,48),FrameLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        bell.addView(ui.icon("2003:631","imgGroup93",19,20),FrameLayout.LayoutParams(ui.dp(19),ui.dp(20),Gravity.CENTER))
        greeting.addView(bell,ui.lp(48,48).apply { marginEnd=ui.dp(10) })
        greeting.addView(avatar(account.name)); headerContent.addView(greeting); ui.gap(headerContent,18)
        val completed=orders.filter { it.status=="COMPLETED" }
        val total=completed.sumOf { it.points }
        val panel=ui.column().apply { background=ui.box(ui.surface,18); setPadding(ui.dp(10),ui.dp(16),ui.dp(12),ui.dp(14)); minimumHeight=ui.dp(126) }
        val pointRow=ui.row()
        val pointLabels=ui.column(); pointLabels.addView(ui.text(if(account.collector)"Penjemputan selesai" else "Poin kamu",16)); pointLabels.addView(ui.text(if(account.collector)ui.number(completed.size) else ui.number(total),32,true))
        pointRow.addView(pointLabels,LinearLayout.LayoutParams(0,-2,1f))
        val redeem=FrameLayout(this).apply { contentDescription="Tentang poin demo"; isClickable=true; isFocusable=true; setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("Poin demonstrasi").setMessage("Poin ini dihitung dari penimbangan yang dikonfirmasi warga. Belum dapat ditukar dengan uang atau hadiah.").setPositiveButton("Mengerti",null).show()
        } }
        val enlarged=resources.configuration.fontScale>1.1f
        redeem.addView(ui.text("Tukar poin",13,color=Color.WHITE).apply { gravity=Gravity.CENTER; background=ui.box(ui.green,15); setPadding(0,0,if(enlarged)0 else ui.dp(14),0) },FrameLayout.LayoutParams(ui.dp(102),ui.dp(29),Gravity.CENTER))
        if(!enlarged)redeem.addView(ui.icon("2003:631","imgArrow1",14,14),FrameLayout.LayoutParams(ui.dp(14),ui.dp(14),Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd=ui.dp(8) })
        pointRow.addView(redeem,ui.lp(102,48)); panel.addView(pointRow); ui.gap(panel,14)
        panel.addView(View(this).apply { background=ui.box(ui.pale,5); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },ui.lp(-1,9))
        headerContent.addView(panel); ui.gap(headerContent,72); header.addView(headerContent)
        outer.addView(header,ui.lp())
        val actions=ui.row()
        val actionData=listOf(Triple("Setor sampah","imgSetorSampah1","select"))
        actionData.forEachIndexed { i,(label,asset,target) ->
            val action=ui.card(12).apply { background=ui.box(ui.surface,18,true); minimumHeight=ui.dp(72); orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(ui.dp(24),0,ui.dp(24),0); isClickable=true; isFocusable=true; contentDescription=label.replace('\n',' '); setOnClickListener { go(target,true) } }
            val actionIcon=FrameLayout(this)
            actionIcon.addView(ui.icon("2003:631",listOf("imgGroup180","imgGroup181","imgGroup182")[i],38,39),FrameLayout.LayoutParams(ui.dp(38),ui.dp(39)))
            actionIcon.addView(ui.icon("2003:631",asset,22,22),FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER))
            action.addView(actionIcon,ui.lp(38,39)); ui.gap(action,14); action.addView(ui.text(label,15,true,ui.greenText,600))
            actions.addView(action,LinearLayout.LayoutParams(0,-2,1f).apply { if(i>0)marginStart=ui.dp(25) })
        }
        outer.addView(actions,ui.lp().apply { marginStart=ui.dp(35); marginEnd=ui.dp(32); topMargin=ui.dp(-44) })
        content=ui.column().apply { setPadding(ui.dp(32),ui.dp(20),ui.dp(32),ui.dp(24)) }; outer.addView(content)
        val sectionHeader=ui.row(); sectionHeader.addView(ui.text(if(account.collector)"Pesanan terbaru" else "Pesanan aktif",20,true,weight=600),LinearLayout.LayoutParams(0,-2,1f))
        sectionHeader.addView(ui.button("Lihat",false) { go("orders",true) }.apply { textSize=ui.sp(16); typeface=ui.typeface(600); minHeight=ui.dp(48); minimumHeight=ui.dp(48); setPadding(0,0,0,0); isSingleLine=true; background=ui.box(ui.surface,0) },ui.lp(64)); content.addView(sectionHeader); ui.gap(content,4)
        val active=orders.filterNot { it.complete }
        if(active.isEmpty()) empty("Belum ada pesanan",if(account.collector)"Permintaan warga akan muncul di sini." else "Mulai dengan memilih sampah yang ingin dijemput.")
        else active.take(2).forEach { orderCard(it) }
        ui.gap(content,24)
        content.addView(ui.text("Info terbaru",20,true)); ui.gap(content,16)
        val info=ui.card().apply { isClickable=true; isFocusable=true; setOnClickListener { go("guide") }; contentDescription="Buka panduan pemilahan" }
        val row=ui.row()
        val infoIcon=FrameLayout(this)
        infoIcon.addView(ui.icon("2003:631","imgGroup185",39,39),FrameLayout.LayoutParams(ui.dp(39),ui.dp(39)))
        infoIcon.addView(ui.icon("2003:631","imgBankSampah1"),FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
        row.addView(infoIcon,ui.lp(39,39)); val copy=ui.column().apply { setPadding(ui.dp(10),0,0,0) }
        copy.addView(ui.text("Program Bank Sampah",16,true)); copy.addView(ui.text("Panduan pilah dan setor sampah",13,color=ui.muted)); row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },ui.lp(24,24))
        info.addView(row); content.addView(info)
        notice("Layanan demonstrasi · Belum ada mitra penjemputan nyata.")
    }

    private fun collectorHome(account: User) {
        content.setPadding(ui.dp(20),ui.dp(14),ui.dp(24),ui.dp(20))
        val greeting=ui.row(); greeting.addView(avatar(account.name))
        val label=ui.column().apply { setPadding(ui.dp(8),0,0,0) }
        label.addView(ui.text("Selamat datang",14,color=ui.muted)); ui.gap(label,4); label.addView(ui.text(account.name,18,true))
        greeting.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        val bell=FrameLayout(this).apply {
            contentDescription="Lihat pembaruan pesanan"; isClickable=true; isFocusable=true
            setOnClickListener { go("orders",true) }
        }
        bell.addView(ui.icon("2003:631","imgEllipse11",48,48),FrameLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        bell.addView(ui.icon("2003:631","imgGroup93",19,20),FrameLayout.LayoutParams(ui.dp(19),ui.dp(20),Gravity.CENTER))
        val showNew=getSharedPreferences("collector",MODE_PRIVATE).getBoolean("showNew",true)
        val pending=orders.count { it.status=="REQUESTED" && it.id !in skippedQueue && showNew }
        if(pending>0) {
            val badge=ui.text(if(pending>9)"9+" else pending.toString(),11,true,Color.WHITE).apply {
                gravity=Gravity.CENTER; background=ui.box(Color.rgb(219,21,20),24); setPadding(0,0,0,0)
                minimumWidth=ui.dp(18); minimumHeight=ui.dp(18)
            }
            bell.addView(badge,FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.TOP).apply { marginEnd=ui.dp(2); topMargin=ui.dp(2) })
        }
        greeting.addView(bell,ui.lp(48,48)); content.addView(greeting); ui.gap(content,28)
        val done=orders.filter { it.status=="COMPLETED" }
        val panel=FrameLayout(this).apply { background=ui.box(Color.rgb(45,204,144),18) }
        fun inPanel(view: View,x: Int,y: Int,w: Int,h: Int=-2) { panel.addView(view,FrameLayout.LayoutParams(ui.dp(w),if(h<0)h else ui.dp(h)).apply { marginStart=ui.dp(x); topMargin=ui.dp(y) }) }
        inPanel(ui.icon("2060:547","imgEllipse12",79,83),276,0,79,83)
        inPanel(ui.text("Poin terkumpul · demo",16,color=Color.WHITE,weight=500),19,15,230)
        inPanel(ui.text(ui.number(done.sumOf { it.points }),32,true,Color.WHITE),19,34,230)
        val recorded=ui.column(); recorded.addView(ui.text("Total setoran tercatat",13,color=Color.WHITE))
        recorded.addView(ui.text(ui.kg(done.sumOf { o -> o.actual.sumOf { it.grams } }),13,true,Color.WHITE))
        inPanel(recorded,17,91,190)
        val pointInfo=FrameLayout(this).apply {
            contentDescription="Tentang poin demo"; isClickable=true; isFocusable=true
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Poin demonstrasi").setMessage("Poin bukan saldo uang dan belum dapat dicairkan. Berat berasal dari penimbangan yang dikonfirmasi warga.").setPositiveButton("Mengerti",null).show() }
        }
        pointInfo.addView(ui.text("Tentang poin",12,true,ui.greenText).apply { gravity=Gravity.CENTER; background=ui.box(Color.WHITE,24) },FrameLayout.LayoutParams(ui.dp(104),ui.dp(28),Gravity.CENTER))
        inPanel(pointInfo,230,84,104,48); content.addView(panel,ui.lp(-1,135)); ui.gap(content,28)
        val preferences=getSharedPreferences("collector",MODE_PRIVATE)
        val available=preferences.getBoolean("showNew",true)
        val availability=ui.card(12); val availabilityRow=ui.row()
        val status=ui.column(); status.addView(ui.text("Tampilkan pesanan baru",15,true)); status.addView(ui.text("Filter antrean pada perangkat ini",12,color=ui.muted))
        availabilityRow.addView(status,LinearLayout.LayoutParams(0,-2,1f))
        availabilityRow.addView(Switch(this).apply { isChecked=available; contentDescription="Tampilkan pesanan baru"; thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE); trackTintList=android.content.res.ColorStateList.valueOf(ui.cta); minHeight=ui.dp(48); setOnCheckedChangeListener { _,v -> preferences.edit().putBoolean("showNew",v).apply(); render() } },ui.lp(64,48))
        availability.addView(availabilityRow); content.addView(availability); ui.gap(content,28)
        content.addView(ui.text("Pesanan Terbaru",20,true,weight=600)); ui.gap(content,14)
        val queue=orders.filter { !it.complete && it.id !in skippedQueue && (available || it.status!="REQUESTED") }
        if(queue.isEmpty())empty("Belum ada pesanan baru","Muat ulang untuk melihat permintaan warga.") else queue.take(3).forEach { order ->
            val card=ui.card(14)
            val top=ui.row(); top.addView(avatar(order.ownerName)); val labels=ui.column().apply { setPadding(ui.dp(10),0,0,0) }
            labels.addView(ui.text(order.ownerName,13,true,weight=600)); labels.addView(ui.text(order.address,12,color=ui.muted).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }); top.addView(labels,LinearLayout.LayoutParams(0,-2,1f)); card.addView(top)
            ui.gap(card,12); card.addView(ui.text(order.items.joinToString(" · ") { "${material(it.materialId).name} ${ui.kg(it.grams)}" },13,true,ui.greenText,600)); ui.gap(card,14)
            if(order.status=="REQUESTED") {
                val buttons=ui.row()
                buttons.addView(ui.button("Lewati",false) { skippedQueue.add(order.id); render() }.apply { textSize=ui.sp(15); minHeight=ui.dp(48); minimumHeight=ui.dp(48) },LinearLayout.LayoutParams(0,ui.dp(48),1f))
                buttons.addView(ui.button("Terima pesanan") { selectedId=order.id; transition("accept") }.apply { textSize=ui.sp(15); minHeight=ui.dp(48); minimumHeight=ui.dp(48) },LinearLayout.LayoutParams(0,ui.dp(48),1.5f).apply { marginStart=ui.dp(12) }); card.addView(buttons)
            } else card.addView(ui.button(statusLabel(order.status)+" · Lihat",false) { selectedId=order.id; go("detail") }.apply { textSize=ui.sp(14) })
            content.addView(card); ui.gap(content,14)
        }
        content.addView(ui.button("Muat ulang",false) { skippedQueue.clear(); refresh() }); notice("Akun pengepul demo · Belum ada dispatch atau saldo uang nyata.")
    }

    private fun avatar(name: String): View {
        val encoded=if(name==user?.name)accountProfile.optString("avatarBase64") else ""
        if(encoded.isNotEmpty()) {
            val bitmap=runCatching { val bytes=android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP); android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size) }.getOrNull()
            if(bitmap!=null)return ImageView(this).apply { setImageBitmap(bitmap); scaleType=ImageView.ScaleType.CENTER_CROP; background=ui.box(ui.pale,40); clipToOutline=true; layoutParams=ui.lp(48,48); contentDescription="Foto profil" }
        }
        return ui.text(name.split(' ').take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),18,true,ui.greenText).apply { gravity=Gravity.CENTER; background=ui.box(ui.pale,40); layoutParams=ui.lp(48,48) }
    }
    private fun empty(heading: String,body: String) {
        val box=ui.card(20); box.addView(ui.text(heading,17,true)); ui.gap(box,8); box.addView(ui.text(body,14,color=ui.muted)); content.addView(box)
    }
    private fun orderList(history: Boolean) {
        if(history) { history(); return }
        title(if(user?.collector==true)"Pesanan masuk" else "Pesanan saya",true)
        val tabs=ui.row()
        tabs.addView(ui.button("Aktif",!history) { go("orders",true) },LinearLayout.LayoutParams(0,-2,1f))
        tabs.addView(ui.button("Riwayat",history) { go("history",true) },LinearLayout.LayoutParams(0,-2,1f))
        content.addView(tabs); ui.gap(content,20)
        val list=orders.filter { it.complete==history }
        if(list.isEmpty())empty(if(history)"Riwayat masih kosong" else "Belum ada pesanan aktif","Pesanan akan tampil di sini sesuai status terbarunya.")
        list.forEach(::orderCard)
        ui.gap(content,16); content.addView(ui.button("Muat ulang",false) { refresh() })
        if(!history && user?.collector==false) { ui.gap(content,12); content.addView(ui.button("Setor sampah") { go("select") }) }
    }
    private fun orderCard(order: Order) {
        val card=ui.card(18).apply { isClickable=true; isFocusable=true; contentDescription="Pesanan ${order.shortId}, ${statusLabel(order.status)}"; setOnClickListener { selectedId=order.id; go("detail") } }
        val line=ui.row(); line.addView(ui.text("#${order.shortId}",16,true),LinearLayout.LayoutParams(0,-2,1f))
        line.addView(ui.text(statusLabel(order.status),12,true,ui.greenText).apply { background=ui.box(ui.pale,16); setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(6)) },ui.lp())
        card.addView(line); ui.gap(card,16)
        if(user?.collector==false && !order.complete) { card.addView(orderTimeline(order)); ui.gap(card,20); ui.divider(card); ui.gap(card,16) }
        card.addView(ui.text(if(user?.collector==true)order.ownerName else order.collectorName.ifEmpty { "Menunggu pengepul" },17,true))
        ui.gap(card,6); card.addView(ui.text("${order.date} · ${order.slot}",13,color=ui.muted))
        ui.gap(card,10); card.addView(ui.text(order.items.joinToString(" · ") { "${material(it.materialId).name} ${ui.kg(it.grams)}" },14,color=ui.muted))
        if(order.status=="AWAITING_CONFIRMATION") { ui.gap(card,10); card.addView(ui.text("Berat aktual siap dikonfirmasi",14,true,ui.greenText)) }
        if(user?.collector==false) {
            val points=if(order.actual.isEmpty())order.items.sumOf { it.grams*material(it.materialId).rate/1000 } else order.points
            ui.gap(card,16)
            card.addView(ui.text("${if(order.actual.isEmpty())"Perkiraan poin" else "Poin demo"}   ${ui.number(points)}",14,true,ui.greenText).apply { background=ui.box(ui.pale,16); setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14)) })
        }
        content.addView(card); ui.gap(content,14)
    }
    private fun orderTimeline(order: Order): LinearLayout {
        val reached=when(order.status) { "SCHEDULED"->0; "ON_THE_WAY","ARRIVED"->1; "AWAITING_CONFIRMATION"->2; "COMPLETED"->3; else->-1 }
        val row=ui.row()
        listOf("Dijadwalkan","Dijemput","Ditimbang","Selesai").forEachIndexed { index,label ->
            val item=ui.column().apply { gravity=Gravity.CENTER; contentDescription="$label, ${if(index<=reached)"tercapai" else "belum tercapai"}" }
            val dot=FrameLayout(this).apply { background=ui.box(if(index<=reached)ui.cta else ui.pale,20) }
            if(index<=reached)dot.addView(ImageView(this).apply { setImageResource(R.drawable.ic_check) },FrameLayout.LayoutParams(ui.dp(15),ui.dp(15),Gravity.CENTER))
            item.addView(dot,ui.lp(24,24)); ui.gap(item,6)
            item.addView(ui.text(label,10,index<=reached,if(index<=reached)ui.greenText else ui.muted).apply { gravity=Gravity.CENTER })
            row.addView(item,LinearLayout.LayoutParams(0,-2,1f))
        }
        return row
    }

    private fun historyDate(order: Order)=runCatching { java.time.Instant.parse(order.updatedAt).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate() }.getOrElse { LocalDate.parse(order.date) }
    private fun history() {
        title("Riwayat pesanan",true)
        val today=LocalDate.now(ZoneId.of("Asia/Jakarta"))
        val from=when(historyPeriod) { "today"->today; "week"->today.with(java.time.DayOfWeek.MONDAY); "month"->today.withDayOfMonth(1); else->LocalDate.MIN }
        val list=orders.filter { it.complete && !historyDate(it).isBefore(from) }.sortedByDescending { it.updatedAt }
        val done=list.filter { it.status=="COMPLETED" }
        val stats=ui.row().apply { background=ui.box(ui.surface,20,true); setPadding(ui.dp(8),ui.dp(20),ui.dp(8),ui.dp(20)) }
        listOf(list.size.toString() to "Pesanan",ui.kg(done.sumOf { o->o.actual.sumOf { it.grams } }) to "Total berat",ui.number(done.sumOf { it.points }) to if(user?.collector==true)"Poin warga" else "Poin demo").forEachIndexed { index,(value,label) ->
            if(index>0)stats.addView(View(this).apply { setBackgroundColor(ui.outline) },ui.lp(1,48))
            val column=ui.column().apply { gravity=Gravity.CENTER }
            column.addView(ui.text(value,18,true).apply { gravity=Gravity.CENTER }); ui.gap(column,6)
            column.addView(ui.text(label,11,color=ui.muted).apply { gravity=Gravity.CENTER }); stats.addView(column,LinearLayout.LayoutParams(0,-2,1f))
        }
        content.addView(stats); ui.gap(content,20)
        val filters=ui.row().apply { background=ui.box(ui.pale,24) }
        listOf("today" to "Hari ini","week" to "Minggu ini","month" to "Bulan ini").forEach { (period,label) ->
            filters.addView(ui.button(label,historyPeriod==period) { historyPeriod=period; render() }.apply { isSelected=historyPeriod==period; textSize=ui.sp(13); minHeight=ui.dp(48); minimumHeight=ui.dp(48); setPadding(0,0,0,0) },LinearLayout.LayoutParams(0,ui.dp(48),1f))
        }
        content.addView(filters); ui.gap(content,24)
        if(list.isEmpty())empty("Belum ada riwayat pada periode ini","Pesanan selesai, ditolak, atau dibatalkan akan tampil di sini.")
        list.groupBy { historyDate(it) }.forEach { (date,entries) ->
            content.addView(ui.text(if(date==today)"Hari ini" else if(date==today.minusDays(1))"Kemarin" else date.toString(),13,true,ui.muted)); ui.gap(content,12)
            entries.forEach { order ->
                val row=ui.row().apply { background=ui.box(ui.surface,20,true); minimumHeight=ui.dp(81); setPadding(ui.dp(16),ui.dp(12),ui.dp(12),ui.dp(12)); isClickable=true; isFocusable=true; contentDescription="Riwayat ${order.shortId}, ${statusLabel(order.status)}"; setOnClickListener { selectedId=order.id; go(if(order.status=="COMPLETED")"summary" else "detail") } }
                val name=if(user?.collector==true)order.ownerName else order.collectorName.ifBlank { "Pesanan ${order.shortId}" }
                row.addView(avatar(name),ui.lp(48,48))
                val labels=ui.column().apply { setPadding(ui.dp(14),0,ui.dp(6),0) }
                labels.addView(ui.text(name,16,true)); ui.gap(labels,4)
                val time=runCatching { java.time.Instant.parse(order.updatedAt).atZone(ZoneId.of("Asia/Jakarta")).format(java.time.format.DateTimeFormatter.ofPattern("HH.mm")) }.getOrDefault("")
                labels.addView(ui.text(time,13,color=ui.muted)); row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(ui.text(statusLabel(order.status),12,true,ui.greenText).apply { background=ui.box(ui.pale,16); setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8)) },ui.lp(-2,-2))
                content.addView(row); ui.gap(content,14)
            }
        }
        ui.gap(content,12)
        content.addView(ui.button("Semua riwayat",false) { historyPeriod="all"; render() })
        ui.gap(content,12); content.addView(ui.button("Muat ulang",false) { refresh() })
        notice("Waktu pembaruan terakhir dalam WIB. Poin berasal dari pesanan selesai dan tidak memiliki nilai uang.")
    }
    private fun material(id: String)=materials.first { it.id==id }
    private fun materialIcon(id: String)=when(id) { "plastic"->"imgBotolPlastik1"; "paper"->"imgKertas1"; "glass"->"imgKaca1"; else->"imgLogam1" }

    private fun selection() {
        content.clipToPadding=false
        content.clipChildren=false
        content.setPadding(ui.dp(29),0,ui.dp(31),ui.dp(24))
        title("Setor sampah",true)
        (content.getChildAt(0).layoutParams as LinearLayout.LayoutParams).marginStart=ui.dp(-18)
        content.addView(ui.text("Pilih jenis sampah yang ingin kamu\nsetorkan",15,color=ui.muted)); ui.gap(content,32)
        (materials+Waste("other","Lainnya",0,"Belum tersedia di demo")).forEach { waste ->
            val enabled=waste.id!="other"
            val card=ui.card(12).apply { minimumHeight=ui.dp(73) }
            val row=ui.row()
            val tile=FrameLayout(this)
            tile.addView(ui.icon("2003:681","imgGroup186",41,39),FrameLayout.LayoutParams(ui.dp(41),ui.dp(39)))
            tile.addView(ui.icon("2003:681",if(enabled)materialIcon(waste.id) else "imgLainnya1",24),FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            row.addView(tile,ui.lp(41,39))
            val labels=ui.column().apply { setPadding(ui.dp(15),0,0,0) }
            labels.addView(ui.text(waste.name,16,true,ui.greenText,600)); labels.addView(ui.text(if(enabled)"${ui.number(waste.rate)} poin/kg" else waste.guide,16,color=ui.muted))
            row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
            val check=FrameLayout(this)
            fun updateCheck() {
                check.removeAllViews()
                val checked=waste.id in selected
                check.addView(ui.icon("2003:681",if(checked)"imgEllipse15" else "imgEllipse16",24,22),FrameLayout.LayoutParams(ui.dp(24),ui.dp(22),Gravity.CENTER))
                if(checked)check.addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_check); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },FrameLayout.LayoutParams(ui.dp(15),ui.dp(15),Gravity.CENTER))
                card.contentDescription="${waste.name}, ${if(!enabled)"belum tersedia" else if(checked)"dipilih" else "belum dipilih"}"
            }
            updateCheck(); row.addView(check,ui.lp(32,48)); card.addView(row)
            card.isFocusable=true
            card.accessibilityDelegate=object: View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View,info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host,info); info.isCheckable=enabled; info.isChecked=waste.id in selected
                }
            }
            card.setOnClickListener {
                if(enabled) { if(waste.id in selected)selected.remove(waste.id) else selected.add(waste.id); updateCheck() }
                else AlertDialog.Builder(this).setTitle("Lainnya belum tersedia").setMessage("Demo baru menerima empat jenis sampah yang tercantum. B3, elektronik, dan sampah medis tidak diterima.").setPositiveButton("Mengerti",null).show()
            }
            content.addView(card); ui.gap(content,16)
        }
        ui.gap(content,32); content.addView(ui.button("Lanjut") { if(selected.isEmpty())error("Pilih setidaknya satu jenis sampah.") else go("request") })
        notice("Poin simulasi · Jenis khusus belum diterima.")
    }

    private fun field(key: String,label: String,hint: String,input: Int=InputType.TYPE_CLASS_TEXT): EditText {
        content.addView(ui.text(label,15,true)); ui.gap(content,8)
        return ui.field(hint,input).also { it.setText(draft.optString(key)); it.contentDescription=label; formFields[key]=it; content.addView(it); ui.gap(content,20) }
    }
    private fun captureDraft() { if(page=="request")formFields.forEach { (k,v) -> draft.put(k,v.text.toString()) } }
    private fun requestForm() {
        title("Jadwalkan penjemputan",true)
        notice("Jadwal ini adalah permintaan. Pengepul perlu mengonfirmasinya.")
        selected.forEach { id -> field("weight_$id","Perkiraan berat ${material(id).name} (kg)","Contoh: 2,5",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) }
        field("address","Alamat penjemputan","Jalan, nomor rumah, RT/RW, dan kelurahan",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val today=LocalDate.now(ZoneId.of("Asia/Jakarta"))
        if(!draft.has("date"))draft.put("date",today.toString())
        content.addView(ui.text("Tanggal penjemputan",15,true)); ui.gap(content,8)
        val dateButton=ui.button(draft.getString("date"),false) {}
        dateButton.setOnClickListener {
            val date=runCatching { LocalDate.parse(draft.getString("date")) }.getOrDefault(today)
            DatePickerDialog(this,{ _,y,m,d -> draft.put("date",LocalDate.of(y,m+1,d).toString()); dateButton.text=draft.getString("date") },date.year,date.monthValue-1,date.dayOfMonth).apply {
                datePicker.minDate=today.atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
                datePicker.maxDate=today.plusDays(30).atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli(); show()
            }
        }
        content.addView(dateButton); ui.gap(content,20); content.addView(ui.text("Waktu (WIB)",15,true))
        val spinner=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,slots)
            setSelection(slots.indexOf(draft.optString("slot")).coerceAtLeast(0)); minimumHeight=ui.dp(56)
        }
        spinner.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { draft.put("slot",slots[position]) }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        content.addView(spinner,ui.lp()); ui.gap(content,20)
        field("notes","Catatan (opsional)","Patokan lokasi atau instruksi aman",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        notice("Alamat dibagikan kepada pengepul akun demo untuk memproses penjemputan. Gunakan alamat contoh untuk demo.")
        lateinit var submit: Button
        submit=ui.button("Ajukan penjemputan") {
            captureDraft(); draft.put("slot",spinner.selectedItem.toString())
            val items=try { selected.map { Item(it,parseWeight(draft.optString("weight_$it"))) } } catch (e: IllegalArgumentException) { error(e.message.orEmpty()); return@button }
            val address=draft.optString("address").trim()
            if(address.length !in 10..400) { formFields["address"]?.error="Isi alamat 10–400 karakter."; return@button }
            if(items.sumOf { it.grams }>200_000) { error("Total berat maksimal 200 kg."); return@button }
            if(draft.optString("notes").length>500) { error("Catatan maksimal 500 karakter."); return@button }
            val payload=JSONObject().put("items",items.json()).put("address",address).put("date",draft.getString("date")).put("slot",draft.getString("slot")).put("notes",draft.optString("notes"))
            perform(submit,{Order.from(api.request("/api/orders",payload,draftKey).getJSONObject("order"))}) { order ->
                orders=listOf(order)+orders.filterNot { it.id==order.id }; selectedId=order.id; selected.clear(); draft=JSONObject(); draftKey=UUID.randomUUID().toString(); page="detail"; render()
            }
        }
        content.addView(submit); ui.gap(content,12)
        content.addView(ui.button("Periksa pesanan saya",false) { go("orders",true) })
        notice("Jika koneksi terputus, periksa pesanan sebelum mengajukan ulang. Kiriman ulang formulir yang sama memakai kunci permintaan yang sama.")
    }
    private fun parseWeight(value: String,zero: Boolean=false): Int {
        val number=try { BigDecimal(value.trim().replace(',','.')).multiply(BigDecimal(1000)).intValueExact() } catch (_: Exception) { throw IllegalArgumentException("Masukkan berat dalam kg, maksimal tiga angka desimal.") }
        require(number in (if(zero)0 else 100)..(if(zero)200_000 else 100_000)) { "Berat per jenis harus ${if(zero)"0–200" else "0,1–100"} kg." }
        return number
    }

    private fun detail() {
        val order=orders.find { it.id==selectedId }
        if(order==null) { title("Detail pesanan",true); empty("Pesanan belum tersedia","Muat ulang untuk mengambil status terbaru."); content.addView(ui.button("Muat ulang",false) { refresh() }); return }
        if(user?.collector==true) { collectorDetail(order); return }
        title("Detail pesanan",true)
        if(user?.collector==true) {
            val map=ImageView(this).apply { assets.open("figma/2003_951_imgPetaDetailPesanan1.png").use { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(it,null)) }; scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription="Ilustrasi peta dari desain, bukan lokasi pesanan" }
            content.addView(map,ui.lp(-1,160)); notice("Ilustrasi peta · Gunakan alamat di bawah untuk lokasi sebenarnya.")
        }
        val summary=ui.card()
        summary.addView(ui.text("#${order.shortId}",18,true)); ui.gap(summary,8)
        summary.addView(ui.text(statusLabel(order.status),15,true,ui.greenText)); ui.gap(summary,16)
        summary.addView(orderTimeline(order))
        ui.gap(summary,16); ui.divider(summary); ui.gap(summary,16)
        summary.addView(ui.text(if(user?.collector==true)order.ownerName else order.collectorName.ifEmpty { "Menunggu pengepul menerima" },17,true))
        ui.gap(summary,8); summary.addView(ui.text("${order.date} · ${order.slot} WIB",14,color=ui.muted))
        ui.gap(summary,16); summary.addView(ui.text("Lokasi penjemputan",13,color=ui.muted)); ui.gap(summary,6); summary.addView(ui.text(order.address,15,true))
        content.addView(summary); ui.gap(content,16)
        val waste=ui.card(); waste.addView(ui.text(if(order.actual.isEmpty())"Perkiraan sampah" else "Hasil penimbangan",16,true)); ui.gap(waste,12)
        val shown=order.actual.ifEmpty { order.items }
        shown.forEach { item ->
            val row=ui.row(); row.addView(ui.icon("2003:681",materialIcon(item.materialId)))
            val label=ui.column().apply { setPadding(ui.dp(12),0,0,0) }; label.addView(ui.text(material(item.materialId).name,16,true)); label.addView(ui.text("${ui.kg(item.grams)} · ${ui.number(material(item.materialId).rate)} poin/kg",13,color=ui.muted))
            row.addView(label); waste.addView(row); ui.gap(waste,16)
        }
        val points=if(order.actual.isEmpty())order.items.sumOf { it.grams*material(it.materialId).rate/1000 } else order.points
        waste.addView(ui.text("${if(order.actual.isEmpty())"Perkiraan poin" else "Poin tercatat"}: ${ui.number(points)} · simulasi",15,true,ui.greenText))
        content.addView(waste)
        if(order.notes.isNotBlank()) { ui.gap(content,16); content.addView(ui.text(order.notes,14,color=ui.muted).apply { background=ui.box(ui.pale,16); setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16)) }) }
        if(order.reason.isNotBlank()) { ui.gap(content,16); content.addView(ui.text("Alasan: ${order.reason}",14,color=ui.muted)) }
        ui.gap(content,24)
        if(user?.collector==true) when(order.status) {
            "REQUESTED" -> { actionButton("Terima pesanan","accept"); ui.gap(content,12); content.addView(ui.button("Tolak dengan alasan",false) { reasonDialog("reject") }) }
            "SCHEDULED" -> actionButton("Mulai perjalanan","start")
            "ON_THE_WAY" -> content.addView(ui.button("Konfirmasi tiba") { go("arrival") })
            "ARRIVED" -> content.addView(ui.button("Timbang sampah") { go("weigh") })
            "AWAITING_CONFIRMATION" -> notice("Menunggu warga mengonfirmasi berat aktual.")
        } else when(order.status) {
            "REQUESTED","SCHEDULED" -> content.addView(ui.button("Batalkan pesanan",false) { reasonDialog("cancel") })
            "AWAITING_CONFIRMATION" -> content.addView(ui.button("Periksa ringkasan transaksi") { go("summary") })
        }
        if(order.status=="COMPLETED" || (user?.collector==true && order.status=="AWAITING_CONFIRMATION"))content.addView(ui.button("Ringkasan transaksi",false) { go("summary") })
        ui.gap(content,12); content.addView(ui.button("Muat ulang status",false) { refresh() })
    }

    private fun collectorDetail(order: Order) {
        val outer=content; outer.setPadding(0,0,0,0)
        val header=FrameLayout(this)
        fun place(view: View,x: Int,y: Int,w: Int,h: Int) { header.addView(view,FrameLayout.LayoutParams(ui.dp(w),ui.dp(h)).apply { marginStart=ui.dp(x); topMargin=ui.dp(y) }) }
        val map=ImageView(this).apply {
            assets.open("figma/2003_951_imgPetaDetailPesanan1.png").use { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(it,null)) }
            scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription="Ilustrasi peta dari Figma, bukan lokasi pesanan"
        }
        place(map,0,0,402,221)
        val back=FrameLayout(this).apply { contentDescription="Kembali"; isClickable=true; isFocusable=true; setOnClickListener { go("orders",true) } }
        back.addView(ui.icon("2003:951","imgAtIconsArrowLeft3",33,27),FrameLayout.LayoutParams(ui.dp(33),ui.dp(27),Gravity.CENTER))
        place(back,12,0,48,48)
        place(ui.text("Detail pesanan",14,true).apply { gravity=Gravity.CENTER; background=ui.box(ui.surface,16) },62,9,135,33)
        place(ui.text("Peta ilustrasi · Bukan GPS",12,true,ui.greenText).apply { gravity=Gravity.CENTER; background=ui.box(ui.surface,16) },6,136,190,33)
        val person=ui.row().apply { background=ui.box(ui.surface,20,true); setPadding(ui.dp(10),ui.dp(10),ui.dp(6),ui.dp(10)) }
        person.addView(avatar(order.ownerName),ui.lp(58,58))
        val labels=ui.column().apply { setPadding(ui.dp(10),0,ui.dp(4),0) }
        labels.addView(ui.text(order.ownerName,16,true).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }); ui.gap(labels,4)
        labels.addView(ui.text(statusLabel(order.status),12,color=ui.muted)); person.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        listOf("Chat" to "imgIconChat1","Telepon" to "imgIconTelepon1").forEachIndexed { index,(label,asset) ->
            val button=FrameLayout(this).apply {
                contentDescription="$label belum tersedia"; isClickable=true; isFocusable=true
                setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("$label belum tersedia").setMessage("Demo ini belum memiliki layanan $label atau kontak mitra nyata. Tidak ada pesan atau panggilan yang dikirim.").setPositiveButton("Mengerti",null).show() }
            }
            button.addView(ui.icon("2003:951",asset,24,24),FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            person.addView(button,ui.lp(48,48).apply { if(index>0)marginStart=ui.dp(8) })
        }
        place(person,24,176,357,80); outer.addView(header,ui.lp(-1,256))
        content=ui.column().apply { setPadding(ui.dp(26),ui.dp(25),ui.dp(26),ui.dp(24)) }; outer.addView(content)
        val location=ui.row().apply { background=ui.box(ui.surface,20,true); setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12)); minimumHeight=ui.dp(70) }
        val pin=FrameLayout(this).apply { background=ui.box(ui.pale,14) }
        pin.addView(ui.icon("2003:951","imgIconLokasi3",25,27),FrameLayout.LayoutParams(ui.dp(25),ui.dp(27),Gravity.CENTER)); location.addView(pin,ui.lp(44,43))
        val address=ui.column().apply { setPadding(ui.dp(16),0,0,0) }
        address.addView(ui.text("Lokasi penjemputan",13,true,ui.muted)); ui.gap(address,5); address.addView(ui.text(order.address,13,true))
        location.addView(address,LinearLayout.LayoutParams(0,-2,1f)); content.addView(location); ui.gap(content,17)
        val waste=ui.card(14); waste.addView(ui.text("Jenis sampah",14,true)); ui.gap(waste,12)
        val shown=order.actual.ifEmpty { order.items }
        shown.chunked(2).forEach { items ->
            val row=ui.row().apply { gravity=Gravity.TOP }
            items.forEachIndexed { index,item ->
                val chip=ui.row().apply { background=ui.box(ui.pale,14); setPadding(ui.dp(10),ui.dp(10),ui.dp(6),ui.dp(10)); minimumHeight=ui.dp(58) }
                chip.addView(ui.icon("2003:681",materialIcon(item.materialId),28,30),ui.lp(28,30))
                val text=ui.column().apply { setPadding(ui.dp(8),0,0,0) }
                text.addView(ui.text(if(item.materialId=="plastic")"Plastik" else material(item.materialId).name,14,true)); ui.gap(text,4); text.addView(ui.text(ui.kg(item.grams),12,color=ui.muted))
                chip.addView(text,LinearLayout.LayoutParams(0,-2,1f)); row.addView(chip,LinearLayout.LayoutParams(0,-2,1f).apply { if(index>0)marginStart=ui.dp(12) })
            }
            waste.addView(row); ui.gap(waste,12)
        }
        ui.divider(waste); ui.gap(waste,12)
        val total=ui.row(); total.addView(ui.text(if(order.actual.isEmpty())"Total estimasi" else "Hasil timbang",12,color=ui.muted),LinearLayout.LayoutParams(0,-2,1f))
        val points=if(order.actual.isEmpty())shown.sumOf { it.grams*material(it.materialId).rate/1000 } else order.points
        total.addView(ui.text("${ui.kg(shown.sumOf { it.grams })} · ${ui.number(points)} pt",12,true,ui.greenText),ui.lp(-2,-2)); waste.addView(total); content.addView(waste)
        if(order.notes.isNotBlank()) {
            ui.gap(content,20)
            val notes=ui.row().apply { background=ui.box(ui.pale,18); setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12)); minimumHeight=ui.dp(66) }
            notes.addView(ui.icon("2003:951","imgIconChat2",24,24),ui.lp(24,24))
            notes.addView(ui.text(order.notes,13,color=ui.muted).apply { setPadding(ui.dp(12),0,0,0) },LinearLayout.LayoutParams(0,-2,1f)); content.addView(notes)
        }
        ui.gap(content,28)
        when(order.status) {
            "REQUESTED" -> { actionButton("Terima pesanan","accept"); ui.gap(content,12); content.addView(ui.button("Tolak dengan alasan",false) { reasonDialog("reject") }) }
            "SCHEDULED" -> actionButton("Mulai perjalanan","start")
            "ON_THE_WAY" -> content.addView(ui.button("Konfirmasi tiba") { go("arrival") })
            "ARRIVED" -> content.addView(ui.button("Timbang sampah") { go("weigh") })
            "AWAITING_CONFIRMATION","COMPLETED" -> content.addView(ui.button("Ringkasan transaksi") { go("summary") })
        }
        notice("${order.date} · ${order.slot} WIB · #${order.shortId}\nPenjemputan demo, tanpa rute atau ETA aktual.")
        if(order.reason.isNotBlank())notice("Alasan: ${order.reason}")
        content.addView(ui.button("Muat ulang status",false) { refresh() })
    }

    private fun actionButton(label: String,action: String) { lateinit var button: Button; button=ui.button(label) { transition(action,button=button) }; content.addView(button) }
    private fun transition(action: String,body: JSONObject=JSONObject(),button: View?=null) {
        perform(button,{Order.from(api.request("/api/orders/$selectedId/$action",body).getJSONObject("order"))}) { order ->
            orders=listOf(order)+orders.filterNot { it.id==order.id }
            page=when(action) { "arrive"->"weigh"; "weigh","confirm"->"summary"; else->"detail" }; render()
        }
    }
    private fun arrival() {
        val order=orders.find { it.id==selectedId }?:return
        if(user?.collector!=true || order.status !in setOf("ON_THE_WAY","ARRIVED")) { page="detail"; detail(); return }
        title("Konfirmasi tiba",true)
        content.addView(orderTimeline(order)); ui.gap(content,64)
        content.addView(ui.icon("2003:806","imgAlamat1",112,112).apply { layoutParams=ui.lp(-1,112) }); ui.gap(content,48)
        content.addView(ui.text("Kamu sudah sampai?",26,true).apply { gravity=Gravity.CENTER }); ui.gap(content,16)
        content.addView(ui.text("Konfirmasi kedatangan ke lokasi ${order.ownerName} untuk mulai menimbang sampah.",17,color=ui.muted).apply { gravity=Gravity.CENTER }); ui.gap(content,20)
        content.addView(ui.text(order.address,15,true).apply { gravity=Gravity.CENTER }); ui.gap(content,56)
        lateinit var button: Button
        button=ui.button("Mulai timbang sampah") { if(order.status=="ARRIVED")go("weigh") else transition("arrive",button=button) }
        content.addView(button); notice("Konfirmasi manual · Aplikasi tidak melacak GPS atau memverifikasi kedatangan secara otomatis.")
    }

    private fun transactionSummary() {
        val order=orders.find { it.id==selectedId }?:return
        title("Ringkasan transaksi",true)
        content.addView(ui.text(if(order.status=="COMPLETED")"Pesanan selesai · #${order.shortId}" else "Periksa sebelum menyelesaikan pesanan",15,color=ui.muted)); ui.gap(content,24)
        val details=ui.card(18); details.addView(ui.text("Rincian sampah",14,true,ui.muted)); ui.gap(details,16)
        order.actual.ifEmpty { order.items }.forEach { item ->
            val row=ui.row(); row.addView(ui.icon("2003:681",materialIcon(item.materialId),30,30),ui.lp(41,39))
            val labels=ui.column().apply { setPadding(ui.dp(12),0,ui.dp(8),0) }
            labels.addView(ui.text(material(item.materialId).name,15,true)); ui.gap(labels,6)
            labels.addView(ui.text("${ui.kg(item.grams)} × ${ui.number(material(item.materialId).rate)} poin",12,color=ui.muted))
            row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ui.text("${ui.number(item.grams*material(item.materialId).rate/1000)} pt",14,true),ui.lp(-2,-2))
            details.addView(row); ui.gap(details,12)
        }
        content.addView(details); ui.gap(content,24)
        val total=ui.column(24).apply { background=ui.box(ui.pale,24); gravity=Gravity.CENTER }
        total.addView(ui.text("Total poin untuk ${order.ownerName}",18,true,ui.greenText).apply { gravity=Gravity.CENTER }); ui.gap(total,16)
        total.addView(ui.text(ui.number(order.points),36,true,ui.greenText).apply { gravity=Gravity.CENTER }); ui.gap(total,12)
        total.addView(ui.text(if(order.status=="COMPLETED")"Poin demo telah tercatat" else "Poin tercatat setelah warga mengonfirmasi",13,color=ui.greenText).apply { gravity=Gravity.CENTER })
        content.addView(total); ui.gap(content,40)
        if(user?.collector==false && order.status=="AWAITING_CONFIRMATION") {
            content.addView(ui.button("Selesaikan transaksi") {
                AlertDialog.Builder(this).setTitle("Selesaikan pesanan?").setMessage("Berat aktual ${ui.kg(order.actual.sumOf { it.grams })}, ${ui.number(order.points)} poin simulasi. Pastikan hasil penimbangan sudah sesuai.")
                    .setNegativeButton("Periksa lagi",null).setPositiveButton("Konfirmasi") { _,_ -> transition("confirm") }.show()
            })
        } else if(order.status=="COMPLETED")content.addView(ui.button("Kembali ke riwayat") { go("history",true) })
        else { notice("Menunggu warga meninjau dan mengonfirmasi hasil penimbangan."); content.addView(ui.button("Muat ulang status",false) { refresh() }) }
        notice("Poin simulasi · Tidak dapat dicairkan atau ditukar dengan uang.")
    }
    private fun reasonDialog(action: String) {
        val reason=ui.field("Alasan (5–300 karakter)")
        val dialog=AlertDialog.Builder(this).setTitle(if(action=="cancel")"Batalkan pesanan?" else "Tolak pesanan?")
            .setView(reason).setNegativeButton("Kembali",null).setPositiveButton("Konfirmasi",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(reason.text.toString().trim().length !in 5..300)reason.error="Tulis alasan 5–300 karakter."
            else { val value=reason.text.toString(); dialog.dismiss(); transition(action,JSONObject().put("reason",value)) }
        } }; dialog.show()
    }
    private fun weigh() {
        val order=orders.find { it.id==selectedId }?:return
        if(user?.collector!=true || order.status!="ARRIVED") { detail(); return }
        content.setPadding(ui.dp(24),0,ui.dp(30),ui.dp(24))
        val back=FrameLayout(this).apply { contentDescription="Kembali"; isClickable=true; isFocusable=true; setOnClickListener { go("detail") } }
        back.addView(ui.icon("2003:681","imgAtIconsArrowLeft2",28,24),FrameLayout.LayoutParams(ui.dp(28),ui.dp(24),Gravity.START or Gravity.CENTER_VERTICAL))
        content.addView(back,ui.lp(48,48)); ui.gap(content,8)
        content.addView(ui.text("Timbang sampah",26,true)); ui.gap(content,8)
        content.addView(ui.text("Masukkan berat riil per jenis sampah",16,color=ui.muted)); ui.gap(content,26)
        if(weightDraft.optString("order")!=order.id)weightDraft=JSONObject().put("order",order.id)
        val inputs=linkedMapOf<String,EditText>()
        val totalLabel=ui.text("",28,true,ui.greenText)
        val weightLabel=ui.text("",13,color=ui.muted)
        fun total() {
            val values=inputs.map { (id,field) -> Item(id,runCatching { parseWeight(field.text.toString(),true) }.getOrDefault(0)) }
            totalLabel.text=ui.number(values.sumOf { it.grams*material(it.materialId).rate/1000 })
            weightLabel.text="dari total berat ${ui.kg(values.sumOf { it.grams })}"
        }
        order.items.forEach { item ->
            val row=FrameLayout(this).apply { background=ui.box(ui.surface,24,true) }
            fun place(view: View,x: Int,y: Int,w: Int,h: Int=-2) { row.addView(view,FrameLayout.LayoutParams(ui.dp(w),if(h<0)h else ui.dp(h)).apply { marginStart=ui.dp(x); topMargin=ui.dp(y) }) }
            val tile=FrameLayout(this).apply { background=ui.box(if(item.materialId=="paper")Color.parseColor("#FFF1D6") else ui.pale,10) }
            tile.addView(if(item.materialId in setOf("plastic","paper"))ui.icon("2003:1022",if(item.materialId=="plastic")"imgIconSampahPlastik2" else "imgIconSampahKertas2",30,27) else ui.icon("2003:681",materialIcon(item.materialId),30,27),FrameLayout.LayoutParams(ui.dp(30),ui.dp(27),Gravity.CENTER))
            place(tile,10,19,42,43)
            place(ui.text(if(item.materialId=="plastic")"Plastik" else material(item.materialId).name,18,true),65,18,84)
            place(ui.text("${ui.number(material(item.materialId).rate)} poin/kg",12,color=ui.muted),65,43,90)
            val field=ui.field("kg",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
                setText(weightDraft.optString(item.materialId,(item.grams/1000.0).toString())); contentDescription="Berat aktual ${material(item.materialId).name} dalam kg"
                textSize=ui.sp(16); typeface=ui.typeface(700); background=null; gravity=Gravity.CENTER; setPadding(0,0,0,0); minHeight=ui.dp(48); setSingleLine()
            }
            inputs[item.materialId]=field
            field.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { weightDraft.put(item.materialId,s.toString()); total() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            place(field,201,17,65,48); place(ui.text("kg",11,color=ui.muted).apply { gravity=Gravity.CENTER },263,32,18,24)
            fun step(add: Boolean) {
                val current=runCatching { parseWeight(field.text.toString(),true) }.getOrDefault(0)
                val next=(current+if(add)100 else -100).coerceIn(0,200_000)
                field.setText(BigDecimal(next).movePointLeft(3).stripTrailingZeros().toPlainString())
            }
            listOf(false,true).forEach { add ->
                val button=FrameLayout(this).apply {
                    background=ui.box(if(add)ui.cta else ui.surface,24,!add); contentDescription="${if(add)"Tambah" else "Kurangi"} berat ${material(item.materialId).name} 0,1 kg"
                    isClickable=true; isFocusable=true; setOnClickListener { step(add) }
                }
                button.addView(ImageView(this).apply { setImageResource(if(add)R.drawable.ic_add else R.drawable.ic_remove) },FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
                place(button,if(add)282 else 151,17,48,48)
            }
            content.addView(row,ui.lp(-1,81)); ui.gap(content,13)
        }
        ui.gap(content,25)
        val panel=ui.row().apply { background=ui.box(ui.pale,20); setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(20)) }
        val label=ui.column(); label.addView(ui.text("Total poin didapat warga",14,true,ui.greenText)); ui.gap(label,8); label.addView(weightLabel)
        panel.addView(label,LinearLayout.LayoutParams(0,-2,1f)); panel.addView(totalLabel,ui.lp(-2,-2)); content.addView(panel); total()
        ui.gap(content,maxOf(32,196-(order.items.size-2)*94))
        lateinit var submit: Button
        submit=ui.button("Konfirmasi berat") {
            val actual=try { inputs.map { (id,input) -> Item(id,parseWeight(input.text.toString(),true)) } } catch (e: IllegalArgumentException) { error(e.message.orEmpty()); return@button }
            if(actual.sumOf { it.grams } !in 100..200_000) { error("Total berat aktual harus 0,1–200 kg."); return@button }
            transition("weigh",JSONObject().put("items",actual.json()),submit)
        }
        content.addView(submit); notice("Warga akan meninjau hasil penimbangan sebelum pesanan selesai. Poin tidak memiliki nilai uang.")
    }

    private fun profile() {
        val account=user?:return
        val outer=content; outer.setPadding(0,0,0,0)
        val green=Color.rgb(45,204,144)
        @Suppress("DEPRECATION")
        window.statusBarColor=green
        val header=FrameLayout(this)
        fun place(view: View,x: Int,y: Int,w: Int,h: Int=-2) {
            header.addView(view,FrameLayout.LayoutParams(if(w<0)w else ui.dp(w),if(h<0)h else ui.dp(h)).apply { marginStart=ui.dp(x); topMargin=ui.dp(y) })
        }
        place(View(this).apply { background=ui.box(green,40) },0,-40,402,267)
        place(ui.icon("2003:806","imgEllipse10",175,171),-49,-71,175,171)
        place(ui.icon("2003:806","imgEllipse8",260,281),228,-161,260,281)
        place(ui.text("Profil",26,true),54,3,320)
        val back=FrameLayout(this).apply { contentDescription="Kembali"; isClickable=true; isFocusable=true; setOnClickListener { go("home") } }
        back.addView(ui.icon("2003:681","imgAtIconsArrowLeft2",28,24),FrameLayout.LayoutParams(ui.dp(28),ui.dp(24),Gravity.CENTER))
        place(back,7,0,42,48)
        val profileCard=View(this).apply { background=ui.box(ui.surface,24,true) }
        place(profileCard,14,90,375,197)
        val picture=avatar(account.name)
        if(picture is TextView)picture.textSize=ui.sp(40)
        place(picture,152,47,98,99)
        val area=if(account.collector)accountProfile.optString("area").ifBlank { "Pengepul · akun demo" } else {
            val rt=accountProfile.optString("rt"); val rw=accountProfile.optString("rw")
            if(rt.isNotBlank() && rw.isNotBlank())"Warga RT $rt / RW $rw" else "Warga · akun demo"
        }
        val identity=ui.column().apply { gravity=Gravity.CENTER }
        identity.addView(ui.text(account.name,26,true).apply { gravity=Gravity.CENTER; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
        ui.gap(identity,4); identity.addView(ui.text(area,16,color=ui.muted).apply { gravity=Gravity.CENTER })
        place(identity,27,155,348)
        identity.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(348),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val dividerY=maxOf(ui.dp(223),ui.dp(155)+identity.measuredHeight+ui.dp(12))
        val extraHeight=dividerY-ui.dp(223)
        profileCard.layoutParams.height=ui.dp(197)+extraHeight
        header.addView(View(this).apply { setBackgroundColor(ui.outline) },FrameLayout.LayoutParams(ui.dp(352),ui.dp(1)).apply { marginStart=ui.dp(27); topMargin=dividerY })
        val completed=orders.filter { it.status=="COMPLETED" }
        val stats=ui.row()
        listOf(ui.number(completed.sumOf { it.points }) to "Poin demo",completed.size.toString() to "Setoran",ui.kg(completed.sumOf { o -> o.actual.sumOf { it.grams } }) to "Sampah").forEachIndexed { i,(value,label) ->
            if(i>0)stats.addView(View(this).apply { setBackgroundColor(ui.outline) },ui.lp(1,43))
            val item=ui.column().apply { gravity=Gravity.CENTER }
            item.addView(ui.text(value,16,true).apply { gravity=Gravity.CENTER }); ui.gap(item,4); item.addView(ui.text(label,10,color=ui.muted).apply { gravity=Gravity.CENTER })
            stats.addView(item,LinearLayout.LayoutParams(0,-2,1f))
        }
        header.addView(stats,FrameLayout.LayoutParams(ui.dp(352),ui.dp(48)).apply { marginStart=ui.dp(27); topMargin=dividerY+ui.dp(7) })
        outer.addView(header,LinearLayout.LayoutParams(-1,ui.dp(287)+extraHeight))
        content=ui.column().apply { setPadding(ui.dp(20),ui.dp(21),ui.dp(23),ui.dp(24)) }; outer.addView(content)
        fun menu(label: String,icon: String,tile: String,last: Boolean=false,action: ()->Unit): LinearLayout {
            val row=ui.row().apply { minimumHeight=ui.dp(83); setPadding(ui.dp(28),0,ui.dp(14),0); isClickable=true; isFocusable=true; contentDescription=label; setOnClickListener { action() } }
            val art=FrameLayout(this)
            art.addView(ui.icon("2003:806",tile,41,39),FrameLayout.LayoutParams(ui.dp(41),ui.dp(39)))
            art.addView(ui.icon("2003:806",icon,24,24),FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            row.addView(art,ui.lp(41,39)); row.addView(ui.text(label,20,true,weight=600).apply { setPadding(ui.dp(20),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
            if(!last)row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },ui.lp(24,24))
            return row
        }
        val actions=ui.column().apply { background=ui.box(ui.surface,24,true) }
        actions.addView(menu("Edit Profil","imgEditProfil1","imgGroup198") { profileSection="all"; go("edit-profile") }); ui.divider(actions)
        actions.addView(menu("Wilayah Operasional","imgAlamat1","imgVector") { profileSection="area"; go("edit-profile") }); ui.divider(actions)
        if(account.collector) {
            actions.addView(menu("Kendaraan","imgAlamat1","imgVector") { profileSection="vehicle"; go("edit-profile") }); ui.divider(actions)
        }
        actions.addView(menu("Keluar","imgKeluar1","imgVector1",true) { signOut() }); content.addView(actions)
        ui.gap(content,20)
        content.addView(ui.button("Ubah password",false) { signOut(recovery=true) }); ui.gap(content,12)
        content.addView(ui.button("Panduan & batas layanan",false) { go("guide") })
        notice("Akun demo · Identitas tidak diverifikasi resmi. Poin tidak dapat dicairkan.")
    }

    private fun signOut(recovery: Boolean=false) {
        val email=user?.email.orEmpty()
        AlertDialog.Builder(this).setTitle(if(recovery)"Ubah password?" else "Keluar dari akun?")
            .setMessage(if(recovery)"Keluar dari sesi ini, lalu verifikasi OTP demo untuk mengganti password." else "Sesi perangkat ini akan dihapus. Pesanan tetap tersimpan di server.")
            .setNegativeButton("Kembali",null).setPositiveButton(if(recovery)"Lanjut" else "Keluar") { _,_ ->
                perform(work={api.request("/api/auth/logout",JSONObject())}) {
                    vault.clear(); api.token=""; user=null; accountProfile=JSONObject(); orders=emptyList(); selected.clear(); draft=JSONObject(); draftKey=UUID.randomUUID().toString()
                    stopPolling()
                    if(recovery)accountFlow.showRecovery(email) else login()
                }
            }.show()
    }

    private fun editProfile() {
        val account=user?:return
        title(when(profileSection) { "area"->"Wilayah Operasional"; "vehicle"->"Kendaraan"; else->"Edit Profil" },true)
        val inputs=linkedMapOf<String,EditText>()
        fun input(key: String,label: String,value: String,type: Int=InputType.TYPE_CLASS_TEXT) {
            content.addView(ui.text(label,15,true,weight=600)); ui.gap(content,8)
            inputs[key]=ui.field(label,type).apply { setText(value); contentDescription=label; content.addView(this) }; ui.gap(content,20)
        }
        if(profileSection=="all") {
            input("name","Nama Lengkap",account.name)
            input("phone","No WhatsApp",accountProfile.optString("phone"),InputType.TYPE_CLASS_PHONE)
            content.addView(ui.text(account.email,14,color=ui.muted)); ui.gap(content,20)
        }
        if(profileSection=="area") {
            input("address","Alamat",accountProfile.optString("address"),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            input("rt","RT",accountProfile.optString("rt"),InputType.TYPE_CLASS_NUMBER)
            input("rw","RW",accountProfile.optString("rw"),InputType.TYPE_CLASS_NUMBER)
            if(account.collector)input("area","Wilayah layanan demo",accountProfile.optString("area"))
        }
        var vehicle: Spinner?=null
        if(account.collector && profileSection=="vehicle") {
            content.addView(ui.text("Jenis kendaraan",15,true)); ui.gap(content,8)
            vehicle=Spinner(this).apply {
                adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Motor","Pick-up","Truk"))
                setSelection(listOf("motor","pickup","truck").indexOf(accountProfile.optString("vehicle")).coerceAtLeast(0)); minimumHeight=ui.dp(56)
            }; content.addView(vehicle); ui.gap(content,20)
        }
        lateinit var save: Button
        save=ui.button("Simpan perubahan") {
            val payload=JSONObject(); inputs.forEach { (key,field) -> payload.put(key,field.text.toString().trim()) }
            vehicle?.let { payload.put("vehicle",listOf("motor","pickup","truck")[it.selectedItemPosition]) }
            perform(save,{
                val result=api.request("/api/profile",payload).getJSONObject("profile")
                Pair(User.from(api.request("/api/me").getJSONObject("user")),result)
            }) { (updated,profile) -> user=updated; accountProfile=profile; page="profile"; render() }
        }
        content.addView(save); notice("Perubahan disimpan pada backend lokal. Gunakan kontak dan alamat contoh untuk demo.")
    }
    private fun guide() {
        title("Panduan pemilahan",true)
        materials.forEach { waste -> content.addView(ui.text(waste.name,20,true)); ui.gap(content,8); content.addView(ui.text(waste.guide,16,color=ui.muted)); ui.gap(content,28) }
        content.addView(ui.text("Perhatikan jenis khusus",20,true)); ui.gap(content,8)
        content.addView(ui.text("Baterai, elektronik, B3, dan sampah medis membutuhkan penanganan khusus. Jangan campurkan ke penjemputan umum. Jenis yang diterima layanan nyata harus disepakati dengan mitra.",16,color=ui.muted))
        notice("Dasar konteks: Permen LHK No. 14 Tahun 2021. Mitra dan wilayah layanan nyata belum tersedia.")
        content.addView(ui.button("Baca sumber resmi",false) {
            val intent=Intent(Intent.ACTION_VIEW,Uri.parse("https://peraturan.bpk.go.id/Details/233722/permen-lhk-no-14-tahun-2021"))
            runCatching { startActivity(intent) }.onFailure { error("Browser tidak tersedia pada perangkat ini.") }
        })
    }

    private fun back() {
        if(user==null) { accountFlow.back(); return }
        go(when(page) { "request"->"select"; "weigh","arrival"->"detail"; "summary"->"orders"; "detail"->"orders"; "edit-profile"->"profile"; "home"->{ finish(); return }; else->"home" })
    }
    @Deprecated("Legacy Android Back support") override fun onBackPressed()=back()
    override fun onSaveInstanceState(out: Bundle) {
        if(user==null)out.putString("accountFlow",accountFlow.snapshot())
        captureDraft(); out.putString("page",page); out.putString("order",selectedId); out.putString("draft",draft.toString()); out.putString("draftKey",draftKey); out.putString("weightDraft",weightDraft.toString()); out.putStringArrayList("selected",ArrayList(selected)); super.onSaveInstanceState(out)
    }
    @Deprecated("External camera/document activity bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode in 711..712)accountFlow.photoResult(requestCode,resultCode,data) }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode in 801..802)accountFlow.permissionResult(requestCode) }
    override fun onResume() { super.onResume(); if(user?.collector==true)startPolling() }
    override fun onPause() { super.onPause(); stopPolling() }
    override fun onDestroy() { pollHandler.removeCallbacksAndMessages(null); accountFlow.close(); worker.shutdownNow(); super.onDestroy() }
}
