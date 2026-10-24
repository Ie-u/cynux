package tv.cynux.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * CYNUX PRODUCTION launcher.
 * Plain Activity + programmatic views, zero library dependencies, to stay light on a 1 GB device.
 *
 * Remote controls:
 *  - D-pad: move focus. OK: open app.
 *  - Long-press OK: add/remove favourite.
 *  - MENU (or the colour/info key if your remote has one): cycle the background theme.
 *  - BACK / HOME: stay on this screen (a launcher has nowhere to go back to).
 */
class MainActivity : Activity() {

    private val gold = Color.parseColor("#C9A24A")
    private val goldSoft = Color.parseColor("#E6C46A")
    private val textMain = Color.parseColor("#F2F2EC")
    private val textDim = Color.parseColor("#9AA79F")

    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var netText: TextView
    private lateinit var wallpaperView: ImageView
    private var activePage = "Home"
    private val ui = Handler(Looper.getMainLooper())
    private val netTick = object : Runnable {
        override fun run() { netText.text = networkLabel(); ui.postDelayed(this, 10_000) }
    }

    private val backgrounds = intArrayOf(R.drawable.bg_forest, R.drawable.bg_onyx, R.drawable.bg_emerald)

    private data class AppEntry(val pkg: String, val activity: String, val label: String, val intent: Intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cynux", MODE_PRIVATE)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        buildShell()
    }

    override fun onResume() {
        super.onResume()
        applyBackground()
        populate()
        ui.post(netTick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(netTick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // HOME pressed while already here: return focus to the first tile.
        window.decorView.post { content.findFocus()?.clearFocus(); firstFocusable(content)?.requestFocus() }
    }

    @Deprecated("Launcher: swallow BACK")
    override fun onBackPressed() {
        if (activePage != "Home") { activePage = "Home"; buildShell() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            prefs.edit().putString("wallpaper", uri.toString()).apply()
            wallpaperView.setImageURI(uri)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            navigate("Settings")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Shell (header + scrolling content) ----------

    private fun buildShell() {
        val root = FrameLayout(this)
        wallpaperView = ImageView(this).apply {
            val savedWallpaper = prefs.getString("wallpaper", null)
            if (savedWallpaper == null) setImageResource(R.drawable.cynux_headquarters)
            else try { setImageURI(android.net.Uri.parse(savedWallpaper)) } catch (_: Exception) { setImageResource(R.drawable.cynux_headquarters) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xB900090B.toInt(), 0x5100080A, 0xAA00080A.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, ViewGroup.LayoutParams(-1, -1))

        // Header: logo, wordmark, network, clock. Uses overscan-safe padding.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(56), dp(32), dp(56), dp(8))
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.logo_mark) }, LinearLayout.LayoutParams(dp(64), dp(64)))

        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, 0, 0) }
        brand.addView(TextView(this).apply {
            text = "CYNUX"; setTextColor(textMain); textSize = 30f; letterSpacing = 0.25f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        brand.addView(TextView(this).apply {
            text = "PRODUCTION"; setTextColor(gold); textSize = 14f; letterSpacing = 0.55f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        netText = TextView(this).apply { setTextColor(textDim); textSize = 16f; setPadding(0, 0, dp(28), 0) }
        header.addView(netText)
        val timeDate = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        timeDate.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            setTextColor(textMain); textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        timeDate.addView(TextClock(this).apply {
            format12Hour = "EEE, d MMM yyyy"; format24Hour = "EEE, d MMM yyyy"
            setTextColor(textDim); textSize = 11f
        })
        header.addView(timeDate)
        val settingsShortcut = TextView(this).apply {
            text = "⚙\nSettings"; gravity = Gravity.CENTER; textSize = 13f; setTextColor(textMain)
            isFocusable = true; isClickable = true
            setOnClickListener { navigate("Settings") }
        }
        header.addView(settingsShortcut, LinearLayout.LayoutParams(dp(70), dp(58)))
        col.addView(header)

        // gold hairline
        col.addView(View(this).apply { setBackgroundColor(Color.parseColor("#55C9A24A")) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(56), dp(8), dp(56), dp(8)) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(14), dp(8), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(0xCC001719.toInt()); setStroke(dp(1), 0x3358B8AC)
            }
        }
        listOf("Home" to "⌂", "Apps" to "▦", "Favorites" to "♡", "Settings" to "⚙",
            "File Manager" to "▱", "Network" to "⌁", "Media Center" to "▷", "About TV" to "ⓘ")
            .forEach { (name, glyph) ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    isFocusable = true; isClickable = true; setPadding(dp(7), 0, 0, 0)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (activePage == name) 0xFFEAC05B.toInt() else 0x00000000)
                    }
                }
                item.addView(TextView(this).apply {
                    text = glyph; textSize = 22f; gravity = Gravity.CENTER
                    setTextColor(if (activePage == name) Color.BLACK else textMain)
                }, LinearLayout.LayoutParams(dp(34), -1))
                item.addView(TextView(this).apply {
                    text = name; textSize = 12f
                    setTextColor(if (activePage == name) Color.BLACK else textDim)
                })
                item.layoutParams = LinearLayout.LayoutParams(-1, dp(45)).apply { setMargins(0, dp(3), 0, dp(3)) }
                item.setOnClickListener { navigate(name) }
                sidebar.addView(item)
            }
        body.addView(sidebar, LinearLayout.LayoutParams(dp(148), -1))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(40)) }
        scroll.addView(content)
        body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(14), 0, 0, 0) })
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        // footer hint
        col.addView(TextView(this).apply {
            text = "OK  open      Hold OK  add/remove favourite      MENU  launcher settings"
            setTextColor(textDim); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        setContentView(root)
    }

    private fun applyBackground() {
        // The Cynux headquarters photograph is the full-screen default wallpaper.
    }

    private fun navigate(name: String) {
        activePage = name
        when (name) {
            "Settings" -> populate()
            "Network" -> try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (e: Exception) {}
            "About TV" -> try { startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) } catch (e: Exception) {}
            "File Manager" -> try {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" })
            } catch (e: Exception) { Toast.makeText(this, "No file manager is available", Toast.LENGTH_SHORT).show() }
            else -> buildShell()
        }
    }

    // ---------- Data ----------

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri: ResolveInfo in pm.queryIntentActivities(q, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == packageName || !seen.add(ai.packageName)) continue
                val it = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(ai.packageName, ai.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                out.add(AppEntry(ai.packageName, ai.name, ri.loadLabel(pm).toString(), it))
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun csv(key: String): MutableList<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun populate() {
        content.removeAllViews()
        val apps = loadApps()
        val byPkg = apps.associateBy { it.pkg }
        val favs = csv("fav").mapNotNull { byPkg[it] }
        val recents = csv("recent").mapNotNull { byPkg[it] }

        val settings = AppEntry("settings", "", "Settings",
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val settingsIcon: Drawable? = try { packageManager.getActivityIcon(settings.intent) } catch (e: Exception) { null }

        if (activePage == "Settings") {
            addSettings()
        } else if (activePage == "Favorites") {
            addHero()
            addRow("FAVOURITE APPS", favs, addCard = true)
        } else if (activePage == "Apps") {
            addHero()
            addRow("ALL APPS", apps)
        } else if (activePage == "Media Center") {
            val mediaApps = apps.filter { app ->
                listOf("video", "media", "music", "player", "gallery", "tv").any(app.label.lowercase()::contains)
            }
            addRow("MEDIA APPS", mediaApps)
        } else {
            addHero()
            addRow("FAVOURITE APPS", favs, addCard = true)
            addRow("RECENT APPS", recents)
            addRecommendedRow()
            addRow("SYSTEM", listOf(settings), settingsIcon)
        }

        content.post { firstFocusable(content)?.requestFocus() }
    }

    private fun addRecommendedRow() {
        content.addView(TextView(this).apply {
            text = "RECOMMENDED FOR YOU"; textSize = 14f; setTextColor(textMain)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(60), dp(14), 0, dp(6))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Discover", "Stream", "Play", "Innovate", "Enjoy").forEachIndexed { i, title ->
            val card = TextView(this).apply {
                text = title; textSize = 14f; gravity = Gravity.BOTTOM or Gravity.START
                setPadding(dp(12), 0, 0, dp(8)); setTextColor(textMain); isFocusable = true
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xC9003737.toInt() - i * 0x00030303, 0xDD081013.toInt())).apply {
                    cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0x556AC4B5)
                }
                setOnFocusChangeListener { v, focused -> v.animate().scaleX(if (focused) 1.05f else 1f).scaleY(if (focused) 1.05f else 1f).setDuration(110).start() }
                setOnClickListener { navigate(if (title == "Stream" || title == "Play") "Apps" else "About TV") }
            }
            row.addView(card, LinearLayout.LayoutParams(dp(145), dp(74)).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        scroller.addView(row); content.addView(scroller)
    }

    private fun addSettings() {
        val title = TextView(this).apply { text = "Launcher Settings"; textSize = 24f; setTextColor(textMain); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(60), dp(12), 0, dp(10)) }
        content.addView(title)
        content.addView(settingTile("Choose wallpaper", "Select a picture from USB or device storage") {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            try { startActivityForResult(picker, 41) } catch (_: Exception) { Toast.makeText(this, "No image picker is available", Toast.LENGTH_SHORT).show() }
        })
        content.addView(settingTile("Restore Cynux background", "Use the supplied headquarters image") {
            prefs.edit().remove("wallpaper").apply(); wallpaperView.setImageResource(R.drawable.cynux_headquarters)
        })
        content.addView(settingTile("Android system settings", "Open the TV's built-in settings") {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
        })
    }

    private fun settingTile(title: String, detail: String, action: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(7), dp(12), dp(7)); isFocusable = true; isClickable = true; background = stateBg()
        }
        tile.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(textMain); typeface = Typeface.DEFAULT_BOLD })
        tile.addView(TextView(this).apply { text = detail; textSize = 11f; setTextColor(textDim) })
        tile.layoutParams = LinearLayout.LayoutParams(-1, dp(59)).apply { setMargins(dp(58), dp(4), dp(24), dp(4)) }
        tile.setOnClickListener { action() }
        return tile
    }

    private fun addHero() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(8), dp(4))
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply { text = "—  W E L C O M E   T O  —"; textSize = 12f; setTextColor(gold) })
        copy.addView(TextView(this).apply {
            text = "Cynux"; textSize = 46f; setTextColor(textMain)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        copy.addView(TextView(this).apply { text = "P R O D U C T I O N"; textSize = 15f; setTextColor(gold) })
        copy.addView(TextView(this).apply {
            text = "Technology  |  Design  |  Ideas  |  Your Future"
            textSize = 13f; setTextColor(textMain); setPadding(0, dp(7), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val promo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xBB003034.toInt(), 0xD9000A0C.toInt())).apply {
                cornerRadius = dp(15).toFloat(); setStroke(dp(1), 0xAA39B7A5.toInt())
            }
        }
        promo.addView(TextView(this).apply { text = "S O L U T I O N S"; textSize = 9f; setTextColor(gold) })
        promo.addView(TextView(this).apply { text = "Explore More\nWith Cynux"; textSize = 16f; setTextColor(textMain); typeface = Typeface.DEFAULT_BOLD })
        promo.addView(TextView(this).apply { text = "Smart Solutions For Modern Living"; textSize = 10f; setTextColor(textDim) })
        row.addView(promo, LinearLayout.LayoutParams(dp(180), dp(112)).apply { setMargins(dp(10), 0, 0, 0) })
        content.addView(row, LinearLayout.LayoutParams(-1, dp(148)))
    }

    private fun addRow(title: String, items: List<AppEntry>, iconOverride: Drawable? = null, addCard: Boolean = false) {
        if (items.isEmpty() && !addCard) return
        content.addView(TextView(this).apply {
            text = title; setTextColor(gold); textSize = 14f; letterSpacing = 0.3f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(60), dp(18), 0, dp(8))
        })
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false
            setPadding(dp(52), dp(4), dp(52), dp(4))
        }
        val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (a in items) rowLayout.addView(makeTile(a, iconOverride))
        if (addCard) rowLayout.addView(makeActionTile("+", "Add App") { activePage = "Apps"; populate() })
        hs.addView(rowLayout)
        content.addView(hs)
    }

    private fun makeActionTile(icon: String, title: String, action: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; background = stateBg()
        }
        tile.addView(TextView(this).apply { text = icon; textSize = 30f; setTextColor(gold); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(64), dp(65)))
        tile.addView(TextView(this).apply { text = title; textSize = 13f; setTextColor(textMain); gravity = Gravity.CENTER })
        tile.layoutParams = LinearLayout.LayoutParams(dp(130), dp(91)).apply { setMargins(dp(8), dp(4), dp(8), dp(4)) }
        tile.setOnFocusChangeListener { v, focused -> v.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f).setDuration(110).start() }
        tile.setOnClickListener { action() }
        return tile
    }

    private fun makeTile(a: AppEntry, iconOverride: Drawable?): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(14), dp(10), dp(10))
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            background = stateBg()
        }
        val icon: Drawable? = iconOverride ?: try { packageManager.getActivityIcon(a.intent) } catch (e: Exception) { null }
        tile.addView(ImageView(this).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(dp(72), dp(72)))
        tile.addView(TextView(this).apply {
            text = a.label; setTextColor(textMain); textSize = 14f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(dp(110), -2))
        tile.layoutParams = LinearLayout.LayoutParams(dp(130), -2).apply { setMargins(dp(8), dp(4), dp(8), dp(4)) }

        // Light focus animation: scale only (cheap on a Mali-450).
        tile.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(110).start()
        }
        tile.setOnClickListener { launch(a) }
        tile.setOnLongClickListener { toggleFav(a); true }
        return tile
    }

    private fun stateBg(): Drawable {
        val sl = android.graphics.drawable.StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_focused), getDrawable(R.drawable.tile_focused))
        sl.addState(intArrayOf(), getDrawable(R.drawable.tile_normal))
        return sl
    }

    private fun launch(a: AppEntry) {
        try {
            startActivity(a.intent)
            if (a.pkg != "settings") {
                val r = csv("recent"); r.remove(a.pkg); r.add(0, a.pkg)
                prefs.edit().putString("recent", r.take(12).joinToString(",")).apply()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFav(a: AppEntry) {
        if (a.pkg == "settings") return
        val f = csv("fav")
        val added = if (f.contains(a.pkg)) { f.remove(a.pkg); false } else { f.add(a.pkg); true }
        prefs.edit().putString("fav", f.joinToString(",")).apply()
        Toast.makeText(this, if (added) "Added to Favourites" else "Removed from Favourites", Toast.LENGTH_SHORT).show()
        populate()
    }

    // ---------- Helpers ----------

    private fun networkLabel(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
            else -> "Online"
        }
    }

    private fun firstFocusable(v: View): View? {
        if (v.isFocusable && v !is ViewGroup) return v
        if (v is ViewGroup) {
            if (v.isFocusable) return v
            for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
