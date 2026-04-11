# AudioCenter Toolkit (Server side)

Working as a control center for parallel audio playback & recording.

Key features:
- Supports the parallel control for multiple mobile devices (Android) through TCP connection / USB network sharing.
- Supports remote audio playback/recording.
- Supports local audio playback/recording as well.
- Supports ultrasound playback at remote devices.
- Supports automatic batch operations. 

More functions to be explored 👌 Hope this toolkit could be helpful for audio-related research/development~

The client side code (Android App) could be found at: [AudioCenter-Client](https://github.com/lannooo/AudioCenterClient)

## Requirements
- Java JDK (version >= 17)
- Jetbrains IDEA or other Java IDE
- Gradle build tool (If using IDEA, it could be easy-to-use with built-in Gradle support)
- Add network listening port: 6666

## Cli Usage
By running Main.java, the control center could be operated with pre-defined Cli commands
### Start/Stop listening server
```
audio-center:>server start
Server started!
audio-center:>server stop
```
(this will open a TCP listener at port 6666)

### Manage remote audio devices
1. list the connected device
```
audio-center:>device list
--------- Devices ----------
[R:on, P:off] <Phone> HUAWEI/LIO-AL00 (6bcfb47f)
```
 You can find the device id at the end, e.g., 6bcfb47f.
 
2. define the capabilities of remote devices
```
device function -d <your_device_id> -c all
device function -d <your_device_id> -c playback
```
By default, the remote device can record audio. If you want to select one as an audio player, enable it with all/playback functions.

### The Audio functions
1. Record with a remote-device
```
audio-center:> audio remote-capture -d 6bcfb47f -o output.wav -t 5 -f
[6bcfb47f] Started Recording: output.wav
```
Hints: 
- -d defines the device id
- -o defines the outputfile
- -t defines the recording duration (not very accurate)
- -f means upload to server side (This will create the output file in the ./audio/<Device_Name> directory

2. Playback with a remote-device
```
audio-center:> audio remote-upload -d 6bcfb47f -p audio/vctk_genuine/p225_020.wav
Uploading 1 files to 1 devices
6bcfb47f: Upload completed
[6bcfb47f] Ready to receive chunks
[6bcfb47f] File uploaded

audio-center:> audio remote-play -d 6bcfb47f -i p225_020.wav
[6bcfb47f] Started Playback: p225_020.wav
[6bcfb47f] Stopped Playback: p225_020.wav
```
Hints:
- -p the audio file to be uploaded
- -i the audio file to be played

We can first upload the audio file from local to remote, then play it with the same filename.

3. Play Ultrasound with remote-device while recording at the same time
```
audio-center:>audio remote-capture -d 6bcfb47f -o ultra.wav -t 5 -f -u 
[6bcfb47f] Started Recording: ultra.wav
```
Hints:
- -u enable ultrasound player at the remote

The ultrasound is sending at 20kHz (fixed at client side, could be customized), recording at 44.1kHz

### More functions?
Commands to be explored in AudioCommands.java/DeviceCommands.java, which are defined in the manner of Spring Cli Framework.
