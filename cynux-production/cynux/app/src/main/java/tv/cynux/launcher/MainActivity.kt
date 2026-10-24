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
    override fun onBackPressed() { /* intentionally empty */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO ||
            keyCode == KeyEvent.KEYCODE_PROG_YELLOW) {
            prefs.edit().putInt("bg", (prefs.getInt("bg", 0) + 1) % backgrounds.size).apply()
            applyBackground()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Shell (header + scrolling content) ----------

    private fun buildShell() {
        val root = FrameLayout(this)
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
        header.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            setTextColor(textMain); textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        col.addView(header)

        // gold hairline
        col.addView(View(this).apply { setBackgroundColor(Color.parseColor("#55C9A24A")) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(56), dp(8), dp(56), dp(8)) })

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(40)) }
        scroll.addView(content)
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // footer hint
        col.addView(TextView(this).apply {
            text = "OK  open      Hold OK  favourite      MENU  change background"
            setTextColor(textDim); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        setContentView(root)
    }

    private fun applyBackground() {
        window.setBackgroundDrawableResource(backgrounds[prefs.getInt("bg", 0) % backgrounds.size])
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

        if (favs.isNotEmpty()) addRow("FAVOURITES", favs)
        if (recents.isNotEmpty()) addRow("RECENT", recents)
        addRow("ALL APPS", apps)
        addRow("SYSTEM", listOf(settings), settingsIcon)

        content.post { firstFocusable(content)?.requestFocus() }
    }

    private fun addRow(title: String, items: List<AppEntry>, iconOverride: Drawable? = null) {
        if (items.isEmpty()) return
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
        hs.addView(rowLayout)
        content.addView(hs)
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
