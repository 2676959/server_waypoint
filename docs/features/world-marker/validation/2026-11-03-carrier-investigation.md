# Carrier attachment investigation — 2026-11-03

This was the third placement attempt, after the original entity-position-packet marker and the direct riding/transformation comparison. It investigated moving a text display through native passenger attachments while keeping its own transformation translation zero. The investigation was stopped because the added complexity exceeded the scope of the experiment. No carrier approach remains in the navigation implementation; the geometry probe and its runner were removed.

## Direction and constraints

Direct riding gives the client local player following, but relative placement still depends on server updates. A nested interaction entity initially looked like a way to move that offset into the carrier. Vanilla's `Entity.rideTick` instead calls the vehicle's `positionRider` and overwrites a passenger's independent position. Sending position packets to a mounted carrier therefore does not provide an independent translation layer. A display parent's rendered transformation also does not move its logical passenger attachment.

An interaction entity supplies a vertical passenger anchor through its height metadata, including signed heights in the tested native implementation. Its width does not supply horizontal displacement. `Player → Interaction → TextDisplay` alone cannot express arbitrary XYZ offsets. Generic attachment rotation uses entity yaw, not pitch.

The investigation inspected the local 26.1.2 vanilla `Entity`, `EntityAttachment`, `EntityDimensions`, `Interaction`, `Display`, `Camel`, `HappyGhast`, `InterpolationHandler` and client rendering/target-selection sources. A minecart's interpolated passenger render offset appeared to depend on an absolute trajectory, undermining the desired local player-following advantage. Happy ghast scale was clamped to 1.0 in the native probe, so scaling its wider seat layout did not provide the needed range.

## Geometry that worked in the native probe

The isolated Paper 1.21.11 probe used this chain:

```text
Player → root Interaction → invisible Camel A → invisible Camel B
       → tip Interaction → TextDisplay
```

This needs five private entities per marker: four helpers and the display. Both standing camels use constant scale 12, giving two horizontal seat links of length L = 6 blocks. For desired horizontal radius r from 0 to 12 and Minecraft yaw bearing θ:

```text
α = acos(r / (2L))
yawA = θ + α
yawB = θ − α
seat(yaw) = (−L sin(yaw), L cos(yaw))
```

The two seat vectors sum to the required horizontal displacement without changing scale or the text's transformation. Their combined vertical seat offset is 48 blocks. The tip interaction height is 64 blocks; the root interaction height is `desiredYOffset − 48 − 64`. The clearance keeps the large camel bounding boxes below the eye in the tested cases. The text retains CENTER billboarding and zero transformation translation, so its actual logical position contains the marker offset and the client can face the text toward the camera independently.

[Retained native geometry results](2026-11-03-carrier-geometry-native.json) came from `/Volumes/ssd/sw-world-marker-carrier-geometry-2026-11-03-final`. The pinned Paper 1.21.11 build 111 / Java 21 runtime exercised 1,920 combinations of radius, bearing, signed vertical displacement and origins, including near the world border. Maximum position error was approximately 0.93 mm. A root displacement propagated exactly. No tested camel bounding box contained the sampled eye position. The entities were never inserted into the server world.

Calling the native three-tick interpolation handlers for both camels produced yaw steps of 30°, 60° and 90° and intermediate horizontal text positions. This proves attachment geometry and that interpolation-class behavior in the probe. It does not prove the actual client will render the complete chain smoothly.

## Why it was abandoned

Horizontal yaw interpolation offered a possible local offset path, but vertical interaction-height metadata still stepped without equivalent interpolation. Five-tick updates also leave gaps around the three-tick living-entity interpolation. Further work would need update scheduling, coordinated yaw packets, angle continuity near zero radius, vertical smoothing and lifecycle handling for the entire chain.

Large carrier hitboxes required extra clearance and still needed rendered-client targeting/collision checks. Five entities per marker add client simulation, packet and lifecycle costs. Native server geometry checks did not establish client FPS, real network interpolation, target-selection safety, appearance or a capacity limit. No rendered client or physical-input test was performed. The user chose to stop this direction and retain the simpler position-packet approach.
