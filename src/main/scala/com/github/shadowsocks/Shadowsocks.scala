/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2012 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package com.github.shadowsocks

import android.app._
import android.content._
import android.content.res.AssetManager
import android.graphics.{Color, Typeface}
import android.os._
import android.preference._
import android.util.Log
import android.view._
import android.widget._
import com.google.analytics.tracking.android.{MapBuilder, EasyTracker}
import de.keyboardsurfer.android.widget.crouton.{Crouton, Style, Configuration}
import java.io._
import java.util.Hashtable
import android.content.pm.{PackageInfo, PackageManager}
import android.net.{Uri, VpnService}
import android.webkit.{WebViewClient, WebView}
import android.app.backup.BackupManager
import scala.concurrent.ops._
import net.simonvt.menudrawer.MenuDrawer

import com.github.shadowsocks.database._
import scala.collection.mutable.ListBuffer
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.utils._
import com.google.zxing.integration.android.IntentIntegrator
import scala.Some
import com.github.shadowsocks.database.Item
import com.github.shadowsocks.database.Category
import com.github.shadowsocks.fragment.{ProfileFragment, SettingsFragment}
import com.atermenji.android.iconicdroid.IconicFontDrawable
import com.atermenji.android.iconicdroid.icon.FontAwesomeIcon

object Typefaces {
  def get(c: Context, assetPath: String): Typeface = {
    cache synchronized {
      if (!cache.containsKey(assetPath)) {
        try {
          val t: Typeface = Typeface.createFromAsset(c.getAssets, assetPath)
          cache.put(assetPath, t)
        } catch {
          case e: Exception =>
            Log.e(TAG, "Could not get typeface '" + assetPath + "' because " + e.getMessage)
            return null
        }
      }
      return cache.get(assetPath)
    }
  }

  private final val TAG = "Typefaces"
  private final val cache = new Hashtable[String, Typeface]
}

object Shadowsocks {

  // Constants
  val PREFS_NAME = "Shadowsocks"

  val TAG = "Shadowsocks"
  val REQUEST_CONNECT = 1

  // Flags
  var vpnEnabled = -1
}

class Shadowsocks
  extends PreferenceActivity
  with CompoundButton.OnCheckedChangeListener
  with MenuAdapter.MenuListener {

  // Flags
  val MSG_CRASH_RECOVER: Int = 1
  val STATE_MENUDRAWER = "com.github.shadowsocks.menuDrawer"
  val STATE_ACTIVE_VIEW_ID = "com.github.shadowsocks.activeViewId"
  val STATE_CURRENT_FRAGMENT = "com.github.shadowsocks.fragment"

  // Variables
  var state = State.INIT
  var prepared = false
  var currentProfile = new Profile
  var switchButton: Switch = null
  var progressDialog: ProgressDialog = null
  var fragmentTransaction: FragmentTransaction = null
  var currentFragmentTag = ProfileFragment.TAG

  lazy val settings = PreferenceManager.getDefaultSharedPreferences(this)
  lazy val status = getSharedPreferences(Key.status, Context.MODE_PRIVATE)
  lazy val stateReceiver = new StateBroadcastReceiver
  lazy val preferenceReceiver = new PreferenceBroadcastReceiver
  lazy val drawer = MenuDrawer.attach(this)
  lazy val menuAdapter = new MenuAdapter(this, getMenuList)
  lazy val listView = new ListView(this)
  lazy val profileManager =
    new ProfileManager(settings, getApplication.asInstanceOf[ShadowsocksApplication].dbHelper)
  lazy val fragmentManager = getFragmentManager

  private val handler: Handler = new Handler {
    override def handleMessage(msg: Message) {
      msg.what match {
        case MSG_CRASH_RECOVER =>
          Crouton.makeText(Shadowsocks.this, R.string.crash_alert, Style.ALERT).show()
          status.edit().putBoolean(Key.isRunning, false).commit()
      }
      super.handleMessage(msg)
    }
  }

  def isServiceStarted(context: Context): Boolean = {
    ShadowsocksService.isServiceStarted(context) || ShadowVpnService.isServiceStarted(context)
  }

  private def showProgress(msg: String): Handler = {
    clearDialog()
    progressDialog = ProgressDialog.show(this, "", msg, true, false)
    new Handler {
      override def handleMessage(msg: Message) {
        clearDialog()
      }
    }
  }

  private def copyAssets(path: String) {
    val assetManager: AssetManager = getAssets
    var files: Array[String] = null
    try {
      files = assetManager.list(path)
    } catch {
      case e: IOException =>
        Log.e(Shadowsocks.TAG, e.getMessage)
    }
    if (files != null) {
      for (file <- files) {
        var in: InputStream = null
        var out: OutputStream = null
        try {
          if (path.length > 0) {
            in = assetManager.open(path + "/" + file)
          } else {
            in = assetManager.open(file)
          }
          out = new FileOutputStream("/data/data/com.github.shadowsocks/" + file)
          copyFile(in, out)
          in.close()
          in = null
          out.flush()
          out.close()
          out = null
        } catch {
          case ex: Exception =>
            Log.e(Shadowsocks.TAG, ex.getMessage)
        }
      }
    }
  }

  private def copyFile(in: InputStream, out: OutputStream) {
    val buffer: Array[Byte] = new Array[Byte](1024)
    var read: Int = 0
    while ( {
      read = in.read(buffer)
      read
    } != -1) {
      out.write(buffer, 0, read)
    }
  }

  private def crash_recovery() {
    val sb = new StringBuilder

    sb.append("kill -9 `cat /data/data/com.github.shadowsocks/pdnsd.pid`").append("\n")
    sb.append("kill -9 `cat /data/data/com.github.shadowsocks/shadowsocks.pid`").append("\n")
    sb.append("kill -9 `cat /data/data/com.github.shadowsocks/tun2socks.pid`").append("\n")
    sb.append("killall -9 pdnsd").append("\n")
    sb.append("killall -9 shadowsocks").append("\n")
    sb.append("killall -9 tun2socks").append("\n")
    sb.append("rm /data/data/com.github.shadowsocks/pdnsd.conf").append("\n")
    Utils.runCommand(sb.toString())

    sb.clear()
    sb.append("kill -9 `cat /data/data/com.github.shadowsocks/redsocks.pid`").append("\n")
    sb.append("killall -9 redsocks").append("\n")
    sb.append("rm /data/data/com.github.shadowsocks/redsocks.conf").append("\n")
    sb.append(Utils.getIptables).append(" -t nat -F OUTPUT").append("\n")
    Utils.runRootCommand(sb.toString())
  }

  private def getVersionName: String = {
    var version: String = null
    try {
      val pi: PackageInfo = getPackageManager.getPackageInfo(getPackageName, 0)
      version = pi.versionName
    } catch {
      case e: PackageManager.NameNotFoundException =>
        version = "Package name not found"
    }
    version
  }

  private def isTextEmpty(s: String, msg: String): Boolean = {
    if (s == null || s.length <= 0) {
      showDialog(msg)
      return true
    }
    false
  }

  def prepareStartService() {
    showProgress(getString(R.string.connecting))
    spawn {
      if (isVpnEnabled) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
          startActivityForResult(intent, Shadowsocks.REQUEST_CONNECT)
        } else {
          onActivityResult(Shadowsocks.REQUEST_CONNECT, Activity.RESULT_OK, null)
        }
      } else {
        if (!serviceStart) {
          switchButton.setChecked(false)
        }
      }
    }
  }

  def onCheckedChanged(compoundButton: CompoundButton, checked: Boolean) {
    if (compoundButton eq switchButton) {
      checked match {
        case true =>
          prepareStartService()
        case false =>
          serviceStop()
      }
    }
  }

  def getLayoutView(view: ViewParent): LinearLayout = {
    view match {
      case layout: LinearLayout => layout
      case _ => if (view != null) getLayoutView(view.getParent) else null
    }
  }

  def initAdView() {
    if (settings.getString(Key.proxy, "") == "198.199.101.152") {
      val layoutView = {
        if (Build.VERSION.SDK_INT > 10) {
          drawer.getContentContainer.asInstanceOf[ViewGroup].getChildAt(0)
        } else {
          getLayoutView(drawer.getContentContainer.getParent)
        }
      }
    }
  }

  override def setContentView(layoutResId: Int) {
    drawer.setContentView(layoutResId)
    onContentChanged()
  }

  /** Called when the activity is first created. */
  override def onCreate(savedInstanceState: Bundle) {

    // Initialize preference
    super.onCreate(savedInstanceState)

    // Initialize profile
    currentProfile = {
      profileManager.getProfile(settings.getInt(Key.profileId, -1)) getOrElse currentProfile
    }

    // Initialize drawer
    menuAdapter.setActiveId(settings.getInt(Key.profileId, -1))
    menuAdapter.setListener(this)
    listView.setAdapter(menuAdapter)
    drawer.setMenuView(listView)
    // The drawable that replaces the up indicator in the action bar
    drawer.setSlideDrawable(R.drawable.ic_drawer)
    // Whether the previous drawable should be shown
    drawer.setDrawerIndicatorEnabled(true)

    // Initialize action bar
    val switchLayout = getLayoutInflater
      .inflate(R.layout.layout_switch, null)
      .asInstanceOf[RelativeLayout]
    val title: TextView = switchLayout.findViewById(R.id.title).asInstanceOf[TextView]
    val tf: Typeface = Typefaces.get(this, "fonts/Iceland.ttf")
    if (tf != null) title.setTypeface(tf)
    switchButton = switchLayout.findViewById(R.id.switchButton).asInstanceOf[Switch]
    getActionBar.setCustomView(switchLayout)
    getActionBar.setDisplayShowTitleEnabled(false)
    getActionBar.setDisplayShowCustomEnabled(true)
    getActionBar.setIcon(R.drawable.ic_stat_shadowsocks)

    // Register broadcast receiver
    registerReceiver(stateReceiver, new IntentFilter(Action.UPDATE_STATE))
    registerReceiver(preferenceReceiver, new IntentFilter(Action.UPDATE_PREFS))

    // Initialize fragments
    if (savedInstanceState != null) {
    }

    // Update status
    if (!isServiceStarted(this)) {
      spawn {
        status.edit.putBoolean(Key.isRoot, Utils.getRoot).apply()
      }
      if (!status.getBoolean(getVersionName, false)) {
        val h = showProgress(getString(R.string.initializing))
        status.edit.putBoolean(getVersionName, true).apply()
        spawn {
          reset()
          currentProfile = profileManager.create()
          navigateToFragment(drawer.getContentContainer.getId, getFragment(ProfileFragment.TAG),
            ProfileFragment.TAG, back = false)
          commitTransactions()
          h.sendEmptyMessage(0)
        }
        return
      }
    }

    navigateToFragment(drawer.getContentContainer.getId, getFragment(ProfileFragment.TAG),
      ProfileFragment.TAG, back = false)
    commitTransactions()
  }

  def openSettings() {
    currentFragmentTag = SettingsFragment.TAG
    navigateToFragment(drawer.getContentContainer.getId, getFragment(SettingsFragment.TAG),
      SettingsFragment.TAG, back = true)
    commitTransactions()
  }

  def ensureTransaction(): FragmentTransaction = {
    if (fragmentTransaction == null) {
      fragmentTransaction = fragmentManager.beginTransaction()
    }

    fragmentTransaction
  }

  def getFragment(tag: String): Fragment = {
    var f = fragmentManager.findFragmentByTag(tag)

    if (f == null) {
      tag match {
        case ProfileFragment.TAG => f = ProfileFragment.instance
        case SettingsFragment.TAG => f = SettingsFragment.instance
        case _ => f = ProfileFragment.instance
      }
    }

    f
  }

  def navigateToFragment(layout: Int, f: Fragment, tag: String, back: Boolean) {
    if (f != null && !f.isAdded) {
      ensureTransaction()
      fragmentTransaction.replace(layout, f, tag)
      if (back) fragmentTransaction.addToBackStack(null)
    }
  }

  def commitTransactions() {
    if (fragmentTransaction != null && !fragmentTransaction.isEmpty) {
      fragmentTransaction.commit()
      fragmentTransaction = null
    }
  }

  override def onRestoreInstanceState(inState: Bundle) {
    super.onRestoreInstanceState(inState)
    drawer.restoreState(inState.getParcelable(STATE_MENUDRAWER))
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelable(STATE_MENUDRAWER, drawer.saveState())
    outState.putInt(STATE_ACTIVE_VIEW_ID, currentProfile.id)
  }

  override def onBackPressed() {
    val drawerState = drawer.getDrawerState
    if (drawerState == MenuDrawer.STATE_OPEN || drawerState == MenuDrawer.STATE_OPENING) {
      drawer.closeMenu()
      return
    }
    super.onBackPressed()
  }

  override def onActiveViewChanged(v: View, pos: Int) {
    drawer.setActiveView(v, pos)
  }

  def newProfile(id: Int) {

    val items = getResources.getStringArray(R.array.add_profile_methods)

    val adapter = new
        ArrayAdapter[String](this, android.R.layout.select_dialog_item, android.R.id.text1, items) {
      override def getView(pos: Int, convertView: View, parent: ViewGroup): View = {
        val v = super.getView(pos, convertView, parent)
        val tv = v.findViewById(android.R.id.text1).asInstanceOf[TextView]
        val icon = new IconicFontDrawable(getContext)

        if (pos == 0) {
          icon.setIcon(FontAwesomeIcon.QRCODE)
        } else {
          icon.setIcon(FontAwesomeIcon.WRENCH)
        }
        icon.setIconColor(Color.DKGRAY)
          icon.setIntrinsicHeight(Utils.dpToPx(getContext, 32).toInt)
          icon.setIntrinsicWidth(Utils.dpToPx(getContext, 32).toInt)

        tv.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        tv.setCompoundDrawablePadding(Utils.dpToPx(getContext, 8).toInt)

        v
      }
    }

    val builder = new AlertDialog.Builder(this)
    builder
      .setTitle(R.string.add_profile)
      .setAdapter(adapter, new DialogInterface.OnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
        which match {
          case 0 =>
            dialog.dismiss()
            val h = showProgress(getString(R.string.loading))
            h.postDelayed(new Runnable() {
              def run() {
                val integrator = new IntentIntegrator(Shadowsocks.this)
                integrator.initiateScan()
                h.sendEmptyMessage(0)
              }
            }, 600)
          case 1 =>
            dialog.dismiss()
            addProfile(id)
          case _ =>
        }
      }
    })
    builder.create().show()
  }

  def addProfile(profile: Profile) {
    drawer.closeMenu(true)

    val h = showProgress(getString(R.string.loading))

    handler.postDelayed(new Runnable {
      def run() {
        currentProfile = profile
        profileManager.createOrUpdateProfile(currentProfile)
        profileManager.reload(currentProfile.id)
        menuAdapter.updateList(getMenuList, currentProfile.id)

        sendBroadcast(new Intent(Action.UPDATE_FRAGMENT))

        h.sendEmptyMessage(0)
      }
    }, 600)
  }

  def addProfile(id: Int) {
    drawer.closeMenu(true)

    val h = showProgress(getString(R.string.loading))

    handler.postDelayed(new Runnable {
      def run() {
        currentProfile = profileManager.reload(id)
        profileManager.save()
        menuAdapter.updateList(getMenuList, currentProfile.id)

        sendBroadcast(new Intent(Action.UPDATE_FRAGMENT))

        h.sendEmptyMessage(0)
      }
    }, 600)
  }

  def updateProfile(id: Int) {
    drawer.closeMenu(true)

    val h = showProgress(getString(R.string.loading))

    handler.postDelayed(new Runnable {
      def run() {
        currentProfile = profileManager.reload(id)
        menuAdapter.setActiveId(id)
        menuAdapter.notifyDataSetChanged()

        sendBroadcast(new Intent(Action.UPDATE_FRAGMENT))

        h.sendEmptyMessage(0)
      }
    }, 600)
  }

  def delProfile(id: Int): Boolean = {
    drawer.closeMenu(true)

    val profile = profileManager.getProfile(id)

    if (!profile.isDefined) return false

    new AlertDialog.Builder(this)
      .setMessage(String.format(getString(R.string.remove_profile), profile.get.name))
      .setCancelable(false)
      .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, i: Int) = dialog.cancel()
    })
      .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, i: Int) {
        profileManager.delProfile(id)
        val profileId = {
          val profiles = profileManager.getAllProfiles.getOrElse(List[Profile]())
          if (profiles.isEmpty) -1 else profiles(0).id
        }
        currentProfile = profileManager.load(profileId)
        menuAdapter.updateList(getMenuList, currentProfile.id)

        sendBroadcast(new Intent(Action.UPDATE_FRAGMENT))

        dialog.dismiss()
      }
    })
      .create()
      .show()

    true
  }

  def getProfileList: List[Item] = {
    val list = profileManager.getAllProfiles getOrElse List[Profile]()
    list.map(p => new Item(p.id, p.name, -1, updateProfile, delProfile))
  }

  def getMenuList: List[Any] = {

    val buf = new ListBuffer[Any]()

    buf += new Category(getString(R.string.profiles))

    buf ++= getProfileList

    buf +=
      new Item(-400, getString(R.string.add_profile), android.R.drawable.ic_menu_add, newProfile)

    buf += new Category(getString(R.string.settings))

    buf += new Item(-100, getString(R.string.recovery), android.R.drawable.ic_menu_revert, _ => {
      EasyTracker
        .getInstance(this)
        .send(MapBuilder.createEvent(Shadowsocks.TAG, "reset", getVersionName, null).build())
      recovery()
    })

    buf +=
      new Item(-200, getString(R.string.flush_dnscache), android.R.drawable.ic_menu_delete, _ => {
        EasyTracker
          .getInstance(this)
          .send(
            MapBuilder.createEvent(Shadowsocks.TAG, "flush_dnscache", getVersionName, null).build())
        flushDnsCache()
      })

    buf += new Item(-300, getString(R.string.about), android.R.drawable.ic_menu_info_details, _ => {
      EasyTracker
        .getInstance(this)
        .send(MapBuilder.createEvent(Shadowsocks.TAG, "about", getVersionName, null).build())
      showAbout()
    })

    buf.toList
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home =>
        drawer.toggleMenu()
        return true
    }
    super.onOptionsItemSelected(item)
  }

  protected override def onPause() {
    super.onPause()
    prepared = false
  }

  protected override def onResume() {
    super.onResume()
    if (!prepared) {
      if (isServiceStarted(this)) {
        switchButton.setChecked(true)
        if (ShadowVpnService.isServiceStarted(this)) {
          val style = new Style.Builder().setBackgroundColorValue(Style.holoBlueLight).build()
          val config = new Configuration.Builder().setDuration(Configuration.DURATION_LONG).build()
          switchButton.setEnabled(false)
          Crouton
            .makeText(Shadowsocks.this, R.string.vpn_status, style)
            .setConfiguration(config)
            .show()
        }
        onStateChanged(State.CONNECTED, null)
      } else {
        switchButton.setEnabled(true)
        switchButton.setChecked(false)
        Crouton.cancelAllCroutons()
        if (status.getBoolean(Key.isRunning, false)) {
          spawn {
            crash_recovery()
            handler.sendEmptyMessage(MSG_CRASH_RECOVER)
          }
        }
        onStateChanged(State.STOPPED, null)
      }
    }

    switchButton.setOnCheckedChangeListener(this)

    Config.refresh(this)
  }

  override def onStart() {
    super.onStart()
    EasyTracker.getInstance(this).activityStart(this)
  }

  override def onStop() {
    super.onStop()
    EasyTracker.getInstance(this).activityStop(this)
    clearDialog()
  }

  override def onDestroy() {
    super.onDestroy()
    Crouton.cancelAllCroutons()
    unregisterReceiver(stateReceiver)
    unregisterReceiver(preferenceReceiver)
    new BackupManager(this).dataChanged()
  }

  def reset() {
    crash_recovery()
    copyAssets(System.getABI)
    Utils.runCommand("chmod 755 /data/data/com.github.shadowsocks/iptables\n"
      + "chmod 755 /data/data/com.github.shadowsocks/redsocks\n"
      + "chmod 755 /data/data/com.github.shadowsocks/pdnsd\n"
      + "chmod 755 /data/data/com.github.shadowsocks/shadowsocks\n"
      + "chmod 755 /data/data/com.github.shadowsocks/tun2socks\n")
  }

  private def recovery() {
    val h = showProgress(getString(R.string.recovering))

    serviceStop()
    spawn {
      reset()
      h.sendEmptyMessage(0)
    }
  }

  private def flushDnsCache() {
    val h = showProgress(getString(R.string.flushing))
    spawn {
      Utils.toggleAirplaneMode(getBaseContext)
      h.sendEmptyMessage(0)
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
    if (scanResult != null) {
      Parser.parse(scanResult.getContents) match {
        case Some(profile) => addProfile(profile)
        case _ => // ignore
      }
    } else {
      resultCode match {
        case Activity.RESULT_OK =>
          prepared = true
          if (!serviceStart) {
            switchButton.setChecked(false)
          }
        case _ =>
          clearDialog()
          Log.e(Shadowsocks.TAG, "Failed to start VpnService")
      }
    }
  }

  def isVpnEnabled: Boolean = {
    if (Shadowsocks.vpnEnabled < 0) {
      Shadowsocks.vpnEnabled = if (Build.VERSION.SDK_INT
        >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !Utils.getRoot) {
        1
      } else {
        0
      }
    }
    if (Shadowsocks.vpnEnabled == 1) true else false
  }

  def serviceStop() {
    sendBroadcast(new Intent(Action.CLOSE))
  }

  /** Called when connect button is clicked. */
  def serviceStart: Boolean = {

    val proxy = settings.getString(Key.proxy, "")
    if (isTextEmpty(proxy, getString(R.string.proxy_empty))) return false
    val portText = settings.getString(Key.localPort, "")
    if (isTextEmpty(portText, getString(R.string.port_empty))) return false
    try {
      val port: Int = Integer.valueOf(portText)
      if (port <= 1024) {
        this.showDialog(getString(R.string.port_alert))
        return false
      }
    } catch {
      case ex: Exception =>
        this.showDialog(getString(R.string.port_alert))
        return false
    }

    if (isVpnEnabled) {
      if (ShadowVpnService.isServiceStarted(this)) return false
      val intent: Intent = new Intent(this, classOf[ShadowVpnService])
      Extra.put(settings, intent)
      startService(intent)
      val style = new Style.Builder().setBackgroundColorValue(Style.holoBlueLight).build()
      val config = new Configuration.Builder().setDuration(Configuration.DURATION_LONG).build()
      Crouton.makeText(Shadowsocks.this, R.string.vpn_status, style).setConfiguration(config).show()
      switchButton.setEnabled(false)
    } else {
      if (ShadowsocksService.isServiceStarted(this)) return false
      val intent: Intent = new Intent(this, classOf[ShadowsocksService])
      Extra.put(settings, intent)
      startService(intent)
    }
    true
  }

  private def showAbout() {

    val web = new WebView(this)
    web.loadUrl("file:///android_asset/pages/about.html")
    web.setWebViewClient(new WebViewClient() {
      override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean = {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
      }
    })

    var versionName = ""
    try {
      versionName = getPackageManager.getPackageInfo(getPackageName, 0).versionName
    } catch {
      case ex: PackageManager.NameNotFoundException =>
        versionName = ""
    }

    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.about_title).format(versionName))
      .setCancelable(false)
      .setNegativeButton(getString(R.string.ok_iknow), new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, id: Int) {
        dialog.cancel()
      }
    })
      .setView(web)
      .create()
      .show()
  }

  private def showDialog(msg: String) {
    val builder: AlertDialog.Builder = new AlertDialog.Builder(this)
    builder
      .setMessage(msg)
      .setCancelable(false)
      .setNegativeButton(getString(R.string.ok_iknow), new DialogInterface.OnClickListener {
      def onClick(dialog: DialogInterface, id: Int) {
        dialog.cancel()
      }
    })
    val alert: AlertDialog = builder.create
    alert.show()
  }

  def clearDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss()
      progressDialog = null
    }
  }

  def onStateChanged(s: Int, m: String) {
    if (state != s) {
      state = s
      state match {
        case State.CONNECTING =>
          if (progressDialog == null) {
            progressDialog = ProgressDialog
              .show(Shadowsocks.this, "", getString(R.string.connecting), true, true)
          }
        case State.CONNECTED =>
          clearDialog()
          if (!switchButton.isChecked) switchButton.setChecked(true)
        case State.STOPPED =>
          clearDialog()
          if (switchButton.isChecked) {
            switchButton.setEnabled(true)
            switchButton.setChecked(false)
            Crouton.cancelAllCroutons()
          }
          if (m != null) {
            Crouton.cancelAllCroutons()
            val style = new Style.Builder().setBackgroundColorValue(Style.holoRedLight).build()
            val config = new Configuration.Builder()
              .setDuration(Configuration.DURATION_LONG)
              .build()
            Crouton
              .makeText(Shadowsocks.this, getString(R.string.vpn_error).format(m), style)
              .setConfiguration(config)
              .show()
          }
      }
      sendBroadcast(new Intent(Action.UPDATE_FRAGMENT))
    }
  }

  class PreferenceBroadcastReceiver extends BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent) {
      currentProfile = profileManager.save()
      menuAdapter.updateList(getMenuList, currentProfile.id)
    }
  }

  class StateBroadcastReceiver extends BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent) {
      val state = intent.getIntExtra(Extra.STATE, State.INIT)
      val message = intent.getStringExtra(Extra.MESSAGE)
      onStateChanged(state, message)
    }
  }

}
