public class RTPpacket extends RTPpacketDemo{
    public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
        super(PType, Framenb, Time, data, data_length);
    }

    public RTPpacket(byte[] packet, int packet_size) {
        super(packet, packet_size);
    }

    /*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |V=2|P|X|  CC   |M|     PT      |       sequence number         |
       */
    @Override
    void setRtpHeader() {
        // first byte wise filling of headers
        header[0]  = (byte) ((Version << 6) | (Padding << 5) | (Extension << 4) | CC );
        header[1]  = (byte) (Marker << 7 | PayloadType);
        header[2]  = (byte) (SequenceNumber >> 8);
        // 0xFF to fill missing bits
        header[3]  = (byte) (SequenceNumber & 0xFF);
        header[4]  = (byte) (TimeStamp >> 24);
        header[5]  = (byte) (TimeStamp >> 16);
        header[6]  = (byte) (TimeStamp >> 8);
        header[7]  = (byte) (TimeStamp & 0xFF);
        header[8]  = (byte) (Ssrc >> 24);
        header[9]  = (byte) (Ssrc & 16);
        header[10] = (byte) (Ssrc & 8);
        header[11] = (byte) (Ssrc & 0xFF);
    }
}
