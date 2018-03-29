package com.jsburklund.dmrsapp;

import android.util.Log;

import org.ros.android.android_acm_serial.AcmDevice;
import org.ros.concurrent.CancellableLoop;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import geometry_msgs.Twist;
import sensor_msgs.LaserScan;

/**
 * Created by rosbot on 3/28/18.
 */

public class Neato extends AbstractNodeMain {
    private String TAG = "Neato_Node";
    private double kBASE_WIDTH = 248;  //millimeters
    private double kMAX_SPEED =  300;  //mm/sec

    private AcmDevice acmDevice;
    private InputStream inStream;
    private OutputStream outStream;
    private State state;

    private Publisher<LaserScan> laserScanPub;
    private Publisher<geometry_msgs.Twist> odomPub;
    private Subscriber<Twist> cmdVelSub;

    private double[] wheel_vels;

    enum State {
        NOT_CONNECTED,
        SETUP,
        CONNECTED,
    }

    enum TestMode {
        ON,
        OFF
    }

    public Neato() {
        acmDevice = null;
        inStream = null;
        outStream = null;
        state = State.NOT_CONNECTED;
    }

    @Override
    public GraphName getDefaultNodeName() {  return GraphName.of("neato"); }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        wheel_vels = new double[2];
        wheel_vels[0] = 0;
        wheel_vels[1] = 0;
        //state = State.NOT_CONNECTED;

        laserScanPub = connectedNode.newPublisher("neato/laser", LaserScan._TYPE);
        odomPub = connectedNode.newPublisher("neato/odom", Twist._TYPE);
        cmdVelSub = connectedNode.newSubscriber("neato/cmd_vel", Twist._TYPE);
        //cmdVelSub = connectedNode.newSubscriber("cmd_vel_mux/input/teleop", Twist._TYPE);
        cmdVelSub.addMessageListener(new MessageListener<Twist>() {
            @Override
            public void onNewMessage(Twist twist) {
              double v = twist.getLinear().getX()*1000;
              double omega = twist.getAngular().getZ()*(kBASE_WIDTH/2);
              double k = Math.max(Math.abs(v-omega), Math.abs(v+omega));
              if (k>kMAX_SPEED) {
                  v = v*kMAX_SPEED/k; omega = omega*kMAX_SPEED/k;
              }
              wheel_vels[0] = v-omega;
              wheel_vels[1] = v+omega;
              //Log.d(TAG, "Sending motor commands: "+wheel_vels[0]+" "+wheel_vels[1]);
            }
        });

        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void setup() {

            }
            @Override
            protected void loop() throws InterruptedException {
                if (state == State.NOT_CONNECTED) {
                    return;
                }
                if (state == State.SETUP) {
                  Log.d(TAG, "Entering Setup mode");
                  setTestMode(true);
                  state = State.CONNECTED;
                }
                Log.d(TAG, "Mode: "+state.toString());
                Log.d(TAG, "Motors: "+wheel_vels[0]+", "+wheel_vels[1]);
                // Run everything in sequence to reduce necessity of synchronization primitives
                setMotors(wheel_vels[0], wheel_vels[1], Math.max(Math.abs(wheel_vels[0]), Math.abs(wheel_vels[1])))ii;
                Thread.sleep(100);
            }
        });
    }

    @Override
    public void onShutdown(Node arg0) {
        laserScanPub.shutdown();
        laserScanPub = null;
        odomPub.shutdown();
        odomPub = null;
        cmdVelSub.shutdown();
        cmdVelSub = null;
        try {
            inStream.close(); inStream = null;
            outStream.close(); outStream = null;
            acmDevice.close(); acmDevice = null;
        } catch (Exception e) {}  //Ignore errors since we are shutting down anyways
        //state = State.NOT_CONNECTED;
    }


    public void setACMDevice(AcmDevice acmDevice) {
        Log.d(TAG, "Called setACMDevice");
        state = State.SETUP;
        this.acmDevice = acmDevice;
        this.inStream = acmDevice.getInputStream();
        this.outStream = acmDevice.getOutputStream();
        Log.d(TAG, "State: "+state.toString());
    }

    /* Ported from neato_robot package */
    public void setMotors(double l_dist, double r_dist, double speed) {
        synchronized (outStream) {
            try {
                if (outStream != null) {
                    //TODO Add workaround for setting 0 velocity

                    String output = "setmotor " + ((int) l_dist) + " "
                            + ((int) r_dist) + " "
                            + ((int) speed) + "\n";
                    Log.d(TAG, output);
                    outStream.write(output.getBytes());
                    outStream.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                //state = State.NOT_CONNECTED;
            } //Ignore any output errors
        }
    }

    public void setTestMode(boolean setOn) {
        /*if (state == State.NOT_CONNECTED) {
            return;
        }*/
        synchronized (outStream) {
            try {
                if (setOn) {
                    outStream.write("testmode on\n".getBytes());
                } else {
                    outStream.write("testmode off\n".getBytes());
                }
                outStream.flush();
                acmDevice.getOutputStream().write("setldsrotation on\n".getBytes());
                outStream.flush();
            } catch(IOException e) {
                Log.e(TAG, e.getMessage());
                //state = State.NOT_CONNECTED;
            }
        }
    }
}
