package au.com.mithril.bttest;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "BtTest";
    public static final int SOUND_FOLDER_READ = 1;

    TextView memo1 = null;
    Spinner mSoundList = null;

    // Get the default adapter
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothHeadset mBluetoothHeadset;
    BluetoothA2dp mA2DP;

    Handler mainHandler = null;
    MediaPlayer mPlayer = null;
    MyReceiver mBr = new MyReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(getMainLooper());
        setContentView(R.layout.activity_main);
        memo1 = (TextView) findViewById(R.id.memo1);
        memo1.setMovementMethod(new ScrollingMovementMethod());
        mSoundList = (Spinner) findViewById(R.id.soundlist);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        // Establish connection to the proxy.
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.A2DP);
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
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
            default:
                return super.onOptionsItemSelected(item);
        }
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
        ArrayAdapter<DocHolder> adapter = new ArrayAdapter<DocHolder>(this,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ArrayList<DocHolder> mylist = new ArrayList<DocHolder>();
        switch (requestCode) {
            case SOUND_FOLDER_READ:
                if (data == null) {
                    addln("No data.");
                } else {
                    addln("Folder=" + data.getData());
                    DocumentFile docfile = DocumentFile.fromTreeUri(this,data.getData());
                    for (DocumentFile f : docfile.listFiles()) {
                        if (f.isFile()) {
                            addln(f.getName());
                            mylist.add(new DocHolder(f.getName(),f));
                        }
                    }
                    Collections.sort(mylist);
                    adapter.addAll(mylist);
                    mSoundList.setAdapter(adapter);
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

    public void onDiscoverClick(View view) {
        addln("Discovery.");
        addln("Adaptor=" + mBluetoothAdapter);
        if (mBluetoothAdapter == null) return;
        addln("Enabled=" + mBluetoothAdapter.isEnabled());
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bd : pairedDevices) {
            addln(String.format("%s %s %x", bd.getAddress(), bd.getName(), bd.getBluetoothClass().getMajorDeviceClass()));
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
        }
    };

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;
                addln("Bluetooth Headset found: " + proxy);
            } else if (profile == BluetoothProfile.A2DP) {
                mA2DP = (BluetoothA2dp) proxy;
                addln("Bluetooth A2DP found: " + proxy);
            }
        }

        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = null;
                addln("Bluetooth Headset disconnect.");
            } else if (profile == BluetoothProfile.A2DP) {
                mA2DP = null;
                addln("Bluetooth A2DP disconnect.");
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
        connectDevice("24:AA:42:09:F9:BA");
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
        connectDevice("E2:8B:8E:89:6C:07");
    }

    public void onPlay(View view) {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        addln("Loading player...");
        mPlayer = MediaPlayer.create(this, R.raw.itsatrap);
        addln("Playing.");
        mPlayer.start();
    }

    public class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            addln("Receiver. Action=" + intent.getAction());
            BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            addln("Connected.");
            if (bd != null) {
                addln(bd.getAddress() + " " + bd.getName());
            }
        }
    }

    public class DocHolder implements Comparable<DocHolder> {
        public String name;
        public  DocumentFile file;

        public DocHolder(String name, DocumentFile file) {
            this.name=name;
            this.file=file;
        }

        @Override
        public String toString() {
            if (name!=null && !name.isEmpty()) return name;
            if (file!=null) return file.getName();
            return "(null)";
        }

        @Override
        public int compareTo(@NonNull DocHolder docHolder) {
            return toString().compareTo(docHolder.toString());
        }
    }

}
