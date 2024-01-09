import java.io.BufferedReader;
import java.io.BufferedWriter;

public class Rtsp extends RtspDemo {

    public Rtsp(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter,
                int rtpRcvPort,
                String rtspUrl,
                String videoFileName) {

        super(RTSPBufferedReader, RTSPBufferedWriter, rtpRcvPort, rtspUrl, videoFileName);
    }

    @Override
    boolean play() {
        return false;
    }

    @Override
    boolean pause() {
        return false;
    }

    @Override
    boolean teardown() {
        return false;
    }

    @Override
    void describe() {

    }

    @Override
    void options() {

    }

    @Override
    void send_RTSP_request(String request_type) {

    }
}
