package me.vripper

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.apache.commons.collections4.queue.CircularFifoQueue

class RingAppender : AppenderBase<ILoggingEvent>() {

    object Static {
        private const val CAPACITY = 4096
        private val events = CircularFifoQueue<ILoggingEvent>(CAPACITY)

        fun offer(event: ILoggingEvent): Boolean = synchronized(events) {
            events.offer(event)
        }

        fun poll(): ILoggingEvent? = synchronized(events) {
            events.poll()
        }

        fun isEmpty(): Boolean = synchronized(events) {
            events.isEmpty()
        }
    }

    override fun append(event: ILoggingEvent) {
        Static.offer(event)
    }
}