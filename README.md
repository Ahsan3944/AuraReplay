# AuraReplay

**Next-Generation Server-Side Minecraft Motion Capture & Performance Editing Studio for Paper 1.21.11.**

AuraReplay is not a traditional replay plugin. It is a server-side motion-capture and virtual-performance system designed to capture player/entity performances and turn them into fully editable virtual actors.

## Vision

Capture → Edit → Manipulate → Direct → Compose → Perform

A recorded performance can be transformed after capture: actors can be moved, rotated, scaled, hidden, cloned, renamed, re-equipped, retimed, reversed, morphed, and composed into scenes without modifying the real server world.

## Core Features

- Tick-precise player and entity motion capture
- Player, mob, item, projectile, vehicle and mount tracking
- Movement, rotation, velocity, pose, metadata and action capture
- Editable virtual actors driven by packets/NMS
- Actor transform editing: position, rotation and scale
- Hide/show/freeze/clone actors
- Actor identity editing: name, display name, nametag height/offset and appearance
- Armor and equipment editing with presets
- Skin/profile and entity-morph capabilities where supported
- Motion retiming, speed control, reverse playback, looping and offsets
- Timeline, keyframes, markers, takes and non-destructive editing
- Camera paths, follow/orbit/dolly/spline workflows
- Particles, sounds, dialogue and cinematic cues
- Scene-specific time/weather and viewer-specific visual state
- Packet-based fake blocks and virtual scene changes
- Multi-actor scenes, delayed clones and nested scene composition
- In-game Director Studio UI
- Command parity for major editor operations
- Undo/redo, autosave, recovery and presets
- SQLite metadata + optimized binary recording storage
- Public API for integrations and automation
- Paper 1.21.11 target
- NMS/paperweight-userdev + ProtocolLib packet architecture

## Architecture Principles

AuraReplay separates immutable captured performance data from editable actor instances. Recording capture stays minimal on the server thread, while serialization, compression, indexing and storage are handled asynchronously. Bukkit/Paper world-sensitive operations remain on the appropriate server thread.

The packet/NMS layer is isolated behind version adapters so the core recording, timeline and scene systems do not depend on fragile Minecraft internals.

## Controls

The Studio is designed to support command-driven workflows and an optional future client input bridge for keyboard shortcuts such as a configurable Studio hotkey. Raw arbitrary keyboard input is not assumed to be available to a pure server-only plugin.

## Status

Early architecture / foundation stage.

Target platform: **Paper 1.21.11**

Language: **Java**

License: To be defined.
