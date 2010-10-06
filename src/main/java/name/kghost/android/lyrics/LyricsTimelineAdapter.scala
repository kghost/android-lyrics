package name.kghost.android.lyrics

import java.util.Date
import scala.collection.mutable.HashMap
import android.widget.{ TextView, ListView, BaseAdapter }
import android.view.{ LayoutInflater, View, ViewGroup, Window }
import android.view.animation.{ Animation, AnimationUtils }
import android.app.Activity
import android.os.Handler
import android.util.Log
import utils.With

class LyricsTimelineAdapter(list: ListView, activity: Activity, timeline: Seq[(Int, Int, String)])
  extends BaseAdapter { adapter =>
  val mInflater = LayoutInflater.from(activity)

  private var is_start: Boolean = false
  private var time_song_start: Int = 0
  private var time_real_start: Long = 0
  private def time_song = (new Date().getTime - time_real_start + time_song_start).toInt
  private val showed = new HashMap[Int, Animation]

  private val handler: Handler = new Handler
  private val task = new Runnable {
    override def run: Unit = adapter.update
  }

  def start(time: Int): Unit = {
    time_song_start = time
    time_real_start = new Date().getTime
    for ((x, y) <- showed) {
      y.cancel
    }
    is_start = true
    update
  }

  def stop: Unit = {
    for ((x, y) <- showed) {
      y.cancel
    }
    handler.removeCallbacks(task)
    is_start = false
  }

  private def show(index: Int, dur: Int): Unit = getViewAt(index) match {
    case view: View => {
      val animation = AnimationUtils.loadAnimation(activity, R.anim.timeline_bar)
      animation.setDuration(dur)
      animation.setAnimationListener(new Animation.AnimationListener {
        override def onAnimationRepeat(animation: Animation): Unit = assert(false)
        override def onAnimationStart(animation: Animation): Unit = {
          view.setVisibility(View.VISIBLE)
          assert(!showed.contains(index))
          showed += ((index, animation))
        }
        override def onAnimationEnd(animation: Animation): Unit = {
          assert(showed.contains(index))
          showed -= ((index))
          view.setVisibility(View.GONE)
        }
      })
      view.startAnimation(animation)
    }
    case null => Unit
  }

  private def update: Unit = if (is_start) {
    handler.removeCallbacks(task)
    val now = time_song
    find(now) match {
      case (nearest, showing) => {
        Log.d("LyricsAnimation", now.toString + '|' + nearest.toString + '|' + showing.toString)
        showing foreach {
          case (index, dur) => if (!showed.contains(index)) show(index, dur)
        }
        nearest match {
          case Some(x) => {
            handler.postDelayed(task, x - now)
          }
          case None => Unit
        }
      }
    }
  }

  // return (nearest event time, showing: (index, duration))
  private def find(time: Int): (Option[Int], List[(Int, Int)]) = find(time, timeline, 0, None, Nil)
  private def find(time: Int, seq: Seq[(Int, Int, String)], index: Int, nearest: Option[Int], showing: List[(Int, Int)]): (Option[Int], List[(Int, Int)]) = seq match {
    case (start, end, string) :: rest =>
      find(time, rest, index + 1, nearest match {
        case Some(s) =>
          if (time < start && start < s)
            Some(start)
          else
            nearest
        case None =>
          if (time < start)
            Some(start)
          else
            None
      }, if (start <= time && time < end)
        (index, end - time) :: showing
      else
        showing)
    case Nil => (nearest, showing)
  }

  override def getCount = timeline.size
  override def getItem(i: Int): (Int, Int, String) = timeline(i)
  override def getItemId(position: Int) = position
  override def getView(position: Int, convertView: View, parent: ViewGroup): View =
    With(if (convertView == null)
      mInflater.inflate(R.layout.lyrics_line_item, null)
    else {
      With(convertView) { _.findViewById(R.id.TimelineBar).clearAnimation }
    }) { view =>
      var item = getItem(position)
      view.findViewById(R.id.TimelineText).asInstanceOf[TextView].setText(item._3)
      item match {
        case (start, end, _) => {
          val now = time_song
          if (start <= now && now < end) update
        }
      }
    }
  private def getViewAt(position: Int): View = {
    val p: Int = position - list.getFirstVisiblePosition()
    if (p < 0 || p >= list.getChildCount())
      null;
    else
      list.getChildAt(p).findViewById(R.id.TimelineBar);
  }
}
