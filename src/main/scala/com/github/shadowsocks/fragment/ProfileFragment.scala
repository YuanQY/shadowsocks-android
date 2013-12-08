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

import android.app.Fragment
import android.view.{LayoutInflater, ViewGroup, View}
import android.os.Bundle
import com.github.shadowsocks.{Shadowsocks, R}
import it.gmariotti.cardslib.library.view.CardView
import it.gmariotti.cardslib.library.internal.{CardHeader, Card}
import it.gmariotti.cardslib.library.internal.Card.OnCardClickListener

object ProfileFragment {
  val TAG = "com.github.shadowsocks.fragment.ProfileFragment"

  def instance = new ProfileFragment
}

class ProfileFragment extends Fragment {

  lazy val activity = getActivity.asInstanceOf[Shadowsocks]
  lazy val currentProfile = activity.currentProfile

  lazy val scrollView = activity.findViewById(R.id.profile_scrollview).asInstanceOf[CardView]
  lazy val historyCard = activity.findViewById(R.id.history_card).asInstanceOf[CardView]
  lazy val realtimeCard = activity.findViewById(R.id.realtime_card).asInstanceOf[CardView]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
    savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.profile, container, false)
  }

  override def onActivityCreated(savedInstanceState: Bundle) {
    super.onActivityCreated(savedInstanceState)
    initCards()
  }

  def initCards() {
    initAdCard()
    initHeaderCard()
  }

  def initAdCard() {
    val adCard = activity.findViewById(R.id.ad_card).asInstanceOf[CardView]
    val card = new Card(getActivity, R.layout.profile_ad_card)
    adCard.setCard(card)
  }

  def initHeaderCard() {
    val headerCard = activity.findViewById(R.id.header_card).asInstanceOf[CardView]
    val card = new Card(getActivity, R.layout.profile_header_card)
    val cardHeader = new CardHeader(getActivity)

    cardHeader.setTitle(currentProfile.name)
    card.setTitle(currentProfile.host)
    
    card.addCardHeader(cardHeader)
    card.setOnClickListener(new OnCardClickListener {
      override def onClick(card: Card, view: View) {
        activity.openSettings()
      }
    })

    headerCard.setCard(card)
  }
}
