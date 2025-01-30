package com.example.mdpcontroller.tab;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.mdpcontroller.BluetoothService;
import com.example.mdpcontroller.MainActivity;
import com.example.mdpcontroller.R;


public class ExploreTabFragment extends Fragment {
    View view;
    public Button setRobotBtn;
    public Button setObstaclesBtn;
    public ArenaIntent arenaIntent;
    private boolean isRobotMove;
    private boolean isRobotStop;
    private boolean isReset;
    private AppDataModel appDataModel;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        arenaIntent = ArenaIntent.UNSET;
        isRobotMove = false;
        isRobotStop = false;
        isReset = false;
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_explore_tab, container, false);
        setRobotBtn = view.findViewById(R.id.setRobot);
        setObstaclesBtn = view.findViewById(R.id.setObstacles);
        appDataModel = new ViewModelProvider(requireActivity()).get(AppDataModel.class);
        appDataModel.setArenaIntent(arenaIntent);

        setRobotBtn.setOnClickListener(item -> {
            if (arenaIntent == ArenaIntent.SETTING_OBSTACLES) {
                return;
            }
            if (arenaIntent == ArenaIntent.UNSET) {
                arenaIntent = ArenaIntent.SETTING_ROBOT;
                setRobotBtn.setText(R.string.done);
            } else if (arenaIntent == ArenaIntent.SETTING_ROBOT) {
                arenaIntent = ArenaIntent.UNSET;
                setRobotBtn.setText(String.format(getString(R.string.set_btn_txt), "robot"));
            }
            appDataModel.setArenaIntent(arenaIntent);
        });

        setObstaclesBtn.setOnClickListener(item -> {
            if (arenaIntent == ArenaIntent.SETTING_ROBOT) {
                return;
            }
            if (arenaIntent == ArenaIntent.UNSET) {
                arenaIntent = ArenaIntent.SETTING_OBSTACLES;
                setObstaclesBtn.setText(R.string.done);
            } else if (arenaIntent == ArenaIntent.SETTING_OBSTACLES) {
                arenaIntent = ArenaIntent.UNSET;
                setObstaclesBtn.setText(String.format(getString(R.string.set_btn_txt), "obstacles"));
            }
            appDataModel.setArenaIntent(arenaIntent);
        });

        return view;
    }
}