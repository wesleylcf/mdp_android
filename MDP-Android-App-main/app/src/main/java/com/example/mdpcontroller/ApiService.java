package com.example.mdpcontroller;

import android.view.View;

import com.example.mdpcontroller.arena.ArenaView;
import com.example.mdpcontroller.arena.Obstacle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ApiService {
    private final BluetoothService btService;
    public ApiService(BluetoothService btService) {
        this.btService = btService;
    }

    public void startRobot(View view) {
        try {
            JSONObject json = new JSONObject();
            json.put("cat", "control");
            json.put("value", "start");
            btService.write(json.toString(), false);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendArenaInfo(View view, ArenaView arena) {
        try {
            JSONObject arenaInfo = new JSONObject();
            arenaInfo.put("cat", "obstacles");
            JSONObject valueObject = new JSONObject();
            JSONArray obstaclesArray = new JSONArray();
            int id = 1;

            // Loop through all obstacles in ArenaView and add them to the JSON array
            for (Obstacle obstacle : arena.obstacles) {
                JSONObject obstacleJSON = new JSONObject();
                obstacleJSON.put("x", obstacle.cell.col);
                obstacleJSON.put("y", 19 - obstacle.cell.row);
                obstacleJSON.put("id", id++);
                int direction;
                switch (obstacle.imageDir) {
                    case "TOP":
                        direction = 1;
                        break;
                    case "RIGHT":
                        direction = 0;
                        break;
                    case "BOTTOM":
                        direction = 3;
                        break;
                    case "LEFT":
                        direction = 2;
                        break;
                    default:
                        direction = -1; // Handle unexpected values
                        break;
                }
                obstacleJSON.put("d", direction);
                obstaclesArray.put(obstacleJSON);
            }
            valueObject.put("obstacles", obstaclesArray);
            arenaInfo.put("value", valueObject);

            // Convert JSON to string and send via Bluetooth
            String jsonString = arenaInfo.toString();
            btService.write(jsonString, false);
            System.out.print("Obstacles sent: " + jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
