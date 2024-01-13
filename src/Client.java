/* ------------------
Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
---------------------- */

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Client {
  static int videoLength = 2800;  // Demovideo htw.mjpeg, no metadata in MJPEG

  // GUI
  // ----
  JFrame f = new JFrame("Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JButton optionsButton = new JButton("Options");
  JButton describeButton = new JButton("Describe");
  JPanel mainPanel = new JPanel(); // Container
  JPanel buttonPanel = new JPanel(); // Buttons
  JPanel statsPanel = new JPanel();
  JPanel inputPanel = new JPanel();
  JPanel pufferPanel = new JPanel();
  JLabel iconLabel = new JLabel(); // Image
  JLabel statusLabel = new JLabel("Status: "); // Statistics
  JLabel pufferLabel = new JLabel("Puffer: "); // Statistics
  JLabel receiveLabel = new JLabel("Receive: "); // Statistics
  JLabel receiveLabel2 = new JLabel("Receive2: "); // Statistics
  JLabel statsLabel = new JLabel("Statistics: "); // Statistics
  JLabel statsLabel2 = new JLabel("Statistics2: "); // Statistics
  JLabel fecLabel = new JLabel("FEC: "); // Statistics
  JLabel fecLabel2 = new JLabel("FEC2: "); // Statistics
  ImageIcon icon;
  JTextField textField = new JTextField("mystream", 30);
  JTextField pufferNumber = new JTextField("50", 3);
  JProgressBar progressBuffer = new JProgressBar(0, 100);
  JProgressBar progressPosition = new JProgressBar(0, videoLength);
  JCheckBox checkBoxFec = new JCheckBox("nutze FEC");
  ButtonGroup encryptionButtons = null;

  int iteration = 0;  // for displaying statistics

  // RTP variables:
  // ----------------
  DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
  DatagramSocket FECsocket; // socket to be used to send and receive UDP packets for FEC
  private final RtpHandler rtpHandler;
  static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets
  static int FEC_RCV_PORT = 25001; // port where the client will receive the RTP packets

  static final int MAX_FRAME_SIZE = 65536;
  static final int RCV_RATE = 2;  // interval for receiving loop
  static int jitterBufferSize = 50;      // size of the input buffer => start delay
  static final int FRAME_RATE = 40;  // default frame rate
  Timer timer; // timer used to receive data from the UDP socket
  Timer timerPlay; // timer used to display the frames at correct frame rate

  // RTSP variables
  // ----------------
  Socket RTSPsocket; // socket used to send/receive RTSP messages
  // input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String rtspServer;
  static int rtspPort;
  static String rtspUrl;
  static String VideoFileName; // video file to request to the server

  private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  private static Rtsp rtsp;

  public Client() {
    rtpHandler = new RtpHandler(true); // TODO move
    // build GUI - Frame
    f.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            System.exit(0);
          }
        });
    // Buttons
    buttonPanel.setLayout(new GridLayout(1, 0));
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);
    buttonPanel.add(optionsButton);
    buttonPanel.add(describeButton);
    setupButton.addActionListener(new setupButtonListener());
    playButton.addActionListener(new playButtonListener());
    pauseButton.addActionListener(new pauseButtonListener());
    tearButton.addActionListener(new tearButtonListener());
    optionsButton.addActionListener(new optionsButtonListener());
    describeButton.addActionListener(new describeButtonListener());
    iconLabel.setIcon(null);     // Image display label

    // Statistic Panel
    statsPanel.setLayout(new GridLayout(5, 2));
    statsPanel.add(statusLabel);
    statsPanel.add(pufferLabel);
    statsPanel.add(receiveLabel);
    statsPanel.add(receiveLabel2);
    statsPanel.add(statsLabel);
    statsPanel.add(statsLabel2);
    statsPanel.add(fecLabel);
    statsPanel.add(fecLabel2);
    statsPanel.add(checkBoxFec);
    pufferPanel.add(new JLabel("Puffergröße: "));
    pufferPanel.add(pufferNumber);
    statsPanel.add(pufferPanel);

    inputPanel.setLayout(new BorderLayout());
    inputPanel.add(textField, BorderLayout.SOUTH);

    JPanel encryptionPanel = initEncryptionPanel();

    // frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    mainPanel.add(encryptionPanel);
    mainPanel.add(statsPanel);      // Statistics
    mainPanel.add(progressBuffer);
    mainPanel.add(progressPosition);
    mainPanel.add(inputPanel);
    iconLabel.setBounds(0, 0, 640, 480);
    buttonPanel.setBounds(0, 480, 640, 50);
    encryptionPanel.setBounds(10, 530, 640, 30);
    statsPanel.setBounds(10, 560, 620, 160);
    progressBuffer.setBounds(10, 720, 620, 20);
    progressPosition.setBounds(10, 750, 620, 20);
    inputPanel.setBounds(10, 770, 620, 30);
    // inputPanel.setSize(620,50);

    pufferNumber.setToolTipText("Puffergröße");

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(640, 860));
    f.setVisible(true);

    mainPanel.getRootPane().setDefaultButton(describeButton);
    describeButton.requestFocus();

    // init timer
    // --------------------------
    timer = new Timer(RCV_RATE, new timerListener());
    timer.addActionListener(new FecTimerListener());
    timer.setInitialDelay(0);
    timer.setCoalesce(true); // combines events
  }



  /**
   * Initialization of the GUI
   *
   * @param argv host port file
   * @throws Exception stacktrace at console
   */
  public static void main(String[] argv) throws Exception {
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.FINE);
    Client theClient = new Client();  // Create a Client object

    // get server hostname/IP and RTSP port from the command line
    String ServerHost = argv[0];
    int RTSP_server_port = Integer.parseInt(argv[1]);
    InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
    rtspPort = RTSP_server_port;
    rtspServer = ServerHost;
    rtspUrl = "rtsp://" + ServerHost + ":" + RTSP_server_port + "/";
    VideoFileName = argv[2];    // get video filename to request:
    theClient.textField.setText(VideoFileName);
    if (argv.length > 3) {
      jitterBufferSize = Integer.parseInt(argv[3]); // adjust buffer size
      theClient.pufferNumber.setText(argv[3]);
      logger.log(Level.FINE, "Jitter buffer size: " + jitterBufferSize);
    }

    theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port); // RTSP-connection
    // Set input and output stream filters:
    RTSPBufferedReader =
        new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
    RTSPBufferedWriter =
        new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));
    // initialize RTSP protocol
    rtsp = new Rtsp(RTSPBufferedReader, RTSPBufferedWriter, RTP_RCV_PORT, rtspUrl, VideoFileName);
  }

    class setupButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Setup Button pressed ! ");
      rtsp.setVideoFileName( textField.getText() );
      jitterBufferSize = Integer.parseInt(pufferNumber.getText());

      if (rtsp.setup()) {
        try {
          RTPsocket = new DatagramSocket(RTP_RCV_PORT );  // receive Media data
          // for now FEC packets are received via RTP-Port, so keep comment below
          FECsocket = new DatagramSocket(FEC_RCV_PORT);
          RTPsocket.setSoTimeout(1 ); // smallest value (ms) for blocking time
          FECsocket.setSoTimeout(1 ); // smallest value (ms) for blocking time
          logger.log(Level.FINE, "Socket receive buffer: " + RTPsocket.getReceiveBufferSize());

          rtpHandler.setFecDecryptionEnabled(checkBoxFec.isSelected());
          // Init the play timer
          int timerDelay = FRAME_RATE; // use default delay
          if (rtsp.getFramerate() != 0) { // if information available, use that
            timerDelay = 1000/rtsp.getFramerate(); // delay in ms
          }
          timerPlay = new Timer(timerDelay, new timerPlayListener());
          timerPlay.setCoalesce(true); // combines events
          // timerPlay.setInitialDelay(0);

        } catch (SocketException se) {
          logger.log(Level.SEVERE, "Socket exception: " + se);
          System.exit(0);
        }
        statusLabel.setText("READY");
        mainPanel.getRootPane().setDefaultButton(playButton);
        playButton.requestFocus();
      }
    }
  }

  /** Handler for Play button */
  class playButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Play Button pressed ! ");
        if (rtsp.play()) {
          statusLabel.setText("PLAY ");
          mainPanel.getRootPane().setDefaultButton(pauseButton);
          pauseButton.requestFocus();
          timer.start(); // start playback RTP-frames
          timerPlay.start();
        }
    }
  }

  /** Handler for Pause button */
  class pauseButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Pause Button pressed ! ");
      if (rtsp.pause()) {
        statusLabel.setText("READY ");
        mainPanel.getRootPane().setDefaultButton(playButton);
        playButton.requestFocus();
          timer.stop();  // stop playback
          timerPlay.stop();
          timerPlay.setInitialDelay(0);
      }
    }
  }

  /** Handler for Teardown button */
  class tearButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Teardown Button pressed ! ");
      if (rtsp.teardown()) {
        statusLabel.setText("INIT ");
        progressPosition.setValue(0);
        rtpHandler.reset();
        timer.stop();  // stop playback
        timerPlay.stop();
        FECsocket.close();
        RTPsocket.close();
      }
    }
  }

  /** Handler for Options button */
  class optionsButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Options Button pressed ! ");
      rtsp.options();
    }
  }

  /** Handler for Describe button */
  class describeButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      logger.log(Level.INFO, "Describe Button pressed ! ");
      rtsp.describe();
      if (rtsp.getDuration() > 1) {
        // set progress bar from duration and framerate from server data
        progressPosition.setMaximum((int)rtsp.getDuration() * rtsp.getFramerate() );
      } else {
        progressPosition.setMaximum(videoLength);  // Demovideo htw.mjpeg, no metadata in MJPEG
      }
      mainPanel.getRootPane().setDefaultButton(setupButton);
      setupButton.requestFocus();
    }
  }

  /** Handler for the timer event fetches the RTP-packets and displays the images */
  class timerListener implements ActionListener {
    byte[] buf = new byte[MAX_FRAME_SIZE]; // allocate memory to receive UDP data from server

    public void actionPerformed(ActionEvent e) {
      DatagramPacket rcvDp = new DatagramPacket(buf, buf.length); // RTP needs UDP socket
      try {
        RTPsocket.receive(rcvDp); // receive the DP from the socket:
        rtpHandler.processRtpPacket(rcvDp.getData(), rcvDp.getLength());
      } catch (InterruptedIOException iioe) {
        // System.out.println("Nothing to read");
      } catch (IOException ioe) {
        logger.log(Level.SEVERE, "Exception caught: " + ioe);
      }
    }
  }

  class FecTimerListener implements ActionListener {
    byte[] buf = new byte[MAX_FRAME_SIZE]; // allocate memory to receive UDP data from server

    public void actionPerformed(ActionEvent e) {
      DatagramPacket rcvDp = new DatagramPacket(buf, buf.length); // RTP needs UDP socket
      try {
        FECsocket.receive(rcvDp); // receive the DP from the socket:
        rtpHandler.processRtpPacket(rcvDp.getData(), rcvDp.getLength());
      } catch (InterruptedIOException iioe) {
        // System.out.println("Nothing to read");
      } catch (IOException ioe) {
        logger.log(Level.SEVERE, "Exception caught: " + ioe);
      }
    }
  }

  /** Displays one frame if available */
  class timerPlayListener implements ActionListener {
    boolean videoStart = false;

    public void actionPerformed(ActionEvent e) {
      ReceptionStatistic rs = rtpHandler.getReceptionStatistic();
      byte[] payload;

      // check buffer size and start if filled
      int puffer = rs.latestSequenceNumber - rs.playbackIndex;
      progressBuffer.setValue(puffer);
      progressPosition.setValue(rs.playbackIndex);
      if (iteration % 5 == 0) {
        setStatistics(rs);
        iteration = 0;
      }
      iteration++;

      // check for beginning of display JPEGs
      if ((puffer < jitterBufferSize) && !videoStart) {
        return;
      } else videoStart = true;
      // check for end of display JPEGs
      if (puffer <= 0) { // buffer empty -> finish
        statusLabel.setText("End of Stream");
        return;
      }

      logger.log(Level.FINER, "----------------- Play timer --------------------");
      payload = rtpHandler.nextPlaybackImage();
      if (payload == null) {
          return;
      }

      try {
        // get an Image object from the payload bitstream
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(payload, 0, payload.length);

        icon = new ImageIcon(image);  // display the image as an ImageIcon object
        iconLabel.setIcon(icon);

      } catch (RuntimeException rex) {
        rex.printStackTrace();
      }
    }

    private void setStatistics(ReceptionStatistic rs) {
      DecimalFormat df = new DecimalFormat("0.000");
      pufferLabel.setText("Puffer: " + (rs.latestSequenceNumber - rs.playbackIndex));

      receiveLabel.setText("empfangenes RTP mit SeqNr: " + rs.latestSequenceNumber);
      receiveLabel2.setText("Summe empfangene RTPs: " + rs.receivedPackets);

      statsLabel.setText("RTP: Index / verloren: "
              + rs.playbackIndex + " / " + rs.packetsLost);

      // <p> is for new line
      statsLabel2.setText("<html>Bilder: Index / fehlend: "
              +rs.requestedFrames + " / " + rs.framesLost   + "</html>");

      fecLabel.setText("FEC: korrigiert / nicht korrigiert: "
              + rs.correctedPackets
              + " / "
              + rs.notCorrectedPackets);

      // latestSequenceNumber is the last received RTP packet
      fecLabel2.setText("Ratio vor FEC: "
              + (df.format((double) rs.packetsLost / (double) rs.playbackIndex))
              + "    nach FEC: "
              + (df.format((double) rs.notCorrectedPackets / (double) rs.playbackIndex)));
    }
  }


  private JPanel initEncryptionPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridLayout(1, 0));

    JLabel encryptionLabel = new JLabel("Verschlüsselung:");
    panel.add(encryptionLabel);

    encryptionButtons = new ButtonGroup();
    JRadioButton e_none = new JRadioButton("keine");
    e_none.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_none);
    e_none.setSelected(true);
    panel.add(e_none);

    JRadioButton e_srtp = new JRadioButton("SRTP");
    e_srtp.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_srtp);
    panel.add(e_srtp);

    JRadioButton e_jpeg = new JRadioButton("JPEG");
    e_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(e_jpeg);
    panel.add(e_jpeg);

    JRadioButton a_jpeg = new JRadioButton("JPEG (Angriff)");
    a_jpeg.addItemListener(this::radioButtonSelected);
    encryptionButtons.add(a_jpeg);
    panel.add(a_jpeg);

    return panel;
  }

  private void radioButtonSelected(ItemEvent ev) {
    JRadioButton rb = (JRadioButton)ev.getItem();
    if (rb.isSelected()) {
      String label = rb.getText();
      RtpHandler.EncryptionMode mode = RtpHandler.EncryptionMode.NONE;

      switch (label) {
      case "SRTP":
        mode = RtpHandler.EncryptionMode.SRTP;
        break;
      case "JPEG":
        mode = RtpHandler.EncryptionMode.JPEG;
        break;
      case "JPEG (Angriff)":
        mode = RtpHandler.EncryptionMode.JPEG_ATTACK;
        break;
      default:
        break;
      }

      boolean encryptionSet = rtpHandler.setEncryption(mode);
      if (!encryptionSet) {
        Enumeration<AbstractButton> buttons = encryptionButtons.getElements();
        while (buttons.hasMoreElements()) {
          AbstractButton ab = buttons.nextElement();
          if (ab.getText().equals("keine")) {
            ab.setSelected(true);
          }
        }
      }
    }
  }
}
