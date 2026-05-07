package com.scheduler;

import org.cloudbus.cloudsim.DatacenterBroker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Custom DatacenterBroker implementing the SRDQ scheduling algorithm.
 *
 * Quantum formula (Elmougy et al. 2017, Eq. 1–3):
 *   Q1 (α=1): q_ij = q̃ + (q̃ / (B_ij + q_i(j-1)))²
 *   Q2 (α=0): q_ij = q̃ + (q̃ / (B_ij − q_i(j-1)))²
 *
 * Dynamic threshold (median) updates:
 *   On task completion : q̃ ← q̃ − q̃ / B_terminated
 *   On new task arrival: q̃ ← q̃ + q̃ / B_new
 */
public class SRDQBroker extends DatacenterBroker {

    // ─── Results ───────────────────────────────────────────────────
    private final List<String> scheduleTrace = new ArrayList<>();
    private final List<ScheduleResult> results = new ArrayList<>();
    private double threshold;   // dynamic q̃ — updated throughout execution

    // ─── Inner result record ────────────────────────────────────────
    public static class ScheduleResult {
        public final int    cloudletId;
        public final int    arrivalTime;
        public final long   burstMI;
        public final String queue;
        public final double startTime;
        public final double finishTime;
        public final double tat;
        public final double wt;
        public final double rt;
        public final String quantumUsed;

        public ScheduleResult(int cloudletId, int arrivalTime, long burstMI,
                              String queue, double startTime, double finishTime,
                              String quantumUsed) {
            this.cloudletId  = cloudletId;
            this.arrivalTime = arrivalTime;
            this.burstMI     = burstMI;
            this.queue       = queue;
            this.startTime   = startTime;
            this.finishTime  = finishTime;
            this.tat         = finishTime - arrivalTime;
            this.wt          = this.tat - burstMI;
            this.rt          = startTime - arrivalTime;
            this.quantumUsed = quantumUsed;
        }
    }

    // ─── Constructor ───────────────────────────────────────────────
    public SRDQBroker(String name) throws Exception { super(name); }

    // ─── Accessors ─────────────────────────────────────────────────
    public List<String>        getScheduleTrace() { return scheduleTrace; }
    public List<ScheduleResult> getResults()      { return results; }
    public double               getThreshold()    { return threshold; }

    // ════════════════════════════════════════════════════════════════
    //  CORE ALGORITHM — matches Elmougy et al. 2017 exactly
    // ════════════════════════════════════════════════════════════════

    public void runSRDQSchedule(List<SRDQCloudlet> cloudlets, int mips) {
        scheduleTrace.clear();
        results.clear();
        if (cloudlets.isEmpty()) return;

        // ── 1. Initial threshold = median of ALL burst times ───────
        threshold = computeInitialMedian(cloudlets);
        scheduleTrace.add(String.format(
            "[INIT] Threshold (median burst) = %.2f MI", threshold));

        // ── 2. Partition into Q1 (SJF) and Q2 (RR) ────────────────
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

        shortQ.sort((a, b) -> {
            if (a.getArrivalTime() != b.getArrivalTime())
                return Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            return Long.compare(a.getBurstMI(), b.getBurstMI());
        });
        longQ.sort((a, b) -> Integer.compare(a.getArrivalTime(), b.getArrivalTime()));

        scheduleTrace.add(String.format(
            "[INIT] Short Queue (SJF): %d cloudlets | Long Queue (RR): %d cloudlets",
            shortQ.size(), longQ.size()));

        // ── 3. Track structures ────────────────────────────────────
        boolean[] sjfDone  = new boolean[shortQ.size()];
        boolean[] arrivedQ2 = new boolean[longQ.size()]; // tracks threshold arrival updates

        LinkedList<Integer> readyRR = new LinkedList<>();
        boolean[] inQueueRR = new boolean[longQ.size()];

        double[] firstStart = new double[cloudlets.size()];
        Arrays.fill(firstStart, -1);

        double currentTime  = 0;
        int    q1Counter    = 0;
        int    finishedTotal = 0;
        int    totalProcs   = cloudlets.size();

        // ── 4. Main scheduling loop ────────────────────────────────
        while (finishedTotal < totalProcs) {

            // Enqueue newly arrived Q2 cloudlets + update threshold on arrival
            enqueueArrivedRR(longQ, readyRR, inQueueRR, arrivedQ2, currentTime);

            int  bestSJF = findBestSJF(shortQ, sjfDone, currentTime);
            boolean runSJF = (q1Counter < 2 && bestSJF != -1);
            boolean runRR  = (!runSJF && !readyRR.isEmpty());
            if (!runSJF && !runRR && bestSJF != -1) runSJF = true; // fallback

            if (runSJF) {
                // ── Run SJF cloudlet to completion ─────────────────
                SRDQCloudlet proc  = shortQ.get(bestSJF);
                double       start = currentTime;
                currentTime += proc.getBurstMI();
                proc.setRemaining(0);
                proc.setSrdqStartTime(start);
                proc.setSrdqFinishTime(currentTime);

                if (firstStart[proc.getCloudletId()] < 0)
                    firstStart[proc.getCloudletId()] = start;

                sjfDone[bestSJF] = true;
                finishedTotal++;
                q1Counter++;

                // Dynamic threshold update on completion (paper Eq. 5)
                threshold = updateThresholdOnCompletion(threshold, proc.getBurstMI());

                scheduleTrace.add(String.format(
                    "[t=%.0f]  SJF  -> Cloudlet #%d  (burst=%d, q̃=%.2f)  [q1Counter=%d]",
                    start, proc.getCloudletId(), proc.getBurstMI(), threshold, q1Counter));

                results.add(new ScheduleResult(
                    proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                    "SJF", start, currentTime, "N/A"));

            } else if (runRR) {
                // ── Run RR cloudlet for one quantum slice ──────────
                int          idx  = readyRR.poll();
                SRDQCloudlet proc = longQ.get(idx);

                // Paper quantum formula (Eq. 3): Q2, α=0
                double quantum = computePaperQuantum(threshold, proc.getRemaining(),
                                                     proc.getLastQuantum(), false);
                quantum = Math.max(1, quantum);

                double start = currentTime;
                if (firstStart[proc.getCloudletId()] < 0)
                    firstStart[proc.getCloudletId()] = start;

                long exec = Math.min((long) Math.ceil(quantum), proc.getRemaining());
                currentTime += exec;
                proc.setRemaining(proc.getRemaining() - exec);
                proc.setLastQuantum(quantum);   // save for next round (q_i(j-1))

                if (proc.getSrdqStartTime() < 0)
                    proc.setSrdqStartTime(start);

                scheduleTrace.add(String.format(
                    "[t=%.0f]  RR   -> Cloudlet #%d  (burst=%d, quantum=%.1f, remaining=%d, q̃=%.2f)  [q1Counter=0]",
                    start, proc.getCloudletId(), proc.getBurstMI(),
                    quantum, proc.getRemaining(), threshold));

                // Enqueue mid-slice arrivals BEFORE re-queuing
                enqueueArrivedRR(longQ, readyRR, inQueueRR, arrivedQ2, currentTime);

                if (proc.getRemaining() > 0) {
                    readyRR.add(idx);
                } else {
                    finishedTotal++;
                    inQueueRR[idx] = false;
                    proc.setSrdqFinishTime(currentTime);

                    // Dynamic threshold update on completion (paper Eq. 5)
                    threshold = updateThresholdOnCompletion(threshold, proc.getBurstMI());

                    results.add(new ScheduleResult(
                        proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                        "RR", firstStart[proc.getCloudletId()], currentTime,
                        String.format("%.1f", quantum)));
                }
                q1Counter = 0;

            } else {
                // ── Idle: advance to next arrival ──────────────────
                double nextArrival = Double.MAX_VALUE;
                for (int i = 0; i < shortQ.size(); i++)
                    if (!sjfDone[i])
                        nextArrival = Math.min(nextArrival, shortQ.get(i).getArrivalTime());
                for (SRDQCloudlet c : longQ)
                    if (c.getRemaining() > 0)
                        nextArrival = Math.min(nextArrival, c.getArrivalTime());
                if (nextArrival == Double.MAX_VALUE) break;
                currentTime = Math.max(currentTime, nextArrival);
            }
        }

        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Paper quantum formula (Elmougy et al. Eq. 1–3).
     *   Q1 (isQ1=true):  q = q̃ + (q̃ / (B + q_prev))²
     *   Q2 (isQ1=false): q = q̃ + (q̃ / (B - q_prev))²
     * First round: q_prev = 0, so both reduce to q̃ + (q̃/B)²
     */
    public static double computePaperQuantum(double qe, long remainingBurst,
                                             double prevQuantum, boolean isQ1) {
        double denom;
        if (isQ1) {
            denom = remainingBurst + prevQuantum;
        } else {
            denom = remainingBurst - prevQuantum;
            if (denom <= 0) denom = remainingBurst; // safety: avoid ≤0
        }
        if (denom <= 0) denom = 1;
        return qe + Math.pow(qe / denom, 2);
    }

    /**
     * Dynamic threshold update when a task completes (paper Eq. 5).
     *   q̃ ← q̃ − q̃ / B_terminated
     */
    public static double updateThresholdOnCompletion(double qe, long burstTerminated) {
        if (burstTerminated <= 0) return qe;
        return Math.max(1.0, qe - qe / burstTerminated);
    }

    /**
     * Dynamic threshold update when a new task arrives (paper Eq. 4).
     *   q̃ ← q̃ + q̃ / B_new
     */
    public static double updateThresholdOnArrival(double qe, long burstNew) {
        if (burstNew <= 0) return qe;
        return qe + qe / burstNew;
    }

    /** Initial threshold = median of all burst times. */
    private double computeInitialMedian(List<SRDQCloudlet> cloudlets) {
        long[] bursts = new long[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++)
            bursts[i] = cloudlets.get(i).getBurstMI();
        Arrays.sort(bursts);
        int n = bursts.length;
        return (n % 2 == 1) ? bursts[n / 2]
                            : (bursts[n / 2 - 1] + bursts[n / 2]) / 2.0;
    }

    private int findBestSJF(List<SRDQCloudlet> shortQ, boolean[] done, double t) {
        int best = -1;
        for (int i = 0; i < shortQ.size(); i++) {
            if (!done[i] && shortQ.get(i).getArrivalTime() <= t) {
                if (best == -1 || shortQ.get(i).getBurstMI() < shortQ.get(best).getBurstMI())
                    best = i;
            }
        }
        return best;
    }

    /**
     * Enqueue Q2 cloudlets that have arrived by currentTime.
     * Also updates threshold on their first arrival (paper Eq. 4).
     */
    private void enqueueArrivedRR(List<SRDQCloudlet> longQ,
                                   LinkedList<Integer> readyRR,
                                   boolean[] inQueueRR,
                                   boolean[] arrivedQ2,
                                   double currentTime) {
        for (int i = 0; i < longQ.size(); i++) {
            SRDQCloudlet c = longQ.get(i);
            if (c.getArrivalTime() <= currentTime && c.getRemaining() > 0) {
                if (!arrivedQ2[i]) {
                    // First arrival: update threshold (paper Eq. 4)
                    threshold = updateThresholdOnArrival(threshold, c.getBurstMI());
                    arrivedQ2[i] = true;
                }
                if (!inQueueRR[i]) {
                    readyRR.add(i);
                    inQueueRR[i] = true;
                }
            }
        }
    }
}
