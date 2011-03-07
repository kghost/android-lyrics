package name.kghost.android.lyrics

import android.util.Log
import java.util.Date
import android.os.{ Binder, IBinder }
import android.app.Service
import android.content.Intent

class MusicStateService extends Service {
  private var info: store.LyricsSearchInfo = null
  private var offset: Long = 0
  private val mBinder: IBinder = new MusicStateServiceBinder

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (intent.getBooleanExtra("playing", false) != true
      || intent.getAction == "com.android.music.playbackcomplete") {
      info = null
      offset = 0
      this.stopSelf(startId)
      sendBroadcast(new Intent("info.kghost.android.lyrics.UPDATE"))
      Log.i("MusicStateService", "Destroyed")
      return Service.START_NOT_STICKY;
    } else {
      if (intent.getLongExtra("id", -1) >= 0) {
        info = store.LyricsSearchInfo(
          intent.getStringExtra("artist"),
          intent.getStringExtra("album"),
          intent.getStringExtra("track"))
        offset = (new Date).getTime - intent.getIntExtra("pos", 0)
        Log.i("MusicStateService", "Playing: " + info + " at " + offset)
      } else {
        info = null
        offset = 0
        Log.w("MusicStateService", "Error.1")
      }
      sendBroadcast(new Intent("info.kghost.android.lyrics.UPDATE"))
      return Service.START_STICKY;
    }
  }

  override def onBind(intent: Intent): IBinder = mBinder

  class MusicStateServiceBinder extends Binder {
    def getInfo = info
    def getOffset = offset
  }
}
