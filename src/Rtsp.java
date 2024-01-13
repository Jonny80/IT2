import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Rtsp extends RtspDemo {

    public Rtsp(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter,
                int rtpRcvPort,
                String rtspUrl,
                String videoFileName) {

        super(RTSPBufferedReader, RTSPBufferedWriter, rtpRcvPort, rtspUrl, videoFileName);
    }
    public Rtsp(BufferedReader RTSPBufferedReader, BufferedWriter RTSPBufferedWriter) {
        super(RTSPBufferedReader,RTSPBufferedWriter);
    }

    @Override
    boolean play() {
        if (state==State.READY){
            RTSPSeqNb++;
            send_RTSP_request("PLAY");
            boolean ready = parse_server_response() < 300 ;
            if (ready) {
                state = State.PLAYING;
                return true;
            }
        }else {
            logger.log(Level.WARNING,"Cannot Play : Client not ready");
            return false;
        }
        return false;
    }
    // Socket listening for Server packages and handles (somehow)
    // -> if locally hosted not a real serer
    // incoming ones automatically
    // Server requests are tokenized and processed linewise
    // if req_type setup or describe 2nd line contains filename
    // as 3rd substring divided by /
    // next lines are "Transport" and "CSeq"
    // CSeq inits new token
    // Transport of structure: Transport_x_=_x_-RTP_dest_port-
    @Override
    boolean pause() {
        if (state == State.PLAYING){
            RTSPSeqNb++;
            send_RTSP_request("pause");
            boolean ready = parse_server_response() < 300 ;
            if (ready){
                state = State.READY;
                return true;
            }
        }
        logger.log(Level.WARNING,"Cannot pause: Client not playing");
        return false;
    }

    @Override
    boolean teardown() {
        if (state == State.PLAYING || state==State.READY){
            RTSPSeqNb++;
            send_RTSP_request("teardown");
            boolean ready = parse_server_response() < 300 ;
            if (ready){
                state=State.INIT;
                return true;
            }
        }
        logger.log(Level.WARNING,"Client in an invalid State: cancel request");
        return false;
    }

    @Override
    void describe() {
        RTSPSeqNb++;
        send_RTSP_request("describe");
        boolean ready = parse_server_response() < 300 ;
        if (ready){
            return;
        }
        logger.log(Level.WARNING,"Describe response failed");
    }

    @Override
    void options() {
        RTSPSeqNb++;
        send_RTSP_request("options");
        boolean ready = parse_server_response() < 300 ;
        if (ready){
            System.out.println("Options are: " + getOptions() );
            return;
        }
        logger.log(Level.WARNING,"Error getting Options");
    }
    /**
     * Sends a RTSP request to the server
     * @param request_type String with request type (e.g. SETUP)
     *
     * write Requests to the RTSPBufferedWriter-Stream
     * use logger.log() for logging the request to the console
     * end request with BufferedWriter.flush()
     */
    @Override
    void send_RTSP_request(String request_type) {
        logger.log(Level.INFO,"Sending Request of Type: " + request_type);
        try {
            String url = rtspUrl + getVideoFileName() + "/"+ (request_type.equals("SETUP") ? "trackID=0" : "");
            RTSPBufferedWriter.write(String.format("%s %s%s",request_type.toUpperCase(),url,CRLF));
            RTSPBufferedWriter.write(String.format("CSeq: %s%s",RTSPSeqNb,CRLF));
            if (!request_type.equals("DESCRIBE")){
                RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
            }
            if (request_type.equals("SETUP")){
                //logger.log(Level.INFO,"Destination Port is: ",getRTP_dest_port());
                RTSPBufferedWriter.write(String.format("Transport: RTP/AVP;unicast;client_port=%s-%s%s",25000,25001,CRLF));
            }
            RTSPBufferedWriter.write(CRLF);
            // Triggers sending
            RTSPBufferedWriter.flush();
            logger.log(Level.INFO,"Request of type " + request_type + " sent");
        }catch (Exception e){
            e.printStackTrace();
            logger.log(Level.SEVERE,"Sending Request of type: " + request_type + " failed");
            //TODO maybe add retries
        }
    }
}
