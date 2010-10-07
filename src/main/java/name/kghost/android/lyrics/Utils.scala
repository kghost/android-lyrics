package name.kghost.android.lyrics

import android.view.ViewGroup
import android.app.Activity
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import name.kghost.android.lyrics.utils.With

object Utils {
  def ImageView(activity: Activity, resource: Int): ImageView = With(new ImageView(activity)) { img =>
    img.setAdjustViewBounds(true)
    val size: Float = 32.0f * activity.getResources().getDisplayMetrics().density;
    img.setMaxHeight(size.toInt)
    img.setMaxWidth(size.toInt)
    img.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    img.setScaleType(ScaleType.FIT_XY)
    img.setImageResource(resource)
  }
}