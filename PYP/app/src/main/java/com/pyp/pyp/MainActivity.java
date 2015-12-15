package com.pyp.pyp;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends Activity {

    BandInfo[] pairedBands;
    BandClient bandClient;

    TextView feedback;
    Button attach, read_button, write_button;

    Boolean attached;

    BandPendingResult<ConnectionState> pendingResult;

    ArrayList<Integer> gsrReadings, heartReadings;
    ArrayList<Double> rrReadings;
    ArrayList<String> readings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        feedback = (TextView) findViewById(R.id.feedback);

        gsrReadings = new ArrayList<Integer>();
        heartReadings = new ArrayList<Integer>();
        rrReadings = new ArrayList<Double>();
        readings = new ArrayList<String>();

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

        write_button = (Button) findViewById(R.id.write_button);
        write_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("Thing", "Writing to file");
                writeFile();
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
                gsrReadings.add(bandGsrEvent.getResistance());
            }

        };

        final BandHeartRateEventListener heartRateListener = new BandHeartRateEventListener() {
            @Override
            public void onBandHeartRateChanged(BandHeartRateEvent bandHeartRateEvent) {
                //feedback.setText("Heart Rate: " + bandHeartRateEvent.getHeartRate());
                Log.d("Thing", "Heart Rate: " + bandHeartRateEvent.getHeartRate());
                heartReadings.add(bandHeartRateEvent.getHeartRate());
            }

        };

        final BandRRIntervalEventListener rrListener = new BandRRIntervalEventListener() {
            @Override
            public void onBandRRIntervalChanged(BandRRIntervalEvent bandRRIntervalEvent) {
                Log.d("Thing", "RR Interval: " + bandRRIntervalEvent.getInterval());
                rrReadings.add(bandRRIntervalEvent.getInterval());

                String heart, gsr, rr;

                if (heartReadings.size() == 0) {
                    heart = "Heart: 0 | ";
                }
                else {
                    heart = "Heart: " + heartReadings.get(heartReadings.size() - 1 ) + " | ";
                }
                if (gsrReadings.size() == 0) {
                    gsr = "GSR: 0 | ";
                }
                else {
                    gsr = "GSR: " + gsrReadings.get(gsrReadings.size() - 1) + " | ";
                }

                rr = "RR: " + bandRRIntervalEvent.getInterval();

                readings.add(heart +  gsr + rr);
                Log.d("Thing", "Reading: " + heart + gsr + rr);

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

    public void startRR (BandRRIntervalEventListener listener) {
        try {
            bandClient.getSensorManager().registerRRIntervalEventListener(listener);
        }
        catch (BandException e) {
            Log.d("Thing", "RR band exception: " + e);
        }
    }

    public void writeFile() {
        String filename = "myfile.txt";

        File file = new File(this.getFilesDir(), filename);

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
/*
            FileOutputStream outputStream = openFileOutput(filename, Context.MODE_PRIVATE);

            OutputStreamWriter outputWriter=new OutputStreamWriter(outputStream);

            outputWriter.write(gsr);
            outputWriter.write(heart);
            outputWriter.write(rr);

            outputWriter.close();
            outputStream.close();
*/
            Log.d("Thing", heart);
            Log.d("Thing", this.getFilesDir().toString());

            Toast.makeText(getBaseContext(), "File saved successfully!",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
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
