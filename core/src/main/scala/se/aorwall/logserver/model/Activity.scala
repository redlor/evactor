package se.aorwall.logserver.model

/**
 * An Activity represents a finished business activity
 */

case class Activity (processId: String,
                correlationId: String,
                state: Int,
                startTimestamp: Long,
                endTimestamp: Long) {

}