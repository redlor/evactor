/*
 * Copyright 2012 Albert Örwall
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.evactor.api

import java.net.URLDecoder

import org.codehaus.jackson.map.ObjectMapper
import org.evactor.model.events.Event
import org.evactor.model.State
import org.evactor.storage.EventStorage
import org.evactor.storage.EventStorageExtension
import org.evactor.storage.KpiStorage
import org.evactor.storage.LatencyStorage
import org.evactor.storage.StateStorage
import org.jboss.netty.handler.codec.http.HttpResponse

import com.fasterxml.jackson.module.scala.DefaultScalaModule

import akka.actor.ActorSystem
import unfiltered.response.BadRequest
import unfiltered.response.NotFound
import unfiltered.response.ResponseFunction
import unfiltered.response.ResponseString

class EventAPI (val system: ActorSystem) {

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  // TODO: Some other system to decide which storage solutions that is available.
  // Maybe a LatencyAPI and StateAPI trait extending this one
  val storage: EventStorage with LatencyStorage with StateStorage with KpiStorage = EventStorageExtension(system).getEventStorage() match {
    case Some(s: EventStorage with LatencyStorage with StateStorage with KpiStorage) => s
    case Some(s) => throw new RuntimeException("Storage impl is of the wrong type: %s".format(s))
    case None => throw new RuntimeException("No storage impl found")
  }
  
  def doRequest(
      path: Seq[String], 
      params: Map[String, Seq[String]]): ResponseFunction[HttpResponse] = 
    path match {
      case "channels" :: Nil => getChannels(getCount(params.get("count"), 100))
      case "categories" :: channel :: Nil => getCategories(decode(channel), getCount(params.get("count"), 100))
   	  case "stats" :: tail => getStats(tail, params)
   	  case "events" :: tail => getEvents(tail, params)
   	  case "event" :: id :: Nil => getEvent(id) 
   	  case "latency" :: channel :: Nil => getAvgLatency(decode(channel), None, getInterval(params.get("interval")))
   	  case "latency" :: channel :: category :: Nil => getAvgLatency(decode(channel), Some(decode(category)), getInterval(params.get("interval")))
      case "avg" :: channel :: Nil => getAverage(decode(channel), None, getInterval(params.get("interval")))
      case "avg" :: channel :: category :: Nil => getAverage(decode(channel), Some(decode(category)), getInterval(params.get("interval")))
   	  case _ => BadRequest
  }
  
  protected[api] def getChannels(count: Int): List[Map[String, Any]] = 
    storage.getEventChannels(count)
  
  protected[api] def getCategories(channel: String, count: Int): List[Map[String, Any]] = 
  	storage.getEventCategories(decode(channel), count)

  protected[api] def getStats(path: Seq[String], params: Map[String, Seq[String]]): Map[String, Any] =
    path match {
      case channel :: Nil => storage.getStatistics(decode(channel), None, Some(0L), Some(now), getInterval(params.get("interval")))
   	  case channel :: category :: Nil => storage.getStatistics(decode(channel), Some(decode(category)), Some(0L), Some(now), getInterval(params.get("interval")))
//   	  case channel :: category :: state :: Nil => storage.getStatistics(decode(channel), Some(decode(category)), getState(state), Some(0L), Some(now), getInterval(params.get("interval")))
   	  case e => throw new IllegalArgumentException("Illegal stats request: %s".format(e))
  }
  
  protected[api] def getEvents(path: Seq[String], params: Map[String, Seq[String]]): List[Event] = 
    path match {
 	    case channel :: Nil => storage.getEvents(decode(channel), None, None, None, 10, 0)
 	    case channel :: category :: Nil => storage.getEvents(decode(channel), Some(decode(category)), None, None, 10, 0)
 	    case e => throw new IllegalArgumentException("Illegal events request: %s".format(e))
    }
  	
  protected[api] def getEvent(id: String): Option[Event] = 
    storage.getEvent(id) match {
 	 	  case Some(e: Event) => Some(e)
 	 	  case _ => None
	}
  
  protected[api] def getAvgLatency(channel: String, category: Option[String], interval: String): Map[String, Any] = 
    average(storage.getLatencyStatistics(channel, None, Some(0L), Some(now), interval))
  
  protected def getAverage(channel: String, category: Option[String], interval: String) = {
    average(storage.getSumStatistics(channel, category, Some(0L), Some(now), interval))
  }
  
  protected[api] def average ( sum: (Long, List[(Long, Long)])) = 
    Map ("timestamp" -> sum._1, 
         "stats" -> sum._2.map { 
    case (x,y) => if(x > 0) y/x
                  else 0
  })
  
  implicit protected[api] def anyToResponse(any: AnyRef): ResponseFunction[HttpResponse] = any match {
    case None => NotFound
    case Some(obj) => ResponseString(mapper.writeValueAsString(obj))
    case _ => ResponseString(mapper.writeValueAsString(any))
  }  
  	
  protected[api] def decode(name: String) = {
    URLDecoder.decode(name, "UTF-8")
  }
  
  protected[api] def getCount(count: Option[Seq[String]], default: Int): Int = count match {
    case Some(s) => s.mkString.toInt 
    case None => default
  }
  
  protected[api] def getInterval (interval: Option[Seq[String]]) = interval match {
    case Some( i :: Nil) => i
		case None => "day"
  }
  
  protected[api] def getState(state: Seq[String]) = State.apply(state.mkString)
  
  implicit protected[api] def toCountMap(list: List[(String, Long)]): List[Map[String, Any]] =
    list.map { (t) => Map("name" -> t._1, "count" -> t._2) }
  
  implicit protected[api] def toStatsMap(stats: (Long, List[Long])): Map[String, Any] = 
    Map ("timestamp" -> stats._1, "stats" -> stats._2)
    
  protected[api] def now = System.currentTimeMillis
  
}