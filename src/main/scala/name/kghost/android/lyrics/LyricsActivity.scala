package name.kghost.android.lyrics

import java.util.Date
import android.widget.{ AdapterView, BaseAdapter, Button, ImageView, ListView, TextView }
import android.view.{ LayoutInflater, Menu, MenuItem, View, ViewGroup, Window }
import android.app.{ Activity, Dialog }
import android.content.{ ComponentName, BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter, ServiceConnection }
import android.os.{ Bundle, IBinder, RemoteException }
import android.widget.Toast
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

  private case class MediaServiceEvent(val c: ServiceConnection, val srv: MusicStateService#MusicStateServiceBinder) extends Event
  private case class MediaServiceNullEvent extends Event

  private case class MenuSaveEvent extends Event
  private case class MenuDeleteEvent extends Event

  private def defaultProvider = factory.get(getSharedPreferences(LyricsActivity.PREFS_NAME, 0).getString(LyricsActivity.PREFS_DEFAULT_PROVIDER, ""))

  private class MediaServiceErrorState extends State {
    override def entry: Unit = {
      setContentView(R.layout.error)
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => Some(new MediaServiceReadyState(e.c, e.srv))
      case e: MediaServiceNullEvent => None
      case e: ActivityResumeEvent => {
        Toast.makeText(activity, R.string.ERROR_CANNOT_BIND_MEDIASERIVCE, Toast.LENGTH_LONG).show()
        None
      }
      case e => super.action(e)
    }
  }

  private class MediaServiceLoadingState extends State {
    override def entry: Unit = {
      setContentView(R.layout.loading)

      lazy val c: ServiceConnection = new ServiceConnection() {
        override def onServiceConnected(className: ComponentName, s: IBinder): Unit = s match {
          case srv: MusicStateService#MusicStateServiceBinder =>
            fsm.dispatch(MediaServiceEvent(c, srv))
          case srv => {
            Toast.makeText(activity, R.string.ERROR_CANNOT_BIND_MEDIASERIVCE, Toast.LENGTH_LONG).show()
            fsm.dispatch(MediaServiceNullEvent())
          }
        }

        override def onServiceDisconnected(className: ComponentName): Unit = {
          fsm.dispatch(MediaServiceNullEvent())
        }
      }

      if (!bindService(new Intent().setClass(activity, classOf[MusicStateService]), c, Context.BIND_AUTO_CREATE)) {
        Toast.makeText(activity, R.string.ERROR_CANNOT_BIND_MEDIASERIVCE, Toast.LENGTH_LONG).show()
        fsm.dispatch(MediaServiceNullEvent())
      }
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => Some(new MediaServiceReadyState(e.c, e.srv))
      case e: MediaServiceNullEvent => Some(new MediaServiceErrorState)
      case e => super.action(e)
    }
  }

  private class MediaServiceReadyState(c: ServiceConnection, srv: MusicStateService#MusicStateServiceBinder) extends State {
    private case class MusicPauseEvent extends Event
    private case class MusicResumeEvent(val info: store.LyricsSearchInfo, val offset: Long) extends Event

    private var inner: StateMachine = null

    private var main: ViewGroup = null
    private var artist: TextView = null
    private var album: TextView = null
    private var track: TextView = null

    private def update = {
      val info = srv.getInfo
      if (info != null)
        fsm.dispatch(MusicResumeEvent(info, srv.getOffset))
      else
        fsm.dispatch(MusicPauseEvent());
    }

    private var receiverRegisted = false
    private var receiver = new BroadcastReceiver {
      override def onReceive(context: Context, intent: Intent): Unit = update
    }

    override def entry: Unit = {
      setContentView(R.layout.main)

      main = findViewById(R.id.Main).asInstanceOf[ViewGroup]
      artist = findViewById(R.id.Artist).asInstanceOf[TextView]
      album = findViewById(R.id.Album).asInstanceOf[TextView]
      track = findViewById(R.id.Track).asInstanceOf[TextView]

      registerReceiver(receiver, With(new IntentFilter()) {
        _.addAction("info.kghost.android.lyrics.UPDATE")
      })
      receiverRegisted = true
      update

      inner = new StateMachine(new NotPlayingState)
    }
    override def exit: Unit = {
      inner.finish
      inner = null

      artist = null
      album = null
      track = null
      main = null

      unbindService(c)
    }
    override def action(ev: Event): Option[State] = ev match {
      case e: MediaServiceEvent => None
      case e: MediaServiceNullEvent => Some(new MediaServiceErrorState)
      case e: MusicResumeEvent =>
        With(None) { x =>
          artist.setText(e.info.artist)
          album.setText(e.info.album)
          track.setText(e.info.track)
          inner.dispatch(ev)
        }
      case e: MusicPauseEvent =>
        With(None) { x =>
          artist.setText("")
          album.setText("")
          track.setText("")
          inner.dispatch(ev)
        }
      case e: ActivityResumeEvent => {
        if (!receiverRegisted) {
          registerReceiver(receiver, With(new IntentFilter()) {
            _.addAction("info.kghost.android.lyrics.UPDATE")
          })
          receiverRegisted = true
        }
        update
        None
      }
      case e: ActivityPauseEvent => {
        if (receiverRegisted) {
          unregisterReceiver(receiver)
          receiverRegisted = false
        }
        inner.dispatch(ev)
        None
      }
      case e: Event => inner.dispatch(e); None
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
        case e: MusicResumeEvent => Some(new PlayingState(e.info, e.offset))
        case e: MusicPauseEvent => None
        case e => super.action(e)
      }
    }
    private class PlayingState(info: store.LyricsSearchInfo, offset: Long) extends State {
      private var inner: StateMachine = null
      private var inner2: StateMachine = null

      private case class LyricsEvent(val lyrics: store.ILyrics) extends Event
      private case class TimelineLyricsEvent(val lyrics: store.ILyricsWithTimeline) extends Event

      private case class SearchEvent(val info: store.LyricsSearchInfo, val service: store.LyricsService) extends Event
      private case class SearchCustomEvent(val info: store.LyricsSearchInfo) extends Event
      private case class SearchCandidateEvent(val info: Seq[store.LyricsResultInfo]) extends Event
      private case class SearchResultEvent(val lyrics: store.LyricsResultInfo) extends Event
      private case class SearchDoneEvent extends Event

      override def entry: Unit = {
        inner = new StateMachine(new NoLyricsState)
        inner2 = new StateMachine(new SearchIdleState)

        inner2.dispatch(SearchEvent(info, factory.local))
      }
      override def exit: Unit = {
        inner2.finish
        inner2 = null
        inner.finish
        inner = null
      }
      override def action(ev: Event): Option[State] = ev match {
        case e: MusicResumeEvent =>
          if (info != e.info)
            Some(new PlayingState(e.info, e.offset))
          else if (e.offset != offset) {
            inner.dispatch(e); None
          } else
            None
        case e: MusicPauseEvent => Some(new NotPlayingState)
        case e: ActivitySearchEvent => customSearch; None
        case e => inner.dispatch(e); None
      }

      private def search: Unit = if (defaultProvider != null)
        inner2.dispatch(SearchEvent(info, defaultProvider))
      else
        chooseProvider

      private def customSearch: Unit = if (defaultProvider != null)
        inner2.dispatch(SearchCustomEvent(info))
      else
        chooseProvider

      private class SearchIdleState extends State {
        override def action(ev: Event): Option[State] = ev match {
          case e: SearchEvent => Some(new SearchingState(e.info, e.service))
          case e: SearchCustomEvent => Some(new SearchCustomState(e.info))
          case e => super.action(e)
        }
      }

      private class SearchCustomState(info: store.LyricsSearchInfo) extends State {
        private var dialog: Dialog = null
        private var provider = defaultProvider
        override def entry: Unit = {
          dialog = new Dialog(activity)
          dialog.setTitle("Custom Search")
          dialog.setContentView(R.layout.custom_find_dialog)
          val custom_artist = dialog.findViewById(R.id.ArtistInput).asInstanceOf[TextView]
          val custom_album = dialog.findViewById(R.id.AlbumInput).asInstanceOf[TextView]
          val custom_track = dialog.findViewById(R.id.TrackInput).asInstanceOf[TextView]
          custom_artist.setText(info.artist)
          custom_album.setText(info.album)
          custom_track.setText(info.track)

          dialog.findViewById(R.id.OK).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = if (provider != null)
              inner2.dispatch(SearchEvent(store.LyricsSearchInfo(custom_artist.getText, custom_album.getText, custom_track.getText), provider))
            else
              Toast.makeText(activity, "Please select provider", Toast.LENGTH_LONG).show
          })

          dialog.findViewById(R.id.Cancel).asInstanceOf[Button].setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = dialog.cancel
          })

          With(dialog.findViewById(R.id.Provider)) { v =>
            ProviderChooser.buildView(activity, v, provider, true)
            v.setOnClickListener(new View.OnClickListener {
              override def onClick(v: View): Unit =
                (new ProviderChooser(activity, factory, defaultProvider) {
                  override def onChoosen(choosen: store.LyricsService): Unit = {
                    provider = choosen
                    ProviderChooser.buildView(activity, v, provider, true)
                  }
                }).show
            })
          }

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

      private class SearchingState(info: store.LyricsSearchInfo, srv: store.LyricsService) extends State { s =>
        private var task: utils.AsyncTaskWithProgress[Integer, Seq[store.LyricsResultInfo]] = null
        override def entry: Unit =
          if (srv != null) {
            task = With(new utils.AsyncTaskWithProgress[Integer, Seq[store.LyricsResultInfo]](activity, "Finding lyrics") {
              override def doInBackground(infos: AnyRef*): Seq[store.LyricsResultInfo] =
                srv.find(infos(0).asInstanceOf[store.LyricsSearchInfo])
              override def onPostExecute(result: Seq[store.LyricsResultInfo]): Unit = {
                super.onPostExecute(result)
                if (s.isActive)
                  inner2.dispatch(SearchCandidateEvent(result))
              }
              override def onCancelled: Unit = {
                super.onCancelled
                if (s.isActive)
                  inner2.dispatch(SearchDoneEvent())
              }
            }) { _.execute(info) }
          } else
            inner2.dispatch(SearchDoneEvent())
        override def exit: Unit = if (task != null) task.cancel(true)
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

      private class GettingResultState(info: store.LyricsResultInfo) extends State { s =>
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
              if (s.isActive)
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
              if (s.isActive)
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
        private val v = inflater.inflate(R.layout.main_lyrics_timeline, null).asInstanceOf[ListView]
        override def entry: Unit = {
          timelineAdapter = new LyricsTimelineAdapter(v, inflater, lyrics.timeline)
          v.setAdapter(timelineAdapter)
          timelineAdapter.start(((new Date).getTime - srv.getOffset).toInt)

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
              timelineAdapter.start(((new Date).getTime - srv.getOffset).toInt)
            }
          case e: ActivityPauseEvent => timelineAdapter.stop; None
          case e: ActivityResumeEvent =>
            With(None) { x =>
              timelineAdapter.start(((new Date).getTime - srv.getOffset).toInt)
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
      case R.id.ChooseProvider => chooseProvider; true
      case _ => super.onOptionsItemSelected(item)
    }

  private def chooseProvider: Unit = (new ProviderChooser(this, factory, defaultProvider) {
    override def onChoosen(choosen: store.LyricsService): Unit = {
      val editor = getSharedPreferences(LyricsActivity.PREFS_NAME, 0).edit
      editor.putString(LyricsActivity.PREFS_DEFAULT_PROVIDER, choosen.name)
      editor.commit()
    }
  }).show; true
}

object LyricsActivity {
  val PREFS_NAME = "prefs"
  val PREFS_DEFAULT_PROVIDER = "DefaultProvider"
}
