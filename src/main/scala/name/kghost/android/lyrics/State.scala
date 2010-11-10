package name.kghost.android.lyrics

import scala.collection.mutable.Queue
import android.util.Log

class State {
  private var isActive_ = false
  final def isActive = isActive_
  final def internalEntry: Unit = { isActive_ = true; entry }
  final def internalExit: Unit = { exit; isActive_ = false }
  protected def entry: Unit = {}
  protected def exit: Unit = {}
  def action(ev: Event): Option[State] = ev match {
    case Event(name) => Log.e("LyricsState", "State " + this.getClass.getName + " unknown event " + name); None
  }
}

class FinalState extends State

class StateMachine(private var state: State) {
  state.internalEntry
  private val queue = new Queue[Event]
  private var dispatching = false
  def dispatch(ev: Event): Unit = {
    if (dispatching) {
      queue.enqueue(ev)
    } else {
      dispatching = true
      dispatch2(ev)
      while (!queue.isEmpty) {
        dispatch2(queue.dequeue)
      }
      dispatching = false
    }
  }
  private def dispatch2(ev: Event): Unit = state.action(ev) match {
    case Some(n: State) => {
      state.internalExit
      state = n
      state.internalEntry
    }
    case None => Unit
  }
  def finish: Unit = state.internalExit
}

class Event() {
  val name = this.getClass.getName
}

object Event {
  def unapply(ev: Event): Option[String] = Some(ev.name)
}

case class SimpleEvent(override val name: String) extends Event
