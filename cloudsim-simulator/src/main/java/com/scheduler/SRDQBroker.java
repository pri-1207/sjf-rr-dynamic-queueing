package com.scheduler;

import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Custom DatacenterBroker implementing the SRDQ scheduling algorithm.
 *
 * All scheduling decisions (threshold, queue assignment, 2:1 interleaving,
 * dynamic quantum) are made here at the broker level.  VMs use
 * CloudletSchedulerSpaceShared — the broker controls the submission order.
 *
 * Algorithm reference:
 *   Elmougy, Sarhan & Joundy (2017).  "A novel hybrid of Shortest job first
 *   and round Robin with dynamic variable quantum time task scheduling technique."
 */
public class SRDQBroker extends DatacenterBroker {

    // ─── Scheduling results ────────────────────────────────────────

    /** Ordered trace entries printed during scheduling. */
    private final List<String> scheduleTrace = new ArrayList<>();

    /** Final per-cloudlet results, populated after runSRDQSchedule(). */
    private final List<ScheduleResult> results = new ArrayList<>();

    /** Threshold computed from median burst time. */
    private long threshold;

    // ─── Inner class for per-cloudlet results ──────────────────────

    public static class ScheduleResult {
        public final int cloudletId;
        public final int arrivalTime;
        public final long burstMI;
        public final String queue;       // "SJF" or "RR"
        public final double startTime;
        public final double finishTime;
        public final double tat;         // Turnaround Time
        public final double wt;          // Waiting Time
        public final double rt;          // Response Time
        public final String quantumUsed; // quantum value or "N/A"

        public ScheduleResult(int cloudletId, int arrivalTime, long burstMI,
                              String queue, double startTime, double finishTime,
                              String quantumUsed) {
            this.cloudletId = cloudletId;
            this.arrivalTime = arrivalTime;
            this.burstMI = burstMI;
            this.queue = queue;
            this.startTime = startTime;
            this.finishTime = finishTime;
            this.tat = finishTime - arrivalTime;
            this.wt = this.tat - burstMI;
            this.rt = startTime - arrivalTime;
            this.quantumUsed = quantumUsed;
        }
    }

    // ─── Constructor ───────────────────────────────────────────────

    public SRDQBroker(String name) throws Exception {
        super(name);
    }

    // ─── Accessors ─────────────────────────────────────────────────

    public List<String> getScheduleTrace() {
        return scheduleTrace;
    }

    public List<ScheduleResult> getResults() {
        return results;
    }

    public long getThreshold() {
        return threshold;
    }

    // ════════════════════════════════════════════════════════════════
    //  CORE ALGORITHM
    // ════════════════════════════════════════════════════════════════

    /**
     * Runs the full SRDQ scheduling algorithm on the given cloudlets.
     * This must be called before CloudSim.startSimulation().
     *
     * @param cloudlets  the cloudlets to schedule (with arrivalTime set)
     * @param mips       MIPS rating of the target VM (for time conversion)
     */
    public void runSRDQSchedule(List<SRDQCloudlet> cloudlets, int mips) {
        scheduleTrace.clear();
        results.clear();

        if (cloudlets.isEmpty()) return;

        // ── 1. Compute threshold (median of burst times) ───────────
        threshold = computeMedianThreshold(cloudlets);
        scheduleTrace.add(String.format(
            "[INIT] Threshold (median burst) = %d MI", threshold));

        // ── 2. Partition into Short Queue (SJF) and Long Queue (RR) ─
        List<SRDQCloudlet> shortQ = new ArrayList<>();
        List<SRDQCloudlet> longQ  = new ArrayList<>();

        for (SRDQCloudlet c : cloudlets) {
            if (c.getBurstMI() <= threshold) {
                c.setAssignedQueue("SJF");
                shortQ.add(c);
            } else {
                c.setAssignedQueue("RR");
                longQ.add(c);
            }
        }

        // Sort shortQ by arrival (tie-break by burst)
        shortQ.sort((a, b) -> {
            if (a.getArrivalTime() != b.getArrivalTime())
                return Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            return Long.compare(a.getBurstMI(), b.getBurstMI());
        });

        // Sort longQ by arrival
        longQ.sort((a, b) -> Integer.compare(a.getArrivalTime(), b.getArrivalTime()));

        scheduleTrace.add(String.format(
            "[INIT] Short Queue (SJF): %d cloudlets | Long Queue (RR): %d cloudlets",
            shortQ.size(), longQ.size()));

        // ── 3. Prepare tracking structures ─────────────────────────
        boolean[] sjfDone = new boolean[shortQ.size()];

        // RR ready queue (indices into longQ)
        LinkedList<Integer> readyRR = new LinkedList<>();
        boolean[] inQueueRR = new boolean[longQ.size()];

        // Track first-start for response time
        double[] firstStart = new double[cloudlets.size()];
        Arrays.fill(firstStart, -1);

        // Map from cloudlet ID to its index in firstStart
        // (cloudlet IDs are 0-based from Main.java)

        double currentTime = 0;
        int q1Counter = 0;
        int finishedTotal = 0;
        int totalProcs = cloudlets.size();

        // ── 4. Main scheduling loop ────────────────────────────────
        while (finishedTotal < totalProcs) {

            // Enqueue newly arrived RR cloudlets
            enqueueArrivedRR(longQ, readyRR, inQueueRR, currentTime);

            // Find best SJF candidate (shortest burst, arrived)
            int bestSJF = findBestSJF(shortQ, sjfDone, currentTime);

            boolean runSJF = false;
            boolean runRR  = false;

            // Decision logic: 2:1 interleaving
            if (q1Counter < 2 && bestSJF != -1) {
                runSJF = true;
            } else if (!readyRR.isEmpty()) {
                runRR = true;
            } else if (bestSJF != -1) {
                // Fallback: longQ empty, keep running SJF
                runSJF = true;
            }

            if (runSJF) {
                // ── Run SJF cloudlet to completion ─────────────────
                SRDQCloudlet proc = shortQ.get(bestSJF);
                double start = currentTime;
                currentTime += proc.getBurstMI();
                proc.setRemaining(0);
                proc.setSrdqStartTime(start);
                proc.setSrdqFinishTime(currentTime);

                // Track first start for response time
                if (firstStart[proc.getCloudletId()] < 0) {
                    firstStart[proc.getCloudletId()] = start;
                }

                sjfDone[bestSJF] = true;
                finishedTotal++;
                q1Counter++;

                scheduleTrace.add(String.format(
                    "[t=%.0f]  SJF  -> Cloudlet #%d  (burst=%d)   [q1Counter=%d]",
                    start, proc.getCloudletId(), proc.getBurstMI(), q1Counter));

                results.add(new ScheduleResult(
                    proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                    "SJF", start, currentTime, "N/A"));

            } else if (runRR) {
                // ── Run RR cloudlet for one quantum slice ──────────
                int quantum = computeDynamicQuantum(longQ, currentTime);
                int idx = readyRR.poll();
                SRDQCloudlet proc = longQ.get(idx);

                double start = currentTime;

                // Track first start for response time
                if (firstStart[proc.getCloudletId()] < 0) {
                    firstStart[proc.getCloudletId()] = start;
                }

                long exec = Math.min(quantum, proc.getRemaining());
                currentTime += exec;
                proc.setRemaining(proc.getRemaining() - exec);
                proc.setQuantumUsed(quantum);

                if (proc.getSrdqStartTime() < 0) {
                    proc.setSrdqStartTime(start);
                }

                scheduleTrace.add(String.format(
                    "[t=%.0f]  RR   -> Cloudlet #%d  (burst=%d, quantum=%d, remaining=%d)  [q1Counter=0]",
                    start, proc.getCloudletId(), proc.getBurstMI(),
                    quantum, proc.getRemaining()));

                // ── Mid-execution arrivals: enqueue BEFORE re-queuing ──
                enqueueArrivedRR(longQ, readyRR, inQueueRR, currentTime);

                if (proc.getRemaining() > 0) {
                    readyRR.add(idx);
                } else {
                    finishedTotal++;
                    inQueueRR[idx] = false;
                    proc.setSrdqFinishTime(currentTime);

                    results.add(new ScheduleResult(
                        proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                        "RR", firstStart[proc.getCloudletId()], currentTime,
                        String.valueOf(quantum)));
                }

                q1Counter = 0;

            } else {
                // ── Idle: jump to next arrival ─────────────────────
                double nextArrival = Double.MAX_VALUE;
                for (int i = 0; i < shortQ.size(); i++) {
                    if (!sjfDone[i]) {
                        nextArrival = Math.min(nextArrival, shortQ.get(i).getArrivalTime());
                    }
                }
                for (SRDQCloudlet c : longQ) {
                    if (c.getRemaining() > 0) {
                        nextArrival = Math.min(nextArrival, c.getArrivalTime());
                    }
                }
                if (nextArrival == Double.MAX_VALUE) break;
                currentTime = Math.max(currentTime, nextArrival);
            }
        }

        // ── Add results for RR cloudlets that finished via multiple slices ──
        // (These were already added when remaining hit 0 above)

        // Sort results by cloudlet ID for clean output
        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Compute the median of all cloudlet burst times (MI lengths).
     * For even-count lists: floor of the average of the two middle values.
     */
    private long computeMedianThreshold(List<SRDQCloudlet> cloudlets) {
        long[] bursts = new long[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) {
            bursts[i] = cloudlets.get(i).getBurstMI();
        }
        Arrays.sort(bursts);

        int n = bursts.length;
        if (n % 2 == 1) {
            return bursts[n / 2];
        } else {
            // Floor of average of two middle values
            return (long) Math.floor((bursts[n / 2 - 1] + bursts[n / 2]) / 2.0);
        }
    }

    /**
     * Compute dynamic quantum = ceil(avg remaining MI of arrived, unfinished longQ cloudlets).
     * Only includes cloudlets whose arrivalTime <= currentTime and remaining > 0.
     * Minimum quantum = 1.
     */
    private int computeDynamicQuantum(List<SRDQCloudlet> longQ, double currentTime) {
        long sum = 0;
        int count = 0;
        for (SRDQCloudlet c : longQ) {
            if (c.getRemaining() > 0 && c.getArrivalTime() <= currentTime) {
                sum += c.getRemaining();
                count++;
            }
        }
        if (count == 0) return 1;
        return Math.max(1, (int) Math.ceil((double) sum / count));
    }

    /**
     * Find the best SJF candidate: shortest burst, arrival <= currentTime.
     * Returns index into shortQ, or -1 if none available.
     */
    private int findBestSJF(List<SRDQCloudlet> shortQ, boolean[] done, double currentTime) {
        int best = -1;
        for (int i = 0; i < shortQ.size(); i++) {
            if (!done[i] && shortQ.get(i).getArrivalTime() <= currentTime) {
                if (best == -1 || shortQ.get(i).getBurstMI() < shortQ.get(best).getBurstMI()) {
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * Enqueue all longQ cloudlets that have arrived by currentTime and are
     * not already in the ready queue and have remaining > 0.
     */
    private void enqueueArrivedRR(List<SRDQCloudlet> longQ,
                                   LinkedList<Integer> readyRR,
                                   boolean[] inQueueRR,
                                   double currentTime) {
        for (int i = 0; i < longQ.size(); i++) {
            if (!inQueueRR[i]
                    && longQ.get(i).getRemaining() > 0
                    && longQ.get(i).getArrivalTime() <= currentTime) {
                readyRR.add(i);
                inQueueRR[i] = true;
            }
        }
    }
}
