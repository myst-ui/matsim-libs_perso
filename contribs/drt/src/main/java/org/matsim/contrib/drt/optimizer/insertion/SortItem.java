package org.matsim.contrib.drt.optimizer.insertion;


public class SortItem {
    double time;
    int occupancyChange;

    public SortItem(double time, int occupancyChange) {
        this.time = time;
        this.occupancyChange = occupancyChange;
    }

    public double getTime() {
        return time;
    }

    public int getOccupancyChange() {
        return occupancyChange;
    }
}