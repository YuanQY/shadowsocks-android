package com.github.shadowsocks.view

import it.gmariotti.cardslib.library.internal.Card
import com.github.shadowsocks.R
import android.view.{View, ViewGroup}
import com.google.ads.{AdRequest, AdSize, AdView}
import android.app.Activity

class AdCard(context: Activity) extends Card(context, R.layout.profile_ad_card) {

  override def setupInnerViewElements(parent: ViewGroup, view: View) {
  }
}
