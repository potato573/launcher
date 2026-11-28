package io.github.potato573.aura.anim

/** Global tempo of every spring animation (the user's "animation speed" setting). */
object Motion {
    /** 1 = as designed; below 1 is calmer, above 1 is snappier. */
    @Volatile
    var speed = 1f
}
