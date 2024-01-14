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
        if (!fecNr.containsKey(nr)) {
            System.out.println("No fec packet for seqNr found");
            return false;
        }
        int fecNR = fecNr.get(nr);
        if (!fecStack.containsKey(fecNR)) {
            System.out.println("FecStack does not contain FeCPacket");
            return false;
        }
        FECpacket feCpacket = fecStack.get(fecNR);
        if (feCpacket == null) {
            System.out.println("No fec packet found");
            return false;
        }
        List<RTPpacket> rtPpacketList = getCorrespondingMediaPackets(nr,mediaPackets,false);
        if (rtPpacketList == null || rtPpacketList.isEmpty()) {
            System.out.println("Packetlist is empty or null");
            return false;
        }
        System.out.println("*******************************************");
        System.out.println(String.format("size corresponding media Packets %s",rtPpacketList.size()));
        int supposedLength = rtPpacketList.size() - 1;
        rtPpacketList = rtPpacketList.stream().filter(Objects::nonNull).toList();
        System.out.println(String.format("size corresponding media Packets after filter %s",rtPpacketList.size()));
        System.out.println("*******************************************");
        return rtPpacketList.size() >= supposedLength;
    }

    // restore package by using xor
    // needed is fec package and one rtp package
    @Override
    RTPpacket correctRtp(int nr, HashMap<Integer, RTPpacket> mediaPackets) {
        int fecNR = fecNr.get(nr);
        FECpacket feCpacket = fecStack.get(fecNR);
        List<RTPpacket> requiredPackets = getCorrespondingMediaPackets(nr,mediaPackets,true);
        if (requiredPackets == null) return null;
        requiredPackets.forEach(feCpacket::addRtp);
        return feCpacket.getLostRtp(nr);
    }
    List<RTPpacket> getCorrespondingMediaPackets(int nr,HashMap<Integer, RTPpacket> mediaPackets,boolean filter) {
        int fecNR = fecNr.get(nr);
        FECpacket feCpacket = fecStack.get(fecNR);
        if (feCpacket == null) return null;
        if (!fecList.containsKey(nr)) return null;
        if (filter) return fecList.get(nr).stream().map(mediaPackets::get).filter(Objects::nonNull).toList();
        return fecList.get(nr).stream().map(mediaPackets::get).toList();
    }



}
