package name.kghost.android.lyrics

import scala.collection.mutable.Queue
import android.util.Log

class State {
  private var fsm_ : StateMachine = null
  final def fsm = fsm_
  final def isActive = fsm_ != null
  final def internalEntry(m: StateMachine): Unit = { fsm_ = m; entry }
  final def internalExit: Unit = { exit; fsm_ = null }
  protected def entry: Unit = {}
  protected def exit: Unit = {}
  def action(ev: Event): Option[State] = ev match {
    case Event(name) => Log.e("LyricsState", "State " + this.getClass.getName + " unknown event " + name); None
  }
}

class FinalState extends State

class StateMachine(private var state: State) {
  private val queue = new Queue[Event]
  private var dispatching = false
  lockAndDispatch({ () => state.internalEntry(this) })
  def dispatch(ev: Event): Unit = {
    if (dispatching)
      queue.enqueue(ev)
    else
      lockAndDispatch({ () => dispatch2(ev) })
  }
  private def lockAndDispatch(f: () => Unit): Unit = {
    dispatching = true
    f()
    while (!queue.isEmpty) {
      dispatch2(queue.dequeue)
    }
    dispatching = false
  }
  private def dispatch2(ev: Event): Unit = state.action(ev) match {
    case Some(n: State) => {
      state.internalExit
      state = n
      state.internalEntry(this)
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
