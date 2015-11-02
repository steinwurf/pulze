package com.steinwurf.pulze;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final int PORT = 51423;
    private static final int KEEP_ALIVE_INTERVAL = 100;
    private static final String TAG = "MainActivity";
    RelativeLayout mScreen;
    WifiManager mWifi;
    private ReceiverThread mReceiverThread;
    private WifiManager.WifiLock mWifiLock;
    private KeepAliveThread mKeepAliveThread;
    private TextView mLastPacketText;
    private TextView mPacketCountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScreen = (RelativeLayout)findViewById(R.id.screen);
        mScreen.setBackgroundColor(Color.rgb(255, 0, 0));
        mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiLock = mWifi.createWifiLock("WakeLockPulze");
        mLastPacketText = (TextView)findViewById(R.id.last_packet);
        mPacketCountText = (TextView)findViewById(R.id.packet_count);

        mWifiLock.acquire();
        mReceiverThread = new ReceiverThread();
        mReceiverThread.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mWifiLock.release();
        Log.d(TAG, "Stopping Receiver Thread");
        if (mReceiverThread != null) {
            mReceiverThread.stopTransmission();
            try {
                mReceiverThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "Stopping KeepAlive Thread");
        if (mKeepAliveThread != null) {
            mKeepAliveThread.stopTransmission();
            try {
                mKeepAliveThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "Stopping Activity");
    }

    private class KeepAliveThread extends Thread {
        private final String mAddress;
        private int PORT = 13337;
        private int mInterval;
        private boolean mTransmit = true;

        public KeepAliveThread(String address, int interval)
        {
            mInterval = interval;
            mAddress = address;
        }

        @Override
        public void run() {
            Log.d(TAG, "Starting keep alive...");
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(PORT);
                try {
                    InetAddress host = InetAddress.getByName(mAddress);
                    while (mTransmit) {
                        byte[] buffer = {0x66};
                        DatagramPacket out = new DatagramPacket(buffer, buffer.length, host, PORT);
                        socket.send(out);
                        sleep(mInterval);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } catch (SocketException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }
        public void stopTransmission()
        {
            mTransmit = false;
        }
    }

    private class ReceiverThread extends Thread {

        private boolean mTransmit = true;
        private int mPacketCount = 0;

        @Override
        public void run() {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(PORT);
                socket.setReuseAddress(true);
                socket.setBroadcast(true);

                try {
                    while (mTransmit) {
                        byte[] buffer = new byte[16];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        if (mKeepAliveThread == null) {
                            Log.d(TAG, "Setting keep alive address: " + packet.getAddress().getHostAddress());
                            mKeepAliveThread = new KeepAliveThread(packet.getAddress().getHostAddress(), KEEP_ALIVE_INTERVAL);
                            mKeepAliveThread.start();
                        }
                        String result = new String(buffer);

                        final String[] results = result.split(",");
                        final int packetNumber = Integer.parseInt(results[1]);
                        mPacketCount += 1;

                        Log.d(TAG, "packet " + packetNumber);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resetAnimation(Integer.parseInt(results[0]));
                                mLastPacketText.setText("" + packetNumber);
                                mPacketCountText.setText("" + mPacketCount);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (SocketException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }

        public void stopTransmission()
        {
            mTransmit = false;
        }
    }

    public void resetAnimation(int delay)
    {
        mScreen.setBackgroundColor(Color.rgb(0,255,0));

        ObjectAnimator colorFade = ObjectAnimator.ofObject(
                mScreen,
                "backgroundColor",
                new ArgbEvaluator(),
                0xff00ff00,
                0xffff0000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            colorFade.setAutoCancel(true);
        }
        colorFade.setStartDelay(delay);
        colorFade.setDuration(delay);
        colorFade.start();
    }
}
