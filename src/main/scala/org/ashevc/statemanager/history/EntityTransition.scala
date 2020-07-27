package org.ashevc.statemanager.history

import org.ashevc.statemanager.state.State
import org.joda.time.LocalDateTime

case class EntityTransition(entityId: Long, from: State, to: State, dateTime: LocalDateTime)
