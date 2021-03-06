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
package org.evactor.model.events

import org.evactor.model.attributes.HasLatency
import org.evactor.model.attributes.HasState
import org.evactor.model.State

/**
 * Represents a completed request to a component 
 */
case class RequestEvent (
    override val id: String,
    override val timestamp: Long, 
    val correlationId: String,
    val component: String,
    val inboundRef: Option[String],
    val outboundRef: Option[String],
    val state: State,
    val latency: Long) 
  extends Event(id, timestamp)
  with HasLatency 
  with HasState {

}
