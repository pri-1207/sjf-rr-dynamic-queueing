package com.scheduler;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;

/**
 * Extended Cloudlet that carries SRDQ-specific metadata.
 *
 * Extra fields beyond the base Cloudlet:
 *   - arrivalTime:   when this cloudlet enters the system (from the dataset)
 *   - assignedQueue:  "SJF" or "RR" — set by SRDQBroker after threshold calc
 *   - quantumUsed:   the dynamic quantum value when this cloudlet last ran
 *                     (-1 for SJF cloudlets, since they run to completion)
 *   - remaining:     remaining MI to execute (used by broker for RR slicing)
 *   - startTime:     the simulation time when this cloudlet first starts executing
 *   - finishTime:    the simulation time when this cloudlet finishes
 */
public class SRDQCloudlet extends Cloudlet {

    private int arrivalTime;
    private String assignedQueue;  // "SJF" or "RR"
    private int quantumUsed;       // -1 for SJF
    private long remaining;        // remaining MI for RR slicing
    private double startTime;      // broker-computed start time
    private double finishTime;     // broker-computed finish time
    private double lastQuantum;    // q_i(j-1): quantum used in previous round (paper Eq.1)

    /**
     * Creates an SRDQCloudlet.
     *
     * @param id           unique cloudlet ID
     * @param length       total length in MI (burst time)
     * @param pesNumber    number of PEs required (always 1 for this project)
     * @param fileSize     input file size
     * @param outputSize   output file size
     * @param cpuModel     CPU utilization model
     * @param ramModel     RAM utilization model
     * @param bwModel      bandwidth utilization model
     * @param arrivalTime  the arrival time from the dataset
     */
    public SRDQCloudlet(int id, long length, int pesNumber,
                        long fileSize, long outputSize,
                        UtilizationModel cpuModel,
                        UtilizationModel ramModel,
                        UtilizationModel bwModel,
                        int arrivalTime) {
        super(id, length, pesNumber, fileSize, outputSize,
              cpuModel, ramModel, bwModel);
        this.arrivalTime = arrivalTime;
        this.assignedQueue = "";
        this.quantumUsed = -1;
        this.remaining = length;
        this.startTime = -1;
        this.finishTime = -1;
    }

    // ─── Getters & Setters ─────────────────────────────────────────

    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getAssignedQueue() {
        return assignedQueue;
    }

    public void setAssignedQueue(String assignedQueue) {
        this.assignedQueue = assignedQueue;
    }

    public int getQuantumUsed() {
        return quantumUsed;
    }

    public void setQuantumUsed(int quantumUsed) {
        this.quantumUsed = quantumUsed;
    }

    public long getRemaining() {
        return remaining;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    public double getSrdqStartTime() {
        return startTime;
    }

    public void setSrdqStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getSrdqFinishTime() {
        return finishTime;
    }

    public void setSrdqFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    public double getLastQuantum() { return lastQuantum; }
    public void setLastQuantum(double lastQuantum) { this.lastQuantum = lastQuantum; }

    /**
     * Burst time in MI (convenience alias for getCloudletLength()).
     */
    public long getBurstMI() {
        return getCloudletLength();
    }
}
