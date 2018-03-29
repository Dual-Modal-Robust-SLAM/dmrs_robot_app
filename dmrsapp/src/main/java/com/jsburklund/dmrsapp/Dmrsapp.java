package com.jsburklund.dmrsapp;

import android.app.Activity;
import android.os.Bundle;
import android.app.Activity;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.ros.android.RosActivity;
import org.ros.android.android_acm_serial.AcmDevice;
import org.ros.android.android_acm_serial.AcmDeviceActivity;
import org.ros.android.view.camera.RosCameraPreviewView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.io.IOException;

public class Dmrsapp extends AcmDeviceActivity {
    private int cameraId;
    private RosCameraPreviewView rosCameraPreviewView;
    private Neato robot;
    private AcmDevice acmpasser;

    public Dmrsapp() {
        super("DMRSApp", "DMRSApp");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        rosCameraPreviewView = (RosCameraPreviewView) findViewById(R.id.ros_camera_preview_view);
        this.robot = new Neato();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int numberOfCameras = Camera.getNumberOfCameras();
            final Toast toast;
            if (numberOfCameras > 1) {
                cameraId = (cameraId + 1) % numberOfCameras;
                rosCameraPreviewView.releaseCamera();
                rosCameraPreviewView.setCamera(getCamera());
                toast = Toast.makeText(this, "Switching cameras.", Toast.LENGTH_SHORT);
            } else {
                toast = Toast.makeText(this, "No alternative cameras to switch to.", Toast.LENGTH_SHORT);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toast.show();
                }
            });
        }
        return true;
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        cameraId = 0;
        Log.d("Main_Activity", "Init'ed this.robot: "+(this.robot==null));

        rosCameraPreviewView.setCamera(getCamera());
        try {
            java.net.Socket socket = new java.net.Socket(getMasterUri().getHost(), getMasterUri().getPort());
            java.net.InetAddress local_network_address = socket.getLocalAddress();
            socket.close();
            NodeConfiguration nodeConfiguration =
                    NodeConfiguration.newPublic(local_network_address.getHostAddress(), getMasterUri());
            nodeMainExecutor.execute(rosCameraPreviewView, nodeConfiguration);
            nodeMainExecutor.execute(robot, nodeConfiguration);
        } catch (IOException e) {
            // Socket problem
            Log.e("Camera Tutorial", "socket error trying to get networking information from the master uri");
        }
        Log.d("Main_Activity", "Done init this.robot: "+(this.robot==null));

    }

    private Camera getCamera() {
        Camera cam = Camera.open(cameraId);
        Camera.Parameters camParams = cam.getParameters();
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (camParams.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
        }
        cam.setParameters(camParams);*/
        return cam;
    }


    Runnable sendACM = new Runnable() {
        @Override
        public void run() {
            Log.d("Main_Activity", "Attempting to pass acm device: "+(acmpasser==null));
            Log.d("Main_Activity", "Attempting to pass to robot: "+(robot==null));
            robot.setACMDevice(acmpasser);
        } // This is your code
    };

    @Override
    public void onPermissionGranted(final AcmDevice acmDevice) {
        Toast.makeText(this.getApplicationContext(), "Got an ACM device: "+acmDevice.toString(), Toast.LENGTH_SHORT).show();

        //Attempt to startup the lds sensor
//        Thread t = new Thread() {
//            public void run() {
//                try {
//                    Thread.sleep(1000);
//                    acmDevice.getOutputStream().write("testmode on\n".getBytes());
//                    acmDevice.getOutputStream().flush();
//                    Thread.sleep(500);
//                    acmDevice.getOutputStream().write("setldsrotation on\n".getBytes());
//                    acmDevice.getOutputStream().flush();
//                    Thread.sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        };
//        t.start();
        //Log.d("Main_Activity", "Adding ACMDevice this.robot: "+(this.test==null));
        Log.d("Main_Activity", "Adding ACMDevice this.robot: "+(this.robot==null));
        //this.robot.setACMDevice(acmDevice);
        this.acmpasser = acmDevice;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(sendACM);
    }

    @Override
    public void onPermissionDenied() {

    }
}
