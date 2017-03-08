package com.dream.www.dreamserver;

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

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

    //Video variables:
    //----------------
    int imagenb = 0; //image nb of the image currently transmitted
    StreamMovie video; //StreamMovie object used to access video frames
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms..if we reduce it movie plays in fast forward mode and viceversa.

    static int VIDEO_LENGTH = 500; //length of the video in frames

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
    static String videoFilePath; //video file requested from the client

    final static String CRLF = "\r\n";
    //static volatile int setupstage= 0;

    private Button configureButton;
    private Spinner videoFileSpinner;
    private TextView serverIpTextView;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    HashSet<String> clientIPAddressSet = new HashSet<String>();
    HashSet<Socket> clientSocketSet = new HashSet<Socket>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoFileSpinner = (Spinner) findViewById(R.id.activity_main_videoFileSpinner);
        configureButton = (Button) findViewById(R.id.activity_main_configureButton);
        playButton = (Button) findViewById(R.id.activity_main_playButton);
        pauseButton = (Button) findViewById(R.id.activity_main_pauseButton);
        stopButton = (Button) findViewById(R.id.activity_main_stopButton);
        serverIpTextView = (TextView) findViewById(R.id.activity_main_serverIpTextView);
        serverIpTextView.setText(Utility.getLocalIpAddress());

        File videoDir = new File(Environment.getExternalStorageDirectory()+File.separator+"stream");
        File[] fileArray = videoDir.listFiles();
        final String[] fileNameArray = new String[fileArray.length];
        for(int i=0;i<fileArray.length;i++)
            fileNameArray[i] = fileArray[i].getName();
        ArrayAdapter<String> fileAdapter = new ArrayAdapter<String>(MainActivity.this,R.layout.list_item, fileNameArray);
        videoFileSpinner.setAdapter(fileAdapter);

        videoFileSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                //Log.d(TAG, "selected room is " + position  + " value is " +  roomList.get(position));
                Toast.makeText(MainActivity.this, "file name :"  +  fileNameArray[position], Toast.LENGTH_LONG).show();
                videoFilePath = Environment.getExternalStorageDirectory()+File.separator+"stream"+File.separator+fileNameArray[position];
            }
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(videoFilePath);
                if(file != null && file.exists() && file.length() < 50000000) {
                    playButton.setEnabled(false);
                    playVideo();
                } else {
                    Toast.makeText(MainActivity.this, "Error occured ! Select another file.", Toast.LENGTH_LONG).show();
                }
            }
        });

        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if("Pause".equalsIgnoreCase(pauseButton.getText().toString())) {
                    pauseButton.setText("Resume");
                    pauseVideo();
                } else {
                    pauseButton.setText("Pause");
                    resumeVideo();
                }
            }
        });

        stopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playButton.setEnabled(true);
                pauseButton.setText("Pause");
                stopVideo();
            }
        });

        configureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("DDream","Configure Server Port Button pressed !");
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


    }

    private void startOperation() {
        //get RTSP socket port from the command line
        int RTSPport = 2345;

        //Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket;
        try {
            listenSocket = new ServerSocket(RTSPport);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Server started successfully.", Toast.LENGTH_SHORT).show();
                    configureButton.setEnabled(false);
                }
            });

            while(true){
                Log.d("DDream","start for new");
                Socket RTSPsocket; //socket used to send/receive RTSP messages
                RTSPsocket = listenSocket.accept();
                clientSocketSet.add(RTSPsocket);
                //Get Client IP address
                InetAddress ClientIPAddr = RTSPsocket.getInetAddress();
                final String clientIPAddress = ClientIPAddr.getHostAddress();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "client connected from ip : " + clientIPAddress, Toast.LENGTH_SHORT).show();
                    }
                });

				/*DoCommunicationWithClient conn_c= new DoCommunicationWithClient(RTSPsocket);
				Thread t = new Thread(conn_c);
				t.start();*/
            }
            //Log.d("DDream","Client IP address : "+ RTSPsocket.getInetAddress());
            //listenSocket.close();
        } catch (IOException e1) {
            e1.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Failed to start server.", Toast.LENGTH_SHORT).show();
                }
            });
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
                            Log.d("DDream","filesize"+fileSize);
                            dout.flush();

                            dout.writeUTF(file.getName());
                            dout.flush();

                            byte[] buffer1 = new byte[2048];
                            FileInputStream fin1 = new FileInputStream(file);
                            int length;
                            while((length = fin1.read(buffer1))!=-1)
                            {
                                Log.d("DDream","bufferlength"+length);
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
            Log.d("DDream","Client IP address : "+ clientIPAddress);
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
                    //Log.d("DDream","New RTSP state: READY");
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
                Log.d("DDream","RTSP Server - Received from Client: " + RequestLine);
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
                Log.d("DDream","Exception caught: "+ex);
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
                Log.d("DDream","RTSP Server - Sent response to Client.");
            }
            catch(Exception ex)
            {
                Log.d("DDream","Exception caught: "+ex);
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
                Log.d("DDream","in timer task");

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

                        //Log.d("DDream","Send frame #"+imagenb);
                        //print the header bitstream
                        rtp_packet.printheader();

                        //update GUI
                        //label.setText("Send frame #" + imagenb);
                    }
                    catch(Exception ex)
                    {
                        Log.d("DDream","Exception caught: "+ex);
                        System.exit(0);
                    }
                }
                else
                {
                    Log.d("DDream","in timer task else condition");
                    //if we have reached the end of the video file, stop the timer
                    //timer.stop();
                }
            }
        }
    }

}