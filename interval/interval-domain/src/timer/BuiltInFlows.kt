package dev.justinsmith.interval.timer

/**
 * The flows the app ships with, in the order they are listed. Their words are data, like the built-in presets'
 * names: a flow's name is kept with what it is, and a move's is said aloud. Each takes a round number of minutes
 * with the default get-ready countdown, and no two share a shape, since a finished one is named by its shape.
 * Every move is one of Lift's exercises, named by its key, so the flow shows it by Lift's figure and cues.
 */
val builtInFlows: List<Flow> = listOf(
    Flow(
        key = HIPS_KEY,
        name = "Hips and shoulders",
        use = FlowUse.Before,
        note = "no bell",
        moves = listOf(
            hipSwitch(seconds = 60),
            hipFlexorLunge(seconds = 40),
            deepSquat(seconds = 60),
            worldsGreatestStretch(seconds = 40),
            threadTheNeedle(seconds = 30),
            Move(
                "Shoulder circles",
                seconds = 30,
                cue = "Arm long, the biggest slow circle the shoulder allows, forward then back.",
                sided = true,
                held = false,
                exercise = "ShoulderCircles",
            ),
        ),
    ),
    // The five minutes worth doing every day: the spine, the hips, the squat, the upper back and the backs of the
    // legs, once through. Ahead of the wrists, so five minutes picked on Today are spent on it.
    Flow(
        key = "daily-five",
        name = "Daily five",
        use = FlowUse.After,
        note = "every day",
        moves = listOf(
            catCow(seconds = 40),
            hipFlexorLunge(seconds = 30),
            deepSquat(seconds = 45),
            threadTheNeedle(seconds = 25),
            Move(
                "Downward dog",
                seconds = 45,
                cue = "Hips high, back long. Bend one knee, then the other, to walk the heels down.",
                held = false,
                exercise = "DownwardDog",
            ),
        ),
    ),
    Flow(
        key = WRISTS_KEY,
        name = "Wrists and fingers",
        use = FlowUse.Before,
        note = "before the wall",
        moves = listOf(
            Move(
                "Wrist circles",
                seconds = 30,
                cue = "Fists loose, slow circles, one way then the other.",
                held = false,
                exercise = "WristCircles",
            ),
            Move(
                "Palms down, fingers back",
                seconds = 45,
                cue = "On all fours, fingers pointing at your knees. Rock back until the forearms pull.",
                exercise = "PalmsDownFingersBack",
            ),
            Move(
                "Backs of the hands",
                seconds = 45,
                cue = "On all fours, backs of the hands down, fingers in. Lean in lightly.",
                exercise = "BacksOfTheHands",
            ),
            Move(
                "Finger pulls",
                seconds = 30,
                cue = "Draw each finger back in turn with the other hand, thumb last.",
                sided = true,
                held = false,
                exercise = "FingerPulls",
            ),
            Move(
                "Forearm stretch",
                seconds = 30,
                cue = "Arm long, palm up. Draw the fingers down and back with the other hand.",
                sided = true,
                exercise = "ForearmStretch",
            ),
        ),
    ),
    Flow(
        key = FALLS_KEY,
        name = "Core for falls",
        use = FlowUse.Before,
        note = "no bell",
        moves = listOf(
            Move(
                "Spinal rock",
                seconds = 70,
                cue = "Chest up, then round into a C and rock back, head off the mat. Rock up to sitting.",
                held = false,
                exercise = "SpinalRock",
            ),
            Move(
                "Shoulder roll",
                seconds = 45,
                cue = "Same hand and foot forward. Tuck the head, roll over the shoulder and come all the way up.",
                sided = true,
                held = false,
                exercise = "ShoulderRoll",
            ),
            Move(
                "Sit-up",
                seconds = 70,
                cue = "Lie flat, ribs open. Close the ribs, sit all the way up and extend the spine.",
                held = false,
                exercise = "SitUp",
            ),
            Move(
                "Cross touch",
                seconds = 60,
                cue = "One leg long and straight, elbow to the opposite knee. Side to side, core on.",
                held = false,
                exercise = "CrissCross",
            ),
            Move(
                "V-up across",
                seconds = 75,
                cue = "Lie open, hands down. Close the ribs, sit up into a V and touch the opposite foot.",
                held = false,
                exercise = "CrossVUp",
            ),
        ),
    ),
    Flow(
        key = BEFORE_ROLLING_KEY,
        name = "Before rolling",
        use = FlowUse.Before,
        note = "before BJJ",
        moves = listOf(
            Move(
                "Hip escape",
                seconds = 50,
                cue = "Onto one shoulder, push the hips away from the feet, then bring the knees back in.",
                sided = true,
                held = false,
                exercise = "HipEscape",
            ),
            Move(
                "Bridge and roll",
                seconds = 40,
                cue = "Heels to the hips. Bridge high, then turn over one shoulder and look at the mat.",
                sided = true,
                held = false,
                exercise = "BridgeAndRoll",
            ),
            Move(
                "Technical stand-up",
                seconds = 40,
                cue = "Post a hand behind, lift the hips, swing the leg back under and stand in your stance.",
                sided = true,
                held = false,
                exercise = "TechnicalStandUp",
            ),
            hipSwitch(seconds = 60),
            Move(
                "Sit-out",
                seconds = 60,
                cue = "From hands and knees, thread one leg through under you, then back. Switch sides.",
                held = false,
                exercise = "SitOut",
            ),
            worldsGreatestStretch(seconds = 40),
        ),
    ),
    Flow(
        key = "spine-hamstrings",
        name = "Spine and hamstrings",
        use = FlowUse.After,
        moves = listOf(
            catCow(seconds = 60),
            childsPose(seconds = 70),
            threadTheNeedle(seconds = 45),
            cobra(seconds = 60),
            Move(
                "Downward dog",
                seconds = 60,
                cue = "Hips high, back long. Bend one knee, then the other, to walk the heels down.",
                exercise = "DownwardDog",
            ),
            halfSplit(seconds = 60),
            Move(
                "Forward fold",
                seconds = 60,
                cue = "Feet hip width, knees soft, hang from the hips and let the head go.",
                exercise = "ForwardFold",
            ),
        ),
    ),
    Flow(
        key = "core-mat",
        name = "Core on the mat",
        use = FlowUse.After,
        note = "Pilates",
        moves = corePilates,
    ),
    Flow(
        key = AFTER_ROLLING_KEY,
        name = "After rolling",
        use = FlowUse.After,
        note = "after BJJ",
        moves = listOf(
            Move(
                "Butterfly",
                seconds = 90,
                cue = "Soles of the feet together, knees falling open. Fold forward with a long back.",
                exercise = "Butterfly",
            ),
            Move(
                "Frog",
                seconds = 90,
                cue = "On the forearms, knees wide and shins in line with them. Ease the hips back.",
                exercise = "FrogStretch",
            ),
            pigeon(seconds = 90),
            lyingTwist(seconds = 60),
            threadTheNeedle(seconds = 45),
            childsPose(seconds = 75),
        ),
    ),
    Flow(
        key = "full-reset",
        name = "Full reset",
        use = FlowUse.After,
        moves = listOf(
            catCow(seconds = 90),
            childsPose(seconds = 105),
            hipSwitch(seconds = 120),
            pigeon(seconds = 120),
            hipFlexorLunge(seconds = 60),
            halfSplit(seconds = 60),
            threadTheNeedle(seconds = 60),
            cobra(seconds = 100),
            lyingTwist(seconds = 120),
            deepSquat(seconds = 120),
        ),
    ),
)

// Each is one of Lift's mat exercises, named by its key, so the flow shows it by Lift's figure and cues.
private val corePilates: List<Move>
    get() = listOf(
        pilates(
            "The hundred",
            "Hundred",
            100,
            "Head and shoulders up, legs long. Pump the arms: in for five, out for five.",
        ),
        pilates(
            "Roll-up",
            "RollUp",
            120,
            "Arms overhead, roll up one bone at a time to reach past your toes, and back down.",
        ),
        pilates(
            "Single-leg circles",
            "SingleLegCircles",
            60,
            "One leg to the ceiling, hips still. Circle it across, down and round.",
            true,
        ),
        pilates(
            "Rolling like a ball",
            "RollingLikeABall",
            90,
            "Hold the shins, chin down. Roll back to the shoulders and up to balance.",
        ),
        pilates(
            "Single-leg stretch",
            "SingleLegStretch",
            120,
            "Head up, one knee in, the other leg long. Change legs with each breath.",
        ),
        pilates(
            "Double-leg stretch",
            "DoubleLegStretch",
            120,
            "Curl in, then reach arms and legs long. Circle the arms back to the shins.",
        ),
        pilates(
            "Criss-cross",
            "CrissCross",
            120,
            "Hands behind the head, elbow to the far knee, the other leg long. Slowly.",
        ),
        pilates(
            "Swimming",
            "Swimming",
            90,
            "Face down, arms and legs long and lifted. Flutter the opposite arm and leg.",
        ),
        pilates(
            "Side kicks",
            "SideKickSeries",
            50,
            "On your side, hips stacked. Top leg kicks forward twice, then sweeps back.",
            true,
        ),
    )

private fun pilates(name: String, exercise: String, seconds: Int, cue: String, sided: Boolean = false) =
    Move(name, seconds, cue, sided = sided, held = false, exercise = exercise)

private fun hipSwitch(seconds: Int) = Move(
    "90/90 hip switch",
    seconds,
    cue = "Sit with both knees bent square. Swing them over to the other side and sit tall.",
    held = false,
    exercise = "HipSwitch",
)

private fun hipFlexorLunge(seconds: Int) = Move(
    "Hip flexor lunge",
    seconds,
    cue = "Back knee down, tail tucked. Ease the hips forward, ribs over hips.",
    sided = true,
    exercise = "HipFlexorLunge",
)

private fun deepSquat(seconds: Int) = Move(
    "Deep squat",
    seconds,
    cue = "Heels down, chest up. Elbows inside the knees, pressing them out.",
    exercise = "DeepSquat",
)

private fun threadTheNeedle(seconds: Int) = Move(
    "Thread the needle",
    seconds,
    cue = "On all fours, slide one arm under the other and rest that shoulder down.",
    sided = true,
    exercise = "ThreadTheNeedle",
)

private fun catCow(seconds: Int) = Move(
    "Cat-cow",
    seconds,
    cue = "On all fours, round the back to the ceiling, then let it sink and look ahead.",
    held = false,
    exercise = "CatCow",
)

private fun childsPose(seconds: Int) = Move(
    "Child's pose",
    seconds,
    cue = "Knees wide, sit back to the heels, arms long, forehead down.",
    exercise = "ChildsPose",
)

private fun cobra(seconds: Int) = Move(
    "Cobra",
    seconds,
    cue = "Face down, hands under the shoulders. Press the chest up, hips heavy.",
    exercise = "Cobra",
)

private fun halfSplit(seconds: Int) = Move(
    "Half split",
    seconds,
    cue = "From a low lunge sit back, front leg straight, toes up. Fold over it with a long back.",
    sided = true,
    exercise = "HalfSplit",
)

private fun worldsGreatestStretch(seconds: Int) = Move(
    "World's greatest stretch",
    seconds,
    cue = "Long lunge, elbow to the instep, then turn and reach that arm up.",
    sided = true,
    held = false,
    exercise = "WorldsGreatestStretch",
)

private fun pigeon(seconds: Int) = Move(
    "Pigeon",
    seconds,
    cue = "Front shin across, back leg long, hips square. Fold forward over the shin.",
    sided = true,
    exercise = "Pigeon",
)

private fun lyingTwist(seconds: Int) = Move(
    "Lying twist",
    seconds,
    cue = "On your back, one knee across the body, both shoulders down, look the other way.",
    sided = true,
    exercise = "LyingTwist",
)
