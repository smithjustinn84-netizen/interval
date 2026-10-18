# No rules of the app's own. R8 runs on release builds (`./kotlin package -m androidApp -f aab`), and
# every library that needs keep rules ships them as consumer rules: Room, kotlinx.serialization,
# Koin and Compose. Add a rule here only for something the app itself reaches by reflection.
