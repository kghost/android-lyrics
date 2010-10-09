package name.kghost.android.lyrics

import android.widget.{ AdapterView, BaseAdapter, Button, ImageView, ListView, TextView }
import android.view.{ LayoutInflater, Menu, MenuItem, View, ViewGroup, Window }
import android.app.{ Activity, Dialog }
import android.content.{ ComponentName, BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter, ServiceConnection }
import android.os.{ Bundle, IBinder, RemoteException }
import android.widget.Toast
import com.android.music.IMediaPlaybackService
import utils.With

class LyricsActivity extends Activity { activity =>
  private var factory: store.LyricsServiceFactory = new store.LyricsServiceFactory(this)
  private var machine: StateMachine = null

  private case class ActivityCreateEvent extends Event
  private case class ActivityDestoryEvent extends Event
  private case class ActivityStartEvent extends Event
  private case class ActivityStopEvent extends Event
  private case class ActivityPauseEvent extends Event
  private case class ActivityResumeEvent extends Event
  private case class ActivitySearchEvent extends Event

  private case class MediaServiceEvent(val media: IMediaPlaybackService) extends Event
  private case class MediaServiceNullEvent extends Event

  private case class MenuSaveEvent extends Event
  private case class MenuDeleteEvent extends Event

  private val media_conn = new ServiceConnection() {
    override def onServiceConnected(className: ComponentName, s: IBinder): Unit = {
      machine.dispatch(MediaServiceEvent(IMediaPlaybackService.Stub.asInterface(s)))
    }

    override def onServiceDisconnected(className: ComponentName): Unit = {
      machine.dispatch(MediaServiceNullEvent())
    }
  };

  private class MediaServiceErrorState extends State {
    override def entry: Unit = {
      setContentView(R.layout.error)
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => Some(new MediaServiceReadyState(e.media))
      case e: MediaServiceNullEvent => None
      case e => super.action(e)
    }
  }

  private class MediaServiceLoadingState extends State {
    override def entry: Unit = {
      setContentView(R.layout.loading)

      if (!bindService((new Intent()).setClassName("com.android.music", "com.android.music.MediaPlaybackService"), media_conn, Context.BIND_AUTO_CREATE)) {
        Toast.makeText(activity, R.string.ERROR_CANNOT_BIND_MEDIASERIVCE, Toast.LENGTH_LONG).show()
        machine.dispatch(MediaServiceNullEvent())
      }
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => Some(new MediaServiceReadyState(e.media))
      case e: MediaServiceNullEvent => Some(new MediaServiceErrorState)
      case e => super.action(e)
    }
  }

  private class MediaServiceReadyState(private val media: IMediaPlaybackService) extends State {
    private var inner: StateMachine = null

    private var main: ViewGroup = null
    private var artist: TextView = null
    private var album: TextView = null
    private var track: TextView = null
    private var play: View = null
    private var pause: View = null

    private case class PlayingEvent(val info: store.LyricsSearchInfo) extends Event
    private case class NotPlayingEvent extends Event
    private case class MusicPauseEvent extends Event
    private case class MusicResumeEvent extends Event

    private def media_service_wrapper[T](fun: => T): Option[T] = try {
      Some(fun)
    } catch {
      case ex: RemoteException =>
        With(None) { x =>
          Toast.makeText(activity, R.string.ERROR_MEDIA_REMOTE_EXCEPTION, Toast.LENGTH_LONG).show()
        }
    }

    override def entry: Unit = {
      setContentView(R.layout.main)

      initMediaControl

      main = findViewById(R.id.Main).asInstanceOf[ViewGroup]
      artist = findViewById(R.id.Artist).asInstanceOf[TextView]
      album = findViewById(R.id.Album).asInstanceOf[TextView]
      track = findViewById(R.id.Track).asInstanceOf[TextView]
      play = findViewById(R.id.FramePlay)
      pause = findViewById(R.id.FramePause)

      inner = new StateMachine(new NotPlayingState)
      if (isStart) update
    }
    override def exit: Unit = {
      if (isStart) unregisterReceiver(receiver)
      inner.finish
      inner = null

      play = null
      pause = null
      artist = null
      album = null
      track = null
      main = null

      unbindService(media_conn)
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => None
      case e: MediaServiceNullEvent => Some(new MediaServiceErrorState)
      case e: PlayingEvent =>
        With(None) { x =>
          artist.setText(e.info.artist)
          album.setText(e.info.album)
          track.setText(e.info.track)
          inner.dispatch(ev)
        }
      case e: NotPlayingEvent =>
        With(None) { x =>
          artist.setText("")
          album.setText("")
          track.setText("")
          inner.dispatch(e)
        }
      case e: ActivityStartEvent =>
        With(None) { x =>
          update
        }
      case e: ActivityStopEvent =>
        With(None) { x =>
          unregisterReceiver(receiver)
        }
      case e: Event => inner.dispatch(e); None
    }

    private def initMediaControl: Unit = {
      findViewById(R.id.Play).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = media_service_wrapper {
          if (media.getAudioId > 0)
            media.play
          else
            media.next
        }
      })

      findViewById(R.id.Stop).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = media_service_wrapper {
          media.stop
          updateMediaControl // XXX: media service do not send stop broadcast, do it explicit
          updateMusic(null)
        }
      })

      findViewById(R.id.Pause).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = media_service_wrapper {
          media.pause
        }
      })

      findViewById(R.id.Next).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = media_service_wrapper {
          media.next
        }
      })

      findViewById(R.id.Prev).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = media_service_wrapper {
          media.prev
        }
      })
    }

    private def updateMediaControl: Unit = media_service_wrapper {
      if (media.isPlaying) {
        play.setVisibility(View.GONE)
        pause.setVisibility(View.VISIBLE)
      } else {
        play.setVisibility(View.VISIBLE)
        pause.setVisibility(View.GONE)
      }
    }

    private def tryUpdateMusic: Unit = updateMusic(media_service_wrapper {
      if (media.getAudioId >= 0)
        store.LyricsSearchInfo(media.getArtistName, media.getAlbumName, media.getTrackName)
      else
        null
    } match {
      case Some(x) => x
      case None => null
    })
    private def updateMusic(info: store.LyricsSearchInfo): Unit = if (info != null) {
      machine.dispatch(new PlayingEvent(info))
    } else {
      machine.dispatch(NotPlayingEvent())
    }

    private final val PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
    private final val META_CHANGED = "com.android.music.metachanged"
    private final val QUEUE_CHANGED = "com.android.music.queuechanged"
    private final val PLAYBACK_COMPLETE = "com.android.music.playbackcomplete"
    private final val ASYNC_OPEN_COMPLETE = "com.android.music.asyncopencomplete"

    private val receiver = new BroadcastReceiver {
      override def onReceive(ctx: android.content.Context, intent: android.content.Intent): Unit = intent.getAction match {
        case PLAYSTATE_CHANGED => {
          updateMediaControl
          media_service_wrapper {
            if (media.isPlaying)
              machine.dispatch(MusicResumeEvent())
            else
              machine.dispatch(MusicPauseEvent())
          }
        }
        case META_CHANGED =>
          if (intent.getLongExtra("id", -1) >= 0)
            updateMusic(store.LyricsSearchInfo(intent.getStringExtra("artist"),
              intent.getStringExtra("album"),
              intent.getStringExtra("track")))
          else
            tryUpdateMusic
        case PLAYBACK_COMPLETE => {
          updateMusic(null)
          updateMediaControl
          machine.dispatch(MusicPauseEvent())
        }
      }
    }

    private val filter = With(new IntentFilter()) { f =>
      f.addAction(PLAYSTATE_CHANGED)
      f.addAction(META_CHANGED)
      f.addAction(PLAYBACK_COMPLETE)
    }

    private def update: Unit = {
      registerReceiver(receiver, filter)
      tryUpdateMusic
      updateMediaControl
    }

    private class NotPlayingState extends State {
      private val v = inflater.inflate(R.layout.main_no_music, null)
      override def entry: Unit = {
        main.addView(v)
      }
      override def exit: Unit = {
        main.removeView(v)
      }
      override def action(ev: Event): Option[State] = ev match {
        case e: PlayingEvent => Some(new PlayingState(e.info))
        case e: NotPlayingEvent => None
        case e => super.action(e)
      }
    }
    private class PlayingState(private val info: store.LyricsSearchInfo) extends State {
      private var inner: StateMachine = null
      private var inner2: StateMachine = null

      private case class LyricsEvent(val lyrics: store.ILyrics) extends Event
      private case class TimelineLyricsEvent(val lyrics: store.ILyricsWithTimeline) extends Event

      private case class SearchEvent(val info: store.LyricsSearchInfo, val service: String) extends Event
      private case class SearchCustomEvent(val info: store.LyricsSearchInfo) extends Event
      private case class SearchCandidateEvent(val info: Seq[store.LyricsResultInfo]) extends Event
      private case class SearchResultEvent(val lyrics: store.LyricsResultInfo) extends Event
      private case class SearchDoneEvent extends Event

      override def entry: Unit = {
        inner = new StateMachine(new NoLyricsState)
        inner2 = new StateMachine(new SearchIdleState)

        inner2.dispatch(SearchEvent(info, "Local"))
      }
      override def exit: Unit = {
        inner2.finish
        inner2 = null
        inner.finish
        inner = null
      }
      override def action(ev: Event): Option[State] = ev match {
        case e: PlayingEvent =>
          if (info != e.info)
            Some(new PlayingState(e.info))
          else
            None
        case e: NotPlayingEvent => Some(new NotPlayingState)
        case e: ActivitySearchEvent => customSearch; None
        case e => inner.dispatch(e); None
      }

      private def search: Unit = inner2.dispatch(SearchEvent(info, "QianQian"))
      private def customSearch: Unit = inner2.dispatch(SearchCustomEvent(info))

      private class SearchIdleState extends State {
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchEvent => Some(new SearchingState(e.info, e.service))
          case e: SearchCustomEvent => Some(new SearchCustomState(e.info))
          case e => super.action(e)
        }
      }

      private class SearchCustomState(info: store.LyricsSearchInfo) extends State {
        private var dialog: Dialog = null
        override def entry: Unit = {
          dialog = new Dialog(activity)
          dialog.setTitle("Custom Search")
          dialog.setContentView(R.layout.custom_find_dialog)
          dialog.findViewById(R.id.ArtistInput).asInstanceOf[TextView].setText(info.artist)
          dialog.findViewById(R.id.AlbumInput).asInstanceOf[TextView].setText(info.album)
          dialog.findViewById(R.id.TrackInput).asInstanceOf[TextView].setText(info.track)

          dialog.findViewById(R.id.OK).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit =
              inner2.dispatch(SearchEvent(store.LyricsSearchInfo(artist.getText.toString, album.getText.toString, track.getText.toString), "QianQian"))
          })

          dialog.findViewById(R.id.Cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = dialog.cancel
          })

          dialog.setOnCancelListener(new DialogInterface.OnCancelListener {
            override def onCancel(dialog: DialogInterface): Unit = inner2.dispatch(SearchDoneEvent())
          })

          dialog.setCancelable(true)
          dialog.show()
        }
        override def exit: Unit = dialog.dismiss
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchDoneEvent => Some(new SearchIdleState)
          case e: SearchEvent => Some(new SearchingState(e.info, e.service))
          case e => super.action(e)
        }
      }

      private class SearchingState(info: store.LyricsSearchInfo, service: String) extends State {
        private var task: utils.AsyncTaskWithProgress[Integer, Seq[store.LyricsResultInfo]] = null
        override def entry: Unit =
          task = With(new utils.AsyncTaskWithProgress[Integer, Seq[store.LyricsResultInfo]](activity, "Finding lyrics") {
            override def doInBackground(infos: AnyRef*): Seq[store.LyricsResultInfo] =
              factory.get(service).find(infos(0).asInstanceOf[store.LyricsSearchInfo])
            override def onPostExecute(result: Seq[store.LyricsResultInfo]): Unit = {
              super.onPostExecute(result)
              inner2.dispatch(SearchCandidateEvent(result))
            }
            override def onCancelled: Unit = {
              super.onCancelled
              inner2.dispatch(SearchDoneEvent())
            }
          }) { _.execute(info) }
        override def exit: Unit = task.cancel(true)
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchDoneEvent => Some(new SearchIdleState)
          case e: SearchCandidateEvent => Some(new SelectCandidateState(e.info))
          case e => super.action(e)
        }
      }

      private class SelectCandidateState(info: Seq[store.LyricsResultInfo]) extends State {
        private var dialog: Dialog = null
        override def entry: Unit = info match {
          case null => {
            Toast.makeText(activity, R.string.LYRICS_SERVER_ERROR, Toast.LENGTH_LONG).show()
            inner2.dispatch(SearchDoneEvent())
          }
          case Nil => {
            Toast.makeText(activity, R.string.LYRICS_NOT_FOUND, Toast.LENGTH_LONG).show()
            inner2.dispatch(SearchDoneEvent())
          }
          case x :: Nil => inner2.dispatch(SearchResultEvent(x))
          case _ => {
            val listview = new ListView(activity);
            listview.setAdapter(new BaseAdapter {
              override def getCount = info.size
              override def getItem(i: Int): store.LyricsResultInfo = info(i)
              override def getItemId(position: Int) = position
              override def getView(position: Int, convertView: View, parent: ViewGroup) =
                With(if (convertView == null)
                  inflater.inflate(R.layout.lyrics_list_item, null)
                else
                  convertView) { view =>
                  val item = getItem(position)
                  view.findViewById(R.id.Artist).asInstanceOf[TextView].setText(item.artist)
                  view.findViewById(R.id.Album).asInstanceOf[TextView].setText(item.album)
                  view.findViewById(R.id.Track).asInstanceOf[TextView].setText(item.track)
                  val indicators: ViewGroup = view.findViewById(R.id.Indicators).asInstanceOf[ViewGroup]
                  indicators.removeAllViews
                  indicators.addView(Utils.ImageView(activity, item.provider))
                  if (item.hasTimeline) indicators.addView(Utils.ImageView(activity, R.drawable.clock))
                }
            })

            dialog = new Dialog(activity)

            listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
              override def onItemClick(parentView: AdapterView[_], childView: View, position: Int, id: Long): Unit =
                inner2.dispatch(SearchResultEvent(info(position)))
            });

            dialog.setContentView(listview);
            dialog.setTitle("Select lyrics");

            dialog.setOnCancelListener(new DialogInterface.OnCancelListener {
              override def onCancel(dialog: DialogInterface): Unit = inner2.dispatch(SearchDoneEvent())
            })

            dialog.setCancelable(true);
            dialog.show();
          }
        }
        override def exit: Unit = if (dialog != null) dialog.dismiss
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchDoneEvent => Some(new SearchIdleState)
          case e: SearchResultEvent => Some(new GettingResultState(e.lyrics))
          case e => super.action(e)
        }
      }

      private class GettingResultState(info: store.LyricsResultInfo) extends State {
        private var task: utils.AsyncTaskWithProgress[Integer, (store.ILyrics, store.LyricsServiceError)] = null
        override def entry: Unit =
          task = With(new utils.AsyncTaskWithProgress[Integer, (store.ILyrics, store.LyricsServiceError)](activity, "Loading lyrics") {
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
                  r match {
                    case l: store.ILyricsWithTimeline => {
                      machine.dispatch(TimelineLyricsEvent(l))
                      inner2.dispatch(SearchDoneEvent())
                    }
                    case l: store.ILyrics => {
                      machine.dispatch(LyricsEvent(l))
                      inner2.dispatch(SearchDoneEvent())
                    }
                    case null => Toast.makeText(activity, R.string.LYRICS_SERVER_ERROR, Toast.LENGTH_LONG).show()
                  }
              }
            }
            override def onCancelled: Unit = {
              super.onCancelled
              inner2.dispatch(SearchDoneEvent())
            }
          }) { _.execute(info) }
        override def exit: Unit = task.cancel(true)
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchDoneEvent => Some(new SearchIdleState)
          case e => super.action(e)
        }
      }

      private class NoLyricsState extends State {
        private val v = inflater.inflate(R.layout.main_no_lyrics, null)
        override def entry: Unit = {
          v.findViewById(R.id.Find).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
            override def onClick(v: View): Unit = search
          })

          v.findViewById(R.id.CustomFind).asInstanceOf[Button].setOnClickListener(new View.OnClickListener() {
            override def onClick(v: View): Unit = customSearch
          })

          main.addView(v)
        }
        override def exit: Unit = {
          main.removeView(v)
        }
        override def action(ev: Event): Option[State] = ev match {
          case e: TimelineLyricsEvent => Some(new TimelineLyricsState(e.lyrics))
          case e => super.action(e)
        }
      }

      private class TimelineLyricsState(private val lyrics: store.ILyricsWithTimeline) extends State {
        private var timelineAdapter: LyricsTimelineAdapter = null
        private val v = inflater.inflate(R.layout.main_lyrics_timeline, null)
        override def entry: Unit = {
          val listview = v.asInstanceOf[ListView]
          timelineAdapter = new LyricsTimelineAdapter(listview, activity, lyrics.timeline)
          listview.setAdapter(timelineAdapter)
          listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            override def onItemLongClick(parentView: AdapterView[_], childView: View, position: Int, id: Long): Boolean =
              With(true) { x =>
                val start = lyrics.timeline(position)._1
                media_service_wrapper {
                  media.play
                  media.seek(start)
                  timelineAdapter.start(start)
                }
              }
          })
          listview.setLongClickable(true)
          media_service_wrapper {
            if (media.isPlaying) timelineAdapter.start(media.position.toInt)
          }

          main.addView(v)
        }
        override def exit: Unit = {
          main.removeView(v)
          timelineAdapter.stop
          timelineAdapter = null
        }
        override def action(ev: Event): Option[State] = ev match {
          case e: MenuSaveEvent => {
            try {
              factory.local.saveLyricsWithTimeline(info, lyrics)
              Toast.makeText(activity, "Lyrics Saved", Toast.LENGTH_SHORT).show()
            } catch {
              case ex: Exception => Toast.makeText(activity, ex.getLocalizedMessage, Toast.LENGTH_LONG).show()
            }
            None
          }
          case e: MenuDeleteEvent => {
            try {
              factory.local.deleteLyrics(lyrics)
              Toast.makeText(activity, "Lyrics deleted", Toast.LENGTH_SHORT).show()
            } catch {
              case ex: Exception => Toast.makeText(activity, ex.getLocalizedMessage, Toast.LENGTH_LONG).show()
            }
            None
          }
          case e: TimelineLyricsEvent => Some(new TimelineLyricsState(e.lyrics))
          case e: MusicPauseEvent => timelineAdapter.stop; None
          case e: MusicResumeEvent =>
            With(None) { x =>
              media_service_wrapper {
                if (media.isPlaying) timelineAdapter.start(media.position.toInt)
              }
            }
          case e: ActivityPauseEvent => timelineAdapter.stop; None
          case e: ActivityResumeEvent =>
            With(None) { x =>
              media_service_wrapper {
                if (media.isPlaying) timelineAdapter.start(media.position.toInt)
              }
            }
          case e => super.action(e)
        }
      }
    }
  }

  private var inflater: LayoutInflater = null;
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    inflater = LayoutInflater.from(activity)
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    machine = new StateMachine(new MediaServiceLoadingState)
    machine.dispatch(ActivityCreateEvent())
  }

  override def onDestroy(): Unit = {
    machine.dispatch(ActivityDestoryEvent())
    machine.finish
    machine = null
    super.onDestroy
  }

  private var isStart = false
  override def onStart: Unit = {
    super.onStart
    isStart = true
    machine.dispatch(ActivityStartEvent())
  }

  override def onStop: Unit = {
    isStart = false
    machine.dispatch(ActivityStopEvent())
    super.onStop
  }

  override def onPause: Unit = {
    super.onPause
    machine.dispatch(ActivityPauseEvent())
  }

  override def onResume: Unit = {
    machine.dispatch(ActivityResumeEvent())
    super.onResume
  }

  override def onSearchRequested: Boolean = { machine.dispatch(ActivitySearchEvent()); false }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater().inflate(R.menu.main, menu);
    true;
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId() match {
      case R.id.MenuSave => machine.dispatch(MenuSaveEvent()); true
      case R.id.MenuDelete => machine.dispatch(MenuDeleteEvent()); true
      case _ => super.onOptionsItemSelected(item)
    }
}
