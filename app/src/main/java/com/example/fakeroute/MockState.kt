package com.example.fakeroute

/** État partagé entre l'écran et le service (la vitesse se change en direct). */
object MockState {
    @Volatile var speedKmh: Double = 50.0
    @Volatile var loop: Boolean = false
    /** Liste de [latitude, longitude] */
    @Volatile var waypoints: List<DoubleArray> = emptyList()
}
