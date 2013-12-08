package com.github.shadowsocks.utils

import android.content.Context
import com.nostra13.universalimageloader.core.download.BaseImageDownloader
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import android.graphics.{Bitmap, Color}

class ProfileIconDownloader(context: Context, connectTimeout: Int, readTimeout: Int)
  extends BaseImageDownloader(context, connectTimeout, readTimeout) {

  def this(context: Context) {
    this(context, 0, 0)
  }

  override def getStreamFromOtherSource(imageUri: String, extra: AnyRef): InputStream = {
    val text = imageUri.substring(Scheme.PROFILE.length, Scheme.PROFILE.length + 1)
    val size = Utils.dpToPx(context, 80).toInt
    val idx = imageUri.length % 5
    val color = Seq(Color.MAGENTA, Color.GREEN, Color.BLACK, Color.BLUE, Color.DKGRAY)(
      idx)
    val bitmap = Utils.getBitmap(text, size, size, color, 2)

    val os = new ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    new ByteArrayInputStream(os.toByteArray)
  }
}

class AppIconDownloader(context: Context, connectTimeout: Int, readTimeout: Int)
  extends BaseImageDownloader(context, connectTimeout, readTimeout) {

  def this(context: Context) {
    this(context, 0, 0)
  }

  override def getStreamFromOtherSource(imageUri: String, extra: AnyRef): InputStream = {
    val uid = imageUri.substring(Scheme.APP.length).toInt
    val drawable = Utils.getAppIcon(context.getApplicationContext, uid)
    val bitmap = Utils.drawableToBitmap(drawable)

    val os = new ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
    new ByteArrayInputStream(os.toByteArray)
  }
}
