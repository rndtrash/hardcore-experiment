package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.Location

data class PlayerStateChangeRequest(val state: PlayerState, val location: Location)