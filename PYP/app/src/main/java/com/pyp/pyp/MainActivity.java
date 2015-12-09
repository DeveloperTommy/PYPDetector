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

public class MainActivity extends AppCompatActivity {

    BandInfo[] pairedBands;
    BandClient bandClient;

    TextView feedback;
    Button attach;

    BandPendingResult<ConnectionState> pendingResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        attach = (Button) findViewById(R.id.band_attach);
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachBand();
            }
        });
    }

    public boolean attachBand() {
        pairedBands = BandClientManager.getInstance().getPairedBands();
        bandClient = BandClientManager.getInstance().create(this, pairedBands[0]);

        feedback = (TextView) findViewById(R.id.feedback);
        feedback.setText("Band Attached");

        //Need to place this in an Async Task
        new ConnectBandTask().execute();
        return true;
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
