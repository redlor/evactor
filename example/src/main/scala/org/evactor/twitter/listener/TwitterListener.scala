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
package org.evactor.twitter.listener

import java.io.BufferedReader
import java.io.InputStreamReader
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.evactor.listen.Listener
import com.twitter.ostrich.stats.Stats
import akka.actor.ActorLogging
import akka.actor.ActorRef
import org.apache.commons.codec.binary.Base64
import java.util.zip.GZIPInputStream
import java.io.InputStream
import org.apache.http.params.HttpConnectionParams
import org.evactor.ConfigurationException

class TwitterListener(sendTo: ActorRef, url: String, username: String, password: String) extends Listener with ActorLogging {
  
  var stream = connect()
  var failures = 0  
   
  def receive = {
    case "" => read()
    case msg => log.debug("can't handle {}", msg)
  }

  private[this] def read(){
    
    val inputLine = try{
      stream.readLine()
    } catch {
      case e => {
        log.warning("caught an exception while trying to read from stream: {}", e)
        stream = connect()
        stream.readLine
      }
    } 
    
    if (inputLine == null) {
      Stats.incr("twitterlistener:null")
      log.debug("inputline is null")
      failures = failures +1
      Thread.sleep(50)
    } else if (inputLine.trim.size == 0) {
      Stats.incr("twitterlistener:empty")
      log.debug("inputline is empty")
      failures = failures +1
      Thread.sleep(50)
    } else {
      Stats.incr("twitterlistener:status")
      log.debug("inputline: {}", inputLine)
      sendTo ! inputLine
    }
    
    if(failures > 10){
      log.warning("more than 10 failures in a row, will try to reconnect to the twitter stream")
      stream = connect()
      failures = 0
    }
    
    context.self ! ""
    
  }
  
  private[this] def connect (): BufferedReader = {
    
    if(url == null)
      throw new IllegalArgumentException("No url provided")
    
    if(username == null || password == null)
      throw new IllegalArgumentException("No credentials provided")
    
    val credentials = "%s:%s".format(username, password)
    val client = new DefaultHttpClient();
    val method = new HttpGet(url);
    val encoded = Base64.encodeBase64String(credentials.getBytes)
    method.setHeader("Authorization", "Basic " + encoded);
    method.setHeader("Content-Type", "application/x-www-form-urlencoded") 
    method.setHeader("User-Agent", "evactor") 
    val params = client.getParams()
    HttpConnectionParams.setConnectionTimeout(params, 5000)
    HttpConnectionParams.setSoTimeout(params, 5000)
//    method.setHeader("Accept-Encoding", "deflate, gzip")
//    method.setHeader("Host", "stream.twitter.com")
 
    val response = client.execute(method)
    
    if(response.getStatusLine.getStatusCode >= 400){
      
      if(response.getStatusLine.getStatusCode == 401){
        throw new ConfigurationException("Twitter returned \"401 Unauthorized\". Check the Twitter username and password.")
      } else {
        throw new RuntimeException("Couldn't connect to the Twitter stream API, status returned: %s".format(response.getStatusLine))  
      }
      
    }
    
    val entity = response.getEntity
    new BufferedReader(new InputStreamReader(entity.getContent))
  }
  
  override def preStart() {
    super.preStart()
    context.self ! ""
  }
  
}

//class StreamingGZIPInputStream(val wrapped: InputStream ) extends GZIPInputStream(wrapped) {
//
//  override def available(): Int = wrapped.available()
//
//}