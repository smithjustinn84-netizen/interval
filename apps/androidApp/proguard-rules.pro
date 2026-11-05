# R8 runs on release builds (`./kotlin package -m androidApp -f aab`), and every library that needs keep
# rules ships them as consumer rules: Room, kotlinx.serialization, Koin and Compose. Add a rule here only for
# something the app itself reaches by reflection, or a library's rule that R8's full mode reads too narrowly.

# Glance updates the widget through WorkManager 2.7.1, whose rule keeps its input mergers' classes but not
# their constructors, which R8's full mode then strips: WorkManager makes one by reflection for every work
# request, so the widget never left its loading spinner. Later WorkManager releases ship this rule themselves.
-keep class * extends androidx.work.InputMerger { public <init>(); }
