package name.kghost.android.lyrics

import android.util.Log
import android.content.{ Context, Intent, BroadcastReceiver }

class MusicStateReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    Log.i("MusicStateReceiver", "Receive: " + intent)
    context.startService(new Intent()
      .setClass(context, classOf[MusicStateService])
      .putExtras(intent))
  }
}
