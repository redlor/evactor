package se.aorwall.bam.process.analyse.latency

import collection.immutable.TreeMap
import grizzled.slf4j.Logging
import akka.actor.ActorRef
import se.aorwall.bam.model.events.Event
import se.aorwall.bam.model.attributes.HasLatency
import se.aorwall.bam.process.analyse.Analyser
import se.aorwall.bam.process.analyse.window.Window

class LatencyAnalyser(name: String, eventName: Option[String], maxLatency: Long)
  extends Analyser(name, eventName) with Window with Logging {

  type T = Event with HasLatency
  type S = Long

  var events = new TreeMap[Long, Long]()
  var sum = 0L

	override def receive = {
	    case event: Event with HasLatency => if (handlesEvent(event)) process(event) // TODO: case event: T  doesn't work...
	    case actor: ActorRef => testActor = Some(actor) 
	    case _ => // skip
	}
  
  def process(event: T) {

    trace(context.self + " received: " + event)
	
	// Add new
	val latency = event.latency
	events += (event.timestamp -> latency)
	sum += latency
	
	// Remove old
	val inactiveEvents = getInactive(events)
	events = events.drop(inactiveEvents.size)
	sum += inactiveEvents.foldLeft(0L) {
	  case (a, (k, v)) => a - v
	}
	
	// Count average latency
	val avgLatency = if (sum > 0) {
	  sum / events.size
	} else {
	  0
	}
	
	trace(events)
	debug(context.self + " sum: " + sum + ", no of events: " + events.size + ", avgLatency: " + avgLatency)
	
	if (avgLatency > maxLatency) {
	  alert("Average latency " + avgLatency + "ms is higher than the maximum allowed latency " + maxLatency + "ms")
	} else {
	  backToNormal("back to normal!")
	}    
  }
}