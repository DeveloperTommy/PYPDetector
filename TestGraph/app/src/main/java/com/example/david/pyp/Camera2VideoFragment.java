package com.example.david.pyp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class Camera2VideoFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {


    /* Microsoft Band and Data Processing*/
    BandInfo[] pairedBands;
    BandClient bandClient;

    TextView feedback;
    Button attach, read_button, write_button;

    Boolean attached;

    BandPendingResult<ConnectionState> pendingResult;

    //We are storing these readings for debugging and computing purposes
    ArrayList<Integer> gsrReadings, heartReadings;
    ArrayList<Double> rrReadings;
    ArrayList<String> readings;

    /* Fear Level Handling */
    TextView fearLevel;

    public static final int CALM = 0, ANXIOUS = 1, SCARED = 2, VERY_SCARED = 3, TERRIFIED = 4;

    public String[] fears = {"CALM", "ANXIOUS", "SCARED", "VERY_SCARED", "TERRIFIED"};
    public int fear = CALM;
    /* End of Fear Level Handling */

    //We create a class for each reading to indicate a data point that we have. Each reading is based on each heartbeat from our RR Interval
    private class Reading {
        int heartRate, gsrRate;
        double rrRate;

        public Reading() {
            heartRate = 0;
            gsrRate = 0;
            rrRate = 0;
        }

        public Reading(int heart, int gsr, double rr) {
            heartRate = heart;
            gsrRate = gsr;
            rrRate = rr;
        }
    }

    //This is our fear buffer where we continually add and replace into this buffer and compute baselines as well as averages.
    Reading[] fearBuffer;

    boolean baselined = false;
    double baselineHeart = 0, baselineGsr = 0, averageHeart, averageGsr;
    int readingIdx = -1;

    /* End of Microsoft Band and Data Processing*/


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "Camera2VideoFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

     //An {@link AutoFitTextureView} for camera preview.
     private AutoFitTextureView mTextureView;

    //Button to record video
    private Button mButtonVideo;


     //A refernce to the opened {@link android.hardware.camera2.CameraDevice}
    private CameraDevice mCameraDevice;

     /* A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
        preview.
     */
    private CameraCaptureSession mPreviewSession;

    /*
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link android.util.Size} of video recording.
     */
    private Size mVideoSize;

    //Camera preview
    private CaptureRequest.Builder mPreviewBuilder;


     // MediaRecorder
    private MediaRecorder mMediaRecorder;


     // Boolean to check whether the app is recording video now
    private boolean mIsRecordingVideo;


     //An additional thread for running tasks that shouldn't block the UI.
    private HandlerThread mBackgroundThread;


     //A {@link Handler} for running tasks in the background.
    private Handler mBackgroundHandler;


     //A {@link Semaphore} to prevent the app from exiting before closing the camera.
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);




    private LineGraphSeries<DataPoint> series1;
    private LineGraphSeries<DataPoint> series2;
    private int lastX = 0;
    private double lastHeartRate = 0;
    private double lastRr  = 0;

    private GraphView graph;

    //{@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        //Camera starts preview once app is opened
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    /**
     * Sets resolution and aspect ratio of video camera.
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 2 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Optimal width and height of video camera.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        Activity activity = getActivity();

        /* Microsoft Band + Data Analysis */
        feedback = (TextView) activity.findViewById(R.id.feedback);

        gsrReadings = new ArrayList<Integer>();
        heartReadings = new ArrayList<Integer>();
        rrReadings = new ArrayList<Double>();
        readings = new ArrayList<String>();

        fearBuffer = new Reading[10];

        fearLevel = (TextView) activity.findViewById(R.id.fear);

        attached = false;

        attach = (Button) activity.findViewById(R.id.band_attach);
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachBand();
                attached = true;
            }
        });

        read_button = (Button) activity.findViewById(R.id.read_button);
        read_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (attached) {

                    feedback.setText("Reading data..");
                    readData();
                } else {
                    feedback.setText("Band not attached yet");
                }
            }
        });

        write_button = (Button) activity.findViewById(R.id.write_button);
        write_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("Thing", "Writing to file");
                writeFile();
                takeScreenshot();
            }
        });

        /* End of Microsoft Band + Data Analysis */

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mButtonVideo = (Button) view.findViewById(R.id.video);
        mButtonVideo.setOnClickListener(this);

        // Set the graph view instance
        graph = (GraphView) view.findViewById(R.id.graph);

        //Declaration of series for graph
        series1 = new LineGraphSeries<DataPoint>();
        series1.setColor(Color.RED);
        series2 = new LineGraphSeries<DataPoint>();
        series2.setColor(Color.BLUE);
        graph.addSeries(series1);
        graph.addSeries(series2);

        // legend of series to be displayed on graph
        series1.setTitle("Heart Rate");
        series2.setTitle("RR Reading");
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);


        // viewport customization. Sets scale of the graph
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(25);
        viewport.setMaxY(100);
        viewport.setScrollable(true);


    }

    public boolean attachBand() {
        pairedBands = BandClientManager.getInstance().getPairedBands();
        bandClient = BandClientManager.getInstance().create(getActivity(), pairedBands[0]);

        feedback.setText("Band Attached");

        //Need to place this in an Async Task
        new ConnectBandTask().execute();
        return true;
    }

    public void readData() {
        final BandGsrEventListener gsrListener = new BandGsrEventListener() {
            @Override
            public void onBandGsrChanged(BandGsrEvent bandGsrEvent) {
                Log.d("Thing", "GSR: " + bandGsrEvent.getResistance());
                gsrReadings.add(bandGsrEvent.getResistance());

                //We want to skip the first five values as usually those are noise from locking in heart rate
                if (!baselined && gsrReadings.size() > 5) {
                    //We set a baseline once we have 10 true GSR values which is approximately 75 seconds into the game (5 seconds per GSR reading)
                    readingIdx = (readingIdx + 1) % fearBuffer.length;

                    fearBuffer[readingIdx] = new Reading(heartReadings.get(heartReadings.size() - 1),
                                                            bandGsrEvent.getResistance(),
                                                            rrReadings.get(rrReadings.size() - 1));
                    if (readingIdx == 9) {
                        baselined = true;
                    }

                }
            }

        };

        final BandHeartRateEventListener heartRateListener = new BandHeartRateEventListener() {
            @Override
            public void onBandHeartRateChanged(BandHeartRateEvent bandHeartRateEvent) {
                //Add heart rate listeners and assign variables for future computing
                Log.d("Thing", "Heart Rate: " + bandHeartRateEvent.getHeartRate());
                heartReadings.add(bandHeartRateEvent.getHeartRate());
                lastHeartRate = bandHeartRateEvent.getHeartRate();
            }


        };

        final BandRRIntervalEventListener rrListener = new BandRRIntervalEventListener() {
            @Override
            public void onBandRRIntervalChanged(BandRRIntervalEvent bandRRIntervalEvent) {
                Log.d("Thing", "RR Interval: " + bandRRIntervalEvent.getInterval());
                rrReadings.add(bandRRIntervalEvent.getInterval());

                String heart, gsr, rr;

                int hReading = 0, gReading = 0;
                double rReading;

                if (heartReadings.size() == 0) {
                    heart = "Heart: 0 | ";
                }
                else {
                    heart = "Heart: " + heartReadings.get(heartReadings.size() - 1 ) + " | ";
                    hReading = heartReadings.get(heartReadings.size() - 1 );
                }
                if (gsrReadings.size() == 0) {
                    gsr = "GSR: 0 | ";
                }
                else {
                    gsr = "GSR: " + gsrReadings.get(gsrReadings.size() - 1) + " | ";
                    gReading = gsrReadings.get(gsrReadings.size() - 1);
                }

                //Data collected to be saved and logged
                rr = "RR: " + bandRRIntervalEvent.getInterval();
                rReading = bandRRIntervalEvent.getInterval();

                /* Analyze Scare Data */

                //Once heart rate settles, find baseline heart rate and gsr by taking the first five values' average post-noise for heart rate and gsr
                if (baselined && baselineHeart == 0 && baselineGsr == 0) {
                    for (int i = 0; i < fearBuffer.length/2; i++) {
                        baselineHeart += fearBuffer[i].heartRate;
                        baselineGsr += fearBuffer[i].gsrRate;
                    }

                    baselineHeart /= 5;
                    baselineGsr /= 5;
                }
                else if (baselined && gReading != 0 && hReading != 0) {

                    //Compute averages for GSR and Heartrate after getting a baseline
                    averageGsr = getGsrAverage();
                    averageHeart = getHeartAverage();

                    //After taking the buffer, we continue to average the buffer to be used
                    int prevIdx = readingIdx;
                    readingIdx = (readingIdx + 1) % fearBuffer.length;
                    fearBuffer[readingIdx] = new Reading(hReading, gReading, rReading);

                    //We want to check heartbeat first as that will tell us how scared they may be feeling
                    if (hReading > baselineHeart + 35) {
                        fear = TERRIFIED;
                    }
                    if (hReading > baselineHeart + 25) {
                        fear = VERY_SCARED;
                    }
                    if (hReading > baselineHeart + 15) {
                        fear = SCARED;
                    }

                    //GSR is a more reliable trend to follow based on these values for fear/anxiety if heartbeat fails
                    if (gReading < 0.7 * baselineGsr) {
                        fear = ANXIOUS;
                    }

                    if (gReading < 0.6 * baselineGsr) {
                        fear = SCARED;
                    }

                    if (gReading < 0.4 * baselineGsr) {
                        fear = VERY_SCARED;
                    }

                    //Heart rate above average indicates rising fear
                    if (hReading - averageHeart > 2) {
                        if (fear == CALM) {
                            fear = ANXIOUS;
                        }
                        else if (fear == ANXIOUS) {
                            fear = SCARED;
                        }

                        //Checks for drops in GSR
                        if (fearBuffer[prevIdx].gsrRate - gReading > fearBuffer[prevIdx].gsrRate * 0.5) {
                            fear = TERRIFIED;
                        }
                        else if (fearBuffer[prevIdx].gsrRate - gReading > fearBuffer[prevIdx].gsrRate * 0.35) {
                            if (fear != TERRIFIED) {
                                fear++;
                            }
                        }
                        else if (fearBuffer[prevIdx].gsrRate - gReading > fearBuffer[prevIdx].gsrRate * 0.1) {
                            if (fear != VERY_SCARED) {
                                fear++;
                            }
                        }

                    }
                    else if (hReading - averageHeart < -1) {
                        //If heart rate decreases and there is a slight increase in GSR, the person is calming
                        if (baselineGsr * 0.5 > gReading) {
                            if (fear != ANXIOUS) {
                                fear--;
                            }
                        }
                        else if (fear != CALM) {
                            fear--;
                        }
                    }

                    //JUMP SCARE DETECTION
                    //If the gsr is 15% less than the baseline, check for significant changes (such as a 10% drop of the current gsr). Heart rate won't change that much
                    //Significant drops in gsr also can indicate jump scares or terror
                    if (gReading < 0.15 * baselineGsr || ((fearBuffer[readingIdx].gsrRate - gReading) > 0.1 * fearBuffer[readingIdx].gsrRate)) {
                        fear = TERRIFIED;
                    }

                }

                Log.d("Thing", fears[fear]);

                /* End of Analysis */

                readings.add(heart + gsr + rr);
                lastRr = bandRRIntervalEvent.getInterval();
                Log.d("Thing", "Reading: " + heart + gsr + rr);
            }
        };

        Log.d("Thing", "Checking consent");

        // register the listener
        if(bandClient.getSensorManager().getCurrentHeartRateConsent() !=
                UserConsent.GRANTED) {
            // user hasn’t consented, request consent
            // the calling class is an Activity and implements
            // HeartRateConsentListener
            Log.d("Thing", "Not consented");
            bandClient.getSensorManager().requestHeartRateConsent(getActivity(), new HeartRateConsentListener() {
                @Override
                public void userAccepted(boolean b) {
                    startHeartRate(heartRateListener);
                    startGSR(gsrListener);
                    startRR(rrListener);
                }
            });
        }
        else {
            Log.d("Thing", "Consented");
            startHeartRate(heartRateListener);
            startGSR(gsrListener);
            startRR(rrListener);
        }

    }

    //Iterate through our buffer to compute average
    public double getGsrAverage() {
        int sum = 0;
        for (int i = 0; i < fearBuffer.length; i++) {
            sum += fearBuffer[i].gsrRate;
        }

        return sum / fearBuffer.length;
    }

    //Iterate through our buffer to compute average
    public double getHeartAverage() {
        int sum = 0;
        for (int i = 0; i < fearBuffer.length; i++) {
            sum += fearBuffer[i].heartRate;
        }

        return sum / fearBuffer.length;
    }


    public void startHeartRate(BandHeartRateEventListener listener)  {
        try {
            bandClient.getSensorManager().registerHeartRateEventListener(listener);
        }
        catch (BandException e) {
            Log.d("Thing", "Heart rate band exception: " + e);
        }

    }

    public void startGSR (BandGsrEventListener listener) {
        try {
            bandClient.getSensorManager().registerGsrEventListener(listener);
        }
        catch (BandException e) {
            Log.d("Thing", "GSR band exception: " + e);
        }
    }

    public void startRR (BandRRIntervalEventListener listener) {
        try {
            bandClient.getSensorManager().registerRRIntervalEventListener(listener);
        }
        catch (BandException e) {
            Log.d("Thing", "RR band exception: " + e);
        }
    }

    //This is primarily used for our data collection for classifying and finding trends
    public void writeFile() {

        Activity activity = getActivity();

        String filename = "myfile.txt";

        File file = new File(activity.getFilesDir(), filename);

        String gsr = "GSR: \n";
        String heart = "\n HEARTBEAT: \n";
        String rr = "\n RR: \n";

        String allReadings = "Readings: ";

        for (int i = 0; i < readings.size(); i++) {
            allReadings += "\n " + readings.get(i);
        }

        for (int i = 0; i < gsrReadings.size(); i++) {
            if (i == gsrReadings.size() - 1 ) {
                gsr += gsrReadings.get(i);
            }
            else {
                gsr += gsrReadings.get(i) + ",";
            }
        }

        for (int i = 0; i < heartReadings.size(); i++) {
            if (i == heartReadings.size() - 1 ) {
                heart += heartReadings.get(i);
            }
            else {
                heart += heartReadings.get(i) + ",";
            }
        }

        for (int i = 0 ; i < rrReadings.size(); i ++) {
            if (i == rrReadings.size() - 1 ) {
                rr += rrReadings.get(i);
            }
            else {
                rr += rrReadings.get(i) + ",";
            }
        }


        try {

            File myFile = new File("/sdcard/mysdfile.txt");
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            OutputStreamWriter myOutWriter =
                    new OutputStreamWriter(fOut);
            myOutWriter.append(gsr);
            myOutWriter.append(heart);
            myOutWriter.append(rr);
            myOutWriter.append(allReadings);
            myOutWriter.close();
            fOut.close();

            Log.d("Thing", heart);
            Log.d("Thing", activity.getFilesDir().toString());

            Toast.makeText(activity.getBaseContext(), "File saved successfully!",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Async Task required for connection to Microsoft Band 2
    private class ConnectBandTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... nothing) {
            pendingResult = bandClient.connect();
            try {
                ConnectionState state = pendingResult.await();
                if(state == ConnectionState.CONNECTED) {
                    // do work on success
                    Log.d("Thing", "Success");
                } else {
                    // do work on failure
                    Log.d("Thing", "Unsuccessful");
                }
            } catch(InterruptedException ex) {
                // handle InterruptedException
            } catch(BandException ex) {
                // handle BandException
            }
            return null;
        }

        protected void onProgressUpdate() {

        }

        protected void onPostExecute() {

        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // we're going to simulate real time with thread that append data to the graph
        new Thread(new Runnable() {

            @Override
            public void run() {
                // we add 100 new entries
                for (int i = 0; i < 100; i++) {
                    if(getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                addEntry(); //This calls the method that will append the data for graphs.
                            }
                        });

                        // sleep to slow down the add of entries
                        try {
                            Thread.sleep(600);
                        } catch (InterruptedException e) {
                            // manage error ...
                        }
                    }
                }
            }
        }).start();

        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }


    }

    // Appends the data of heart rate and RR intervals to the graph. New data point added every time this method is called.
    private void addEntry() {
       if (heartReadings.size() != 0) {
           series1.appendData(new DataPoint(lastX++, lastHeartRate), true, 9999);
      }
        //The RR readings are scaled to a multiple of 100.
        if (rrReadings.size() != 0){
           series2.appendData(new DataPoint(lastX++, lastRr * 100), true, 9999);

       }

        //We place this here instead of on a listener because we don't want the listener to also run on the UI thread
        fearLevel.setText(fears[fear]);
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video: {
                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    startRecordingVideo();
                }
                break;
            }

        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    //Method that closes the camera
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFile(activity).getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    //Writes video file to device
    private File getVideoFile(Context context) {
        return new File(context.getExternalFilesDir(null), "video.mp4");
    }

    private void startRecordingVideo() {
        try {
            // UI
            mButtonVideo.setText(R.string.stop);
            mIsRecordingVideo = true;

            // Start recording
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mButtonVideo.setText(R.string.record);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Activity activity = getActivity();
        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + getVideoFile(activity), //Saves video file when recording ends
                    Toast.LENGTH_SHORT).show();
        }
        startPreview();
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }

    //This method takes a screenshot of the device. Will be used to save graph data
    private void takeScreenshot() {
        Date now = new Date();
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);

        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpg";

            // create bitmap screen capture
            View v1 = getActivity().getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
        }
    }

}
