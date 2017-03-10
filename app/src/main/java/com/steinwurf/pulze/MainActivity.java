package com.steinwurf.pulze;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.FloatRange;
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
import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final int PORT = 51423;
    private static final String GROUPNAME = "224.0.0.251";
    private static final String TAG = "MainActivity";
    RelativeLayout mScreen;
    WifiManager mWifi;
    WifiManager.WifiLock mWifiLock;
    private ReceiverThread mReceiverThread;
    private KeepAliveThread mKeepAliveThread;
    private TextView mLastPacketText;
    private TextView mLostPacketsText;
    private TextView mPacketLossText;
    private TextView mPacketCountText;
    private TextView mKeepAliveText;
    private TextView mWifiSleepPolicyText;
    private TextView mWifiLockTypeText;
    private ObjectAnimator mColorFade;
    private Thread mFadeThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        mScreen = (RelativeLayout)findViewById(R.id.screen);
        mScreen.setBackgroundColor(Color.rgb(255, 0, 0));
        mWifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiLock = mWifi.createWifiLock(TAG);
        mLastPacketText = (TextView)findViewById(R.id.last_packet);
        mLostPacketsText = (TextView)findViewById(R.id.lost_packets);
        mPacketLossText = (TextView)findViewById(R.id.packet_loss);
        mPacketCountText = (TextView)findViewById(R.id.packet_count);
        mKeepAliveText = (TextView)findViewById(R.id.keep_alive);
        mWifiSleepPolicyText = (TextView)findViewById(R.id.wifi_sleep_policy);
        mWifiLockTypeText = (TextView)findViewById(R.id.wifi_lock_type);

        if (mWifi != null){
            WifiManager.MulticastLock lock = mWifi.createMulticastLock("mylock");
            lock.acquire();
        }

        mReceiverThread = new ReceiverThread();
        mReceiverThread.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
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

        public KeepAliveThread(String address, final int interval)
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

    private class Packet {

        public static final int MIN_LENGTH = 23;
        public static final int MAX_LENGTH = 2000;
        public boolean mValid = false;
        public int mPacketNumber;
        public int mSendInterval;
        public int mKeepAliveInterval;
        public int mWifiSleepPolicy;
        public int mWifiLockType;

        Packet(byte[] buffer)
        {
            ByteBuffer wrapped = ByteBuffer.wrap(buffer);

            try {
                mPacketNumber = wrapped.getInt(0);
                mSendInterval = wrapped.getInt(4);
                mKeepAliveInterval = wrapped.getInt(8);
                mWifiSleepPolicy = wrapped.getInt(12);
                mWifiLockType = wrapped.getInt(16);

            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                return;
            }

            mValid = true;
        }
    }

    private class ReceiverThread extends Thread {

        private boolean mTransmit = true;
        private int mRemotePort = 0;

        private int mPacketCount = 0;
        private int mFirstPacketNumber = 0;
        private int mLastPacketNumber = 0;
        private int mLostPackets = 0;
        private double mPacketLoss = 0.0;

        private byte[] mBuffer = new byte[Packet.MAX_LENGTH];

        // Initialize SleepPolicy and WifiLock to bogus values, forcing the first packet to set
        // SleepPolicy and LockType
        private int mWifiSleepPolicy = -1;
        private int mWifiLockType = -1;

        @Override
        public void run() {
            MulticastSocket socket = null;
            try {
                socket = new MulticastSocket(PORT);
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.joinGroup(InetAddress.getByName(GROUPNAME));

                try {
                    while (mTransmit) {
                        DatagramPacket packet = new DatagramPacket(mBuffer, mBuffer.length);
                        socket.receive(packet);

                        // If the port from which the packet was received is
                        // new - new sender - reset
                        if (packet.getPort() != mRemotePort) {
                            mRemotePort = packet.getPort();
                            mPacketCount = 0;
                            mFirstPacketNumber = 0;
                        }

                        final Packet p = new Packet(mBuffer);
                        if (!p.mValid) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mScreen.setBackgroundColor(Color.rgb(255, 255, 0));
                                }
                            });
                            Log.w(TAG, "Got bogus message");
                            continue;
                        }

                        // Handle keep alive
                        if (mKeepAliveThread != null && mKeepAliveThread.mInterval != p.mKeepAliveInterval) {
                            mKeepAliveThread.stopTransmission();
                            try {
                                mKeepAliveThread.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mKeepAliveThread = null;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mKeepAliveText.setText("");
                                }
                            });
                        }

                        if (mKeepAliveThread == null && p.mKeepAliveInterval != 0) {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mKeepAliveText.setText("" + p.mKeepAliveInterval);
                                }
                            });
                            mKeepAliveThread = new KeepAliveThread(
                                    packet.getAddress().getHostAddress(), p.mKeepAliveInterval);
                            mKeepAliveThread.start();
                        }
                        // End of handle keep alive

                        // Update wifi sleep policy
                        if (p.mWifiSleepPolicy != mWifiSleepPolicy) {
                            mWifiSleepPolicy = p.mWifiSleepPolicy;

                            Settings.System.putInt(
                                    getContentResolver(),
                                    Settings.System.WIFI_SLEEP_POLICY,
                                    mWifiSleepPolicy);

                        }

                        // Update wifi lock type
                        if (p.mWifiLockType != mWifiLockType) {
                            mWifiLockType = p.mWifiLockType;
                            if (mWifiLock.isHeld()) {
                                mWifiLock.release();
                            }
                            mWifiLock = mWifi.createWifiLock(mWifiLockType, TAG);
                            mWifiLock.acquire();
                        }

                        // Calculate packet loss
                        if (mPacketCount == 0) {
                            mFirstPacketNumber = p.mPacketNumber;
                        }

                        if (p.mPacketNumber < mFirstPacketNumber) {
                            mFirstPacketNumber = p.mPacketNumber;
                        }

                        if (p.mPacketNumber > mLastPacketNumber) {
                            mLastPacketNumber = p.mPacketNumber;
                        }

                        mPacketCount += 1;

                        int span = 1 + mLastPacketNumber - mFirstPacketNumber;

                        mLostPackets = span - mPacketCount;

                        mPacketLoss = (double) mLostPackets / (double) span  * 100.0;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                // Set minimum value to the display animation
                                int displayInterval = p.mSendInterval;
                                if (displayInterval < 100) {
                                    displayInterval = 100;
                                }

                                resetAnimation(displayInterval);
                                mLastPacketText.setText("" + mLastPacketNumber);
                                mPacketCountText.setText("" + mPacketCount);
                                mLostPacketsText.setText("" + mLostPackets);
                                mPacketLossText.setText(String.format("%.2f", mPacketLoss) + " %");
                                mWifiSleepPolicyText.setText("" + mWifiSleepPolicy);
                                mWifiLockTypeText.setText("" + mWifiLockType);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
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

    // Copied from ArgbEvaluator, which is only available on newer devices.
    public static Object evaluate(float fraction, int start, int end) {
        int startA = (start >> 24) & 0xff;
        int startR = (start >> 16) & 0xff;
        int startG = (start >> 8) & 0xff;
        int startB = start & 0xff;

        int endA = (end >> 24) & 0xff;
        int endR = (end >> 16) & 0xff;
        int endG = (end >> 8) & 0xff;
        int endB = end & 0xff;

        return (int)((startA + (int)(fraction * (endA - startA))) << 24) |
               (int)((startR + (int)(fraction * (endR - startR))) << 16) |
               (int)((startG + (int)(fraction * (endG - startG))) << 8) |
               (int)((startB + (int)(fraction * (endB - startB))));
    }

    public void resetAnimation(final int delay) {
        mScreen.setBackgroundColor(Color.rgb(0, 255, 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (mColorFade != null) {
                mColorFade.cancel();
            }

            mColorFade = ObjectAnimator.ofObject(
                    mScreen,
                    "backgroundColor",
                    new ArgbEvaluator(),
                    0xff00ff00,
                    0xffff0000);

            mColorFade.setStartDelay(delay);
            mColorFade.setDuration(delay);
            mColorFade.start();
        }
        else
        {
            if (mFadeThread != null) {
                mFadeThread.interrupt();
                try {
                    mFadeThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mFadeThread = new Thread() {

                private int mI;

                @Override
                public void run(){

                    try {

                        sleep((int)(delay * 1.5));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mScreen.setBackgroundColor(Color.rgb(255, 0, 0));
                            }
                        });
                        // We don't fade the backround on older devices. The reason for this is
                        // that the fading is too slow if the delay is too low, meaning the devices
                        // will start to flicker even though all packets are received.
                        /*
                        sleep(delay);
                        for(mI = 0; mI < delay; mI += 2) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    int color = (int) evaluate(
                                            (float)mI / (float)delay,
                                            Color.rgb(0, 255, 0),
                                            Color.rgb(255, 0, 0));
                                    mScreen.setBackgroundColor(color);
                                }
                            });
                            sleep(1);
                        }
                        */
                    } catch (InterruptedException e) {
                    }
                }
            };

            mFadeThread.start();
        }
    }
}
                        // though they are receiving all packets.
                        // long if the delay is too short. This causes the devices to flicker even
                        // We don't fade the background on older devices, as it seems to take too
