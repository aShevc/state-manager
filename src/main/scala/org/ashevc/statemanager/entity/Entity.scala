package org.ashevc.statemanager.entity

import org.ashevc.statemanager.state.State

case class Entity(id: Long, name: String, state: State)
