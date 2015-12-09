package com.pyp.pyp;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import com.microsoft.band.sensors.HeartRateConsentListener;

public class MainActivity extends AppCompatActivity {

    BandInfo[] pairedBands;
    BandClient bandClient;

    TextView feedback;
    Button attach, read_button;

    Boolean attached;

    BandPendingResult<ConnectionState> pendingResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        feedback = (TextView) findViewById(R.id.feedback);

        attached = false;

        attach = (Button) findViewById(R.id.band_attach);
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachBand();
                attached = true;
            }
        });

        read_button = (Button) findViewById(R.id.read_button);
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
    }

    public boolean attachBand() {
        pairedBands = BandClientManager.getInstance().getPairedBands();
        bandClient = BandClientManager.getInstance().create(this, pairedBands[0]);

        feedback.setText("Band Attached");

        //Need to place this in an Async Task
        new ConnectBandTask().execute();
        return true;
    }

    public void readData() {
        final BandGsrEventListener gsrListener = new BandGsrEventListener() {
            @Override
            public void onBandGsrChanged(BandGsrEvent bandGsrEvent) {
                //feedback.setText("GSR: " + bandGsrEvent.getResistance());
                Log.d("Thing", "GSR: " + bandGsrEvent.getResistance());
            }
        };

        final BandHeartRateEventListener heartRateListener = new BandHeartRateEventListener() {
            @Override
            public void onBandHeartRateChanged(BandHeartRateEvent bandHeartRateEvent) {
                //feedback.setText("Heart Rate: " + bandHeartRateEvent.getHeartRate());
                Log.d("Thing", "Heart Rate: " + bandHeartRateEvent.getHeartRate());
            }

        };

        Log.d("Thing", "Checking consent");

//        try {
        // register the listener
            if(bandClient.getSensorManager().getCurrentHeartRateConsent() !=
                    UserConsent.GRANTED) {
                // user hasn’t consented, request consent
                // the calling class is an Activity and implements
                // HeartRateConsentListener
                Log.d("Thing", "Not consented");
                bandClient.getSensorManager().requestHeartRateConsent(this, new HeartRateConsentListener() {
                    @Override
                    public void userAccepted(boolean b) {
                        startHeartRate(heartRateListener);
                        startGSR(gsrListener);
                    }
                });
            }
        else {
                Log.d("Thing", "Consented");
                startHeartRate(heartRateListener);
                startGSR(gsrListener);
            }


//            bandClient.getSensorManager().registerGsrEventListener(
//                    gsrListener);
//        } catch(BandException ex) {
//        // handle BandException
//            Log.d("Thing", "Band exception occured: " + ex);
//        }

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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

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


}
