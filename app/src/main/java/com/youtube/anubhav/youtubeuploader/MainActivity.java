package com.youtube.anubhav.youtubeuploader;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.youtube.anubhav.youtubeuploader.Activities.Home;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
               startHome();
            }
        },2000);

    }

    private void startHome(){
        startActivity(new Intent(this,Home.class));
        finish();
    }
}
