package com.example.mdpcontroller.tab;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AppDataModel extends ViewModel {
    private final MutableLiveData<ArenaIntent> arenaIntent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRobotMove = new MutableLiveData<Boolean>();
    private final MutableLiveData<Boolean> isRobotStop = new MutableLiveData<Boolean>();
    private final MutableLiveData<Boolean> isReset = new MutableLiveData<Boolean>();
    private final MutableLiveData<String> robotDirection = new MutableLiveData<String>();

    public void setArenaIntent(ArenaIntent intent) {
        arenaIntent.setValue(intent);
    }

    public LiveData<ArenaIntent> getArenaIntent() {
        return arenaIntent;
    }
    public void setRobotMove(Boolean item) {
        isRobotMove.setValue(item);
    }
    public LiveData<Boolean> getRobotMove() {
        return isRobotMove;
    }
    public void setRobotStop(Boolean item) {
        isRobotStop.setValue(item);
    }
    public LiveData<Boolean> getRobotStop() {
        return isRobotStop;
    }
    public void setIsReset(Boolean item) {
        isReset.setValue(item);
    }
    public LiveData<Boolean> getIsReset() {
        return isReset;
    }
    public void setRobotDirection(String item) {
        robotDirection.setValue(item);
    }
    public LiveData<String> getRobotDirection() {
        return robotDirection;
    }
}
