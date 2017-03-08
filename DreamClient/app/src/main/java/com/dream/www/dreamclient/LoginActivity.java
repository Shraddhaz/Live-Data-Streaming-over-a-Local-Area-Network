package com.dream.www.dreamclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;


public class LoginActivity extends AppCompatActivity {

    EditText uname,pass;
    private Button login,cancel;

    private final String TAG = MainActivity.class.getCanonicalName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //code for button click start
        uname=(EditText)findViewById(R.id.login_layout_userNameEditText);
        pass=(EditText)findViewById(R.id.login_layout_passwordEditText);

        login=(Button) findViewById(R.id.login_layout_OkButton);


        login.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                String username=uname.getText().toString();
                String paswrd=pass.getText().toString();

                if(!username.equals("user") && !paswrd.equals("user"))
                {
                    Toast.makeText(getApplicationContext(), "please enter valid details", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Intent i=new Intent(LoginActivity.this,MainActivity.class);
                    startActivity(i);
                    Toast.makeText(getApplicationContext(), "welcome", Toast.LENGTH_SHORT).show();

                }

            }
        });

    }
}

