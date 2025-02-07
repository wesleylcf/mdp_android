package com.example.mdpcontroller;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.example.mdpcontroller.arena.Cell;
import com.example.mdpcontroller.arena.Obstacle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BluetoothService {
    public static BluetoothAdapter mBluetoothAdapter;
    public static BluetoothSocket mBluetoothSocket;
    public static BluetoothDevice mConnectedDevice;

    public static BluetoothManager manager;
    public static boolean CONNECT_AS_CLIENT = true; // if false, will make device discoverable and accept connections
    public static Cell[][] cells;
    public static ArrayList<Obstacle> obstacles = null;
    public enum BluetoothStatus {
        UNCONNECTED, SCANNING, CONNECTING, CONNECTED, DISCONNECTED
    }

    private static BluetoothStatus btStatus;
    private static  final String[] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier (https://novelbits.io/uuid-for-custom-services-and-characteristics/#:~:text=%E2%80%9CA%20UUID%20is%20a%20universally,by%20their%2016%2Dbit%20aliases.)
    private static final int MAX_RECONNECT_RETRIES = 5;
    private static final String NAME = "MDP_Group_16_Tablet";
    public static ConnectedThread mConnectedThread;
    public AcceptThread mAcceptThread;
    public ConnectAsClientThread mClientThread;
    public static Activity mContext;



    // sends bt_status_changed broadcast when status is set
    public static void setBtStatus(BluetoothStatus newStatus, Map<String, String> extras, Activity context) {
        btStatus = newStatus;
        Intent intent = new Intent("bt_status_changed");
        for (String key: extras.keySet()){
            intent.putExtra(key, extras.get(key));
        }
        System.out.println("BtStatus changed to "+newStatus.toString());
        context.sendBroadcast(intent);
    }

    public static BluetoothStatus getBtStatus(){
        return btStatus;
    }
    private static boolean hasPermissions(Activity activity) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
    public static void stopSearch() {
        mBluetoothAdapter.cancelDiscovery();
    }

    public static void startSearch() {
        mBluetoothAdapter.startDiscovery();
    }

    public static void initialize(Activity activity){
        // Request permissions
        if (!hasPermissions(activity)) {
            ActivityCompat.requestPermissions(activity, permissions, 1);
        }

        if (btStatus == null) setBtStatus(BluetoothStatus.UNCONNECTED, new HashMap<String, String>(), activity);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        // Request to turn on bluetooth
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivity(enableBtIntent);
        }

        // Request to turn on location
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Build the alert dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Location Services Not Active");
            builder.setMessage("Please enable Location Services and GPS");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Show location settings when the user acknowledges the alert dialog
                    Intent enableLocIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(enableLocIntent);
                }
            });
            Dialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }
    }

    public BluetoothService(Activity context){
        mContext = context;
    }

    public void startConnectedThread() {
        mConnectedThread = new ConnectedThread(mBluetoothSocket);
        mConnectedThread.start();
    }

    public boolean write(String message, boolean DEBUG){
        if (mConnectedThread == null){
            Toast.makeText(mContext, "Bluetooth not connected!", Toast.LENGTH_SHORT).show();
            return false;
        }
        // comment out the \n below only when performing checklist
        message = message + "\n";
//        if (DEBUG) {
//            try {
//                mConnectedThread.write(message.getBytes("US-ASCII"));
//            } catch (UnsupportedEncodingException e) {
//                mConnectedThread.write(message.getBytes());
//            }
//
//        } else {
            mConnectedThread.write(message.getBytes());
//        }
        return true;
    }

    public void disconnect() {
        if (mConnectedThread != null) mConnectedThread.cancel();
        mConnectedDevice = null;
    }

    public void clientConnect(String address, String name, Activity context) {
        mClientThread = new ConnectAsClientThread(address, name, context);
        mClientThread.start();
    }

    public void serverStartListen(Activity context) {
        if (CONNECT_AS_CLIENT) return;
        if(mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        {
            // make device discoverable
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30);
            context.startActivityForResult(discoverableIntent, 1);
        }
        if (mAcceptThread == null){
            mAcceptThread = new AcceptThread(context);
            mAcceptThread.start();
        }
    }

    public void serverStopListen() {
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
    }

    private static BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            return device.createInsecureRfcommSocketToServiceRecord(BT_MODULE_UUID);
        } catch (Exception e) {
            System.out.println("Could not create connection");
        }
        return  device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    private static class ConnectAsClientThread extends Thread {
        public Activity context;
        public String name, address;

        public ConnectAsClientThread(String address, String name, Activity context) {
            this.context = context;
            this.address = address;
            this.name = name;
        }

        @Override
        public void run() {
            System.out.println("starts");
            int retries = 0;
            BluetoothService.stopSearch();
            BluetoothDevice device = BluetoothService.mBluetoothAdapter.getRemoteDevice(address);
            BluetoothService.mBluetoothSocket = null;

            while ((BluetoothService.mBluetoothSocket == null || !BluetoothService.mBluetoothSocket.isConnected()) && retries < 3) {
                Intent intent = new Intent("message_received");
                intent.putExtra("message", String.format("DEBUG/Retry(%d) for connecting as bluetooth client", retries));
                context.sendBroadcast(intent);
                try {
                    BluetoothService.mBluetoothSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    intent = new Intent("message_received");
                    intent.putExtra("message", "DEBUG/Socket creation failed");
                    context.sendBroadcast(intent);
                    retries++;
                    continue;
                }
                // Establish the Bluetooth socket connection.
                try {
                    BluetoothService.mBluetoothSocket.connect();
                    BluetoothService.mConnectedDevice = device;
                } catch (IOException e) {
                    try {
                        System.out.println(e.getMessage());
                        BluetoothService.mBluetoothSocket.close();
                        BluetoothService.mBluetoothSocket = null;
                    } catch (IOException e2) {
                        intent = new Intent("message_received");
                        intent.putExtra("message", "DEBUG/Socket creation failed while connecting");
                        context.sendBroadcast(intent);
                    }
                    intent.putExtra("message", "DEBUG/Connection Failed");
                    context.sendBroadcast(intent);
                    Map<String, String> extra = new HashMap<>();
                    BluetoothService.setBtStatus(BluetoothStatus.UNCONNECTED, extra, context);
                    retries++;
                    continue;
                }
                intent = new Intent("message_received");
                intent.putExtra("message", String.format("DEBUG/Connected. Device: %s(%s)", device.getName(), device.getAddress()));
                context.sendBroadcast(intent);
                Map<String, String> extra = new HashMap<>();
                extra.put("device", !name.equals("null") ? name : address);
                BluetoothService.setBtStatus(BluetoothService.BluetoothStatus.CONNECTED, extra, context);
                break;
            }
        }
    }

    private static class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private final Activity context;

        public AcceptThread(Activity context) {
            this.context = context;
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, BT_MODULE_UUID);
            } catch (IOException e) {
                System.out.println("Socket's listen() method failed");
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    System.out.println("Socket's accept() method failed");
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    mBluetoothSocket = socket;
                    mConnectedDevice = socket.getRemoteDevice();
                    String name = socket.getRemoteDevice().getName();
                    String address = socket.getRemoteDevice().getAddress();
                    Map<String, String> extra = new HashMap<>();
                    extra.put("device", !name.equals("null") ? name : address);
                    Intent intent = new Intent("message_received");
                    intent.putExtra("message", "DEBUG/Connected!");
                    context.sendBroadcast(intent);
                    BluetoothService.setBtStatus(BluetoothService.BluetoothStatus.CONNECTED, extra, context);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        System.out.println(e.getMessage());;
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                System.out.println("Could not close the connect socket");
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                System.out.println("Error occurred when creating input stream " + e.getMessage());
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                System.out.println("Error occurred when creating output stream " + e.getMessage());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            // mmBuffer store for the stream
            byte[] buffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity.
                    Intent intent = new Intent("message_received");
                    intent.putExtra("message", new String(buffer, 0, numBytes));
                    mContext.sendBroadcast(intent);
                } catch (IOException e) {
                    System.out.println("DEBUG/Input stream was disconnected " + e.getMessage());
                    setBtStatus(BluetoothStatus.DISCONNECTED, new HashMap<>(), mContext);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                System.out.println("Error occurred when sending data " + e.getMessage());

                // Send a failure message back to the activity.
                Intent intent = new Intent("message_received");
                intent.putExtra("message", "DEBUG/Couldn't send data to the other device");
                mContext.sendBroadcast(intent);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                System.out.println("Could not close the connect socket " + e.getMessage());
            }
        }
    }

    // Receiver for DEVICE_DISCONNECTED, automatically tries to reconnect as client
    public class BluetoothLostReceiver extends BroadcastReceiver {

        Activity main;

        public BluetoothLostReceiver(Activity main) {
            super();
            this.main = main;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (getBtStatus() == BluetoothStatus.DISCONNECTED) {
                Intent intent2 = new Intent("message_received");
                if (CONNECT_AS_CLIENT){
                    intent2.putExtra("message", String.format("DEBUG/Connection lost, attempting to reconnect (%s, %s)", mConnectedDevice.getName(), mConnectedDevice.getAddress()));
                    context.sendBroadcast(intent2);
                    if(getBtStatus() == BluetoothStatus.DISCONNECTED && mConnectedDevice != null) {
                        clientConnect(mConnectedDevice.getAddress(), mConnectedDevice.getName(), main);
                    }
                    // Max tries elapsed
                    intent2 = new Intent("message_received");
                    intent2.putExtra("message", "DEBUG/Reconnect failed");
                    context.sendBroadcast(intent2);
                    setBtStatus(BluetoothStatus.UNCONNECTED, new HashMap<>(), (Activity) context);
                }
                else {
                    // reconnect as server
                    intent2.putExtra("message", "DEBUG/Connection lost, making device discoverable...");
                    context.sendBroadcast(intent2);
                    serverStartListen(main);
                }
            }
        }
    }

}
