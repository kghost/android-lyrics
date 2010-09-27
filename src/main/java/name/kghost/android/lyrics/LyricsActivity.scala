package name.kghost.android.lyrics

import android.widget.{ AdapterView, TextView, ListView, BaseAdapter, Button }
import android.view.{ LayoutInflater, View, ViewGroup, Window }
import android.app.{ Activity, Dialog }
import android.content.{ ComponentName, BroadcastReceiver, Context, Intent, IntentFilter, ServiceConnection }
import android.os.{ Bundle, IBinder, RemoteException }
import android.widget.Toast
import com.android.music.IMediaPlaybackService
import utils.With

class LyricsActivity extends Activity { activity =>
  private case class MediaServiceNotReadyException extends RuntimeException

  private var playing: store.LyricsSearchInfo = null

  private def media_load: Boolean = media_service_ != null
  private var media_service_ : IMediaPlaybackService = null;
  private def media_service = if (media_service_ != null) media_service_ else throw MediaServiceNotReadyException();

  private val media = new ServiceConnection() {
    override def onServiceConnected(className: ComponentName, s: IBinder): Unit = {
      media_service_ = IMediaPlaybackService.Stub.asInterface(s)
      findViewById(R.id.Loading).setVisibility(View.GONE)
      findViewById(R.id.Main).setVisibility(View.VISIBLE)
      updateMusic
      updateMediaControl
    }

    override def onServiceDisconnected(className: ComponentName): Unit = {
      media_service_ = null
    }
  };

  var inflater: LayoutInflater = null;
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    inflater = LayoutInflater.from(activity)
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    if (!bindService((new Intent()).setClassName("com.android.music", "com.android.music.MediaPlaybackService"), media, Context.BIND_AUTO_CREATE)) {
      Toast.makeText(this, R.string.ERROR_CANNOT_BIND_MEDIASERIVCE, Toast.LENGTH_LONG).show()
      setContentView(R.layout.error)
      return
    }

    setContentView(R.layout.main)

    findViewById(R.id.Find).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = search(playing)
    })

    findViewById(R.id.CustomFind).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = customSearch(playing)
    })

    initMediaControl
  }

  override def onDestroy(): Unit = {
    unbindService(media)
    super.onDestroy
  }

  private val PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
  private val META_CHANGED = "com.android.music.metachanged"
  private val QUEUE_CHANGED = "com.android.music.queuechanged"
  private val PLAYBACK_COMPLETE = "com.android.music.playbackcomplete"
  private val ASYNC_OPEN_COMPLETE = "com.android.music.asyncopencomplete"

  private val receiver = new BroadcastReceiver {
    override def onReceive(ctx: android.content.Context, intent: android.content.Intent): Unit = intent.getAction match {
      case PLAYSTATE_CHANGED => {
          updateMediaControl
          if (timelineAdapter != null) media_service_wrapper {
            if (media_service.isPlaying)
              timelineAdapter.start(media_service.position.toInt)
            else
              timelineAdapter.stop
          }
        }
      case META_CHANGED => updateMusic(if (intent.getLongExtra("id", -1) >= 0)
          Some(store.LyricsSearchInfo(intent.getStringExtra("artist"),
          intent.getStringExtra("album"),
          intent.getStringExtra("track")))
        else
          Some(null))
      case PLAYBACK_COMPLETE => {
          updateMusic(Some(null))
          updateMediaControl
          if (timelineAdapter != null) timelineAdapter.stop
        }
    }
  }

  private val filter = With(new IntentFilter()) { f =>
    f.addAction(PLAYSTATE_CHANGED)
    f.addAction(META_CHANGED)
    f.addAction(PLAYBACK_COMPLETE)
  }

  override def onStart: Unit = {
    super.onStart

    registerReceiver(receiver, filter)

    if (media_load) {
      updateMusic
      updateMediaControl
    }
  }

  override def onStop: Unit = {
    unregisterReceiver(receiver)
    super.onStop
  }

  override def onPause: Unit = {
    super.onPause

    if (timelineAdapter != null) {
      timelineAdapter.stop
    }
  }

  override def onResume: Unit = {
    if (timelineAdapter != null) media_service_wrapper {
      if (media_service.isPlaying) timelineAdapter.start(media_service.position.toInt)
    }

    super.onResume
  }

  override def onSearchRequested: Boolean = { customSearch(playing); false }

  private def media_service_wrapper[T](fun: => T): Option[T] = try {
    if (media_load) {
      Some(fun)
    } else With(None) { x =>
      Toast.makeText(this, R.string.ERROR_MEDIA_NOT_READY, Toast.LENGTH_LONG).show()
    }
  } catch {
    case ex: RemoteException => With(None) { x =>
        Toast.makeText(this, R.string.ERROR_MEDIA_REMOTE_EXCEPTION, Toast.LENGTH_LONG).show()
      }
  }

  private def initMediaControl: Unit = {
    findViewById(R.id.Play).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = media_service_wrapper {
        if (media_service.getAudioId > 0)
          media_service.play
        else
          media_service.next
      }
    })

    findViewById(R.id.Stop).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = media_service_wrapper {
        media_service.stop
        updateMediaControl // XXX: media service do not send stop broadcast, do it explicit
        updateMusic(Some(null))
      }
    })

    findViewById(R.id.Pause).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = media_service_wrapper {
        media_service.pause
      }
    })

    findViewById(R.id.Next).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = media_service_wrapper {
        media_service.next
      }
    })

    findViewById(R.id.Prev).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = media_service_wrapper {
        media_service.prev
      }
    })
  }

  private def updateMediaControl: Unit = media_service_wrapper {
    if (media_service.isPlaying) {
      findViewById(R.id.FramePlay).setVisibility(View.GONE)
      findViewById(R.id.FramePause).setVisibility(View.VISIBLE)
    } else {
      findViewById(R.id.FramePlay).setVisibility(View.VISIBLE)
      findViewById(R.id.FramePause).setVisibility(View.GONE)
    }
  }

  private def updateMusic: Unit = updateMusic(None)
  private def updateMusic(info: Option[store.LyricsSearchInfo]): Unit = {
    val new_info = info match {
      case Some(x) => x
      case None => media_service_wrapper {
          if (media_service.getAudioId >= 0)
            store.LyricsSearchInfo(media_service.getArtistName, media_service.getAlbumName, media_service.getTrackName)
          else
            null
        } match {
          case Some(x) => x
          case None => null
        }
    }
    if (playing != new_info) {
      playing = new_info
      if (playing != null) {
        findViewById(R.id.Artist).asInstanceOf[TextView].setText(playing.artist)
        findViewById(R.id.Album).asInstanceOf[TextView].setText(playing.album)
        findViewById(R.id.Track).asInstanceOf[TextView].setText(playing.track)
        lyricsAreaVisible(R.id.MainNoLyrics)
      } else {
        findViewById(R.id.Artist).asInstanceOf[TextView].setText("")
        findViewById(R.id.Album).asInstanceOf[TextView].setText("")
        findViewById(R.id.Track).asInstanceOf[TextView].setText("")
        lyricsAreaVisible(R.id.MainNoMusic)
      }
    }
  }

  private def lyricsAreaVisible(id: Int): Unit = {
    ((R.id.MainNoMusic, null, null)
      :: (R.id.MainNoLyrics, null, null)
      :: (R.id.MainLyrics, null, null)
      :: (R.id.MainLyricsWithTimeline, { x: View =>
        media_service_wrapper {
          if (media_service.isPlaying) timelineAdapter.start(media_service.position.toInt)
        }
      }, { x: View =>
        timelineAdapter.stop
        timelineAdapter = null
      })
      :: Nil) foreach {
      case (v, onShow, onHide) if (v == id) =>
        val view = findViewById(v)
        if (view.getVisibility != View.VISIBLE) {
          if (onShow != null) onShow(view)
          view.setVisibility(View.VISIBLE)
        }
      case (v, onShow, onHide) =>
        val view = findViewById(v)
        if (view.getVisibility != View.GONE) {
          view.setVisibility(View.GONE)
          if (onHide != null) onHide(view)
        }
    }
  }

  private def search(info: store.LyricsSearchInfo): Unit = try {
    if (info == null) {
      Toast.makeText(activity, R.string.ERROR_MEDIA_NOT_READY, Toast.LENGTH_LONG).show()
      return
    }
    // FIXME: string id ?
    (new utils.AsyncTaskWithProgress[Integer, Seq[store.LyricsResultInfo]](activity, "Finding lyrics") {
      override def doInBackground(infos: AnyRef*): Seq[store.LyricsResultInfo] = store.LyricsService.find(infos(0).asInstanceOf[store.LyricsSearchInfo])
      override def onPostExecute(result: Seq[store.LyricsResultInfo]): Unit = {
        super.onPostExecute(result)
        activity.gotCandidateLyrics(result)
      }
    }).execute(info)
  } catch {
    case ex: RemoteException => Toast.makeText(activity, R.string.ERROR_MEDIA_REMOTE_EXCEPTION, Toast.LENGTH_LONG).show()
  }

  private def customSearch(info: store.LyricsSearchInfo): Unit = {
    val dialog = new Dialog(this)
    dialog.setTitle("Custom Search")
    dialog.setContentView(R.layout.custom_find_dialog)
    val artist = dialog.findViewById(R.id.ArtistInput).asInstanceOf[TextView]
    val album = dialog.findViewById(R.id.AlbumInput).asInstanceOf[TextView]
    val track = dialog.findViewById(R.id.TrackInput).asInstanceOf[TextView]

    if (info != null) {
      artist.setText(info.artist)
      album.setText(info.album)
      track.setText(info.track)
    }

    dialog.findViewById(R.id.OK).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        search(store.LyricsSearchInfo(artist.getText.toString, album.getText.toString, track.getText.toString))
        dialog.dismiss
      }
    })

    dialog.findViewById(R.id.Cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        dialog.dismiss
      }
    })

    dialog.setCancelable(true)
    dialog.show()
  }

  private def gotCandidateLyrics(lyrics: Seq[store.LyricsResultInfo]): Unit = lyrics match {
    case null => Toast.makeText(activity, R.string.LYRICS_SERVER_ERROR, Toast.LENGTH_LONG).show()
    case Nil => Toast.makeText(activity, R.string.LYRICS_NOT_FOUND, Toast.LENGTH_LONG).show()
    case x :: Nil => gotLyricsInfo(x)
    case _ => {
        val listview = new ListView(activity);
        listview.setAdapter(new BaseAdapter {
          override def getCount = lyrics.size
          override def getItem(i: Int): store.LyricsResultInfo = lyrics(i)
          override def getItemId(position: Int) = position
          override def getView(position: Int, convertView: View, parent: ViewGroup) =
            With(if (convertView == null)
              inflater.inflate(R.layout.lyrics_list_item, null)
            else
              convertView) { view =>
              view.findViewById(R.id.Artist).asInstanceOf[TextView].setText(getItem(position).artist)
              view.findViewById(R.id.Album).asInstanceOf[TextView].setText(getItem(position).album)
              view.findViewById(R.id.Track).asInstanceOf[TextView].setText(getItem(position).track)
            }
        })

        val dialog = new Dialog(this)

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
          override def onItemClick(parentView: AdapterView[_], childView: View, position: Int, id: Long): Unit = {
            gotLyricsInfo(lyrics(position))
            dialog.dismiss()
          }
        });

        dialog.setContentView(listview);
        dialog.setTitle("Select lyrics");
        dialog.setCancelable(true);
        dialog.show();
      }
  }

  // FIXME: string id ?!
  private def gotLyricsInfo(info: store.LyricsResultInfo): Unit =
    (new utils.AsyncTaskWithProgress[Integer, (store.ILyrics, store.LyricsServiceError)](activity, "Loading lyrics") {
      override def doInBackground(infos: AnyRef*): (store.ILyrics, store.LyricsServiceError) =
        try {
          (infos(0).asInstanceOf[store.LyricsResultInfo].getResult, null)
        } catch {
          case e: store.LyricsServiceError => (null, e)
        }
      override def onPostExecute(result: (store.ILyrics, store.LyricsServiceError)): Unit = {
        super.onPostExecute(result)
        result match {
          case (r, e) =>
            if (e != null) Toast.makeText(activity, e.getMessage, Toast.LENGTH_LONG).show()
            gotLyrics(r)
        }
      }
    }).execute(info);

  private var timelineAdapter: LyricsTimelineAdapter = null
  private def gotLyrics(lyrics: store.ILyrics): Unit = lyrics match {
    case null => Toast.makeText(this, R.string.LYRICS_SERVER_ERROR, Toast.LENGTH_LONG).show()
    case l: store.ILyricsWithTimeline =>
      val listview = findViewById(R.id.MainLyricsWithTimeline).asInstanceOf[ListView]
      if (timelineAdapter != null) timelineAdapter.stop
      timelineAdapter = new LyricsTimelineAdapter(this, l.timeline)
      listview.setAdapter(timelineAdapter)
      listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
        override def onItemLongClick(parentView: AdapterView[_], childView: View, position: Int, id: Long): Boolean =
          With(true) { x =>
            val start = l.timeline(position)._1
            media_service_wrapper {
              media_service.play
              media_service.seek(start)
              timelineAdapter.start(start)
            }
          }
      })
      listview.setLongClickable(true)
      lyricsAreaVisible(R.id.MainLyricsWithTimeline)
    case l: store.ILyrics =>
      findViewById(R.id.LyricsContent).asInstanceOf[TextView].setText(l.lyrics)
      lyricsAreaVisible(R.id.MainLyrics)
  }
}
