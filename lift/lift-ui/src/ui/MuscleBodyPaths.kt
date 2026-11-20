package dev.justinsmith.lift.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import dev.justinsmith.lift.domain.Muscle

/**
 * Normalized (`100 × 220`, centred at `x = 50`) anatomical silhouettes and muscle regions for the front and back
 * body map. Every [Muscle] maps to at least one region on the front, the back, or both; paths are parsed lazily
 * once and reused across draws.
 */
internal object MuscleBodyPaths {
    const val VIEW_WIDTH = 100f
    const val VIEW_HEIGHT = 220f

    /** Front body silhouette: head + unified torso, arms, and legs. */
    val frontSilhouetteData: List<String> = listOf(
        // Head
        "M 50 6 C 55.8 6 59.5 11.2 59.5 18 C 59.5 24.8 55.8 29.8 50 29.8 " +
            "C 44.2 29.8 40.5 24.8 40.5 18 C 40.5 11.2 44.2 6 50 6 Z",
        // Body (neck -> right side -> legs -> left side -> neck)
        "M 44.5 28.5 L 55.5 28.5 L 56.5 35.5 " +
            "C 61.5 37.5 67.5 39.5 73 41.5 C 77.5 43.2 80 47.5 80.5 53 " +
            "L 83 78 L 87.5 106 L 90 118 C 90.5 121.5 88.5 125 85.5 125.5 " +
            "C 83 125.8 81.5 123 81 119.5 L 79.5 111 L 73 84 L 70 63 " +
            "L 68.5 86 L 67 98 C 69.5 106 70.5 117 69 131 L 66 157 " +
            "C 67.5 167 67.5 179 65.5 192 L 64 205 L 67.5 213 " +
            "C 68 215.5 65.5 217 61 217 C 57.5 217 55.5 215 55.5 212 " +
            "L 55.5 204 L 54.5 182 L 53.5 158 L 51.5 121 L 50 116 L 48.5 121 " +
            "L 46.5 158 L 45.5 182 L 44.5 204 L 44.5 212 " +
            "C 44.5 215 42.5 217 39 217 C 34.5 217 32 215.5 32.5 213 " +
            "L 36 205 L 34.5 192 C 32.5 179 32.5 167 34 157 L 31 131 " +
            "C 29.5 117 30.5 106 33 98 L 31.5 86 L 30 63 L 27 84 L 20.5 111 " +
            "L 19 119.5 C 18.5 123 17 125.8 14.5 125.5 C 11.5 125 9.5 121.5 10 118 " +
            "L 12.5 106 L 17 78 L 19.5 53 C 20 47.5 22.5 43.2 27 41.5 " +
            "C 32.5 39.5 38.5 37.5 43.5 35.5 Z",
    )

    /** Back body silhouette (same athletic proportions as front). */
    val backSilhouetteData: List<String> = frontSilhouetteData

    /** Front-view muscle regions keyed by [Muscle]. */
    val frontMuscleData: Map<Muscle, List<String>> = mapOf(
        Muscle.Traps to listOf(
            "M 44 35.5 L 35.5 39.5 L 36.5 44 L 45 42 Z",
            "M 56 35.5 L 64.5 39.5 L 63.5 44 L 55 42 Z",
        ),
        Muscle.Shoulders to listOf(
            "M 34 41 C 27 42.5 22 46 21 53 C 20.8 57.5 23.5 59.5 27.5 58 C 30.5 55 32.5 49 34 41 Z",
            "M 66 41 C 73 42.5 78 46 79 53 C 79.2 57.5 76.5 59.5 72.5 58 C 69.5 55 67.5 49 66 41 Z",
        ),
        Muscle.Chest to listOf(
            "M 48.8 45 C 43 44.5 36.5 46 33.5 50.5 C 32.5 56 34.5 62.5 41 63.5 C 45.5 64 48.5 61.5 48.8 58 Z",
            "M 51.2 45 C 57 44.5 63.5 46 66.5 50.5 C 67.5 56 65.5 62.5 59 63.5 C 54.5 64 51.5 61.5 51.2 58 Z",
        ),
        Muscle.Biceps to listOf(
            "M 27.5 59.5 C 24 60 21 63 20 70 C 19.5 76 21.5 81 24.5 81.5 C 27 81 28.5 74 28.5 65 Z",
            "M 72.5 59.5 C 76 60 79 63 80 70 C 80.5 76 78.5 81 75.5 81.5 C 73 81 71.5 74 71.5 65 Z",
        ),
        Muscle.Forearms to listOf(
            "M 23 83.5 C 19.5 84.5 17 90 15 100 L 13.5 110 " +
                "C 14.5 112 17 112 18.5 110 L 22 98 C 24 91.5 24.5 86 23 83.5 Z",
            "M 77 83.5 C 80.5 84.5 83 90 85 100 L 86.5 110 " +
                "C 85.5 112 83 112 81.5 110 L 78 98 C 76 91.5 75.5 86 77 83.5 Z",
        ),
        Muscle.Abs to listOf(
            "M 42 66.5 L 58 66.5 C 57.8 78 57 90 55.5 102 C 53.5 104.5 46.5 104.5 44.5 102 C 43 90 42.2 78 42 66.5 Z",
        ),
        Muscle.Obliques to listOf(
            "M 34.5 65.5 L 40.5 67.5 L 41.5 96 L 36 98 C 33.5 90 33 78 34.5 65.5 Z",
            "M 65.5 65.5 L 59.5 67.5 L 58.5 96 L 64 98 C 66.5 90 67 78 65.5 65.5 Z",
        ),
        Muscle.HipFlexors to listOf(
            "M 35 100 L 42.5 102 L 45.5 111 L 34.5 111 C 33.5 107 34 103 35 100 Z",
            "M 65 100 L 57.5 102 L 54.5 111 L 65.5 111 C 66.5 107 66 103 65 100 Z",
        ),
        Muscle.Quads to listOf(
            "M 34 113.5 L 43 114 L 44.5 154 C 41.5 156.5 37.5 156.5 35.5 153 C 33 141 32 126 34 113.5 Z",
            "M 66 113.5 L 57 114 L 55.5 154 C 58.5 156.5 62.5 156.5 64.5 153 C 67 141 68 126 66 113.5 Z",
        ),
        Muscle.Adductors to listOf(
            "M 44 114.5 L 48.8 117 L 46.5 147 L 45 147 Z",
            "M 56 114.5 L 51.2 117 L 53.5 147 L 55 147 Z",
        ),
    )

    /** Back-view muscle regions keyed by [Muscle]. */
    val backMuscleData: Map<Muscle, List<String>> = mapOf(
        Muscle.Traps to listOf(
            "M 44.5 34.5 L 55.5 34.5 L 65.5 41.5 L 57 52 L 50 58.5 L 43 52 L 34.5 41.5 Z",
        ),
        Muscle.RearDelts to listOf(
            "M 33.5 42.5 C 27 43.5 22 47 21.5 53.5 C 22 57.5 25 58.5 29 56.5 C 32 53.5 33.5 48 33.5 42.5 Z",
            "M 66.5 42.5 C 73 43.5 78 47 78.5 53.5 C 78 57.5 75 58.5 71 56.5 C 68 53.5 66.5 48 66.5 42.5 Z",
        ),
        Muscle.UpperBack to listOf(
            "M 36.5 47.5 L 42.5 53 L 50 60 L 57.5 53 L 63.5 47.5 L 64 62 C 59 66.5 54 68 50 68 C 46 68 41 66.5 36 62 Z",
        ),
        Muscle.Lats to listOf(
            "M 33 58 C 36.5 64 41.5 68.5 45.5 70 L 42 86.5 L 35 84.5 C 32 76.5 31.5 66.5 33 58 Z",
            "M 67 58 C 63.5 64 58.5 68.5 54.5 70 L 58 86.5 L 65 84.5 C 68 76.5 68.5 66.5 67 58 Z",
        ),
        Muscle.Triceps to listOf(
            "M 27.5 59 C 24 59.5 20.8 63 20 71 C 19.8 77.5 22 82 25 82 C 27.5 81 29 73.5 28.5 64.5 Z",
            "M 72.5 59 C 76 59.5 79.2 63 80 71 C 80.2 77.5 78 82 75 82 C 72.5 81 71 73.5 71.5 64.5 Z",
        ),
        Muscle.Forearms to listOf(
            "M 23 83.5 C 19.5 84.5 17 90 15 100 L 13.5 110 " +
                "C 14.5 112 17 112 18.5 110 L 22 98 C 24 91.5 24.5 86 23 83.5 Z",
            "M 77 83.5 C 80.5 84.5 83 90 85 100 L 86.5 110 " +
                "C 85.5 112 83 112 81.5 110 L 78 98 C 76 91.5 75.5 86 77 83.5 Z",
        ),
        Muscle.LowerBack to listOf(
            "M 43 72 L 57 72 L 59.5 96.5 C 55.5 99 44.5 99 40.5 96.5 Z",
        ),
        Muscle.Glutes to listOf(
            "M 34 99.5 C 39.5 98.5 45.5 100 49 103 L 49 121.5 " +
                "C 44 124.5 36.5 124 33 119 C 30.8 113 31.5 104.5 34 99.5 Z",
            "M 66 99.5 C 60.5 98.5 54.5 100 51 103 L 51 121.5 " +
                "C 56 124.5 63.5 124 67 119 C 69.2 113 68.5 104.5 66 99.5 Z",
        ),
        Muscle.Hamstrings to listOf(
            "M 33.5 123.5 C 38.5 126 44 126 47.5 124 L 46 155 " +
                "C 43 157.5 38 157.5 35.5 154.5 C 33 144 32.5 133 33.5 123.5 Z",
            "M 66.5 123.5 C 61.5 126 56 126 52.5 124 L 54 155 " +
                "C 57 157.5 62 157.5 64.5 154.5 C 67 144 67.5 133 66.5 123.5 Z",
        ),
        Muscle.Calves to listOf(
            "M 36 164.5 C 39.5 162.5 43.5 163.5 45 166.5 " +
                "C 45.8 176 44.8 188 43 197.5 C 40.5 199 38 198.5 37 196 C 34.5 186 34 174 36 164.5 Z",
            "M 64 164.5 C 60.5 162.5 56.5 163.5 55 166.5 " +
                "C 54.2 176 55.2 188 57 197.5 C 59.5 199 62 198.5 63 196 C 65.5 186 66 174 64 164.5 Z",
        ),
    )

    val frontSilhouette: List<Path> by lazy { frontSilhouetteData.map(::parse) }
    val backSilhouette: List<Path> by lazy { backSilhouetteData.map(::parse) }
    val frontMuscles: Map<Muscle, List<Path>> by lazy { frontMuscleData.mapValues { (_, list) -> list.map(::parse) } }
    val backMuscles: Map<Muscle, List<Path>> by lazy { backMuscleData.mapValues { (_, list) -> list.map(::parse) } }

    private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()
}
