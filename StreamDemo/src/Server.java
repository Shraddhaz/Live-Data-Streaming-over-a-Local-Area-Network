/* ------------------
   UML Streaming Server 
   usage: java Server 
   ---------------------- */


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class Server extends JFrame {

	//RTP variables:
	//----------------
	DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
	DatagramPacket senddp; //UDP packet containing the video frames

	InetAddress ClientIPAddr; //Client IP address
	int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)
	
	//GUI:
	//----------------
	//ADD A PANEL

	JPanel mainPanel;
	JPanel config_PortPanel;
	JPanel connection_statusPanel;
	JButton selectFileButton = new JButton("Select File");
	JButton setupButton = new JButton("Start Server");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton stopButton = new JButton("Stop");
	//JLabel label;

	JLabel statusinfo;
	
	//Video variables:
	//----------------
	int imagenb = 0; //image nb of the image currently transmitted
	StreamMovie video; //StreamMovie object used to access video frames
	static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
	static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms..if we reduce it movie plays in fast forward mode and viceversa.

	static int VIDEO_LENGTH = 500; //length of the video in frames

	Timer timer; //timer used to send the images at the video frame rate
	Timer timer1; //timer used to send the images at the FASTER video frame rate
	byte[] buf; //buffer used to store the images to send to the client 

	//RTSP variables
	//----------------
	//rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	//rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
	final static int DESCRIBE = 7;
	final static int FORWARD = 8;
	static int state; //RTSP Server state == INIT or READY or PLAY
	Socket RTSPsocket; //socket used to send/receive RTSP messages
	//input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String videoFilePath = ""; //video file requested from the client
	static int RTSP_ID = 213654; //ID of the RTSP session
	int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";
	static volatile int setupstage= 0;
	HashSet<String> clientIPAddressSet = new HashSet<String>();
	HashSet<Socket> clientSocketSet = new HashSet<Socket>();
	//--------------------------------
	//Constructor
	//--------------------------------
	public Server(){

		//init Frame
		super("UML Streaming Server ");
		
		//Handler to close the main window
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				//stop the timer and exit
				System.exit(0);
			}});

		//GUI:
		mainPanel= new JPanel();
		connection_statusPanel= new JPanel();
		config_PortPanel=new JPanel();
		//label = new JLabel("Send frame #        ", JLabel.CENTER); 
		
		statusinfo = new JLabel("Ready!");

		config_PortPanel.add(selectFileButton);
		config_PortPanel.add(setupButton);
		
		//connection_statusPanel.add(label);
		connection_statusPanel.add(statusinfo);
		connection_statusPanel.add(playButton);
		connection_statusPanel.add(pauseButton);
		connection_statusPanel.add(stopButton);
		
		//frame layout
		mainPanel.setPreferredSize(new Dimension(500,150));

		mainPanel.setLayout(null);
		mainPanel.add(config_PortPanel);

		mainPanel.add(connection_statusPanel);

		config_PortPanel.setBounds(0,0,500,50);
		connection_statusPanel.setBounds(0,60,500,100);

		setupButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Configure Server Port Button pressed !");
				//setupstage++;
				//configureButton.setText("Configured Successfully");
				new Thread(new Runnable() {
					@Override
					public void run() {
						startOperation();
					}
				}).start();
			}
		});
		
		selectFileButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				int returnVal = fileChooser.showOpenDialog(Server.this);
		        if (returnVal == JFileChooser.APPROVE_OPTION) {
		            File file = fileChooser.getSelectedFile();
		            //This is where a real application would open the file.
		            statusinfo.setText("Opening: " + file.getAbsolutePath());
		            videoFilePath = file.getAbsolutePath();
		        } else {
		            statusinfo.setText("Open command cancelled by user.");
		        }
				//fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
				//int returnVal = fileChooser.showOpenDialog(this);
			}
		});
		
		playButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				File file = new File(videoFilePath);
				if(file != null && file.exists() && file.length() < 5000000) {
					playButton.setEnabled(false);
					playVideo();
				} else {
					statusinfo.setText("Error occured ! Select another file.");
				}
			}
		});
		
		pauseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if("Pause".equalsIgnoreCase(pauseButton.getText().toString())) {
					pauseButton.setText("Resume");
					pauseVideo();
				} else {
					pauseButton.setText("Pause");
					resumeVideo();
				}
			}
		});
		
		stopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				playButton.setEnabled(true);
				pauseButton.setText("Pause");
				stopVideo();
			}
		});
		
		getContentPane().add(mainPanel, BorderLayout.CENTER);
	}

	private void startOperation() {
		//get RTSP socket port from the command line
		int RTSPport = 2345;

		//Initiate TCP connection with the client for the RTSP session
		ServerSocket listenSocket;
		try {
			listenSocket = new ServerSocket(RTSPport);
			statusinfo.setText("Server started successfully.");
			setupButton.setEnabled(false);

			while(true){ 
				System.out.println("start for new");
				Socket RTSPsocket; //socket used to send/receive RTSP messages
				RTSPsocket = listenSocket.accept();
				clientSocketSet.add(RTSPsocket);
				//Get Client IP address
				InetAddress ClientIPAddr = RTSPsocket.getInetAddress();
				final String clientIPAddress = ClientIPAddr.getHostAddress();
				
				statusinfo.setText("client connected from ip : " + clientIPAddress);

			}
			//System.out.println("Client IP address : "+ RTSPsocket.getInetAddress());
			//listenSocket.close();
		} catch (IOException e1) {
			statusinfo.setText("Failed to start server.");
		}
	}
	
	public void playVideo() {

		String requestType = "PLAY";
		//Set input and output stream filters:
		for(final Socket clientSocket : clientSocketSet) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						//in = new DataInputStream (clientSocket.getInputStream());
						DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
						File file = new File(videoFilePath);
						if(file != null && file.exists()) {
							dout.writeInt(1);
							dout.flush();
							long fileSize = file.length();
							dout.writeLong(fileSize);
							System.out.println("filesize"+fileSize);
							dout.flush();

							dout.writeUTF(file.getName());
							dout.flush();

							byte[] buffer1 = new byte[2048];
							FileInputStream fin1 = new FileInputStream(file);
							int length;
							while((length = fin1.read(buffer1))!=-1)
							{
								System.out.println("bufferlength"+length);
								dout.write(buffer1, 0, length);
							}
							dout.flush();
							fin1.close();
							//file.delete();
						}
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}).start();

		} 
	}
	
	public void stopVideo() {
		String requestType = "STOP";
		//Set input and output stream filters:
		for(final Socket clientSocket : clientSocketSet) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						//in = new DataInputStream (clientSocket.getInputStream());
						DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
						dout.writeInt(3);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}

	}
	
	public void pauseVideo() {
		String requestType = "PAUSE";
		//Set input and output stream filters:
		for(final Socket clientSocket : clientSocketSet) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						//in = new DataInputStream (clientSocket.getInputStream());
						DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
						dout.writeInt(2);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}
	
	public void resumeVideo() {
		String requestType = "RESUME";
		//Set input and output stream filters:
		for(final Socket clientSocket : clientSocketSet) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						//in = new DataInputStream (clientSocket.getInputStream());
						DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
						dout.writeInt(4);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}
	
	//------------------------------------
	//main
	//------------------------------------
	public static void main(String argv[]) throws Exception
	{
		//create a Server object
		Server theServer = new Server();

		//show GUI:
		theServer.pack();
		theServer.setVisible(true);
		//theServer.mainPanel.setToolTipText("Enter a port number and press on Configure Server Port");	
		theServer.mainPanel.setBackground (new Color(200,240,120));
		theServer.config_PortPanel.setBackground (new Color(200,140,120));
		theServer.connection_statusPanel.setBackground (new Color(200,240,120));
		
	}


	
	
	
	public class DoCommunicationWithClient implements Runnable {
		Socket RTSPsocket; //socket used to send/receive RTSP messages
		InetAddress ClientIPAddr; //Client IP address
		Timer timer; //timer used to send the images at the video frame rate
		Timer timer1; //timer used to send the images at the FASTER video frame rate
		TimerTask task;

		//RTP variables:
		//----------------
		DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
		DatagramPacket senddp; //UDP packet containing the video frames
		
		int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)
		
		//input and output stream filters
		BufferedReader RTSPBufferedReader;
		BufferedWriter RTSPBufferedWriter;
		byte[] buf = new byte[15000]; //buffer used to store the images to send to the client

		int state; //RTSP Server state == INIT or READY or PLAY
		int RTSP_ID = 213654; //ID of the RTSP session
		int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
		
		public DoCommunicationWithClient(Socket RTSPsocket) {
			this.RTSPsocket = RTSPsocket;
		}

		public void run () {

			task = new MTimerTask();
			timer = new Timer();
			//timer.schedule(task, 100);

			timer1 = new Timer();
			//timer.schedule(task, 20);

			//Initiate RTSPstate
			state = INIT;

			//Set input and output stream filters:
			try {
				RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );
				RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			//Get Client IP address
			ClientIPAddr = RTSPsocket.getInetAddress();
			String clientIPAddress = ClientIPAddr.getHostAddress();
			clientIPAddressSet.add(clientIPAddress);
			System.out.println("Client IP address : "+ clientIPAddress);
			//Print Client IP address
			
			//Wait for the SETUP message from the client
			int request_type;
			boolean done = false;
			while(!done)
			{
				request_type = parse_RTSP_request(); //blocking

				if (request_type == SETUP)
				{
					done = true;
					//theServer.mainPanel.setToolTipText("connection request received from Client, SETUP in progress");	

					//update RTSP state
					state = READY;
					//System.out.println("New RTSP state: READY");
					//theServer.statusinfo.setText("RTSP state: READY");

					//Send response
					send_RTSP_response();

					//init the VideoStream object:
					try {
						video = new StreamMovie(videoFilePath);
						RTPsocket = new DatagramSocket();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					//init RTP socket
				}
			}

			//loop to handle RTSP requests
			while(true)
			{
				//parse the request
				request_type = parse_RTSP_request(); //blocking

				if ((request_type == PLAY) && (state == READY))
				{
					//send back response
					send_RTSP_response();
					//start timer

					timer.scheduleAtFixedRate(task, 100, 100);
					//timer.start();
					//update state
					state = PLAYING;
					//theServer.statusinfo.setText("RTSP state: PLAYING");
				}
				if ((request_type == FORWARD) && (state == READY))
				{
					FRAME_PERIOD = 20;
					//send back response
					send_RTSP_response();
					//start timer
					//timer.schedule(task, 100);
					//timer1.start();
					//update state
					state = PLAYING;
					//statusinfo.setText("RTSP state: FORWARD");
				}
				else if (request_type == DESCRIBE) 
				{
					//send back response
					send_RTSP_response();
					//start timer
					//timer.schedule(task, 100);
					//timer.start();
					//update state
					//state = PLAYING;
					//statusinfo.setText("Welcome to UML Streaming Server\n");
				}
				else if ((request_type == PAUSE) && (state == PLAYING))
				{
					//send back response
					send_RTSP_response();
					//stop timer
					timer.cancel();
					timer1.cancel();

					//timer.stop();
					//timer1.stop();
					//update state
					state = READY;
					//theServer.statusinfo.setText("RTSP state: READY");
				}
				else if ((request_type == PAUSE) && (state == FORWARD))
				{
					//send back response
					send_RTSP_response();
					//stop timer
					timer.cancel();
					timer1.cancel();
					//timer.stop();
					//timer1.stop();
					//update state
					state = READY;
					//theServer.statusinfo.setText("RTSP state: READY");
				}
				else if (request_type == TEARDOWN)
				{
					//send back response
					send_RTSP_response();
					//stop timer
					timer.cancel();
					//timer.stop();
					//close sockets
					try {
						RTSPsocket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					RTPsocket.close();

					System.exit(0);
				}
			}
		}

		//------------------------------------
		//Parse RTSP Request
		//------------------------------------
		private int parse_RTSP_request()
		{
			int request_type = -1;
			try{
				//parse request line and extract the request_type:
				String RequestLine = RTSPBufferedReader.readLine();
				System.out.println("RTSP Server - Received from Client: " + RequestLine);
				if(RequestLine != null) {
					StringTokenizer tokens = new StringTokenizer(RequestLine);
					String request_type_string = tokens.nextToken();

					//convert to request_type structure:
					if ((new String(request_type_string)).compareTo("SETUP") == 0)
						request_type = SETUP;
					else if ((new String(request_type_string)).compareTo("PLAY") == 0)
						request_type = PLAY;
					else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
						request_type = PAUSE;
					else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
						request_type = TEARDOWN;
					else if ((new String(request_type_string)).compareTo("FORWARD") == 0)
						request_type = FORWARD;
					else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
						request_type = DESCRIBE;

					if (request_type == SETUP)
					{
						//extract VideoFileName from RequestLine
						//VideoFileName = tokens.nextToken();
						//videoFileName = Environment.getExternalStorageDirectory()+File.separator+"movie.Mjpeg";
					}

					//parse the SeqNumLine and extract CSeq field
					String SeqNumLine = RTSPBufferedReader.readLine();
					System.out.println(SeqNumLine);
					tokens = new StringTokenizer(SeqNumLine);
					tokens.nextToken();
					RTSPSeqNb = Integer.parseInt(tokens.nextToken());

					//get LastLine
					String LastLine = RTSPBufferedReader.readLine();
					System.out.println(LastLine);

					if (request_type == SETUP)
					{
						//extract RTP_dest_port from LastLine
						tokens = new StringTokenizer(LastLine);
						for (int i=0; i<3; i++)
							tokens.nextToken(); //skip unused stuff
						RTP_dest_port = Integer.parseInt(tokens.nextToken());
					}
				}
				//else LastLine will be the SessionId line ... do not check for now.
			}
			catch(Exception ex)
			{
				System.out.println("Exception caught: "+ex);
				System.exit(0);
			}
			return(request_type);
		}

		//------------------------------------
		//Send RTSP Response
		//------------------------------------
		private void send_RTSP_response()
		{
			try{
				RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
				RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
				RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
				RTSPBufferedWriter.flush();
				System.out.println("RTSP Server - Sent response to Client.");
			}
			catch(Exception ex)
			{
				System.out.println("Exception caught: "+ex);
				System.exit(0);
			}
		}

		public class MTimerTask extends TimerTask
		{
			@Override
			public void run() {
				//------------------------
				//Handler for timer
				//------------------------
				System.out.println("in timer task");

				//if the current image nb is less than the length of the video
				if (imagenb < VIDEO_LENGTH)
				{
					//update current imagenb
					imagenb++;

					try {
						//get next frame to send from the video, as well as its size
						int image_length = video.getnextframe(buf);

						//Builds an PacketShaper object containing the frame
						PacketShaper rtp_packet = new PacketShaper(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, buf, image_length);

						//get to total length of the full rtp packet to send
						int packet_length = rtp_packet.getlength();
						//length.setText("packet length: "+packet_length+" Timestamp: "+rtp_packet.gettimestamp()+"\n");

						//Print progress
						//progress.setValue(rtp_packet.gettimestamp());

						//retrieve the packet bitstream and store it in an array of bytes
						byte[] packet_bits = new byte[packet_length];
						rtp_packet.getpacket(packet_bits);

						//send the packet as a DatagramPacket over the UDP socket 
						senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
						RTPsocket.send(senddp);

						//System.out.println("Send frame #"+imagenb);
						//print the header bitstream
						rtp_packet.printheader();

						//update GUI
						//label.setText("Send frame #" + imagenb);
					}
					catch(Exception ex)
					{
						System.out.println("Exception caught: "+ex);
						System.exit(0);
					}
				}
				else
				{
					System.out.println("in timer task else condition");
					//if we have reached the end of the video file, stop the timer
					//timer.stop();
				}
			}
		}
	}
}