# Multimodal Capture V2 Notes

## Overview

V2 reconnects the validated standalone ultrasonic capture chain to the multimodal capture system.

- Ultrasonic server: `Ultrasound_capture/UltrasonicCenterServer`
- Ultrasonic client: `Ultrasound_capture/UltrasonicCenterClient`
- Multimodal orchestrator: MATLAB in `Multimodal_data_capture/matlab_client`

## Sync model

V2 keeps the V1 synchronization model on the MATLAB side instead of moving timing control into the server.

The intended sequence is:

1. Sync clocks with the ultrasonic server using `/api/time/sync`.
2. Compute a common `baseTriggerTime` in MATLAB.
3. Derive the ultrasonic command send time and radar command send time using startup-delay compensation.
4. Send both commands at the planned moments.

This preserves the V1 requirement that multimodal timing alignment is driven by MATLAB scheduling plus measured startup delays.

## Important timing note

`PHONE_STARTUP_DELAY` in V2 must include the standalone ultrasonic client pre-cue beep and recorder/player startup latency.

The current V2 entrypoint uses:

```matlab
PHONE_STARTUP_DELAY = 2200;
```

If the ultrasonic app behavior changes, this delay should be re-measured before collecting formal data.

## Versioning

V2 metadata writes:

- `capture_system.version = "V2"`
- `capture_system.audio_chain_version = "V2"`

It also persists the ultrasonic FMCW configuration and the relative server-side uploaded audio path when available.

The current V2 flow also requests post-upload deletion on the Android client, so the phone-side local recording is removed after a successful forward/upload.
