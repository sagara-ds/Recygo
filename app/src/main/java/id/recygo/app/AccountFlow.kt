package id.recygo.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Onboarding + account state. Passwords, OTP text and reset grants stay in memory. */
internal class AccountFlow(private val host: MainActivity,private val ui: Ui,private val api: Api) {
    private val prefs=host.getSharedPreferences("onboarding",Activity.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private var phase="WELCOME"
    private var role=""
    private var values=JSONObject()
    private val fields=linkedMapOf<String,EditText>()
    private var password=""
    private var confirmation=""
    private var nik=""
    private var avatar: Bitmap?=null
    private var identity: Bitmap?=null
    private var photoTarget="avatar"
    private val accepted=linkedSetOf("plastic","paper")
    private var challenge=""
    private var purpose=""
    private var resetGrant=""
    private var resendAt=0L
    private var emailMode=false
    private lateinit var canvas: AuthCanvas
    private var codeFields=emptyList<EditText>()
    private var distributingCode=false

    fun splash(done: ()->Unit) {
        fields.clear(); canvas=host.accountCanvas(); canvas.frame.setBackgroundColor(ui.cta)
        host.accountBarColor(ui.cta)
        canvas.image("2003:176","imgLogo",90,235,222,194)
        canvas.text("RecyGo",100,418,202,32,700,Color.WHITE,true)
        canvas.text("Kelola sampah anda",60,464,282,20,600,Color.WHITE,true)
        canvas.text("Memuat aplikasi…",70,745,262,20,color=Color.WHITE,center=true)
        val animations=android.provider.Settings.Global.getFloat(host.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
        if(animations)canvas.frame.getChildAt(0).apply { scaleX=0.94f; scaleY=0.94f; animate().scaleX(1f).scaleY(1f).setDuration(550).start() }
        handler.postDelayed({ if(!host.isFinishing && !host.isDestroyed)done() },if(animations)900 else 100)
    }
    fun start()=show(if(prefs.getBoolean("complete",false))"ROLE" else "WELCOME")
    fun showFind()=show("FIND")
    fun showRecovery(email: String) { values.put("resetEmail",email); show("FORGOT") }
    private fun capture() {
        fields.forEach { (key,field) -> when(key) {
            "password" -> password=field.text.toString()
            "confirmation" -> confirmation=field.text.toString()
            "nik" -> nik=field.text.toString()
            else -> values.put(key,field.text.toString())
        } }
    }
    private fun show(next: String) {
        capture(); fields.clear(); handler.removeCallbacksAndMessages(null); phase=next; canvas=host.accountCanvas()
        when(next) {
            "WELCOME" -> welcome()
            "LOCATION" -> permission(false)
            "NOTIFICATION" -> permission(true)
            "ROLE" -> roles()
            "RESIDENT_INFO" -> residentInfo()
            "RESIDENT_ADDRESS" -> residentAddress()
            "COLLECTOR_ID" -> collectorIdentity()
            "COLLECTOR_CONTACT" -> collectorContact()
            "COLLECTOR_BUSINESS" -> collectorBusiness()
            "FIND" -> find()
            "PASSWORD_LOGIN" -> passwordLogin()
            "FORGOT" -> forgot()
            "OTP" -> otp()
            "NEW_PASSWORD" -> newPassword()
            "RESET_DONE" -> resetDone()
        }
    }
    private fun wave(screen: String="2003:549",name: String="imgPreview4")=canvas.footer(screen,name,489,138)
    private fun backControl()=canvas.back(::back,phase in setOf("RESIDENT_INFO","RESIDENT_ADDRESS","COLLECTOR_ID","COLLECTOR_CONTACT","COLLECTOR_BUSINESS"))
    private fun field(key: String,label: String,hint: String,x: Int,y: Int,width: Int=342,passwordField: Boolean=false,input: Int=InputType.TYPE_CLASS_TEXT): EditText {
        val value=when(key) { "password"->password; "confirmation"->confirmation; "nik"->nik; else->values.optString(key) }
        val recovery=phase=="NEW_PASSWORD"
        return canvas.field(label,value,hint,x,y,width,passwordField,input,if(recovery)if(key=="confirmation")60 else 58 else 48,recovery).also { fields[key]=it }
    }
    private fun message(value: String)=host.accountError(value)
    private fun request(button: View?=null,work: ()->JSONObject,done: (JSONObject)->Unit)=host.accountRequest(button,work,done)
    private fun textLink(value: String,x: Int,y: Int,width: Int,action: ()->Unit)=canvas.link(value,x,y,width,action)
    private fun smallAction(value: String,x: Int,y: Int,width: Int,action: ()->Unit) {
        val control=FrameLayout(host).apply { isClickable=true; isFocusable=true; contentDescription=value; setOnClickListener { capture(); action() } }
        control.addView(ui.text(value,15,true,ui.greenText,600).apply { gravity=Gravity.CENTER; background=ui.box(ui.pale,16) },FrameLayout.LayoutParams(ui.dp(width),ui.dp(33),Gravity.CENTER))
        canvas.place(control,x,y-7,width,48)
    }

    private fun welcome() {
        canvas.footer("2003:198","img5B06A270201C4064Ab539Aff7A6D30821",446,298)
        canvas.image("2003:198","imgA7Abbba4Af4180D544F775E2B71690E3RemovebgPreview1",257,73,117,117,-89.33f)
        canvas.text("Selamat datang\ndi RecyGo!",79,120,244,28,700,center=true)
        canvas.text("Yuk, bersama wujudkan\nlingkungan yang lebih bersih\ndan sehat",64,226,285,20,color=Color.parseColor("#777777"),center=true)
        canvas.button("Masuk ke RecyGo",35,406) { show("LOCATION") }.apply {
            textSize=ui.sp(21)
            if(!canvas.largeText) { gravity=Gravity.START or Gravity.CENTER_VERTICAL; setPadding(ui.dp(44),ui.dp(12),0,ui.dp(12)) }
        }
        canvas.image("2003:198","imgAtIconsArrowLeft1",268,429,40,28,180f)
    }
    private fun permission(notification: Boolean) {
        val screen=if(notification)"2003:251" else "2003:225"
        canvas.footer(screen,if(notification)"imgYourParagraphText42" else "imgYourParagraphText41",406,164)
        canvas.image(screen,if(notification)"imgYourParagraphText11" else "imgYourParagraphText1",95,130,211,if(notification)179 else 175)
        canvas.text(if(notification)"Notifikasi" else "Akses Lokasi",70,315,262,28,700,center=true)
        canvas.text(if(notification)"Kami ingin mengirimkan\nnotifikasi penting untuk\nmembantu transaksi anda" else "Kami membutuhkan akses\nlokasi untuk menandai\npenjemputan anda",77,367,248,17,color=Color.parseColor("#666666"),center=true)
        canvas.button(if(notification)"Izinkan Notifikasi" else "Izinkan Lokasi",35,496) {
            capture()
            if(notification) {
                host.prepareNotificationChannel()
                if(Build.VERSION.SDK_INT>=33 && host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)host.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),802)
                else finishOnboarding()
            } else if(host.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)host.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),801)
            else show("NOTIFICATION")
        }
        textLink("Nanti Saja",130,577,142) { if(notification)finishOnboarding() else show("NOTIFICATION") }.apply { textSize=ui.sp(20); typeface=ui.typeface(700) }
    }
    private fun finishOnboarding() { prefs.edit().putBoolean("complete",true).apply(); show("ROLE") }
    fun permissionResult(code: Int) { if(code==801)show("NOTIFICATION") else if(code==802)finishOnboarding() }

    private fun roles() {
        canvas.footer("2003:276","imgYourParagraphText61",405,173)
        canvas.text("Masuk Sebagai apa?",24,35,371,28,700)
        canvas.text("Pilih peranmu untuk melanjutkan",25,76,352,18,300,color=Color.parseColor("#777777"))
        listOf(Triple("COLLECTOR","Pengepul","Kumpulkan sampah &\ndapatkan poin"),Triple("RESIDENT","Warga","Setor sampah &\ntukar poin demo")).forEachIndexed { index,(id,title,description) ->
            val y=if(index==0)136 else 278
            val box=if(canvas.largeText)ui.row().apply { setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16)) } else FrameLayout(host)
            box.apply {
                background=ui.box(if(role==id)ui.pale else ui.surface,22,true); isClickable=true; isFocusable=true; isSelected=role==id; contentDescription="Daftar sebagai $title"
                accessibilityDelegate=object: View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(view: View,info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(view,info); info.className=RadioButton::class.java.name; info.isCheckable=true; info.isChecked=role==id
                    }
                }
                setOnClickListener { role=id; show("ROLE") }
            }
            if(box is LinearLayout) {
                box.addView(canvas.asset("2003:276","imgSampleUserIcon1",66,66),ui.lp(66,66))
                val labels=ui.column().apply { setPadding(ui.dp(16),0,ui.dp(8),0) }
                labels.addView(ui.text(title,21,true)); ui.gap(labels,8); labels.addView(ui.text(description,17,color=Color.parseColor("#666666")))
                box.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                box.addView(canvas.asset("2003:276","imgBack1",24,24).apply { rotation=180f },ui.lp(24,24))
            } else {
            box.addView(canvas.asset("2003:276","imgSampleUserIcon1",66,66),FrameLayout.LayoutParams(ui.dp(66),ui.dp(66)).apply { marginStart=ui.dp(12); topMargin=ui.dp(26) })
            val titleView=ui.text(title,21,true)
            box.addView(titleView,FrameLayout.LayoutParams(ui.dp(200),-2).apply { marginStart=ui.dp(if(index==0)89 else 108); topMargin=ui.dp(if(index==0)23 else 32) })
            box.addView(ui.text(description,17,color=Color.parseColor("#666666")),FrameLayout.LayoutParams(ui.dp(200),-2).apply { marginStart=ui.dp(if(index==0)88 else 106); topMargin=ui.dp(if(index==0)60 else 69) })
            box.addView(canvas.asset("2003:276","imgBack1",24,24).apply { rotation=180f },FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd=ui.dp(14) })
            }
            canvas.place(box,35,y,340,if(index==0)113 else 120)
        }
        canvas.button("Lanjutkan",35,432) {
            if(role.isEmpty())message("Pilih peran warga atau pengepul terlebih dahulu.") else show(if(role=="RESIDENT")"RESIDENT_INFO" else "COLLECTOR_ID")
        }
        canvas.text("Sudah punya akun?",60,519,190,20,color=Color.parseColor("#777777"))
        textLink("Masuk",241,505,82) { show("FIND") }
    }

    private fun photoBlock(y: Int) {
        canvas.photo(avatar,39,y)
        canvas.text("Foto Profil / selfie",128,y,235,20,600)
        canvas.text("Gunakan gambar contoh untuk demo",129,y+24,240,12,color=ui.muted)
        smallAction("Ambil selfie",128,y+54,113) { photoTarget="avatar"; choosePhoto() }
    }
    private fun residentInfo() {
        backControl(); canvas.progress(1,2,"RESIDENT"); photoBlock(142)
        field("name","Nama Lengkap","Nama lengkap",30,293)
        field("phone","No WhatsApp","08xxxxxxxxxx",30,376,input=InputType.TYPE_CLASS_PHONE)
        field("email","Alamat email","nama@contoh.com",30,464,input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        field("password","Password","Minimal 8 karakter",30,547,passwordField=true)
        field("confirmation","Konfirmasi password","Ulangi password",30,635,passwordField=true)
        canvas.button("Lanjut",30,719,342,64) { capture(); if(validateIdentity())show("RESIDENT_ADDRESS") }.apply {
            if(!canvas.largeText) { gravity=Gravity.START or Gravity.CENTER_VERTICAL; setPadding(ui.dp(114),ui.dp(12),0,ui.dp(12)) }
        }
        canvas.image("2003:378","imgVector",213,738,30,20,180f)
    }
    private fun residentAddress() {
        backControl(); canvas.progress(2,2,"RESIDENT")
        canvas.text("Alamat penjemputan",38,128,325,20,600)
        canvas.text("Pengepul akan menjemput sampahmu\nke alamat ini",38,152,300,13,color=ui.muted)
        field("address","Alamat penjemputan","Jalan, nomor rumah, kelurahan",33,251)
        field("rt","RT","01",34,334,143,input=InputType.TYPE_CLASS_NUMBER)
        field("rw","RW","04",234,334,143,input=InputType.TYPE_CLASS_NUMBER)
        smallAction("Pin lokasi di peta",34,401,144,::pinLocation)
        val info=ui.text("Mode demo lokal. Wilayah layanan nyata\nbelum diverifikasi dengan mitra.",13,true,ui.greenText,600).apply { background=ui.box(ui.pale,15); setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16)) }
        canvas.place(info,29,453,342,69)
        consent(610)
        lateinit var submit: Button
        submit=canvas.button("Buat akun warga",30,721,342,64) { capture(); startRegistration(submit) }
    }
    private fun collectorIdentity() {
        backControl(); canvas.progress(1,3,"COLLECTOR"); photoBlock(166)
        field("name","Nama Lengkap","Nama lengkap",30,318)
        field("nik","NIK contoh","16 digit identitas contoh",30,412,input=InputType.TYPE_CLASS_NUMBER)
        val card=ui.card(10); val row=ui.row()
        row.addView(ui.text("Foto KTP contoh\nTidak diverifikasi secara resmi",12,color=ui.muted),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ui.button(if(identity==null)"Unggah" else "Ganti",false) { capture(); photoTarget="identity"; choosePhoto() }.apply { textSize=ui.sp(13); setPadding(0,0,0,0); minHeight=ui.dp(48) },ui.lp(84,48)); card.addView(row)
        canvas.place(card,29,490,342,72)
        canvas.text("Gunakan NIK dan foto sintetis untuk demo.\nData profil disimpan terenkripsi di server lokal.",29,577,342,12,color=ui.muted)
        canvas.button("Lanjut",30,719,342,64) {
            capture()
            if(values.optString("name").trim().length<2)message("Isi nama lengkap.")
            else if(!nik.matches(Regex("\\d{16}")))message("Isi 16 digit NIK contoh.")
            else show("COLLECTOR_CONTACT")
        }
    }
    private fun collectorContact() {
        backControl(); canvas.progress(2,3,"COLLECTOR")
        field("phone","No WhatsApp","08xxxxxxxxxx",30,177,input=InputType.TYPE_CLASS_PHONE)
        field("email","Alamat email","nama@contoh.com",30,272,input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        field("password","Password","Minimal 8 karakter",30,363,passwordField=true)
        field("confirmation","Konfirmasi password","Ulangi password",30,452,passwordField=true)
        val rules=ui.text("Minimal 8 karakter\nKombinasi huruf dan angka\nSama dengan konfirmasi password",13,color=ui.muted).apply { background=ui.box(Color.parseColor("#F5F6F7"),15); setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10)) }
        canvas.place(rules,29,522,342,82)
        canvas.button("Lanjut",30,719,342,64) { capture(); if(validateIdentity())show("COLLECTOR_BUSINESS") }
    }
    private fun collectorBusiness() {
        backControl(); canvas.progress(3,3,"COLLECTOR")
        canvas.text("Data usaha kamu",29,147,342,20,600)
        canvas.text("Supaya penyetor tahu kamu menerima sampah apa\ndan bisa dijangkau di mana",29,171,342,13,color=ui.muted)
        canvas.text("Jenis kendaraan",34,236,332,15,600,ui.muted)
        if(!values.has("vehicle"))values.put("vehicle","motor")
        listOf("motor" to "Motor","pickup" to "Pick-up","truck" to "Truk").forEachIndexed { i,(id,label) ->
            selectionState(canvas.button(label,34+i*115,265,106,58,values.optString("vehicle")==id) { values.put("vehicle",id); show("COLLECTOR_BUSINESS") },values.optString("vehicle")==id,false)
        }
        field("address","Alamat","Alamat usaha atau penjemputan",30,369)
        smallAction("Pin lokasi di peta",34,433,144,::pinLocation)
        canvas.text("Jenis sampah yang diterima",37,488,325,15,600,ui.muted)
        listOf("plastic" to "Plastik","paper" to "Kertas","glass" to "Kaca","metal" to "Logam").forEachIndexed { i,(id,label) ->
            selectionState(canvas.button(label,33+(i%2)*174,520+(i/2)*51,166,48,id in accepted) { if(id in accepted)accepted.remove(id) else accepted.add(id); show("COLLECTOR_BUSINESS") },id in accepted,true)
        }
        consent(631)
        lateinit var submit: Button
        submit=canvas.button("Buat akun pengepul",30,746,342,64) { capture(); startRegistration(submit) }
        canvas.frame.minimumHeight=ui.dp(830)
    }
    private fun selectionState(view: View,checked: Boolean,multiple: Boolean) {
        view.isSelected=checked
        view.accessibilityDelegate=object: View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View,info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className=if(multiple)CheckBox::class.java.name else RadioButton::class.java.name
                info.isCheckable=true; info.isChecked=checked
            }
        }
    }
    private fun consent(y: Int) {
        val check=CheckBox(host).apply {
            isChecked=values.optBoolean("consent"); contentDescription="Setuju penggunaan data demo"
            buttonTintList=android.content.res.ColorStateList.valueOf(ui.cta)
            setOnCheckedChangeListener { _,v -> values.put("consent",v) }
        }
        val description="Saya setuju dengan penggunaan data demo,\ntermasuk berbagi alamat kepada pengepul\nyang memproses pesanan."
        if(canvas.largeText) {
            val row=ui.row(); row.addView(check,ui.lp(48,48)); row.addView(ui.text(description,12,color=ui.muted),LinearLayout.LayoutParams(0,-2,1f)); canvas.place(row,34,y,342)
        } else { canvas.place(check,34,y,40,48); canvas.text(description,78,y+3,294,12,color=ui.muted) }
        textLink("Syarat & Ketentuan · Kebijakan Privasi",65,y+43,300) {
            AlertDialog.Builder(host).setTitle("Penggunaan data demo").setMessage("Gunakan data sintetis. Nama, kontak, alamat, dan foto pilihan disimpan pada backend lokal. Profil dienkripsi. Alamat pesanan dapat dilihat pengepul demo yang berwenang. Belum ada mitra/penjemputan nyata, verifikasi NIK resmi, atau uang yang dapat dicairkan. OTP tersedia hanya pada kotak masuk demo lokal. Tim harus menetapkan kebijakan operasional sebelum publikasi.").setPositiveButton("Mengerti",null).show()
        }
    }
    private fun validateIdentity(): Boolean {
        if(values.optString("name").trim().length !in 2..80) { message("Isi nama lengkap 2–80 karakter."); return false }
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(values.optString("email").trim()).matches()) { message("Isi email yang valid."); return false }
        if(!values.optString("phone").replace(" ","").matches(Regex("\\+?\\d{9,15}"))) { message("Isi nomor WhatsApp yang valid."); return false }
        if(password.length !in 8..128 || !password.any(Char::isLetter) || !password.any(Char::isDigit)) { message("Password minimal 8 karakter, berisi huruf dan angka."); return false }
        if(password!=confirmation) { message("Konfirmasi password belum sama."); return false }
        return true
    }
    private fun encoded(bitmap: Bitmap?): String {
        if(bitmap==null)return ""
        val out=ByteArrayOutputStream(); bitmap.compress(if(bitmap.hasAlpha())Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,75,out)
        if(out.size()>90_000)throw IllegalArgumentException("Gambar terlalu besar. Pilih gambar contoh yang lebih kecil.")
        return android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)
    }
    private fun startRegistration(button: View) {
        if(!validateIdentity()) { show(if(role=="RESIDENT")"RESIDENT_INFO" else "COLLECTOR_CONTACT"); message("Periksa kembali identitas dan password."); return }
        if(values.optString("address").trim().length !in 10..400) { message("Alamat harus 10–400 karakter."); return }
        if(!values.optBoolean("consent")) { message("Centang persetujuan penggunaan data demo."); return }
        if(role=="COLLECTOR" && accepted.isEmpty()) { message("Pilih jenis sampah yang diterima."); return }
        val payload=JSONObject().put("purpose","REGISTER").put("role",role).put("password",password).put("nik",nik).put("consent",true)
        for(key in listOf("name","phone","email","address","rt","rw","area","vehicle","latitude","longitude"))payload.put(key,values.optString(key))
        payload.put("acceptedMaterials",JSONArray(accepted.toList()))
        try { payload.put("avatarBase64",encoded(avatar)); payload.put("identityImageBase64",encoded(identity)) } catch(e: IllegalArgumentException) { message(e.message.orEmpty()); return }
        request(button,{api.request("/api/auth/start",payload)}) { acceptChallenge(it,"REGISTER") }
    }

    private fun find() {
        wave("2003:448","imgPreview1"); backControl()
        canvas.image("2003:448","imgIconTemukanAkun1",157,83,88,88)
        canvas.text("Temukan akunmu",62,189,278,26,700,center=true)
        canvas.text(if(emailMode)"Masukkan email yang kamu\npakai saat mendaftar" else "Masukkan nomor WhatsApp yang\nkamu pakai saat mendaftar",52,236,298,16,color=Color.parseColor("#777777"),center=true)
        field("identifier",if(emailMode)"Alamat email" else "Nomor WhatsApp",if(emailMode)"nama@contoh.com" else "08xxxxxxxxxx",31,363,340,input=if(emailMode)InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS else InputType.TYPE_CLASS_PHONE)
        canvas.text("Kontak digunakan untuk verifikasi akun.\nGunakan akun demo atau data sintetis.",70,439,285,14,color=Color.parseColor("#777777"))
        textLink(if(emailMode)"Gunakan nomor WhatsApp" else "Gunakan email",72,476,258) { emailMode=!emailMode; show("FIND") }
        canvas.button("Lanjutkan",31,532) {
            capture(); val value=values.optString("identifier").trim()
            if(value.length<8)message("Isi nomor atau email yang kamu pakai mendaftar.") else show("PASSWORD_LOGIN")
        }
        textLink("Lupa password?",111,609,180) { show("FORGOT") }
    }
    private fun passwordLogin() {
        wave(); backControl(); canvas.image("2003:549","imgGroup84",155,80,88,88); canvas.image("2003:549","imgGroup",182,111,34,28)
        canvas.text("Masuk ke akun",50,187,302,26,700,center=true)
        canvas.text(values.optString("identifier"),40,235,322,15,color=ui.muted,center=true)
        field("password","Password","Password akunmu",31,307,340,passwordField=true)
        textLink("Lupa password?",210,374,162) { password=""; confirmation=""; fields.clear(); show("FORGOT") }
        lateinit var button: Button
        button=canvas.button("Masuk",31,532) {
            capture(); val input=JSONObject().put("identifier",values.optString("identifier")).put("password",password)
            request(button,{api.request("/api/auth/login",input)}) { authenticated(it) }
        }
        canvas.text("Mode demo lokal · Tidak ada\npenjemputan nyata yang dikirim.",50,621,302,13,color=ui.muted,center=true)
    }
    private fun forgot() {
        wave("2003:479","imgPreview2"); backControl()
        canvas.image("2003:479","imgIconLupaPassword1",157,82,88,88)
        canvas.text("Lupa password?",48,196,306,26,700,center=true)
        canvas.text("Masukkan email akunmu. Kode OTP\nakan tersedia pada kotak masuk demo\nlokal untuk membuat password baru.",39,245,324,16,color=Color.parseColor("#777777"),center=true)
        field("resetEmail","Alamat email","nama@contoh.com",31,381,340,input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        lateinit var button: Button
        button=canvas.button("Kirim kode OTP",31,529) {
            capture(); val email=values.optString("resetEmail").trim()
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { message("Isi alamat email yang valid."); return@button }
            request(button,{api.request("/api/auth/start",JSONObject().put("purpose","RESET").put("email",email))}) { acceptChallenge(it,"RESET") }
        }
    }
    private fun acceptChallenge(result: JSONObject,kind: String) {
        challenge=result.getString("challengeId"); purpose=kind; values.put("otpEmail",result.getString("email"))
        resendAt=System.currentTimeMillis()+result.optInt("resendAfter",45)*1000L; show("OTP")
    }
    private fun otp() {
        // The OTP-specific exports are unavailable; share the existing account
        // wave and recovery artwork instead of referring to missing files.
        wave(); backControl()
        canvas.image("2003:479","imgIconLupaPassword1",161,83,88,88)
        canvas.text("Masukkan kode OTP",39,190,324,26,700,center=true)
        canvas.text("Kode demo tersedia pada kotak masuk lokal",28,226,346,14,color=Color.parseColor("#777777"),center=true)
        canvas.text(values.optString("otpEmail"),30,252,342,14,600,center=true)
        textLink("Ubah email",130,282,142) { show(if(purpose=="REGISTER")if(role=="RESIDENT")"RESIDENT_INFO" else "COLLECTOR_CONTACT" else "FORGOT") }
        val cells=mutableListOf<EditText>()
        val digitsRow=if(canvas.largeText)ui.row() else null
        repeat(6) { index ->
            val cell=ui.field("",InputType.TYPE_CLASS_NUMBER).apply { textSize=ui.sp(20); typeface=ui.typeface(700); gravity=Gravity.CENTER; setPadding(0,0,0,0); setSelectAllOnFocus(true); contentDescription="Digit OTP ${index+1}"; background=ui.box(ui.surface,18,true) }
            if(digitsRow!=null)digitsRow.addView(cell,LinearLayout.LayoutParams(0,ui.dp(66),1f).apply { if(index>0)marginStart=ui.dp(6) })
            else canvas.place(cell,29+index*60,341,50,66)
            cells.add(cell)
            cell.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                    if(distributingCode)return
                    val digits=s.toString().filter(Char::isDigit)
                    if(digits.length>1) {
                        distributingCode=true
                        digits.take(6-index).forEachIndexed { offset,char -> cells[index+offset].setText(char.toString()) }
                        distributingCode=false; cells[(index+digits.length-1).coerceAtMost(5)].requestFocus()
                    } else if(digits.length==1 && index<5)cells[index+1].requestFocus()
                    else if(digits.isEmpty() && before>0 && index>0)cells[index-1].requestFocus()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        if(digitsRow!=null)canvas.place(digitsRow,29,341,344,66)
        codeFields=cells
        val resend=textLink("",73,414,256) {
            if(System.currentTimeMillis()<resendAt)return@textLink
            val payload=JSONObject().put("challengeId",challenge)
            request(work={api.request("/api/auth/resend",payload)}) { acceptChallenge(it,purpose) }
        }
        fun countdown() {
            if(phase!="OTP" || host.isDestroyed)return
            val seconds=((resendAt-System.currentTimeMillis()+999)/1000).coerceAtLeast(0)
            resend.text=if(seconds>0)"Kirim kode ulang (00.${seconds.toString().padStart(2,'0')})" else "Kirim kode ulang"
            resend.isEnabled=seconds==0L; resend.setTextColor(if(seconds==0L)ui.greenText else ui.muted)
            handler.postDelayed({ countdown() },1000)
        }
        countdown()
        lateinit var submit: Button
        submit=canvas.button("Verifikasi",34,532) {
            val code=codeFields.joinToString("") { it.text.toString() }
            if(!code.matches(Regex("\\d{6}"))) { message("Isi enam digit kode OTP."); return@button }
            val payload=JSONObject().put("challengeId",challenge).put("code",code)
            request(submit,{api.request("/api/auth/verify",payload)}) { result ->
                if(purpose=="RESET") { resetGrant=result.getString("resetToken"); password=""; confirmation=""; show("NEW_PASSWORD") }
                else authenticated(result)
            }
        }
        canvas.text("Jangan kirim kode OTP ke siapa pun",52,617,298,15,600,ui.muted,true)
        canvas.text("Kode demo dibaca oleh tim dari backend/data/outbox.\nTidak ada email atau WhatsApp sungguhan yang dikirim.",24,653,354,11,color=ui.muted,center=true)
    }
    private fun newPassword() {
        wave(); backControl(); canvas.image("2003:549","imgGroup84",155,80,88,88); canvas.image("2003:549","imgGroup",182,111,34,28)
        canvas.text("Verifikasi password",45,187,312,26,700,center=true)
        canvas.text("Silakan buat password baru untuk\nakunmu",44,218,314,15,color=Color.parseColor("#777777"),center=true)
        field("password","Password baru","Password baru",31,307,340,passwordField=true)
        field("confirmation","Konfirmasi password","Ulangi password baru",31,408,340,passwordField=true)
        val guide=ui.text("Minimal 8 karakter\nKombinasi huruf dan angka",13,color=ui.muted).apply { background=ui.box(Color.parseColor("#F8F8F8"),15); setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14)) }
        canvas.place(guide,27,504,342,73)
        lateinit var submit: Button
        submit=canvas.button("Simpan password",27,599) {
            capture()
            if(password.length !in 8..128 || !password.any(Char::isLetter) || !password.any(Char::isDigit)) { message("Password minimal 8 karakter, berisi huruf dan angka."); return@button }
            if(password!=confirmation) { message("Konfirmasi password belum sama."); return@button }
            if(resetGrant.isEmpty()) { show("FORGOT"); message("Minta kode OTP baru untuk memulai reset."); return@button }
            val payload=JSONObject().put("resetToken",resetGrant).put("password",password)
            request(submit,{api.request("/api/auth/reset",payload)}) {
                resetGrant=""; password=""; confirmation=""; fields.clear(); show("RESET_DONE")
            }
        }
    }
    private fun resetDone() {
        wave(); canvas.image("2003:479","imgIconLupaPassword1",157,175,88,88)
        canvas.text("Password berhasil diubah",22,327,358,26,700,center=true)
        canvas.text("Demi keamanan akunmu, silakan login ulang\ndengan password yang baru",34,366,334,16,color=ui.muted,center=true)
        canvas.button("Login ulang",31,545) { show("FIND") }
    }
    private fun authenticated(result: JSONObject) {
        password=""; confirmation=""; nik=""; resetGrant=""; avatar=null; identity=null; values=JSONObject(); challenge=""; purpose=""; role=""; fields.clear(); handler.removeCallbacksAndMessages(null)
        host.completeAccountAuth(result)
    }

    private fun choosePhoto() {
        AlertDialog.Builder(host).setTitle(if(photoTarget=="avatar")"Foto profil demo" else "Foto identitas contoh")
            .setItems(arrayOf("Ambil foto","Pilih dari galeri","Gunakan gambar demo")) { _,which -> when(which) {
                0 -> runCatching { host.startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),711) }.onFailure { message("Kamera tidak tersedia. Pilih galeri atau gambar demo.") }
                1 -> host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },712)
                else -> {
                    val image=host.assets.open("figma/2003_378_imgSampleUserIcon3.png").use { BitmapFactory.decodeStream(it) }
                    setPhoto(image)
                }
            } }.setNegativeButton("Batal",null).show()
    }
    @Suppress("DEPRECATION")
    fun photoResult(code: Int,result: Int,data: Intent?) {
        if(result!=Activity.RESULT_OK)return
        val image=try {
            if(code==711)data?.extras?.get("data") as? Bitmap else {
                val uri=data?.data?:return
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                host.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
                if(bounds.outWidth<=0 || bounds.outHeight<=0)throw IllegalArgumentException()
                val options=BitmapFactory.Options().apply { inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/512).coerceAtLeast(1) }
                host.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) }
            }
        } catch (_: Exception) { null }
        if(image==null)message("Gambar tidak dapat dibaca. Coba gambar lain.") else setPhoto(image)
    }
    private fun setPhoto(image: Bitmap) {
        val max=if(photoTarget=="avatar")256 else 512
        val scale=(max.toFloat()/maxOf(image.width,image.height)).coerceAtMost(1f)
        val resized=Bitmap.createScaledBitmap(image,(image.width*scale).toInt().coerceAtLeast(1),(image.height*scale).toInt().coerceAtLeast(1),true)
        if(photoTarget=="avatar")avatar=resized else identity=resized
        show(phase)
    }
    private fun pinLocation() {
        val box=ui.column(20)
        box.addView(ui.text("Pin koordinat (opsional)",18,true)); ui.gap(box,12)
        val latitude=ui.field("Latitude",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED).apply { setText(values.optString("latitude")) }
        val longitude=ui.field("Longitude",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED).apply { setText(values.optString("longitude")) }
        box.addView(latitude); ui.gap(box,12); box.addView(longitude); ui.gap(box,12)
        box.addView(ui.button("Gunakan lokasi perangkat",false) {
            if(host.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) { message("Izin lokasi belum diberikan. Koordinat dapat diisi manual."); return@button }
            val manager=host.getSystemService(LocationManager::class.java)
            val location=runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?:manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
            if(location==null)message("Lokasi belum tersedia. Isi manual atau lanjut tanpa pin.")
            else { latitude.setText(location.latitude.toString()); longitude.setText(location.longitude.toString()) }
        })
        val dialog=AlertDialog.Builder(host).setTitle("Lokasi penjemputan").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lat=latitude.text.toString().replace(',','.').toDoubleOrNull(); val lon=longitude.text.toString().replace(',','.').toDoubleOrNull()
            if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) { latitude.error="Koordinat tidak valid." }
            else { values.put("latitude",lat.toString()); values.put("longitude",lon.toString()); dialog.dismiss() }
        } }; dialog.show()
    }

    fun back() {
        when(phase) {
            "WELCOME" -> host.finish()
            "LOCATION" -> show("WELCOME")
            "NOTIFICATION" -> show("LOCATION")
            "ROLE" -> show("WELCOME")
            "RESIDENT_INFO","COLLECTOR_ID","FIND" -> show("ROLE")
            "RESIDENT_ADDRESS" -> show("RESIDENT_INFO")
            "COLLECTOR_CONTACT" -> show("COLLECTOR_ID")
            "COLLECTOR_BUSINESS" -> show("COLLECTOR_CONTACT")
            "PASSWORD_LOGIN","FORGOT","RESET_DONE" -> show("FIND")
            "OTP" -> show(if(purpose=="REGISTER")if(role=="RESIDENT")"RESIDENT_ADDRESS" else "COLLECTOR_BUSINESS" else "FORGOT")
            "NEW_PASSWORD" -> { resetGrant=""; show("FORGOT") }
            else -> show("ROLE")
        }
    }
    fun snapshot(): String {
        capture()
        return JSONObject().put("phase",phase).put("role",role).put("values",values).put("challenge",challenge).put("purpose",purpose).put("resendAt",resendAt).put("emailMode",emailMode).put("accepted",JSONArray(accepted.toList())).toString()
    }
    fun restore(value: String) {
        val state=runCatching { JSONObject(value) }.getOrElse { start(); return }
        values=state.optJSONObject("values")?:JSONObject(); role=state.optString("role"); challenge=state.optString("challenge"); purpose=state.optString("purpose"); resendAt=state.optLong("resendAt"); emailMode=state.optBoolean("emailMode")
        val types=state.optJSONArray("accepted"); if(types!=null) { accepted.clear(); repeat(types.length()) { accepted.add(types.getString(it)) } }
        val target=state.optString("phase","ROLE")
        show(when(target) { "NEW_PASSWORD" -> "FORGOT"; "RESIDENT_ADDRESS" -> "RESIDENT_INFO"; "COLLECTOR_BUSINESS","COLLECTOR_CONTACT" -> "COLLECTOR_ID"; else -> target })
        if(target in setOf("NEW_PASSWORD","RESIDENT_ADDRESS","COLLECTOR_BUSINESS","COLLECTOR_CONTACT"))message("Data rahasia tidak disimpan saat layar dimuat ulang. Isi kembali untuk melanjutkan.")
    }
    fun close() { handler.removeCallbacksAndMessages(null) }
}
