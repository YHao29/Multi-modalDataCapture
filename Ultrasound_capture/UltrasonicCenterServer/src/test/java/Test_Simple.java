import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Test_Simple {
    public static void main(String[] args) throws LineUnavailableException {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("--------------------------------");
        Mixer recordMixer = null;
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            System.out.println(mixerInfo.toString());
            String info = mixerInfo.toString();
//            if (info.contains("Blue Snowball") && !info.contains("Port")) {
//                recordMixer = mixer;
//                System.out.println("Found Mixer Info");
//                break;
//            }
//            System.out.println(mixerInfo.getClass().getName()+ "/" + mixer.getClass().getName());
//            System.out.println("Name: " + mixerInfo.getName());
//            System.out.println("Vendor: " + mixerInfo.getVendor());
//            System.out.println("Description: " + mixerInfo.getDescription());
//            System.out.println("Version: " + mixerInfo.getVersion());
//            System.out.println("Source Line Info: ");
//            Line.Info[] sourceLineInfos = mixer.getSourceLineInfo();
//            for (Line.Info sourceLineInfo : sourceLineInfos) {
//                int maxLines = mixer.getMaxLines(sourceLineInfo);
//                System.out.println("\t" + sourceLineInfo.toString() + " [" + maxLines + "] " + sourceLineInfo.getClass().getName());
//            }
//            Line.Info[] targetLineInfo = mixer.getTargetLineInfo();
//            System.out.println("Target Line Info: ");
//            for (Line.Info info : targetLineInfo) {
//                int maxLines = mixer.getMaxLines(info);
//                System.out.println("\t" + info.toString() + " [" + maxLines + "] " + info.getClass().getName());
//            }
//            System.out.println("Controls: ");
//            for (Control control : mixer.getControls()) {
//                System.out.println("\t" + control.toString() + " " + control.getClass().getName());
//            }

            System.out.println("-------------------");
        }
        if (recordMixer == null) {
            System.out.println("No Mixer found");
            return;
        }

        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100,
                16, 1, 2, 44100, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!recordMixer.isLineSupported(info)) {
            System.out.println("Line not supported");
        } else {
            TargetDataLine line = (TargetDataLine) recordMixer.getLine(info);
//
            System.out.println(line.getLineInfo());
            line.open(format);
            byte[] buffer = new byte[1024];
            line.start();
            int bytesRead = line.read(buffer, 0, buffer.length);
            System.out.println("Bytes read: " + bytesRead);

            line.close();
        }



//        Port.Info info = Port.Info.HEADPHONE;
//        if (AudioSystem.isLineSupported(info)) {
//            Port port = (Port) AudioSystem.getLine(info);
//            System.out.println(port.getClass().getName());
//            Line.Info lineInfo = port.getLineInfo();
//            System.out.println(lineInfo.toString());
//        }
//        AudioFormat format = null;
//        DataLine.Info info = new DataLine.Info(Clip.class, format);
//        AudioSystem.getLine(info);
//        System.out.println(">>>>>>>>>>>>>>>>>>>>>>");
//        Line.Info[] lineInfos = AudioSystem.getSourceLineInfo(Port.Info.MICROPHONE);
//        for (Line.Info lineInfo : lineInfos) {
//            System.out.println(lineInfo.getClass().getName() + " " + lineInfo);
//        }
//        System.out.println(">>>>>>>>>>>>>>>>>>>>>>");
//        lineInfos = AudioSystem.getTargetLineInfo(Port.Info.HEADPHONE);
//        for (Line.Info lineInfo : lineInfos) {
//            System.out.println(lineInfo.getClass().getName() + " " + lineInfo);
//
//        }

//        try {
//            AudioInputStream audioStream = AudioSystem.getAudioInputStream(
//                    new File("E:\\amax\\home\\mus\\corpora\\LibriSpeech\\dev-clean\\84\\121123\\84-121123-0000.wav"));
//            Clip clip = AudioSystem.getClip();
//            clip.open(audioStream);
//            clip.loop(1);
//            clip.start();
//            Thread.sleep(clip.getMicrosecondLength() / 1000);
//        } catch (UnsupportedAudioFileException | IOException e) {
//            throw new RuntimeException(e);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

    }
}
