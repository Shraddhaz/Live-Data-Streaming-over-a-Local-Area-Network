package com.dream.www.dreamclient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;


public class MainActivity extends AppCompatActivity {
    //RTP variables:
    //----------------
    DatagramPacket rcvdp; //UDP packet received from the server
    static DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets

    Timer timer; //timer used to receive data from the UDP socket
    byte[] buf; //buffer used to store data received from the server
    static int countpk=0;

    //RTSP variables
    //----------------
    //rtsp states
    static String value="idle";
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    final static int FORWARD = 8;
    volatile static int state; //RTSP state == INIT or READY or PLAYING
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    DataInputStream in;
    DataOutputStream out;
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)
    final static String CRLF = "\r\n";
    volatile static int setupstage= 0;
    static int sizem;
    static int tc=1;
    static int dt=1;

    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    private Button connectButton;
    private TextView serverIPAddressEditText;
    VideoView mVideoView;
    private ImageView imageView;
    private int stopPosition;
    private String vfn;
    TimerTask task;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new File(Environment.getExternalStorageDirectory().toString()+File.separator+"temp").delete();

        connectButton = (Button) findViewById(R.id.activity_main_connectButton);
        serverIPAddressEditText = (EditText) findViewById(R.id.activity_main_serverIPAddressEditText);
        serverIPAddressEditText.setText("192.168.43.1");
        mVideoView = (VideoView)findViewById(R.id.activity_main_videoView);

        connectButton.setOnClickListener(new  OnClickListener() {
            @Override
            public void onClick(View v) {
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

        Log.d("DDream","before while");
        try {

            Log.d("DDream","after while");
            //get server RTSP port and IP address from the command line
            //------------------
            int RTSP_server_port = Integer.parseInt("2345");//Integer.parseInt(argv[1]);
            String ServerHost = serverIPAddressEditText.getText().toString();

            Log.d("DDream","ServerHost "+ServerHost+" RTSP_server_port"+RTSP_server_port);

            //Establish a TCP connection with the server to exchange RTSP messages
            //------------------
            RTSPsocket = new Socket(ServerHost, RTSP_server_port);
            Log.d("DDream","new socket "+ServerHost);
            //connection is establish
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connection established.", Toast.LENGTH_SHORT).show();
                }
            });

            Log.d("DDream","Create a TCP Socket to exchange RTSP messages !");
            //Set input and output stream filters:
            in = new DataInputStream(RTSPsocket.getInputStream());
            out = new DataOutputStream(RTSPsocket.getOutputStream());

            Log.d("DDream","RTSP client : Connection successful, ready to play Video ");

            while(true) {
                int todo = in.readInt();
                switch (todo) {
                    case 1:
                        File directory = new File(Environment.getExternalStorageDirectory().toString()+File.separator+"temp");
                        if(!directory.exists())
                            directory.mkdir();

                        long filesSize = in.readLong();
                        if(filesSize != 0)
                        {
                            vfn = in.readUTF();
                            String filePath = directory.getAbsolutePath()+File.separator+vfn;
                            File file = new File(filePath);
                            if(file.exists())
                                file.delete();

                            file.createNewFile();

                            Log.d("DDream","file size===="+filesSize);
                            Log.d("DDream","file path===="+filePath);


                            byte[] buffer = new byte[2048];
                            FileOutputStream fos = new  FileOutputStream(file);
                            int length;
                            while(filesSize>0 && (length = in.read(buffer, 0 , (int)Math.min(buffer.length,filesSize)))!=-1)
                            {

                                Log.d("DDream","dfilesSize"+filesSize);
                                Log.d("DDream","length of buffer"+length);
                                fos.write(buffer, 0, length);
                                filesSize-=length;

                            }
                            //in.read(buffer, 0, buffer.length);
                            fos.flush();
                            fos.close();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mVideoView.setVideoURI(Uri.fromFile(new File(Environment.getExternalStorageDirectory().toString()+File.separator+"temp"+File.separator+vfn)));
                                    mVideoView.requestFocus();
                                    mVideoView.start();
                                }
                            });
                        }
                        break;
                    case 2:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopPosition = mVideoView.getCurrentPosition(); //stopPosition is an int
                                mVideoView.pause();
                            }
                        });
                        break;
                    case 3:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoView.stopPlayback();
                                mVideoView.clearFocus();
                                new File(Environment.getExternalStorageDirectory().toString()+File.separator+"temp").delete();
                            }
                        });
                        break;
                    case 4:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "resume video", Toast.LENGTH_SHORT).show();
                                mVideoView.seekTo(stopPosition);
                                mVideoView.start();
                            }
                        });
                        break;
                    default:
                        break;
                }
            }
			/*commandSocket = new ServerSocket(9876);
			while(true){
				Log.d("DDream","start for new");
				Socket RTSPsocket; //socket used to send/receive RTSP messages
				RTSPsocket = commandSocket.accept();

				DoCommandWithClient conn_c= new DoCommandWithClient(RTSPsocket);
				Thread t = new Thread(conn_c);
				t.start();
			}*/

        } catch (Exception e) {
            e.printStackTrace();
            //connection is establish
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Failed to connect to server.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    class play_movieButtonListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d("DDream","Play Button pressed !");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (state == READY)
                    {
                        //increase RTSP sequence number
                        RTSPSeqNb++;
                        //Send PLAY message to the server
                        send_RTSP_request("PLAY");

                        //Wait for the response
                        if (parse_server_response() != 200)
                            Log.d("DDream","Invalid Server Response");
                        else
                        {
                            //change RTSP state and print out new state
                            state= PLAYING;
                            Log.d("DDream","New RTSP state:PLAYING");

                            //start the timer
                            //timer.start();
                            timer.scheduleAtFixedRate(task, 100, 100);
                        }
                    }//else if state != READY then do nothing
                }
            }).start();
        }
    }

    private void send_RTSP_request(String request_type)
    {
        try{
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            RTSPBufferedWriter.write(request_type+" "+"RTSP/1.0"+CRLF);

            //write the CSeq line:
            RTSPBufferedWriter.write("Cseq: "+RTSPSeqNb+CRLF);

            //check if request_type is equal to "SETUP" and in this case write the Transport: line advertising to the server the port used to receive the RTP packets RTP_RCV_PORT
            if (state == INIT)
            {
                RTSPBufferedWriter.write("TRANSPORT: RTP/UDP; client_port= "+RTP_RCV_PORT+CRLF);
            }
            //otherwise, write the Session line from the RTSPid field
            else {
                RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
            }
            RTSPBufferedWriter.flush();

        }
        catch(Exception ex)
        {
            Log.d("DDream","Exception caught: "+ex);
            System.exit(0);
        }
    }

    //------------------------------------
    //Parse Server Response
    //------------------------------------
    private int parse_server_response()
    {
        int reply_code = 0;

        try{
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            //Log.d("DDream","RTSP Client - Received from Server:");
            Log.d("DDream","StatusLine");


            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); //skip over the RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());

            //if reply code is OK get and print the 2 other lines
            if (reply_code == 200)
            {
                String SeqNumLine = RTSPBufferedReader.readLine();
                Log.d("DDream","SeqNumLine");

                String SessionLine = RTSPBufferedReader.readLine();
                Log.d("DDream","SessionLine");

                //if state == INIT gets the Session Id from the SessionLine
                tokens = new StringTokenizer(SessionLine);
                tokens.nextToken(); //skip over the Session:
                RTSPid = Integer.parseInt(tokens.nextToken());
            }
        }
        catch(Exception ex)
        {
            Log.d("DDream","Exception caught: "+ex);
            System.exit(0);
        }

        return(reply_code);
    }



    public class MTimerTask extends TimerTask
    {
        @Override
        public void run() {

            Log.d("DDream","in timer task");
            //Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(buf, buf.length);


            try{
                //receive the DP from the socket:
                RTPsocket.receive(rcvdp);


                //create an PacketShaper object from the DP
                PacketShaper rtp_packet = new PacketShaper(rcvdp.getData(), rcvdp.getLength());

                //print total packet loss
                sizem=sizem+rtp_packet.getpayload_length();
                dt=tc-rtp_packet.gettimestamp();
                tc=rtp_packet.gettimestamp();

                //countpkt.setText(" Transmission rate: "+(sizem/tc)+" KB/s - Burst rate: "+(rtp_packet.getpayload_length()/(-dt))+ "KB/s");


                switch (state)
                {
                    case 0:
                        value="INIT";
                        break;
                    case 1:
                        value="READY";
                        break;
                    case 2:
                        value="PLAYING";
                        break;
                    case 8:
                        value="FORWARD";
                        break;
                    default:
                        value="IDLE";
                }
                //status.setText(value);

                //print important header fields of the RTP packet received:
                Log.d("DDream","Recieved RTP packet with SeqNum # "+rtp_packet.getsequencenumber()+" TimeStamp "+rtp_packet.gettimestamp()+" ms, of type "+rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the PacketShaper object
                int payload_length = rtp_packet.getpayload_length();
                byte [] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);

                //get an Image object from the payload bitstream
                final Bitmap bm = BitmapFactory.decodeByteArray(payload, 0, payload_length);
                //Toolkit toolkit = Toolkit.getDefaultToolkit();
                //Image image = toolkit.createImage(payload, 0, payload_length);

                //display the image as an ImageIcon object
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        imageView.setImageBitmap(bm);
                    }
                });

                //icon = new ImageIcon(image);
                //iconLabel.setIcon(icon);
            }
            catch (InterruptedIOException iioe){
                //Log.d("DDream","Nothing to read");
            }
            catch (IOException ioe) {
                Log.d("DDream","Exception caught: "+ioe);
            }
        }
    }

    public class DoCommandWithClient implements Runnable {
        Socket socket; //socket used to send/receive RTSP messages
        InetAddress ClientIPAddr; //Client IP address

        //input and output stream filters
        BufferedReader bufferedReader;
        BufferedWriter bufferedWriter;

        public DoCommandWithClient(Socket socket) {
            this.socket = socket;
        }

        public void run () {

            //Set input and output stream filters:
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()) );
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()) );

                while(true) {
                    String requestLine = bufferedReader.readLine();
                    Log.d("DDream","RTSP Client - Received from Server: " + requestLine);
                    if (requestLine != null && (new String(requestLine)).compareTo("PLAY") == 0){
                        if (state == READY)
                        {
                            //increase RTSP sequence number
                            RTSPSeqNb++;
                            //Send PLAY message to the server
                            send_RTSP_request("PLAY");

                            //Wait for the response
                            if (parse_server_response() != 200)
                                Log.d("DDream","Invalid Server Response");
                            else
                            {
                                //change RTSP state and print out new state
                                state= PLAYING;
                                Log.d("DDream","New RTSP state:PLAYING");

                                //start the timer
                                //timer.start();
                                timer.scheduleAtFixedRate(task, 100, 100);
                            }
                        }//else if state != READY then do nothing
                    } else if (requestLine != null && (new String(requestLine)).compareTo("STOP") == 0){
                        //Log.d("DDream","Pause Button pressed !");
                        if (state == PLAYING)
                        {
                            //increase RTSP sequence number
                            RTSPSeqNb ++;

                            //Send PAUSE message to the server
                            send_RTSP_request("PAUSE");

                            //Wait for the response
                            if (parse_server_response() != 200)
                                Log.d("DDream","Invalid Server Response");
                            else
                            {
                                //change RTSP state and print out new state
                                state= READY;
                                Log.d("DDream","New RTSP state:READY");

                                //stop the timer
                                timer.cancel();
                            }
                        }
                        //else if state != PLAYING then do nothing
                    }
                }
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        new File(Environment.getExternalStorageDirectory().toString()+File.separator+"temp").delete();
    }

}
