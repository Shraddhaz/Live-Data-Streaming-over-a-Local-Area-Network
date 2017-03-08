package com.dream.www.dreamserver;

/**
 * Created by solid_000 on 03-Jun-16.
 */
import java.io.FileInputStream;

public class StreamMovie {

    FileInputStream fis; //video file
    int curr_frame_nb; //current frame nb

    //-----------------------------------
    //constructor
    //-----------------------------------
    public StreamMovie(String filename) throws Exception{

        //init variables
        fis = new FileInputStream(filename);
        curr_frame_nb = 0;
    }

    //-----------------------------------
    // getnextframe
    //returns the next frame as an array of byte and the size of the frame
    //-----------------------------------
    public int getnextframe(byte[] frame) throws Exception
    {
        int length = 0;
        String length_string;
        byte[] frame_length = new byte[5];

        //read current frame length
        fis.read(frame_length,0,5);

        //transform frame_length to integer
        length_string = new String(frame_length);
        length = Integer.parseInt(length_string);

        return(fis.read(frame,0,length));
    }
}