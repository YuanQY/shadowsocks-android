package com.github.shadowsocks.cards

import it.gmariotti.cardslib.library.internal.Card
import android.content.Context
import android.view.{View, ViewGroup}
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.R
import android.widget.{TextView, ImageView}
import com.nostra13.universalimageloader.core.{DisplayImageOptions, ImageLoader, ImageLoaderConfiguration}
import com.github.shadowsocks.utils.{Parser, Scheme, ProfileIconDownloader}
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer

class HeaderCard(context: Context, profile: Profile)
  extends Card(context, R.layout.profile_header_card) {

  lazy val config = new ImageLoaderConfiguration.Builder(context)
    .imageDownloader(new ProfileIconDownloader(context))
    .build()
  lazy val loader = ImageLoader.getInstance()

  override def setupInnerViewElements(parent: ViewGroup, view: View) {
    loader.init(config)
    val options = new DisplayImageOptions.Builder()
      .resetViewBeforeLoading()
      .cacheInMemory()
      .cacheOnDisc()
      .displayer(new FadeInBitmapDisplayer(300))
      .build()
    val icon = view.findViewById(R.id.profile_icon).asInstanceOf[ImageView]
    loader.displayImage(Scheme.PROFILE + profile.name, icon, options)

    val uriText = view.findViewById(R.id.profile_uri).asInstanceOf[TextView]
    uriText.setText(Parser.parse(profile))
  }
}
