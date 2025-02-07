package com.example.mdpcontroller.arena;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * I look like this
 * [R, H, R]
 * [R, R, R]
 * [R, R, R]
 * Where each element in the array is a Cell, and letter corresponds to the Cell type
 */
public class Robot {
    public static Cell[][] robotMatrix;
    private static Cell[][] grid;
    //  Looks like this:
    //  [B, H, B]
    //  [B, B, B]
    //  [B, B, B]
    public static String robotDir;
    private static int numGrids = 3; //number of grids to move by for BR,BL,R,L

    public static void initializeRobot(Cell[][] cells){
        robotMatrix = new Cell[3][3];
        robotMatrix[1][1] = null;
        grid = cells;
    }

    public static void printState() {
        System.out.println(String.format("DIRECTION=%s", robotDir));
    }
    public static boolean setRobot(int xCenter, int yCenter,  String dir, ArrayList<Obstacle> obstacles){
        if (!isValidNewPosition(xCenter, yCenter, obstacles)) {
            System.out.println("[setRobot] Invalid position");
            return false;
        }

        setRobotPosition(xCenter, yCenter);
        setRobotDirection(dir);
        return true;
    }
    /**
     * Does NOT validate if position is valid, Robot should not know about the state of the grid
     * Perform validation before calling this method
     * @param xCenter x Coordinate of new centre of robot
     * @param yCenter y Coordinate of new centre of robot
     */
    private static void setRobotPosition(int xCenter, int yCenter){
        int yTopLeft=yCenter-1, xTopLeft= xCenter-1;
        Cell curCell;
        if(yCenter<1 || yCenter>=19 || xCenter>=19 || xCenter<1){
            System.out.println("Out of bounds: Robot need nine cells");
        }else{
            // wipe old robot position
            if (robotMatrix[0][0] != null){ // skip on initial robot set
                for (int i=0; i<robotMatrix[0].length; i++){ // iterate through rows: i = x coordinate
                    for (int j=0; j<robotMatrix.length; j++){ // iterate through cols: j = y coordinate
                        robotMatrix[i][j].type = "";
                    }
                }
            }

            // set new robot position
            for (int i=0; i<robotMatrix[0].length; i++){ // iterate through rows: i = x coordinate
                for (int j=0; j<robotMatrix.length; j++){ // iterate through cols: j = y coordinate
                    curCell = grid[xTopLeft+i][yTopLeft+j];
                    curCell.type = "robot";
                    robotMatrix[i][j] = curCell;
                }
            }
        }

    }

    /**
     * Sets the direction of the Robot
     * @param dir Possible values: N, S, E, W
     */
    private static void setRobotDirection(String dir){
        robotDir = dir;
        // get head
        int xHead =1 , yHead = 0; // Default is N
        switch(dir){
            case("N"): {
                xHead = 1; yHead = 0; break;
            }
            case("S"): {
                xHead = 1; yHead = 2; break;
            }
            case("E"): {
                xHead = 2; yHead = 1; break;
            }
            case("W"): {
                xHead = 0; yHead = 1; break;
            }
        }

        // Update robot direction
        for (int i=0; i<robotMatrix[0].length; i++){ // iterate through cols: i = x coordinate
            for (int j=0; j<robotMatrix.length; j++){ // iterate through cols: j = y coordinate
                if (i==xHead && j==yHead) robotMatrix[i][j].type = "robotHead";
                else robotMatrix[i][j].type = "robot"; // reset old head
            }
        }
    }

    /**
     * Check whether at least one of the new robot cells is an obstacle.
     * @param xCenter row of new centre of robot in the 2D array arena
     * @param yCenter col of new centre of robot in the 2D array arena
     * @param obstacles list of plotted obstacles
     */
    private static boolean isValidNewPosition(int xCenter, int yCenter, ArrayList<Obstacle> obstacles) {
        for (int x = xCenter - 1; x <= xCenter + 1; x++) {
            for (int y = yCenter - 1; y <= yCenter + 1; y++) {
                // contains an obstacle
                for (Obstacle obs : obstacles) {
                    if (obs.cell.col == x && obs.cell.row == y) {
                        System.out.println(String.format("[isValidNewPosition] Contains obstacle(%d,%d)", x, y));
                        return false;
                    }
                }
                // is out of bounds
                if (xCenter-1 < 0 || xCenter+1 > 19 || yCenter-1 < 0 || yCenter > 19) {
                    System.out.println(String.format("[isValidNewPosition] Out of bounds: (%d, %d)", xCenter, yCenter));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * All the robot movement
     * @param dir current direction of the robot
     * @param movement direction of robot movement
     * @param obstacles list of current obstacles
     */
    public static void moveRobot(String dir,String movement,ArrayList<Obstacle> obstacles){
        if (!isValidMove(dir, movement, obstacles)) {
            return;
        }

        switch(movement){
            case("F"):
            case("B"):
                forwardBackwardMove(dir,movement,obstacles);
                break;
            case("L"):
                turnLeft(dir,obstacles);
                break;
            case("BL"):
                reverseLeft(dir,obstacles);
                break;
            case("R"):
                turnRight(dir,obstacles);
                break;
            case("BR"):
                reverseRight(dir,obstacles);
                break;
        }
        return;
    }

    /*
    * For turns, we need to check both the adjacent 3x3 cell in addition to the target 3x3 cell.
    * The intuition is that to get to a diagonal cell, we need to first move to the adjacent cell.
    * For example from (0,0) if we turn right, we go to (0,1) first then (1,1)
    * */
    public static boolean isValidMove(String dir, String movement, ArrayList<Obstacle> obstacles) {
        ArrayList<String> forwardTurns = new ArrayList<>(Arrays.asList("L", "R"));
        ArrayList<String> backwardTurns = new ArrayList<>(Arrays.asList("BL", "BR"));
        int xCenter = robotMatrix[1][1].col, yCenter = robotMatrix[1][1].row;
        if (forwardTurns.contains(movement)) {
            if (dir.equals("E")) xCenter += 3;
            else if (dir.equals("N")) yCenter -= 3;
            else if (dir.equals("W")) xCenter -= 3;
            else if (dir.equals("S")) yCenter += 3;

            for (int x = xCenter - 1; x <= xCenter + 1; x++) {
                for (int y = yCenter - 1; y <= yCenter + 1; y++) {
                    // contains an obstacle
                    for (Obstacle obs : obstacles) {
                        if (obs.cell.col == x && obs.cell.row == y) {
                            System.out.println(String.format("[isValidMove] Contains obstacle(%d, %d)", x, y));
                            return false;
                        }
                    }
                }
            }
        } else if (backwardTurns.contains(movement)) {
            if (dir.equals("E")) xCenter -= 3;
            else if (dir.equals("N")) yCenter += 3;
            else if (dir.equals("W")) xCenter += 3;
            else if (dir.equals("S")) yCenter -= 3;

            for (int x = xCenter - 1; x <= xCenter + 1; x++) {
                for (int y = yCenter - 1; y <= yCenter + 1; y++) {
                    // contains an obstacle
                    for (Obstacle obs : obstacles) {
                        if (obs.cell.col == x && obs.cell.row == y) {
                            System.out.println("[isValidMove] Contains obstacle");
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Turn Robot left
     * @param dir current direction of the robot
     * @param movement direction of robot movement
     * @param obstacles list of current obstacles
     */
    private static void forwardBackwardMove(String dir, String movement,ArrayList<Obstacle> obstacles){
        int xCenter = robotMatrix[1][1].row;
        int yCenter = robotMatrix[1][1].col;
        if((movement == "F" && dir == "N")||(movement == "B" && dir == "S")){
            xCenter -=1;
            setRobot(yCenter,xCenter,dir,obstacles);
        }else if((movement == "F" && dir == "S")||(movement == "B" && dir == "N")){
            xCenter +=1;
        }else if((movement == "F" && dir == "W")||(movement == "B" && dir == "E")){
            yCenter -=1;
        }else if((movement == "F" && dir == "E")||(movement == "B" && dir == "W")){
            yCenter +=1;
        }
        setRobot(yCenter,xCenter,dir,obstacles);

    }

    /**
     * Turn Robot left
     * @param dir current direction of the robot
     * @param obstacles current list of obstacles
     */
    private static void turnLeft(String dir,ArrayList<Obstacle> obstacles){
        int xCenter = robotMatrix[1][1].row;
        int yCenter = robotMatrix[1][1].col;
        String nextDir = dir;
        switch (dir){
            case"N":
                xCenter -=numGrids;
                yCenter -=numGrids;
                nextDir = "W";
                break;
            case"S":
                xCenter +=numGrids;
                yCenter +=numGrids;
                nextDir = "E";
                break;
            case"E":
                xCenter -=numGrids;
                yCenter +=numGrids;
                nextDir = "N";
                break;
            case"W":
                xCenter +=numGrids;
                yCenter -=numGrids;
                nextDir = "S";
                break;
        }
        setRobot(yCenter,xCenter,nextDir,obstacles);

    }
    /**
     * Turn Robot right
     * @param dir current direction of the robot
     * @param obstacles current list of obstacles
     */
    private static void turnRight(String dir,ArrayList<Obstacle> obstacles){
        int xCenter = robotMatrix[1][1].row;
        int yCenter = robotMatrix[1][1].col;
        String nextDir = dir;
        switch (dir){
            case"N":
                xCenter -=numGrids;
                yCenter +=numGrids;
                nextDir = "E";
                break;
            case"S":
                xCenter +=numGrids;
                yCenter -=numGrids;
                nextDir = "W";
                break;
            case"E":
                xCenter +=numGrids;
                yCenter +=numGrids;
                nextDir = "S";
                break;
            case"W":
                xCenter -=numGrids;
                yCenter -=numGrids;
                nextDir = "N";
                break;

        }
        setRobot(yCenter, xCenter, nextDir,obstacles);
    }

    /**
     * Reverse Robot right
     * @param dir current direction of the robot
     * @param obstacles current list of obstacles
     */
    private static void reverseRight(String dir, ArrayList<Obstacle> obstacles){
        int xCenter = robotMatrix[1][1].row;
        int yCenter = robotMatrix[1][1].col;
        String nextDir = dir;
        switch (dir) {
            case "N":
                xCenter += numGrids;
                yCenter += numGrids;
                nextDir = "W";
                break;
            case "S":
                xCenter -= numGrids;
                yCenter -= numGrids;
                nextDir = "E";
                break;
            case "E":
                xCenter += numGrids;
                yCenter -= numGrids;
                nextDir = "N";
                break;
            case "W":
                xCenter -= numGrids;
                yCenter += numGrids;
                nextDir = "S";
                break;
        }
        setRobot(yCenter,xCenter,nextDir,obstacles);
    }
    /**
     * Reverse Robot left
     * @param dir current direction of the robot
     * @param obstacles current list of obstacles
     */
    private static void reverseLeft(String dir, ArrayList<Obstacle> obstacles){
        int xCenter = robotMatrix[1][1].row;
        int yCenter = robotMatrix[1][1].col;
        String nextDir = dir;
        switch (dir) {
            case "N":
                xCenter += numGrids;
                yCenter -= numGrids;
                nextDir = "E";
                break;
            case "S":
                xCenter -= numGrids;
                yCenter += numGrids;
                nextDir = "W";
                break;
            case "E":
                xCenter -= numGrids;
                yCenter -= numGrids;
                nextDir = "S";
                break;
            case "W":
                xCenter += numGrids;
                yCenter += numGrids;
                nextDir = "N";
                break;
        }
        setRobot(yCenter,xCenter,nextDir,obstacles);
    }

}
