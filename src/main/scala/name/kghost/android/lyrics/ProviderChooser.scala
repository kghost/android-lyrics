package name.kghost.android.lyrics

import android.graphics.Color
import android.app.Dialog
import android.widget.ImageView.ScaleType
import android.widget.TextView
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import name.kghost.android.lyrics.utils.With
import name.kghost.android.lyrics.store.LyricsServiceFactory
import android.content.DialogInterface
import android.widget.BaseAdapter
import android.app.AlertDialog
import android.content.Context

abstract class ProviderChooser(context: Context, factory: LyricsServiceFactory, current: store.LyricsService) {
  def onChoosen(choosen: store.LyricsService): Unit
  def show: Unit = {
    var alert: Dialog = null
    val builder: AlertDialog.Builder = new AlertDialog.Builder(context)
    builder.setTitle("Pick lyrics provider")
    val adapter = new BaseAdapter {
      val all = factory.getAll.values.toArray
      override def getCount = all.size
      override def getItem(i: Int) = all(i)
      override def getItemId(position: Int) = position
      override def getView(position: Int, convertView: View, parent: ViewGroup): View =
        With(if (convertView == null)
          LayoutInflater.from(context).inflate(R.layout.provider_list_item, null)
        else
          convertView) { v =>
          val item = getItem(position)
          ProviderChooser.buildView(context, v, item, item == current)
        }
    }
    builder.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, item: Int): Unit = {
        onChoosen(adapter.getItem(item))
        alert.dismiss()
      }
    })
    alert = builder.create
    alert.show
  }
}

object ProviderChooser {
  def buildView(context: Context, view: View, srv: store.LyricsService, highlight: Boolean): Unit = {
    With(view.findViewById(R.id.ProviderName).asInstanceOf[TextView]) { v =>
      v.setText(srv.name)
      if (highlight) v.setTextColor(Color.BLACK)
    }
    With(view.findViewById(R.id.ProviderIcon).asInstanceOf[ImageView]) { img =>
      img.setAdjustViewBounds(true)
      val size: Float = 64.0f * context.getResources().getDisplayMetrics().density
      img.setMaxHeight(size.toInt)
      img.setMaxWidth(size.toInt)
      img.setScaleType(ScaleType.FIT_XY)
      img.setImageResource(srv.provider)
    }
  }
}