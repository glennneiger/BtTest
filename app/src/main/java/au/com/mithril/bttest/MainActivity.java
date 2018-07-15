package au.com.mithril.bttest;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "BtTest";
    public static final int SOUND_FOLDER_READ = 1;

    TextView memo1 = null;
    Spinner mSoundList = null;
    ArrayAdapter<DocHolder> soundArray;

    TextView mSelected = null;

    Spinner mDeviceList = null;
    ArrayAdapter<DevHolder> deviceArray;
    Map<String,String> mDeviceNames = new HashMap<String,String>();

    // Get the default soundArray
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothHeadset mBluetoothHeadset;
    BluetoothA2dp mA2DP;

    Handler mainHandler = null;
    MediaPlayer mPlayer = null;
    MyReceiver mBr = new MyReceiver();
    SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(getMainLooper());
        setContentView(R.layout.activity_main);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mDeviceNames=readMap("devicenames");
        memo1 = (TextView) findViewById(R.id.memo1);
        memo1.setMovementMethod(new ScrollingMovementMethod());

        mSelected=(TextView) findViewById(R.id.selected);

        mSoundList = (Spinner) findViewById(R.id.soundlist);
        soundArray = new ArrayAdapter<DocHolder>(this, android.R.layout.simple_spinner_item);
        soundArray.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSoundList.setAdapter(soundArray);

        mDeviceList = (Spinner) findViewById(R.id.devices);
        deviceArray = new ArrayAdapter<DevHolder>(this, android.R.layout.simple_spinner_item);
        deviceArray.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDeviceList.setAdapter(deviceArray);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        // Establish connection to the proxy.
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.A2DP);
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        this.registerReceiver(mBr, f);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sounds:
                setSounds();
                return true;
            case R.id.jsontest:
                jsonTest();
                return true;
            case R.id.jsonread:
                jsonRead();
                return true;
            case R.id.rename:
                renameDevice();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void renameDevice() {
        DevHolder h = (DevHolder) mDeviceList.getSelectedItem();
        if (h==null) {
            addln("No device selected.");
            return;
        }
        final BluetoothDevice bd = h.file;
        InputQuery.inputQuery(this,"Rename Device", bd.getName() + "\n" + bd.getAddress(), bd.getName(), new DialogOK() {
            @Override
            public void onOK(String value) {
                addln("New value for "+bd.getName()+"="+value);
                if (value.isEmpty()) value=bd.getName();
                mDeviceNames.put(bd.getAddress(),value);
                SharedPreferences.Editor e = mPreferences.edit();
                e.putString("devicenames",mapToJSON(mDeviceNames));
                e.apply();
                for (int i=0; i<deviceArray.getCount(); i++) {
                    DevHolder h = deviceArray.getItem(i);
                    if (h.file.getAddress().equals(bd.getAddress())) {
                        h.name=friendlyName(bd);
                        int j=mDeviceList.getSelectedItemPosition();
                        mDeviceList.setAdapter(null);
                        mDeviceList.setAdapter(deviceArray);
                        mDeviceList.setSelection(j);
                        break;
                    }
                }
            }
        });
    }

    private void jsonRead() {
        Map<String, String> mymap = new HashMap<String, String>();
        if (mPreferences.contains("json")) {
            String s = mPreferences.getString("json", "{}");
            addln(s);
            try {
                JSONObject json = new JSONObject(s);
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    mymap.put(key, json.getString(key));
                }
            } catch (JSONException e) {
                addln("JSON failed: " + e.getMessage());
            }
            addln("Results:");
            for (String key : mymap.keySet()) {
                addln(key + " : " + mymap.get(key));
            }
        }
    }

    private Map<String, String> readMap(String prefkey) {
        Map<String, String> result = new HashMap();
        if (mPreferences.contains(prefkey)) {
            String s = mPreferences.getString(prefkey, "{}");
            try {
                JSONObject json = new JSONObject(s);
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    result.put(key, json.getString(key));
                }
            } catch (JSONException e) {
                addln("JSON failed: " + e.getMessage());
            }
        }
        return result;
    }

    private String mapToJSON(Map mymap) {
        JSONObject json = new JSONObject(mymap);
        return json.toString();
    }

    private void jsonTest() {
        Map<String, String> mymap = new HashMap<String, String>();
        mymap.put("Testing", "This is the value of testing.");
        mymap.put("data", "Better believe it's data.");
        mymap.put("bob", "Short for Kate.");
        JSONObject j = new JSONObject(mymap);
        addln(j.toString());
        SharedPreferences.Editor e = mPreferences.edit();
        e.putString("json", j.toString());
        e.apply();
    }

    String friendlyName(BluetoothDevice bd) {
        if (bd==null) return "";
        if (mDeviceNames.containsKey(bd.getAddress())) {
            return mDeviceNames.get(bd.getAddress());
        }
        return bd.getName();
    }

    private void setSounds() {
        /*Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getDataDirectory().toURI() );
        startActivityForResult(intent, SOUND_FOLDER_READ); */
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(Intent.createChooser(i, "Choose directory"), SOUND_FOLDER_READ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        ArrayList<DocHolder> mylist = new ArrayList<DocHolder>();
        switch (requestCode) {
            case SOUND_FOLDER_READ:
                if (data == null) {
                    addln("No data.");
                } else {
                    addln("Folder=" + data.getData());
                    DocumentFile docfile = DocumentFile.fromTreeUri(this, data.getData());
                    for (DocumentFile f : docfile.listFiles()) {
                        if (f.isFile()) {
                            addln(f.getName());
                            mylist.add(new DocHolder(f.getName(), f));
                        }
                    }
                    Collections.sort(mylist);
                    soundArray.clear();
                    soundArray.addAll(mylist);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, mA2DP);
        this.unregisterReceiver(mBr);
        stopPlaying();
    }

    public void onTestClick(View view) {
        addln("Testing..." + (new Date()));
        if (mBluetoothHeadset != null) { // Headset.
            addln("Headset connected.");
            for (BluetoothDevice bd : mBluetoothHeadset.getConnectedDevices()) {
                addln("Audio connected=" + mBluetoothHeadset.isAudioConnected(bd) + " : " + bd.getName());
                addln("Connection state: " + mBluetoothHeadset.getConnectionState(bd));
            }
        }
        if (mA2DP != null) { // A2DP.
            addln("A2DP connected.");
            for (BluetoothDevice bd : mA2DP.getConnectedDevices()) {
                addln("Is playing=" + mA2DP.isA2dpPlaying(bd) + " : " + bd.getName() + " : " + bd.getAddress());
                addln("Connection state: " + mA2DP.getConnectionState(bd));
                for (ParcelUuid p : bd.getUuids()) {
                    addln(p.toString());
                }
            }
        }
    }

    private void addln(Object msg) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) { // Not in main loop.
            final String holdmsg = msg.toString();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    addln(holdmsg);
                }
            });
            return;
        }
        memo1.append(msg + "\n");
    }

    public boolean isAudio(BluetoothDevice bd) {
      for (ParcelUuid uuid : bd.getUuids()) {
          if (uuid.toString().toUpperCase().startsWith("0000110B")) {
              return true;
          }
      }
      return false;
    }

    public void onDiscoverClick(View view) {
        addln("Discovery.");
        addln("Adaptor=" + mBluetoothAdapter);
        if (mBluetoothAdapter == null) return;
        addln("Enabled=" + mBluetoothAdapter.isEnabled());
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        deviceArray.clear();
        for (BluetoothDevice bd : pairedDevices) {
            addln(String.format("%s %s %x", bd.getAddress(), bd.getName(), bd.getBluetoothClass().getMajorDeviceClass()));
            if (isAudio(bd)) {
                deviceArray.add(new DevHolder(friendlyName(bd), bd));
            }
        }
        mBluetoothAdapter.startDiscovery();
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                addln("Found: " + deviceName + " " + deviceHardwareAddress);
            }
            updateConnected();
        }
    };

    void updateConnected() {
        String s = "";
        if (mA2DP!=null) {
            for (BluetoothDevice bd : mA2DP.getConnectedDevices()) {
                if (!s.isEmpty()) s += "\n";
                s += friendlyName(bd);
            }
        }
        if (s.isEmpty()) s="No Device Connected";
        final String local = s;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                mSelected.setText(local);
            }
        });
    }


    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;
                addln("Bluetooth Headset found: " + proxy);
            } else if (profile == BluetoothProfile.A2DP) {
                mA2DP = (BluetoothA2dp) proxy;
                addln("Bluetooth A2DP found: " + proxy);
                updateConnected();
            }
        }

        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = null;
                addln("Bluetooth Headset disconnect.");
            } else if (profile == BluetoothProfile.A2DP) {
                mA2DP = null;
                addln("Bluetooth A2DP disconnect.");
                updateConnected();
            }
        }
    };

    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     *
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod() {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            addln("Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    private Method getDisconnectMethod() {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("disconnect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            addln("Unable to find disconnect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    public void connectDevice(String address) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bd : pairedDevices) {
            if (bd.getAddress().equals(address)) {
                addln("Found: " + bd);
                Method connect = getConnectMethod();

                //If either is null, just return. The errors have already been logged
                if (connect == null || bd == null) {
                    addln("No method found.");
                    return;
                }

                try {
                    connect.setAccessible(true);
                    connect.invoke(mA2DP, bd);
                } catch (InvocationTargetException ex) {
                    addln("Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString());
                } catch (IllegalAccessException ex) {
                    addln("Illegal Access! " + ex.toString());
                }
            }
        }
    }

    public void onConnectClick(View view) {
        DevHolder h = (DevHolder) mDeviceList.getSelectedItem();
        if (h==null) {
            addln("No device selected.");
        }
        connectDevice(h.file.getAddress());
    }

    public void onDisconnectClick(View view) {
        if (mA2DP != null) {
            addln("Disconnecting...");
            Method disconnect = getDisconnectMethod();
            if (disconnect == null) {
                addln("No disconnect method found.");
                return;
            }
            for (BluetoothDevice bd : mA2DP.getConnectedDevices()) {
                disconnect.setAccessible(true);
                try {
                    disconnect.invoke(mA2DP, bd);
                    addln("Disconnected.");
                } catch (IllegalAccessException e) {
                    addln("Illegal access! " + e.toString());
                } catch (InvocationTargetException e) {
                    addln("UNable to invoke disconnect.");
                }
            }
        }

    }

    public void onConnectWhite(View view) {
        stopPlaying();
    }

    public void stopPlaying() {
        if (mPlayer!=null) {
            mPlayer.release();
            mPlayer=null;
        }
    }

    public void onPlay(View view) {
        stopPlaying();
        updateConnected();
        addln("Loading player...");
        DocHolder d = (DocHolder) mSoundList.getSelectedItem();
        if (d != null) {
            mPlayer = MediaPlayer.create(this, d.file.getUri());
        } else {
            mPlayer = MediaPlayer.create(this, R.raw.itsatrap);
        }
        mPlayer.start();
        addln("Playing.");
    }

    public class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            addln("Receiver. Action=" + intent.getAction());
            BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (bd != null) {
                addln(bd.getAddress() + " " + bd.getName());
            }
            updateConnected();
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateConnected();
                }
            },1000);
        }
    }

    public class DocHolder implements Comparable<DocHolder> {
        public String name;
        public DocumentFile file;

        public DocHolder(String name, DocumentFile file) {
            this.name = name;
            this.file = file;
        }

        @Override
        public String toString() {
            if (name != null && !name.isEmpty()) return name;
            if (file != null) return file.getName();
            return "(null)";
        }

        @Override
        public int compareTo(@NonNull DocHolder docHolder) {
            return toString().compareTo(docHolder.toString());
        }
    }

    public class DevHolder implements Comparable<DevHolder> {
        public String name;
        public BluetoothDevice file;

        public DevHolder(String name, BluetoothDevice file) {
            this.name = name;
            this.file = file;
        }

        @Override
        public String toString() {
            if (name != null && !name.isEmpty()) return name;
            if (file != null) return file.getName();
            return "(null)";
        }

        @Override
        public int compareTo(@NonNull DevHolder devHolder) {
            return toString().compareTo(devHolder.toString());
        }
    }



}
