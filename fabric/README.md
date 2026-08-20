# TrialMiner Fabric

Fabric version of TrialMiner for Minecraft 26.1.2, intended for single-player
worlds (and compatible Fabric servers). Install Fabric Loader and Fabric API,
then place the built `trialminer-fabric-1.0.0.jar` in the instance's `mods`
folder.

Use a Silk Touch tool to mine trial spawners only while they are `INACTIVE` or
in `COOLDOWN`. The item retains the block entity data, including its configured
spawns, rewards, ominous state, and cooldown. Mining is denied during active or
reward-ejection states because Minecraft's live trial counters would otherwise
reset on placement and duplicate encounters.

Build with the included Gradle wrapper:

```sh
./gradlew build
```

The output is `build/libs/trialminer-fabric-1.0.0.jar`.
