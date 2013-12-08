/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2013 <max.c.lv@gmail.com>
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

package com.github.shadowsocks.fragment

import android.preference.{CheckBoxPreference, ListPreference, Preference, PreferenceFragment}
import android.content.{Intent, IntentFilter, Context, BroadcastReceiver}
import com.github.shadowsocks.utils.{Action, Key, State}
import android.os.Bundle
import com.github.shadowsocks.{Shadowsocks, R}
import com.github.shadowsocks.preference.{ProfileEditTextPreference, SummaryEditTextPreference, PasswordEditTextPreference}
import com.github.shadowsocks.database.Profile

object SettingsFragment {
  val TAG = "com.github.shadowsocks.fragment.SettingsFragment"
  def instance = new SettingsFragment
}

class SettingsFragment extends PreferenceFragment {

  var receiver: BroadcastReceiver = null

  val PROXY_PREFS = Array(Key.profileName, Key.proxy, Key.remotePort, Key.localPort, Key.sitekey,
    Key.encMethod)
  val FEATRUE_PREFS = Array(Key.isGFWList, Key.isGlobalProxy, Key.proxyedApps, Key.isTrafficStat,
    Key.isAutoConnect)

  def activity = getActivity.asInstanceOf[Shadowsocks]

  // Help functions
  def updateListPreference(pref: Preference, value: String) {
    pref.setSummary(value)
    pref.asInstanceOf[ListPreference].setValue(value)
  }

  def updatePasswordEditTextPreference(pref: Preference, value: String) {
    pref.setSummary(value)
    pref.asInstanceOf[PasswordEditTextPreference].setText(value)
  }

  def updateSummaryEditTextPreference(pref: Preference, value: String) {
    pref.setSummary(value)
    pref.asInstanceOf[SummaryEditTextPreference].setText(value)
  }

  def updateProfileEditTextPreference(pref: Preference, value: String) {
    pref.asInstanceOf[ProfileEditTextPreference].resetSummary(value)
    pref.asInstanceOf[ProfileEditTextPreference].setText(value)
  }

  def updateCheckBoxPreference(pref: Preference, value: Boolean) {
    pref.asInstanceOf[CheckBoxPreference].setChecked(value)
  }

  def updatePreference(pref: Preference, name: String, profile: Profile) {
    name match {
      case Key.profileName => updateProfileEditTextPreference(pref, profile.name)
      case Key.proxy => updateSummaryEditTextPreference(pref, profile.host)
      case Key.remotePort => updateSummaryEditTextPreference(pref, profile.remotePort.toString)
      case Key.localPort => updateSummaryEditTextPreference(pref, profile.localPort.toString)
      case Key.sitekey => updatePasswordEditTextPreference(pref, profile.password)
      case Key.encMethod => updateListPreference(pref, profile.method)
      case Key.isGFWList => updateCheckBoxPreference(pref, profile.chnroute)
      case Key.isGlobalProxy => updateCheckBoxPreference(pref, profile.global)
      case Key.isTrafficStat => updateCheckBoxPreference(pref, profile.traffic)
      case _ =>
    }
  }

  private def setPreferenceEnabled() {
    val state = getActivity.asInstanceOf[Shadowsocks].state
    val enabled = state != State.CONNECTED && state != State.CONNECTING
    for (name <- PROXY_PREFS) {
      val pref: Preference = findPreference(name)
      if (pref != null) {
        pref.setEnabled(enabled)
      }
    }
    for (name <- FEATRUE_PREFS) {
      val pref: Preference = findPreference(name)
      if (pref != null) {
        val status = getActivity.getSharedPreferences(Key.status, Context.MODE_PRIVATE)
        val isRoot = status.getBoolean(Key.isRoot, false)
        if (Seq(Key.isAutoConnect, Key.isGlobalProxy, Key.isTrafficStat, Key.proxyedApps)
          .contains(name)) {
          pref.setEnabled(enabled && isRoot)
        } else {
          pref.setEnabled(enabled)
        }
      }
    }
  }

  private def updatePreferenceScreen() {
    for (name <- PROXY_PREFS) {
      val pref = findPreference(name)
      updatePreference(pref, name, activity.currentProfile)
    }
    for (name <- FEATRUE_PREFS) {
      val pref = findPreference(name)
      updatePreference(pref, name, activity.currentProfile)
    }
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    addPreferencesFromResource(R.xml.pref_proxy)

    val filter = new IntentFilter()
    filter.addAction(Action.UPDATE_FRAGMENT)
    receiver = new BroadcastReceiver {
      def onReceive(p1: Context, p2: Intent) {
        setPreferenceEnabled()
        updatePreferenceScreen()
      }
    }
    getActivity.getApplicationContext.registerReceiver(receiver, filter)
  }

  override def onDestroy() {
    super.onDestroy()
    getActivity.getApplicationContext.unregisterReceiver(receiver)
  }

  override def onResume() {
    super.onResume()
    setPreferenceEnabled()
    updatePreferenceScreen()
  }

  override def onPause() {
    super.onPause()

    activity.currentProfile = activity.profileManager.save()
  }
}
