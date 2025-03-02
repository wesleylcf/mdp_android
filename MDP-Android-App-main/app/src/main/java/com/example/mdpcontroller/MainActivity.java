package com.example.mdpcontroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mdpcontroller.arena.ArenaView;
import com.example.mdpcontroller.arena.Cell;
import com.example.mdpcontroller.arena.Obstacle;
import com.example.mdpcontroller.arena.ObstacleDialogueFragment;
import com.example.mdpcontroller.arena.Robot;
import com.example.mdpcontroller.tab.AppDataModel;
import com.example.mdpcontroller.tab.ArenaIntent;
import com.example.mdpcontroller.tab.ExploreTabFragment;
import com.example.mdpcontroller.tab.ManualTabFragment;
import com.example.mdpcontroller.tab.PathTabFragment;
import com.example.mdpcontroller.tab.SettingsTabFragment;
import com.example.mdpcontroller.tab.ChatTabFragment;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity<ActivityResultLauncher> extends AppCompatActivity implements ObstacleDialogueFragment.DialogDataListener {
    private final String DELIMITER = "/";
    public boolean DEBUG = false;
    public boolean RUN_TO_END = false;
    private BluetoothService btService;
    private BluetoothService.BluetoothLostReceiver btLostReceiver;
    private BtStatusChangedReceiver conReceiver;
    private AppDataModel appDataModel;
    private ArenaView arena;
    private ApiService api;
    private List<String> moveList;
    // for timer
    private final Handler timerHandler  = new Handler();
    TimerRunnable timerRunnable = null;
    private String curObsNum = "0";

    TabLayout tabLayout;
    ViewPager tabViewPager;
    MainAdapter adapter;

    private boolean MOVING=false;
    private boolean valid_pos;
    private boolean valid_target = true;
    private boolean valid_image = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        BluetoothService.initialize(this);
        btService = new BluetoothService(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView)findViewById(R.id.btMessageTextView)).setMovementMethod(new ScrollingMovementMethod());
        ((ScrollView)findViewById(R.id.SCROLLER_ID)).fullScroll(View.FOCUS_DOWN);
        arena = findViewById(R.id.arena);
        arena.getBluetoothService(btService);
        this.api = new ApiService(btService);
        // Tab-Layout
        tabLayout = findViewById(R.id.tabLayout);
        tabViewPager = findViewById(R.id.tabViewPager);
        adapter = new MainAdapter(getSupportFragmentManager());
        adapter.AddFragment(new ExploreTabFragment(), "Explore");
        adapter.AddFragment(new PathTabFragment(), "Path");
        adapter.AddFragment(new ManualTabFragment(), "Manual");
        adapter.AddFragment(new SettingsTabFragment(), "Settings");
        adapter.AddFragment(new ChatTabFragment(), "Chat");
        tabViewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(tabViewPager);

        // Make device discoverable and accept connections
        btService.serverStartListen(this);

        registerBroadcastReceivers();

        appDataModel = new ViewModelProvider(this).get(AppDataModel.class);

        appDataModel.getArenaIntent().observe(this, data -> {
            arena.arenaIntent = data;
        });
        displayMessage("Status updates will appear here");
        arena.setEventListener(new ArenaView.DataEventListener() {
            @Override
            public void onEventOccurred() {
                if(arena.obstacleEdit){
                    showObstacleDialog();
                }
            }
        });

        moveList = new ArrayList<>();
        setConnectBtn();
    }

    /*
    * BroadcastReceiver is event-driven, where the event key is specified in the Intent.
    * */
    private void registerBroadcastReceivers() {
        registerReceiver(msgReceiver, new IntentFilter("message_received"));
        conReceiver = new BtStatusChangedReceiver(this);
        registerReceiver(conReceiver, new IntentFilter("bt_status_changed"));
        btLostReceiver = btService.new BluetoothLostReceiver(this);
        registerReceiver(btLostReceiver, new IntentFilter("bt_status_changed"));
    }

    // BlueTooth
    public void btConnect_onPress(View view) {
        // connect as server
        if (!BluetoothService.CONNECT_AS_CLIENT && !(BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.CONNECTED)) {
            btService.serverStartListen(this);
        }
        // connect as client
        else{
            Intent intent = new Intent(this, DeviceList.class);
            startActivity(intent);
        }
    }

    // BroadcastReceiver used to parse messages received over bluetooth.
    private final BroadcastReceiver msgReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String fullMessage = intent.getExtras().getString("message");
            if (DEBUG) {
                displayMessage("Debug Mode\n" + fullMessage);
                System.out.println("Debug Mode\n" + fullMessage);
            }
            if (fullMessage == null || fullMessage.isEmpty()) return;

            try {
                JSONObject jsonMessage = new JSONObject(fullMessage);
                String category = jsonMessage.getString("cat");
                JSONObject value = jsonMessage.getJSONObject("value");

                switch (category) {
                    case "image-rec": {
                        String obstacleId = value.getString("obstacle_id");
                        String imageId = value.getString("image_id");

                        int imageIdInt = Integer.parseInt(imageId);
                        if (imageIdInt < 11 || imageIdInt > 40) {
                            valid_image = false;
                        } else {
                            valid_image = true;
                            valid_target = arena.setObstacleImageID(obstacleId, imageId);
                        }
                        if (!valid_target || !valid_image) {
                            displayMessage("Invalid imageID or obstacleID: " + fullMessage);
                        }
                        break;
                    }
                    case "location": {
                        int xCoord = value.getInt("x");
                        int yCoord = value.getInt("y");
                        int direction = value.getInt("d");

                        String orient;
                        switch (direction) {
                            case 0: orient = "E"; break;
                            case 1: orient = "N"; break;
                            case 2: orient = "W"; break;
                            case 3: orient = "S"; break;
                            default: orient = ""; break;
                        }

                        String combined = String.format("(%d,%d,%s)", xCoord, yCoord, orient);
                        System.out.println("combined: " + combined);

                        valid_pos = arena.setRobot(xCoord, 19 - yCoord, orient);
                        if (valid_pos) {
                            TextView robotPosTextView = findViewById(R.id.robotPosTextView);
                            robotPosTextView.setText(combined);
                        } else {
                            displayMessage("Invalid robot position on map: " + fullMessage);
                        }
                        break;
                    }
                    default: {
                        displayMessage("Unrecognized Command\n" + fullMessage);
                        break;
                    }
                }
            } catch (JSONException e) {
                displayMessage("ERROR (" + e.getMessage() + ")\n" + fullMessage);
            }
        }
    };

    // Displays a string in the log TextView, prepends time received as well
    private void displayMessage(String msg) {
        Calendar c = Calendar.getInstance();
        msg = "\n\n"
            +c.get(Calendar.HOUR_OF_DAY)+":"
            +c.get(Calendar.MINUTE)+":"
            +c.get(Calendar.SECOND)+" - "
            +msg;

        ((TextView)findViewById(R.id.btMessageTextView)).append(msg);
    }

    // Create a BroadcastReceiver for bt_status_changed.
    public class BtStatusChangedReceiver extends BroadcastReceiver {
        Activity main;

        public BtStatusChangedReceiver(Activity main) {
            super();
            this.main = main;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Button bt = findViewById(R.id.button_connect);
            if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.CONNECTING) {
                btService.serverStopListen();
                btService.clientConnect(intent.getStringExtra("address"),
                        intent.getStringExtra("name"),
                        main);
            }
            else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.CONNECTED) {
                btService.startConnectedThread();
                btService.serverStopListen();
                displayMessage("Status update\nConnected");
            }
            else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.UNCONNECTED) {
                btService.disconnect();
                btService.serverStartListen(main);
            }
            else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.DISCONNECTED) {
                if (BluetoothService.CONNECT_AS_CLIENT) displayMessage("Status update\nDisconnected, attempting to reconnect...");
                else displayMessage("Status update\nDisconnected, waiting for reconnect...");
            }
            setConnectBtn();
        }
    };

    public void setConnectBtn(){
        Button bt = findViewById(R.id.button_connect);
        if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.CONNECTING) {
            bt.setText(getResources().getString(R.string.button_bluetooth_connecting));
            bt.setBackgroundColor(getResources().getColor(R.color.teal_200));
        }
        else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.CONNECTED) {
            String name = BluetoothService.mConnectedDevice.getName()!=null?BluetoothService.mConnectedDevice.getName(): BluetoothService.mConnectedDevice.getAddress();
            bt.setText(String.format(getResources().getString(R.string.button_bluetooth_connected), name));
            bt.setBackgroundColor(getResources().getColor(R.color.green_500));
        }
        else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.UNCONNECTED) {
            bt.setText(R.string.button_bluetooth_unconnected);
//            bt.setBackgroundColor(getResources().getColor(R.color.primary_400));
        }
        else if (BluetoothService.getBtStatus() == BluetoothService.BluetoothStatus.DISCONNECTED) {
            bt.setText(getResources().getString(R.string.button_bluetooth_disconnected));
            bt.setBackgroundColor(getResources().getColor(R.color.orange_500));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(msgReceiver);
        unregisterReceiver(conReceiver);
        unregisterReceiver(btLostReceiver);
        timerHandler.removeCallbacks(timerRunnable);
    }

    // Tab-bar
    private class MainAdapter extends FragmentPagerAdapter {
        ArrayList<Fragment> fragmentArrayList = new ArrayList<>();
        ArrayList<String> stringArrayList = new ArrayList<>();
        public MainAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }
        public void AddFragment(Fragment fragment, String s){
            fragmentArrayList.add(fragment);
            stringArrayList.add(s);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragmentArrayList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentArrayList.size();
        }
        @Nullable
        @Override
        public CharSequence getPageTitle(int position){
            return stringArrayList.get(position);
        }
    }

    public void chatBtn(View view){
        String sendText = null;
        EditText chatET = (EditText) findViewById(R.id.chatEditText);

        if (chatET != null){
            sendText = chatET.getText().toString();
            System.out.println("Send Text = " + sendText);
            chatET.setText("");
        }
        btService.write(String.format("%s", sendText), DEBUG);

        // Collapse Keyboard on click
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    public void clearChatBtn(View view) {
        EditText chatET = (EditText) findViewById(R.id.chatEditText);
        chatET.setText("");
    }

    public void moveBtn(View view){
        ImageButton pressedBtn = (ImageButton) view;
        String dir;
        String rpiDir;
        String distanceOrAngle = "090";
        switch (pressedBtn.getId()){
            case(R.id.fButton): {
                dir = "F";
                rpiDir = "FW";
                distanceOrAngle = "010";
                break;
            }
            case(R.id.rButton): {
                dir = "R";
                rpiDir = "FR";
                break;
            }
            case(R.id.lButton): {
                dir = "L";
                rpiDir = "FL";
                break;
            }
            case(R.id.bButton): {
                dir = "B";
                rpiDir = "BW";
                distanceOrAngle = "010";
                break;
            }
            case(R.id.brButton): {
                dir = "BR";
                rpiDir = "BR";
                break;
            }
            case(R.id.blButton): {
                dir = "BL";
                rpiDir = "BL";
                break;
            }
            default: return;
        }
        if(Robot.robotMatrix[0][0] == null){
            System.out.println("Robot is not set up on map");
        }else{
            try {
                JSONObject arenaInfo = new JSONObject();
                arenaInfo.put("cat", "manual");
                arenaInfo.put("value", rpiDir + distanceOrAngle);
                boolean validMove = arena.moveRobot(dir);
                if (validMove) {
                    btService.write(arenaInfo.toString(), DEBUG);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void clearObstacles(View view){
        arena.clearObstacles();
    }

    public void toggleDebugMode(View view){
        DEBUG = !DEBUG;
        ((CheckedTextView)findViewById(R.id.toggleDebug)).setChecked(DEBUG);
    }

    public void toggleReconnectAsClient(View view){
        BluetoothService.CONNECT_AS_CLIENT = !BluetoothService.CONNECT_AS_CLIENT;
        if (!BluetoothService.CONNECT_AS_CLIENT) btService.serverStartListen(this);
        ((CheckedTextView)findViewById(R.id.toggleReconnectAsClient)).setChecked(BluetoothService.CONNECT_AS_CLIENT);
    }

    public void toggleRunToEnd(View view){
        RUN_TO_END = !RUN_TO_END;
        ((CheckedTextView)findViewById(R.id.toggleRunToEnd)).setChecked(RUN_TO_END);
    }

    private class TimerRunnable implements Runnable {
        private final TextView timerTextView;
        private long startTime = 0;
        public TimerRunnable(TextView timerTextView){
            this.timerTextView = timerTextView;
        }
        @Override
        public void run(){
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            timerTextView.setText(String.format(Locale.getDefault(),"%02d:%02d", minutes, seconds));

            timerHandler.postDelayed(this, 500);
        }
    }

    public void clearLogsBtn(View view){
        TextView logs = ((TextView)findViewById(R.id.btMessageTextView));
        logs.setText(null);
    }

    public void sendArenaInfo(View view) {
        this.api.sendArenaInfo(view, arena);
    }

    public void onClickSimulatorBtn(View view){
        moveList.clear();
        // reset obstacles
        if(arena.obstacles.size() > 0){
            for(Obstacle obstacle: arena.obstacles){
                obstacle.explored = false;
                obstacle.imageID = "-1";
            }
            arena.invalidate();
        }

        Cell curCell;
        int xCoord, yCoord;
        String dir = "0";
        StringBuilder cmd = new StringBuilder("START/SIMULATOR");

        // Robot position
        if (Robot.robotMatrix[1][1] != null) {
            Cell center = Robot.robotMatrix[1][1];
            cmd.append(String.format(Locale.getDefault(),"/(R,%02d,%02d,0)", center.col, center.row));
        }
        else cmd.append("/(R,01,01,0)");

        // Obstacle position
        for (int i = 0; i < arena.obstacles.size(); i++) {
            switch(arena.obstacles.get(i).imageDir){
                case("TOP"): dir = "90"; break;
                case("LEFT"): dir = "180"; break;
                case("RIGHT"): dir = "0"; break;
                case("BOTTOM"): dir = "-90"; break;
            }
            curCell = arena.obstacles.get(i).cell;
            xCoord = curCell.col;
            yCoord = curCell.row; // invert y coordinates since algorithm uses bottom left as origin
            cmd.append(String.format(Locale.getDefault(), "/(%02d,%02d,%02d,%s)", i, xCoord, yCoord, dir));
        }
        btService.write(cmd.toString(), DEBUG);
        TextView rbTV = findViewById(R.id.obstacleStatusTextView);
        rbTV.setText(R.string.check_path);
    }

    public void startExplore(View view) {
        this.api.startRobot(view);
    }

    public void startTimer(Button b) {
        if (b.getId() == R.id.startExplore) {
            moveList.clear();
            // reset obstacles
            if(arena.obstacles.size() > 0){
                for(Obstacle obstacle: arena.obstacles){
                    obstacle.explored = false;
                    obstacle.imageID = "-1";
                }
                arena.invalidate();
            }
            // prepare timer
            timerRunnable = new TimerRunnable(findViewById(R.id.timerTextViewExplore));
            curObsNum = "0";
            b.setText(R.string.stop_explore);
            Cell curCell;
            int xCoord, yCoord;
            String dir = "0";
            StringBuilder cmd = new StringBuilder("START/EXPLORE");

            // TODO: send Robot position?
            int robotX = 1, robotY = 1;
            if (Robot.robotMatrix[1][1] != null) {
                Cell center = Robot.robotMatrix[1][1];
                robotX = center.col;
                robotY = 19-center.row;
//                cmd.append(String.format(Locale.getDefault(),"/(R,%02d,%02d,0)", center.col, 19-center.row));
                if (DEBUG) {
                    displayMessage(String.format("Robot coordinates: (%d, %d)", center.col, 19-center.row));
                }
            }

//            // Obstacle position
//            for (int i = 0; i < arena.obstacles.size(); i++) {
//                switch(arena.obstacles.get(i).imageDir){
//                    case("RIGHT"): dir = "0"; break;
//                    case("TOP"): dir = "1"; break;
//                    case("LEFT"): dir = "2"; break;
//                    case("BOTTOM"): dir = "3"; break;
//                }
//                curCell = arena.obstacles.get(i).cell;
//                xCoord = curCell.col;
//                yCoord = 19-curCell.row; // invert y coordinates since algorithm uses bottom left as origin
//                if (DEBUG) {
//                    displayMessage(String.format("Obstacle(%d) coordinates: (%d, %d) with direction enum %s", i, xCoord, yCoord, dir));
//                }
//                cmd.append(String.format(Locale.getDefault(), "/(%02d,%02d,%02d,%s)", i, xCoord, yCoord, dir));
//                timerRunnable.startTime = 0;
//            }
//            btService.write(cmd.toString(), DEBUG);
            api.sendArenaInfo(arena, arena);
            TextView rbTV = findViewById(R.id.obstacleStatusTextView);
            rbTV.setText(R.string.calc_path);
            timerRunnable.startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
        } else if (b.getId() == R.id.startPath) {
            api.startRobot(arena);
            timerRunnable = new TimerRunnable(findViewById(R.id.timerTextViewPath));
//            btService.write("START/PATH", DEBUG);
            b.setText(R.string.stop_fastest_path);
            timerRunnable.startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
        } else {
            throw new RuntimeException("Invalid button id for startTimer");
        }
        toggleActivateButtons(false);
    }

    public void stopTimer(Button b) {
        if (b.getId() == R.id.startExplore) {
            b.setText(R.string.start_explore);
        } else {
            b.setText(R.string.start_fastest_path);
        }
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable = null;
        toggleActivateButtons(true);
//                btService.write("STOP", DEBUG);
        TextView rbTV = findViewById(R.id.obstacleStatusTextView);
        rbTV.setText(R.string.idle);
    }

    public void startStopTimer(View view){
        try {
            Button b = (Button) view;
            if (timerRunnable == null) {
                startTimer(b);
            } else {
                stopTimer(b);
            }
        } catch (Exception e) {
            if (DEBUG) {
                displayMessage(e.getMessage());
            }
            System.out.println(e.getMessage());
        }
    }

    private void toggleActivateButtons(boolean val){
        // deactivate obstacle and robot setting when robot is moving
        try{
            if (appDataModel.getArenaIntent().getValue() == ArenaIntent.SETTING_OBSTACLES) {
                findViewById(R.id.setObstacles).callOnClick();
            }
            if (appDataModel.getArenaIntent().getValue() == ArenaIntent.SETTING_ROBOT) {
                findViewById(R.id.setRobot).callOnClick();
            }
            findViewById(R.id.setObstacles).setEnabled(val);
            findViewById(R.id.setRobot).setEnabled(val);
            findViewById(R.id.clearObstacles).setEnabled(val);
            // disable tabs
            TabLayout tabLayout = findViewById(R.id.tabLayout);
            LinearLayout tabStrip = ((LinearLayout) tabLayout.getChildAt(0));
            tabStrip.setEnabled(val);
            for (int i = 0; i < tabStrip.getChildCount(); i++) {
                tabStrip.getChildAt(i).setClickable(val);
            }
            if (RUN_TO_END) {
                findViewById(R.id.startExplore).setEnabled(val);
                findViewById(R.id.startPath).setEnabled(val);
            }
        } catch (Exception e) {
        System.out.println(e.getMessage());
        }
    }
    private void showObstacleDialog() {
        int obsIndex = arena.obstacles.indexOf(arena.editingObs);
        int obsX = arena.editingObs.getCell().getCol();
        int obsY = arena.editingObs.getCell().getRow();
        String imageDir=arena.editingObs.getImageDir();;
        String imageID;
        if(arena.editingObs.explored == false){
             imageID=Integer.toString(obsIndex+1);
        }else{
            imageID = arena.editingObs.getImageID();
        }

        ObstacleDialogueFragment obstacleDialogueFragment =
                ObstacleDialogueFragment.newInstance(obsIndex,imageID,imageDir,obsX,obsY,btService);
        obstacleDialogueFragment.show(getFragmentManager(),"hello");
        obstacleDialogueFragment.setCancelable(false);
    }

    @Override
    public void dialogData(int obsIndex, String imageDir, int x, int y) {
        Cell curCell;
        Cell oldCell = arena.obstacles.get(obsIndex).cell;

        for (Map.Entry<Cell, RectF> entry : arena.gridMap.entrySet()) {
            curCell = entry.getKey();

            if(curCell.getCol() == x && curCell.getRow() == y && curCell.getType() == ""){
                curCell.setType("obstacle");
                arena.obstacles.get(obsIndex).setImageDir(imageDir);
                arena.obstacles.get(obsIndex).setCell(curCell);
                oldCell.setType("");
            }else if(curCell.getCol() == x && curCell.getRow() == y && curCell.getType() != ""){
                arena.obstacles.get(obsIndex).setImageDir(imageDir);
                if (curCell != oldCell) {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Grid is already occupied",
                            Toast.LENGTH_SHORT);

                    toast.show();
                }
            }
        }
        arena.invalidate();
    }

    @Override
    public void setObstacleEdit(boolean obsEdit) {
        arena.obstacleEdit = obsEdit;
    }

    @Override
    public void onPause(){
        super.onPause();
        if (arena == null) return;
        BluetoothService.obstacles = arena.obstacles;
        BluetoothService.cells = arena.cells;
    }

    @Override
    public void onResume(){
        super.onResume();
        if (BluetoothService.obstacles == null) return;
        arena.obstacles = BluetoothService.obstacles;
        for (Obstacle obs: arena.obstacles){
            obs.cell = arena.cells[obs.cell.col][obs.cell.row];
            obs.cell.setType("obstacle");
        }
    }



}