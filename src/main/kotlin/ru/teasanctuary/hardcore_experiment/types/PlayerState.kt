package ru.teasanctuary.hardcore_experiment.types

enum class PlayerState {
    /**
     * Начальное состояние. Игрок живой.
     */
    Alive,

    /**
     * Игрок умер, даётся время на возрождение, режим наблюдателя ограничен местом смерти.
     */
    LimitedSpectator,

    /**
     * Игрок умер окончательно, снимается ограничение на перемещение.
     */
    Spectator
}