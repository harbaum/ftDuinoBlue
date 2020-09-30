package org.harbaum.ftduinoblue;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

// A service that interacts with the BLE device via the Android BLE API.
public class Hm10Service extends Service {
    private final static String TAG = Hm10Service.class.getSimpleName();
    private final static String MIN_VERSION = "1.0.0";

    private static class DemoSetup {
        public int id;
        public String name, address;

        public DemoSetup(int id, String name, String address) {
            this.id = id; this.name = name; this.address = address;
        }
    }

    // list of demo setups that appear if the user clicks the "run demo" button on the
    // scan page
    private static final DemoSetup[] demoSetups = {
            new DemoSetup(R.raw.demo_layout_1, "Demo #1: About ftDuinoBlue", "de:30:c0:de:00:01"),
            new DemoSetup(R.raw.demo_layout_2, "Demo #2: Buttons", "de:30:c0:de:00:02"),
            new DemoSetup(R.raw.demo_layout_3, "Demo #3: Joysticks", "de:30:c0:de:00:03"),
            new DemoSetup(R.raw.demo_layout_4, "Demo #4: Switches", "de:30:c0:de:00:04"),
            new DemoSetup(R.raw.demo_layout_5, "Demo #5: Landscape", "de:30:c0:de:00:05"),
            new DemoSetup(R.raw.demo_layout_6, "Demo #6: Slider", "de:30:c0:de:00:06")
    };

    public final static String ACTION_NOTIFY_GONE = "org.harbaum.ftDuinoBlue.GONE";

    public final static String ACTION_NOTIFY_NO_BLE = "org.harbaum.ftDuinoBlue.NO_BLE";
    public final static String ACTION_REQUEST_BLUETOOTH = "org.harbaum.ftDuinoBlue.REQUEST_BLUETOOTH";
    public final static String ACTION_REQUEST_LOCATION = "org.harbaum.ftDuinoBlue.REQUEST_LOCATION";
    public final static String ACTION_LOCATION_OK = "org.harbaum.ftDuinoBlue.REQUEST_LOCATION_OK";
    public final static String ACTION_NOTIFY_INITIALIZED = "org.harbaum.ftDuinoBlue.INIT";
    public final static String ACTION_NOTIFY_DESTROYED = "org.harbaum.ftDuinoBlue.DESTROY";

    public final static String ACTION_START_SCAN = "org.harbaum.ftDuinoBlue.REQUEST_START_SCAN";
    public final static String ACTION_STOP_SCAN = "org.harbaum.ftDuinoBlue.REQUEST_STOP_SCAN";
    public final static String ACTION_NOTIFY_DEVICE = "org.harbaum.ftDuinoBlue.DEVICE";
    public final static String ACTION_SETUP_DEMO = "org.harbaum.ftDuinoBlue.DEMO";

    public final static String ACTION_CONNECT = "org.harbaum.ftDuinoBlue.REQUEST_CONNECT";
    public final static String ACTION_DISCONNECT = "org.harbaum.ftDuinoBlue.REQUEST_DISCONNECT";
    public final static String ACTION_BLUETOOTH_ENABLED = "org.harbaum.ftDuinoBlue.BT_ENABLED";
    public final static String ACTION_DISCONNECTED = "org.harbaum.ftDuinoBlue.DISCONNECTED";
    public final static String ACTION_NOTIFY_INFORMATION = "org.harbaum.ftDuinoBlue.INFO";
    public final static String ACTION_NOTIFY_LAYOUT = "org.harbaum.ftDuinoBlue.NOTIFY_LAYOUT";
    public final static String ACTION_SEND_MESSAGE = "org.harbaum.ftDuinoBlue.SEND_MESSAGE";
    public final static String ACTION_NOTIFY_MESSAGE = "org.harbaum.ftDuinoBlue.NOTIFY_MESSAGE";

    private List<BluetoothDevice> mDevices;

    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattCharacteristic mUartCharacteristic = null;

    private boolean mDemoEnabled = false;

    private int mState = STATE_NONE;
    private static final int STATE_NONE = 0;
    private static final int STATE_INITIALIZING = 1;
    private static final int STATE_IDLE = 2;
    private static final int STATE_SCANNING = 3;
    private static final int STATE_CONNECTING = 4;
    private static final int STATE_CONNECTED = 5;
    private static final int STATE_DISCONNECTING = 6;

    private static String mPendingRequest = null;
    private static int mRetryCounter = 0;
    private static byte[] mIncomingBytes = new byte[]{};
    private static Handler mHandler = new Handler();
    private static Runnable mTimeout = null;
    private static Runnable mSelfDestructTimeout = null;
    private static byte[] mTransmitBuffer = null;
    private static Boolean mTransmitInProgress = false;
    private static Toast mCurrentToast = null;

    private final static UUID UUID_UART_SERVICE =
            UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    private final static UUID UUID_UART_CHRACTERISTIC =
            UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
    private final static UUID UUID_CCC_DESCRIPTOR =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final static int MAX_MSG_LEN = 20;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private final BroadcastReceiver mActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case ACTION_START_SCAN:
                    Log.d(TAG, "START SCAN");

                    // send demo devices if demo is enabled
                    if(mDemoEnabled) setupDemoDevices();

                    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                        // add devices that already are in current list
                        for(BluetoothDevice dev: mDevices)
                            sendDevice(dev);

                        startScanning();
                    }
                    break;

                case ACTION_STOP_SCAN:
                    Log.d(TAG, "STOP SCAN");
                    stopScanning();
                    break;

                case ACTION_CONNECT:
                    String addr = intent.getStringExtra("addr");
                    Log.d(TAG, "CONNECT: " + addr);
                    connect(addr);
                    break;

                case ACTION_DISCONNECT:
                    Log.d(TAG, "DISCONNECT");
                    // there should be a gatt instance. Otherwise it may have been a demo
                    // device. So send "disconnected" in that case
                    if(mBluetoothGatt == null) {
                        Log.d(TAG, "No GATT connection: Demo disconnect");
                        sendStatus(ACTION_DISCONNECTED);
                    } else
                        disconnect();
                    break;

                case ACTION_BLUETOOTH_ENABLED:
                    // we actually don't do anything here since the MainActivity will
                    // next request location access by itself ...
                    break;

                case ACTION_LOCATION_OK:
                    // location services are ok. Give Activity go for
                    // further action
                    mState = STATE_IDLE;
                    sendStatus(ACTION_NOTIFY_INITIALIZED);
                    break;

                case ACTION_SEND_MESSAGE:
                    String message = intent.getStringExtra("message");
                    Log.d(TAG, "SEND MESSAGE: " + message);
                    send(message);
                    break;

                case ACTION_SETUP_DEMO:
                    Log.d(TAG, "SETUP DEMO");
                    mDemoEnabled = true;
                    setupDemoDevices();
                    break;

                case ACTION_NOTIFY_GONE:
                    Log.d(TAG, "MAIN GONE");

                    // the main app is potentially gone -> start 5 seconds self destruct timer
                    Log.d(TAG, "installing self destruct timer");
                    mHandler.postDelayed(mSelfDestructTimeout = new Runnable() {
                        @Override
                        public void run() {
                            Log.w(TAG, "SelfDestructTimeout fired!");
                            stopSelf();
                        }
                    }, 2000);
                    break;

                default:
                    Log.w(TAG, "Unexpected intent: " + intent);
                    break;
            }
        }
    };

    private
    void setupDemoDevices() {
        for(DemoSetup setup: demoSetups)  {
           sendDevice(setup.name, setup.address);
        }
    }

    private
    void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START_SCAN);
        filter.addAction(ACTION_STOP_SCAN);
        filter.addAction(ACTION_BLUETOOTH_ENABLED);
        filter.addAction(ACTION_LOCATION_OK);
        filter.addAction(ACTION_CONNECT);
        filter.addAction(ACTION_DISCONNECT);
        filter.addAction(ACTION_SEND_MESSAGE);
        filter.addAction(ACTION_SETUP_DEMO);
        filter.addAction(ACTION_NOTIFY_GONE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mActivityReceiver, filter);
    }

    @Override
    public void onCreate() {
        // The service is being created
        Log.w(TAG, "onCreate");

        this.mDevices = new ArrayList<>();
        this.mState = STATE_INITIALIZING;

        setFilter();
        setupBLE();
    }

    void sendStatus(String s, Map<String, String> values) {
        Log.d(TAG, "sendStatus("+s+")");
        Intent intent = new Intent();
        intent.setAction(s);
        if(values != null)
            for(String key: values.keySet())
                intent.putExtra(key, values.get(key));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void sendStatus(String s) {
        sendStatus(s, null);
    }

    private void sendConFail(String message) {
        Log.d(TAG, "sendConFail(" + message + ")");
        Intent i = new Intent();
        i.setAction(ACTION_NOTIFY_INFORMATION);
        i.putExtra("title", "Connection failed");
        i.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void sendDevice(final String name, final String addr) {
        Log.d(TAG, "sendResult(" + name + "," + addr + ")");
        sendStatus(ACTION_NOTIFY_DEVICE, new HashMap<String, String>()
            {{ put( "name", name ); put("addr", addr); }});
    }

    private void sendDevice(final BluetoothDevice dev) {
        Log.d(TAG, "sendResult(" + dev.getName() + "," + dev.getAddress() + ")");
        sendStatus(ACTION_NOTIFY_DEVICE, new HashMap<String, String>()
        {{ put( "name", dev.getName() ); put("addr", dev.getAddress()); }});
    }

    private void sendMessage(final String cmd, final String data) {
        Log.d(TAG, "sendMessage(" + cmd + "," + data + ")");
        sendStatus(ACTION_NOTIFY_MESSAGE, new HashMap<String, String>()
            {{ put( "cmd", cmd ); put("data", data); }});
    }

    private void sendLayout(String layout, boolean isDemo) {
        Log.d(TAG, "sendLayout()");
        Intent i = new Intent();
        i.setAction(ACTION_NOTIFY_LAYOUT);
        i.putExtra("layout", layout);
        i.putExtra("demo", isDemo);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        Log.w(TAG, "onStartCommand: " + intent);

        if(mSelfDestructTimeout != null) {
            Log.d(TAG, "cancelling self destruction");
            mHandler.removeCallbacks(mSelfDestructTimeout);
        }

        // it seems the service has left initialization already. So tell this
        // the caller immediately. If first initialization was fast then this may
        // be sent twice (once be the init process started in onCreate and one here).
        if(mState > STATE_INITIALIZING)
            sendStatus(ACTION_NOTIFY_INITIALIZED);

        return Service.START_NOT_STICKY;
    }

    void addDevice(BluetoothDevice dev) {
        // check if we have that device already in our list
        for (int i = 0; i < this.mDevices.size(); i++) {
            // check if addresses are equal
            if (this.mDevices.get(i).getAddress().equals(dev.getAddress())) {
                // check if the device got a name and we don't already have one
                if ((this.mDevices.get(i).getName() == null || this.mDevices.get(i).getName().isEmpty()) &&
                        (dev.getName() != null && !dev.getName().isEmpty())) {
                    Log.d(TAG, "Device got a missing name! Updating it");
                    this.mDevices.set(i, dev);
                    // send updated device
                    Log.d(TAG, "updated " + dev);
                    sendDevice(dev);
                }
                return;
            }
        }
        // send new device
        this.mDevices.add(dev);
        Log.d(TAG, "added " + dev);
        sendDevice(dev);
    }

    @SuppressLint("ResourceType")
    public void connect(String addr) {
        mState = STATE_CONNECTING;

        // check if the user tries to use a demo setup
        for(DemoSetup demo: demoSetups) {
            if(demo.address.equals(addr)) {
                Log.d(TAG, "User clicked demo setup: " + addr);
                Resources res = getResources();

                InputStream inputStream = res.openRawResource(demo.id);
                StringBuilder textBuilder = new StringBuilder();
                try (
                    Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1)
                        textBuilder.append((char) c);
                    testParseLayout(textBuilder.toString(), true); // demo layout
                } catch(IOException e) {
                    sendConFail("Loading demo XML failed!");
                }

                return;
            }
        }

        Log.d(TAG, "connect " + addr);
        for (int i = 0; i < this.mDevices.size(); i++) {
            // check if addresses are equal, connect if yes
            if (this.mDevices.get(i).getAddress().equals(addr)) {
                Log.d(TAG, "device found");
                mBluetoothGatt = this.mDevices.get(i).connectGatt(this, false, gattCallback);
                return;
            }
        }

        // this error should actually never happen as it is an internal problem
        sendConFail("Device not found");
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // ignore and device that does not announce any service uuid
            if ((Objects.requireNonNull(result.getScanRecord()).getServiceUuids() == null) ||
                (result.getScanRecord().getServiceUuids().size() == 0)) return;

            // ignore any device that does not advertise the hm10 uart service
            if (!result.getScanRecord().getServiceUuids().get(0).equals(new ParcelUuid(UUID_UART_SERVICE)))
                return;

            addDevice(result.getDevice());
        }
    };

    private void startScanning() {
        mBluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(leScanCallback);
        mState = STATE_SCANNING;
    }

    private void stopScanning() {
        Log.d(TAG, "Stop scanning");
        if (mState == STATE_SCANNING) {
            mBluetoothLeScanner.stopScan(leScanCallback);
            mState = STATE_IDLE;
        } else
            Log.d(TAG, "Wasn't scanning " + mState);
    }

    private
    boolean testParseLayout(String xml, boolean isDemo) {
        LayoutXmlParser parser = new LayoutXmlParser();
        try {
            // dry run parser to make sure we have valid xml to work on
            parser.parse(new ByteArrayInputStream(xml.getBytes()));
            sendLayout(xml, isDemo);
            return true;
        } catch (XmlPullParserException | IOException e) {
            Log.w(TAG, "PARSE EXCEPTION! " + e.toString());
            sendConFail("XML parse failed:\n" + e.toString());
        }
        return false;
    }

    private
    byte[] appendChecksumAndNl(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[b.length + 4];  // plus :XX\n
        int csum = 0;
        for (int i = 0; i < b.length; i++) {
            csum = (csum + (b[i] & 0xff)) & 0xff;  // inner &0xff makes byte unsigned
            result[i] = b[i];
        }

        byte[] e = String.format(":%02X\n", csum).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(e, 0, result, b.length, e.length);

        return result;
    }

    void send(String s) {
        send(s, false);
    }

    void installTimeout() {
        mHandler.postDelayed(mTimeout = new Runnable() {
                @Override
                public void run() {
                    Log.w(TAG, "Timeout retry: "+mRetryCounter);
                    mRetryCounter--;
                    if(mRetryCounter == 0) {
                        sendConFail("Timeout waiting for: " + mPendingRequest);
                        disconnect();
                    } else {
                        // re-send. Since we are this time not asking to
                        // restart the retry timers we have to re-install
                        // ourselves
                        installTimeout();
                        send(mPendingRequest);
                    }
                }
            }, 1000);
    }

    public
    void send(String s, boolean expectReply) {

        // remove any leading and trailing whitespace from message
        s = s.trim();

        // when running demo the uart characteristic is not there and we instead show
        // a "toast" telling the user what would be sent
        if(mUartCharacteristic == null) {
            // remove any previous toast to update refresh
            if(mCurrentToast != null) mCurrentToast.cancel();
            mCurrentToast = Toast.makeText(this, s, Toast.LENGTH_SHORT);
            mCurrentToast.show();
            return;
        }

        // gatt connection may already be gone ...
        if(mBluetoothGatt == null)
            return;

        if (expectReply) {
            mPendingRequest = s;
            mRetryCounter = 5;
            installTimeout();
        }

        // append checksum and newline to this message
        byte[] msg = appendChecksumAndNl(s);

        // check if there's a transmission in progress. Don't send anything
        // then. Instead append the entire message to the transmit buffer
        if(mTransmitInProgress) {
            if(mTransmitBuffer == null)
                mTransmitBuffer = msg;
            else {
                // append cmd2 to current transmit buffer
                byte[] newBuffer = new byte[mTransmitBuffer.length + msg.length];
                System.arraycopy(mTransmitBuffer, 0, newBuffer, 0, mTransmitBuffer.length);
                System.arraycopy(msg, 0, newBuffer, mTransmitBuffer.length, msg.length);

                mTransmitBuffer = newBuffer;
                return;
            }
        }

        // ble does not allow us to send more than MAX_MSG_LEN (20) bytes at once. More needs
        // to be splitted and sent in smaller chunks.
        if(msg.length > MAX_MSG_LEN) {
            // split into two messages
            byte[] cmd1 = new byte[MAX_MSG_LEN];
            byte[] cmd2 = new byte[msg.length - MAX_MSG_LEN];
            System.arraycopy(msg, 0, cmd1, 0, MAX_MSG_LEN);
            System.arraycopy(msg, MAX_MSG_LEN, cmd2, 0, msg.length - MAX_MSG_LEN);

            // first part becomes message to be sent immediately
            msg = cmd1;

            // append second part to transmit buffer if it already exists
            if(mTransmitBuffer == null)
                mTransmitBuffer = cmd2;
            else {
                // append cmd2 to current transmit buffer
                byte[] newBuffer = new byte[mTransmitBuffer.length + cmd2.length];
                System.arraycopy(mTransmitBuffer, 0, newBuffer, 0, mTransmitBuffer.length);
                System.arraycopy(cmd2, 0, newBuffer, mTransmitBuffer.length, cmd2.length);

                mTransmitBuffer = newBuffer;
            }
        }

        // write command
        mTransmitInProgress = true;
        mUartCharacteristic.setWriteType(WRITE_TYPE_NO_RESPONSE);
        mUartCharacteristic.setValue(msg);
        if (!mBluetoothGatt.writeCharacteristic(mUartCharacteristic))
            Log.e(TAG, String.format("ERROR: writeCharacteristic failed for characteristic: %s", mUartCharacteristic.getUuid()));
    }

    public void disconnect() {
        // cancel any pending timeout
        if(mPendingRequest != null) {
            mHandler.removeCallbacks(mTimeout);
            mPendingRequest = null;
        }

        setNotify(mUartCharacteristic, false);

        // and disconnect from the device
        if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mState = STATE_DISCONNECTING;
        }
    }

    public boolean setNotify(BluetoothGattCharacteristic characteristic, final boolean enable) {
        // Check if characteristic is valid
        if (characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring setNotify request");
            return false;
        }

        // Get the CCC Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_CCC_DESCRIPTOR);
        if (descriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
            return false;
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
            return false;
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        if(mBluetoothGatt == null) {
            Log.w(TAG, "No GATT connection");
            return false;
        }

        if (!mBluetoothGatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)) {
            Log.e(TAG, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
        }

        // Then write to descriptor
        descriptor.setValue(finalValue);
        return mBluetoothGatt.writeDescriptor(descriptor);
    }

    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                if (!mBluetoothGatt.discoverServices()) {
                    sendConFail("Unable to start service discovery.");
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    mState = STATE_IDLE;
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // if we weren't connected before, then this is a failed connection
                if ((mState != STATE_DISCONNECTING) && (mState != STATE_CONNECTED)) {
                    Log.e(TAG, "state:" + mState);
                    sendConFail("Unable to establish bluetooth connection.");
                }
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                mState = STATE_IDLE;

                Log.i(TAG, "Disconnected from GATT server.");
                sendStatus(ACTION_DISCONNECTED);
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // check if the UART service is available
            if ((status == BluetoothGatt.GATT_SUCCESS) &&
                (mBluetoothGatt.getService(UUID_UART_SERVICE) != null) &&
                (mBluetoothGatt.getService(UUID_UART_SERVICE).getCharacteristic(UUID_UART_CHRACTERISTIC) != null)) {
                // if we get here, then it's a HM10 and we can talk UART with it
                Log.d(TAG, "UART service available");
                mIncomingBytes = new byte[]{};

                mUartCharacteristic =
                        mBluetoothGatt.getService(UUID_UART_SERVICE).getCharacteristic(UUID_UART_CHRACTERISTIC);

                if (!setNotify(mUartCharacteristic, true)) {
                    mUartCharacteristic = null;
                    sendConFail("Failed to enable notifications.");
                    mBluetoothGatt.disconnect();
                }

                // if we get here, the next will be the write callback for writing the descriptor

            } else {
                if (status != BluetoothGatt.GATT_SUCCESS)
                    sendConFail("No services found.");
                else if (mBluetoothGatt.getService(UUID_UART_SERVICE) == null)
                    sendConFail("UART service not found.");
                else
                    sendConFail("UART characteristic not found.");

                mBluetoothGatt.disconnect();
            }
        }

        private
        void processLine(String line) {
            // everything up to first whitespace is the command name
            String[] parts = line.split(" +", 2);  // split line at first ':'
            String cmd = parts[0];
            String data = (parts.length > 1) ? parts[1].trim() : "";

            if (mPendingRequest != null) {
                if (mPendingRequest.equals(cmd)) {
                    Log.d(TAG, "Got expected reply for " + mPendingRequest + ", canceling timeout");
                    mHandler.removeCallbacks(mTimeout);
                    mPendingRequest = null;
                } else {
                    // send all unknown commands to the activities
                    sendMessage(cmd, data);
                }

                switch (cmd) {
                    case "VERSION":
                        // parse version
                        try {
                            if (new Version(data).compareTo(new Version(MIN_VERSION)) < 0) {
                                sendConFail("Reported version V" + data + " is too old. Required version is V" + MIN_VERSION + " or newer.");
                                disconnect();
                            } else
                                send("LAYOUT", true);
                        } catch (IllegalArgumentException e) {
                            sendConFail("Invalid version detected:\n" + data);
                            disconnect();
                        }
                        break;

                    case "LAYOUT":
                        if (!testParseLayout(data, false))
                            disconnect();
                        break;

                    default:
                        Log.w(TAG, "unexpected pending request: " + mPendingRequest);

                }
            } else
                sendMessage(cmd, data);
        }

        // check if there's a nl/cr in the byte array
        private
        int findLineBreak(byte[] data)  {
            for (int i = 0; i < data.length; ++i)
                if(data[i] == '\n')
                    return i;

            return -1;
        }

        // join to byte arrays
        private
        byte[] join(byte[] a, byte[] b) {
            byte[] combined = new byte[a.length + b.length];
            for (int i = 0; i < combined.length; ++i)
                combined[i] = (i < a.length) ? a[i] : b[i - a.length];
            return combined;
        }

        // parse a byte array containing a line
        private
        void parseLineBytes(byte[] lineBytes) {
            // third last byte should be ':' followed by a hex checksum byte
            if ((lineBytes.length > 3) && (lineBytes[lineBytes.length - 3] == ':')) {
                // calculate checksum
                int calc_sum = 0;
                for (int i = 0; i < lineBytes.length - 3; ++i) {
                    int by = (lineBytes[i] >= 0) ? lineBytes[i] : (256 + lineBytes[i]);
                    if (by >= 32) calc_sum = (calc_sum + by) & 255;
                }

                // last two bytes are hex checksum
                byte[] csumbytes = Arrays.copyOfRange(lineBytes, lineBytes.length - 2, lineBytes.length);
                try {
                    int csum = Integer.valueOf(new String(csumbytes, StandardCharsets.UTF_8), 16);

                    // check if checksum matches
                    if (csum == calc_sum) {
                        // cut checksum from bytearray
                        lineBytes = Arrays.copyOfRange(lineBytes, 0, lineBytes.length - 3);

                        // got a line of bytes to parse
                        String line = new String(lineBytes, StandardCharsets.UTF_8).trim();

                        processLine(line);
                    } else
                        Log.w(TAG, "Checksum failure");
                } catch (NumberFormatException e) {
                    // if anything goes wrong we'll just ignore this (broken) message
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            // Assemble message in byte array. Assembly has to take place on the byte
            // array since e.g. 16 bit characters may be split over transfer bounds and
            // would thus not be re-assembled correctly
            mIncomingBytes = join(mIncomingBytes, characteristic.getValue());

            // now check if there is a delimiter (\n or \r) in the buffer
            int lineBreakIndex = findLineBreak(mIncomingBytes);
            while(lineBreakIndex >= 0) {
                // Check if line break is right at start of array. This means that
                // we received an empty line
                if(lineBreakIndex == 0) {
                    // if there is _only_ the delimiter, then restart with an empty array, otherwise
                    // keep bytes 1...
                    if(mIncomingBytes.length <= 1) mIncomingBytes = new byte[]{};
                    else mIncomingBytes = Arrays.copyOfRange(mIncomingBytes, 1, mIncomingBytes.length);
                } else {
                    // check if the byte before \n is a \r. Don't include that if it's present.
                    byte[] lineBytes = Arrays.copyOfRange(mIncomingBytes, 0, lineBreakIndex -
                            ((mIncomingBytes[lineBreakIndex-1] == '\r')?1:0));

                    // restart with empty array if the delimiter is the last char in the array
                    if (lineBreakIndex == mIncomingBytes.length - 1) mIncomingBytes = new byte[]{};
                    else mIncomingBytes = Arrays.copyOfRange(mIncomingBytes, lineBreakIndex, mIncomingBytes.length);

                    // finally run the line through the parser
                    parseLineBytes(lineBytes);
                }
                lineBreakIndex = findLineBreak(mIncomingBytes);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "characteristic written");

                mTransmitInProgress = false;   // transmission done
                if(mTransmitBuffer != null) {
                    // extract at most MAX_MSG_LEN bytes
                    byte[] msg = null;
                    if(mTransmitBuffer.length < MAX_MSG_LEN) {
                        msg = mTransmitBuffer;
                        mTransmitBuffer = null;
                    } else {
                        // more than MAX_MSG_LEN bytes: extract first MAX_MSG_LEN bytes
                        msg = new byte[MAX_MSG_LEN];
                        byte[] remain = new byte[mTransmitBuffer.length - MAX_MSG_LEN];
                        System.arraycopy(mTransmitBuffer, 0, msg, 0, MAX_MSG_LEN);
                        System.arraycopy(mTransmitBuffer, MAX_MSG_LEN, remain, 0, mTransmitBuffer.length - MAX_MSG_LEN);
                        mTransmitBuffer = remain;
                    }

                    if(msg != null) {
                        // write data from transmit buffer
                        mTransmitInProgress = true;   // and another tranmission in progress
                        mUartCharacteristic.setWriteType(WRITE_TYPE_NO_RESPONSE);
                        mUartCharacteristic.setValue(msg);
                        if (!mBluetoothGatt.writeCharacteristic(mUartCharacteristic))
                            Log.e(TAG, String.format("ERROR: writeCharacteristic failed for characteristic: %s", mUartCharacteristic.getUuid()));
                    }
                }

            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            // Do some checks first
            if (status != BluetoothGatt.GATT_SUCCESS)
                Log.e(TAG, "Write descriptor failed");

            // Check if this was the Client Configuration Descripto
            if (descriptor.getUuid().equals(UUID_CCC_DESCRIPTOR)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Check if we were turning notifications on or off
                    byte[] value = descriptor.getValue();
                    if (value != null) {
                        if (value[0] != 0) {
                            mTransmitInProgress = false;
                            // Notify set to on, add it to the set of notifying characteristics
                            Log.d(TAG, "added notification");
                            send("\nVERSION", true);  // request version and expect reply
                        }
                    } else
                        Log.d(TAG, "removed notification");
                }
            }
        }
    };

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        sendStatus(ACTION_NOTIFY_DESTROYED);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mActivityReceiver);
    }

    private void setupBLE() {
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            sendStatus(ACTION_NOTIFY_NO_BLE);
            mState = STATE_IDLE;
            sendStatus(ACTION_NOTIFY_INITIALIZED);
        } else {
            // ok, there is BLE on this device, get access to bluetooth hardware
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

            // Ensures Bluetooth is available on the device and it is enabled. If not,
            // displays a dialog requesting user permission to enable Bluetooth.
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                sendStatus(ACTION_REQUEST_BLUETOOTH);
            } else {
                Log.d(TAG, "Bluetooth ok. Request location access");
                sendStatus(ACTION_REQUEST_LOCATION);
            }
        }
    }
}
