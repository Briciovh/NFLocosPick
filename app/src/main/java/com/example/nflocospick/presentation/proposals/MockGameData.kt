package com.example.nflocospick.presentation.proposals

data class MockGame(
    val id: Int,
    val awayTeam: String,
    val awayCity: String,
    val homeTeam: String,
    val homeCity: String,
    val gameTime: String,
    val isLocked: Boolean = false
)

val mockWeekGames = listOf(
    MockGame(1, "CHIEFS",  "Kansas City",   "RAIDERS",  "Las Vegas",    "SUN · NOV 24 · 1:00 PM ET"),
    MockGame(2, "COWBOYS", "Dallas",         "EAGLES",   "Philadelphia", "SUN · NOV 24 · 4:25 PM ET"),
    MockGame(3, "49ERS",   "San Francisco",  "PACKERS",  "Green Bay",    "MON · NOV 25 · 8:15 PM ET"),
    MockGame(4, "BILLS",   "Buffalo",        "DOLPHINS", "Miami",        "JUE · NOV 21 · 8:20 PM ET", isLocked = true),
)
