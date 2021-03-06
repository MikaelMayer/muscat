/* Copyright 2009-2016 EPFL, Lausanne */
package ch.epfl.lara.concprog
package instrumentation
package monitors

trait SchedulableMonitor extends Monitor {
  def scheduler: Scheduler
  
  // Can be overriden.
  override def waitDefault() = {
    scheduler.log("wait")
    scheduler updateThreadState Wait(this, scheduler.threadLocks.filter(_ != this), scheduler.threadLocks)
  }
  override def synchronizedDefault[T](toExecute: =>T): T = {
    scheduler.log("synchronized check") 
    val prevLocks = scheduler.threadLocks
    scheduler updateThreadState Sync(this, prevLocks, this +: prevLocks) // If this belongs to prevLocks, should just continue.
    scheduler.log("synchronized -> enter")
    try {
      val s = toExecute
      scheduler updateThreadState Running(prevLocks)
      scheduler.log("synchronized -> out")
      s
    } catch {
      case e: runtime.NonLocalReturnControl[_] => 
      scheduler updateThreadState Running(prevLocks)
      scheduler.log("synchronized -> out")
      throw e
    }
  }
  override def notifyDefault() = {
    scheduler mapOtherStates {
      state => state match {
        case Wait(lockToAquire, locks, expectedResultingLocks) if lockToAquire == this => SyncUnique(lockToAquire, locks, expectedResultingLocks)
        case e => e
      }
    }
    scheduler.log("notify")
  }
  override def notifyAllDefault() = {
    scheduler mapOtherStates {
      state => state match {
        case Wait(lockToAquire, locks, expectedResultingLocks) if lockToAquire == this => Sync(lockToAquire, locks, expectedResultingLocks)
        case SyncUnique(lockToAquire, locks, expectedResultingLocks) if lockToAquire == this => Sync(lockToAquire, locks, expectedResultingLocks)
        case e => e
      }
    }
    scheduler.log("notifyAll")
  }
}
