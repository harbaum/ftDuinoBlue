package org.harbaum.ftduinoblue;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ScanActivity extends AppCompatActivity implements DeviceViewAdapter.ItemClickListener {
    private final static String TAG = ScanActivity.class.getSimpleName();

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int ENABLE_BLUETOOTH = 2;

    private static DeviceViewAdapter adapter;
    private Hm10Service hm10Service = null;

    private final BroadcastReceiver mHm10ServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "Received intent: "+intent);

            switch(Objects.requireNonNull(intent.getAction())) {
                case Hm10Service.ACTION_NOTIFY_NO_BLE:
                    sendRequest(Hm10Service.ACTION_SETUP_DEMO);

                    AlertDialog alertDialog = new AlertDialog.Builder(ScanActivity.this).create();
                    alertDialog.setTitle(getString(R.string.ble_not_supported_title));
                    alertDialog.setMessage(getString(R.string.ble_not_supported));
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                    break;

                case Hm10Service.ACTION_REQUEST_BLUETOOTH:
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
                    break;

                case Hm10Service.ACTION_REQUEST_LOCATION:
                    getLocationAccess();
                    break;

                case Hm10Service.ACTION_NOTIFY_DEVICE:
                    // hide "no device found" text once the first device has been found
                    if(adapter.getItemCount() == 0)
                        findViewById(R.id.initLayout).setVisibility(View.GONE);

                    String name = intent.getStringExtra("name");
                    String addr = intent.getStringExtra("addr");
                    Log.d(TAG, "GOT DEVICE " + name + " " + addr);
                    adapter.append(name, addr);
                    break;

                case Hm10Service.ACTION_DISCONNECTED:
                    adapter.setBusy(-1);
                    findViewById(R.id.scanningBar).setVisibility(View.VISIBLE);
                    sendRequest(Hm10Service.ACTION_START_SCAN);
                    break;

                case Hm10Service.ACTION_NOTIFY_DESTROYED:
                    Log.d(TAG,"hm10 service is gone");
                    // the hm10 service has sent a notification that it has been
                    // destroyed. Assume any connection to be gone.
                    adapter.setBusy(-1);
                    adapter.clearList();  // forget everything about devices already detected
                    break;

                case Hm10Service.ACTION_NOTIFY_INITIALIZED:
                    // only start scanner if it's not already in progress
                    if(findViewById(R.id.scanningBar).getVisibility() != View.VISIBLE) {
                        findViewById(R.id.scanningBar).setVisibility(View.VISIBLE);
                        sendRequest(Hm10Service.ACTION_START_SCAN);
                    }
                    break;

                case Hm10Service.ACTION_NOTIFY_INFORMATION:
                    String title = intent.getStringExtra("title");
                    String message = intent.getStringExtra("message");

                    // TODO: clear current list of known devices? xyz
                    AlertDialog alertFailDialog = new AlertDialog.Builder(ScanActivity.this).create();
                    alertFailDialog.setTitle(title);
                    if(message != null) alertFailDialog.setMessage(message);
                    alertFailDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertFailDialog.show();
                    break;

                case Hm10Service.ACTION_NOTIFY_LAYOUT:
                    String layout = intent.getStringExtra("layout");
                    boolean demo = intent.getBooleanExtra("demo", false);
                    Intent launchIntent = new Intent(ScanActivity.this, ControlActivity.class);
                    launchIntent.putExtra("layout", layout);  // the layout as XML string
                    launchIntent.putExtra("demo", demo);      // boolean whether this is a builtin demo layout
                    startActivity(launchIntent);
                    break;

                default:
                    Log.w(TAG, "Unexpected intent: " + intent);
                    break;
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.about) {
            AboutWindow();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");

        getMenuInflater().inflate(R.menu.scan_menu, menu);

        return true;
    }

    public void AboutWindow() {
        AlertDialog.Builder aboutWindow = new AlertDialog.Builder(this);//creates a new instance of a dialog box

        final View customLayout = getLayoutInflater().inflate(R.layout.about_view, null);
        aboutWindow.setView(customLayout);

        TextView tx = customLayout.findViewById(R.id.about_text_view);
        tx.setText(String.format(getString(R.string.project_description), getString(R.string.app_name), BuildConfig.VERSION_NAME));
        tx.setAutoLinkMask(RESULT_OK);

        //again to enable any website urls or email addresses to be clickable links
        tx.setMovementMethod(LinkMovementMethod.getInstance());
        Linkify.addLinks(tx, Linkify.WEB_URLS);

        aboutWindow.setIcon(R.mipmap.ic_launcher);
        aboutWindow.setTitle(R.string.about_title);

        aboutWindow.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();//when the OK button is clicked, dismiss() is called to close it
            }
        });
        aboutWindow.show();
    }

    void sendRequest(String s, Map<String, String> values) {
        Log.d(TAG, "sendRequest("+s+")");
        Intent intent = new Intent();
        intent.setAction(s);
        if(values != null)
            for(String key: values.keySet())
                intent.putExtra(key, values.get(key));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void sendRequest(String s) {
        sendRequest(s, null);
    }

    // this is called whenever the Activity is destroyed. This even happens if the user
    // rotates the device ...
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");

        // tell service that this app is potentially gone
        sendRequest(Hm10Service.ACTION_NOTIFY_GONE);
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHm10ServiceReceiver);
    }

    // callback after "enable bluetooth?" dialog.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 4711) {
            Log.w(TAG, "Result for Control Activity: " + resultCode);
        }

        if (requestCode == ENABLE_BLUETOOTH)
            if(resultCode == Activity.RESULT_OK) {
                sendRequest(Hm10Service.ACTION_BLUETOOTH_ENABLED);
                getLocationAccess();
            } else {
                Log.d(TAG, "Bluetooth enabling rejected");
                finish();
            }
    }

    // callback after "application needs location access" dialog.
    @Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_FINE_LOCATION)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendRequest(Hm10Service.ACTION_LOCATION_OK);
            } else {
                Log.d(TAG, "Location access rejected");
                finish();
            }
    }

    void getLocationAccess() {
        Log.w(TAG, "request location access");

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (ScanActivity.this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                // permission already granted? Give ok immediately
                sendRequest(Hm10Service.ACTION_LOCATION_OK);
            } else {
                // request location permission from user
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_FINE_LOCATION);
            }
        } else
            sendRequest(Hm10Service.ACTION_LOCATION_OK);
    }

    @Override
    public void onItemClick(View view, final int position) {
        // make sure we don't start another connection while one is in progress
        if(!adapter.isBusy()) {
            // check if address starts with "de:30:c0:de" as this indicates that we run a demo
            if(!adapter.getDevice(position).startsWith("de:30:c0:de"))
                Toast.makeText(this, "Connecting " + adapter.getDevice(position), Toast.LENGTH_SHORT).show();

            sendRequest(Hm10Service.ACTION_STOP_SCAN);
            findViewById(R.id.scanningBar).setVisibility(View.GONE);

            adapter.setBusy(position);

            sendRequest(Hm10Service.ACTION_CONNECT, new HashMap<String, String>()
                {{ put("addr", adapter.getDevice(position)); }});
        } else
            Log.d(TAG, "Already connecting!");
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Hm10Service.ACTION_NOTIFY_NO_BLE);
        filter.addAction(Hm10Service.ACTION_REQUEST_BLUETOOTH);
        filter.addAction(Hm10Service.ACTION_REQUEST_LOCATION);
        filter.addAction(Hm10Service.ACTION_NOTIFY_INITIALIZED);
        filter.addAction(Hm10Service.ACTION_NOTIFY_DEVICE);
        filter.addAction(Hm10Service.ACTION_NOTIFY_INFORMATION);
        filter.addAction(Hm10Service.ACTION_DISCONNECTED);
        filter.addAction(Hm10Service.ACTION_NOTIFY_LAYOUT);
        filter.addAction(Hm10Service.ACTION_NOTIFY_DESTROYED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mHm10ServiceReceiver, filter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");

        if(hm10Service == null) {
            Log.d(TAG, "Starting service");
            Intent intent = new Intent(this, Hm10Service.class);
            startService(intent);
        } else
            Log.d(TAG, "Service already running");
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        // save list of devices
        savedInstanceState.putParcelableArrayList("foundDevices", adapter.getDeviceList() );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        setFilters();  // Start listening to broadcast notifications e.g. from Hm10Service

        setContentView(R.layout.activity_scan);

        // get list of already detected devices if present
        ArrayList<DeviceViewAdapter.DeviceEntry> savedDevices = null;
        if(savedInstanceState != null) {
            savedDevices = savedInstanceState.getParcelableArrayList("foundDevices");
            if(savedDevices != null && savedDevices.size() > 0)
                findViewById(R.id.initLayout).setVisibility(View.GONE);
        }

        // setup the demo button
        findViewById(R.id.demo_button).setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) { sendRequest(Hm10Service.ACTION_SETUP_DEMO); }
         });

        // set up the RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceViewAdapter(this, savedDevices);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);

        // add line between rows
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
            DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }
}