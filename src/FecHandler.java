import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FecHandler extends FecHandlerDemo {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public static final int RTP_PAYLOAD_JPEG = 26;
    public FecHandler(int size) {
        super(size);
    }

    public FecHandler(boolean useFec) {
        super(useFec);
    }

    /**
     * @param nr           Sequence Nr. missing number
     * @param mediaPackets map of packets with sequence number as key
     * @return
     */
    @Override
    boolean checkCorrection(int nr, HashMap<Integer, RTPpacket> mediaPackets) {
        nrLost++;
        RTPpacket missing = getMissingPacket(nr, mediaPackets);
        return (missing == null);
    }

    // restore package by using xor
    // needed is fec package and one rtp package
    @Override
    RTPpacket correctRtp(int nr, HashMap<Integer, RTPpacket> mediaPackets) {
        FECpacket feCpacket = fecStack.get(fecNr.get(nr));
        RTPpacket missing = getMissingPacket(nr, mediaPackets);
        if (missing == null) {
            nrNotCorrected++;
            return null;
        }
        // restore from other rtp package
        // Move to checkCorrection
        RTPpacket restoredPacket;
        logger.log(Level.INFO,"packetSize: FEC " + feCpacket.payload.length+" RTP: "+missing.payload.length);
        //Retoring package Data
        nrCorrected++;
        return null;
    }

    RTPpacket getMissingPacket(int nr, HashMap<Integer, RTPpacket> mediaPackets) {
        FECpacket feCpacket = fecStack.get(fecNr.get(nr));
        if (feCpacket == null) return null;

        feCpacket.printHeaders();
        // Should be 2
        List<RTPpacket> rtPpacketList = feCpacket.getRtpList().stream().map(mediaPackets::get).filter(Objects::nonNull).toList();
        if (!rtPpacketList.isEmpty()){
            rtPpacketList.get(0).printheader();
        }else {
            logger.log(Level.WARNING,"RTP packet not found");

        }
        return rtPpacketList.get(0);
    }
}
