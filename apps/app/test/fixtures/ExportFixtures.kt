package dev.justinsmith.interval.fixtures

/**
 * An export as 0.2's first builds wrote it, schema 1: a plan was always an interval workout, with no `lift`, and
 * nothing said how a workout felt. A constant, not a resource: common tests also run as Android local tests,
 * which load none.
 */
const val SCHEMA_1_EXPORT = """
{
    "schema": 1,
    "app": "interval",
    "version": "0.2.0",
    "exportedAtEpochMillis": 1760443200000,
    "settings": {
        "theme": "Light",
        "keepScreenOn": false,
        "weightKg": 82.0,
        "weightUnit": "Kg",
        "effort": "Steady",
        "weeklyGoal": 3,
        "reminderMinuteOfDay": 420,
        "cues": { "sound": true, "voice": false, "halfway": true, "vibrate": true }
    },
    "timer": { "prepSeconds": 10, "workSeconds": 40, "restSeconds": 20, "rounds": 6, "activity": "Rowing" },
    "presets": [
        { "id": 3, "name": "Hills", "config": { "prepSeconds": 10, "workSeconds": 45, "restSeconds": 15, "rounds": 8, "activity": "Running" } }
    ],
    "history": [
        { "id": 7, "finishedAtEpochMillis": 1760400000000, "config": { "prepSeconds": 10, "workSeconds": 45, "restSeconds": 15, "rounds": 8, "activity": "Running" }, "elapsedSeconds": 490, "endedEarly": false, "workActiveSeconds": 360 },
        { "id": 8, "finishedAtEpochMillis": 1760300000000, "config": { "prepSeconds": 10, "workSeconds": 20, "restSeconds": 10, "rounds": 8, "activity": "General" }, "elapsedSeconds": 130, "endedEarly": true, "workActiveSeconds": null }
    ],
    "plan": [
        { "id": 2, "name": "Hills", "config": { "prepSeconds": 10, "workSeconds": 45, "restSeconds": 15, "rounds": 8, "activity": "Running" }, "weekday": 2, "epochDay": null, "presetId": 3, "createdAtEpochMillis": 1760000000000 }
    ],
    "lift": {
        "bellsKg": [16.0, 24.0],
        "barKg": null,
        "restSeconds": 120,
        "bands": [],
        "exercises": [],
        "programs": [],
        "following": null,
        "sessions": [
            {
                "uid": "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
                "startedAtEpochMillis": 1760350000000,
                "finishedAtEpochMillis": 1760350900000,
                "title": "Swings",
                "program": null,
                "day": null,
                "sets": [
                    { "id": 41, "exercise": "TwoHandSwing", "equipment": "Kettlebell", "bellKg": 24.0, "reps": 10, "side": null, "double": false, "seconds": null, "band": null, "loggedAtEpochMillis": 1760350100000 }
                ]
            }
        ]
    }
}
"""

/** A file from a build newer than any this one knows. */
const val SCHEMA_12_EXPORT = """{ "schema": 12, "app": "interval", "version": "0.9.0", "exportedAtEpochMillis": 0 }"""

/** Some other app's JSON. */
const val FOREIGN_JSON = """{ "schema": 1, "app": "someone-else", "workouts": [] }"""

/** Half a file. */
const val DAMAGED_EXPORT = """{ "schema": 2, "app": "interval", "version": "0.2.0", "history": [ { "id": 1, "fini"""
