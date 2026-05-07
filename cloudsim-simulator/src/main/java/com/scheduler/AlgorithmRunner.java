package com.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Pure-Java implementations of comparison scheduling algorithms.
 * All schedulers model a single-CPU (single-VM equivalent) execution
 * so that results are comparable to the SRDQ 1-VM baseline.
 *
 * Algorithms implemented:
 *   1. SJF   — non-preemptive Shortest Job First
 *   2. RR    — Round Robin with fixed quantum (initial median)
 *   3. SRSQ  — SJF + RR with Static Quantum (same 2-queue structure as SRDQ,
 *               but quantum = current q̃ directly, no per-task formula)
 *   4. TSPBRR — Time Slice Priority Based Round Robin
 *               (quantum proportional to burst time, shorter tasks served first)
 */
public class AlgorithmRunner {

    // ════════════════════════════════════════════════════════════════
    //  1. SJF — Shortest Job First (non-preemptive)
    // ════════════════════════════════════════════════════════════════

    public static List<SRDQBroker.ScheduleResult> runSJF(List<SRDQCloudlet> cloudlets) {
        List<SRDQBroker.ScheduleResult> results = new ArrayList<>();
        boolean[] done = new boolean[cloudlets.size()];
        double    currentTime = 0;
        int       finished    = 0;

        while (finished < cloudlets.size()) {
            // Find arrived, shortest-burst unfinished task
            int best = -1;
            for (int i = 0; i < cloudlets.size(); i++) {
                if (!done[i] && cloudlets.get(i).getArrivalTime() <= currentTime) {
                    if (best == -1 ||
                        cloudlets.get(i).getBurstMI() < cloudlets.get(best).getBurstMI())
                        best = i;
                }
            }
            if (best == -1) {
                // Idle: jump to next arrival
                double next = Double.MAX_VALUE;
                for (int i = 0; i < cloudlets.size(); i++)
                    if (!done[i])
                        next = Math.min(next, cloudlets.get(i).getArrivalTime());
                if (next == Double.MAX_VALUE) break;
                currentTime = next;
                continue;
            }
            SRDQCloudlet proc  = cloudlets.get(best);
            double       start = currentTime;
            currentTime += proc.getBurstMI();
            done[best] = true;
            finished++;
            results.add(new SRDQBroker.ScheduleResult(
                proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                "SJF", start, currentTime, "N/A"));
        }
        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
        return results;
    }

    // ════════════════════════════════════════════════════════════════
    //  2. RR — Round Robin with fixed quantum = initial median
    // ════════════════════════════════════════════════════════════════

    public static List<SRDQBroker.ScheduleResult> runRR(List<SRDQCloudlet> cloudlets) {
        double fixedQuantum = computeMedian(cloudlets);
        if (fixedQuantum < 1) fixedQuantum = 1;

        // Working copies of remaining burst
        long[]   remaining  = new long[cloudlets.size()];
        double[] firstStart = new double[cloudlets.size()];
        Arrays.fill(firstStart, -1);
        for (int i = 0; i < cloudlets.size(); i++)
            remaining[i] = cloudlets.get(i).getBurstMI();

        List<SRDQBroker.ScheduleResult> results = new ArrayList<>();
        LinkedList<Integer> readyQ   = new LinkedList<>();
        boolean[]           inQueue  = new boolean[cloudlets.size()];
        boolean[]           finished = new boolean[cloudlets.size()];
        double[]            finishT  = new double[cloudlets.size()];

        double currentTime   = 0;
        int    finishedCount = 0;

        // Seed initial arrivals
        enqueueArrived(cloudlets, readyQ, inQueue, finished, currentTime);

        while (finishedCount < cloudlets.size()) {
            if (readyQ.isEmpty()) {
                double next = nextArrival(cloudlets, finished);
                if (next == Double.MAX_VALUE) break;
                currentTime = next;
                enqueueArrived(cloudlets, readyQ, inQueue, finished, currentTime);
                continue;
            }
            int          idx  = readyQ.poll();
            SRDQCloudlet proc = cloudlets.get(idx);
            double       start = currentTime;

            if (firstStart[idx] < 0) firstStart[idx] = start;

            long exec = Math.min((long) Math.ceil(fixedQuantum), remaining[idx]);
            currentTime += exec;
            remaining[idx] -= exec;

            // Enqueue new arrivals before re-queuing current
            enqueueArrived(cloudlets, readyQ, inQueue, finished, currentTime);

            if (remaining[idx] > 0) {
                readyQ.add(idx);
            } else {
                finished[idx]   = true;
                finishT[idx]    = currentTime;
                inQueue[idx]    = false;
                finishedCount++;
                results.add(new SRDQBroker.ScheduleResult(
                    proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                    "RR", firstStart[idx], currentTime,
                    String.format("%.1f", fixedQuantum)));
            }
        }
        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
        return results;
    }

    // ════════════════════════════════════════════════════════════════
    //  3. SRSQ — SJF + RR with Static Quantum
    //     Same 2-queue, 2:1 interleaving as SRDQ.
    //     Quantum = current q̃ (updated on completions), not per-task formula.
    // ════════════════════════════════════════════════════════════════

    public static List<SRDQBroker.ScheduleResult> runSRSQ(List<SRDQCloudlet> cloudlets) {
        List<SRDQBroker.ScheduleResult> results = new ArrayList<>();
        if (cloudlets.isEmpty()) return results;

        double qe = computeMedian(cloudlets);

        // Partition into Q1 (SJF) and Q2 (RR)
        List<SRDQCloudlet> shortQ = new ArrayList<>();
        List<SRDQCloudlet> longQ  = new ArrayList<>();
        for (SRDQCloudlet c : cloudlets) {
            if (c.getBurstMI() <= qe) shortQ.add(c);
            else                       longQ.add(c);
        }
        shortQ.sort((a, b) -> {
            if (a.getArrivalTime() != b.getArrivalTime())
                return Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            return Long.compare(a.getBurstMI(), b.getBurstMI());
        });
        longQ.sort((a, b) -> Integer.compare(a.getArrivalTime(), b.getArrivalTime()));

        // Working state
        boolean[]           sjfDone   = new boolean[shortQ.size()];
        long[]              remaining = new long[longQ.size()];
        double[]            firstStart = new double[cloudlets.size()];
        Arrays.fill(firstStart, -1);
        for (int i = 0; i < longQ.size(); i++)
            remaining[i] = longQ.get(i).getBurstMI();

        LinkedList<Integer> readyRR = new LinkedList<>();
        boolean[]           inQ     = new boolean[longQ.size()];

        double currentTime   = 0;
        int    q1Counter     = 0;
        int    finishedTotal = 0;

        while (finishedTotal < cloudlets.size()) {
            // Enqueue arrived Q2 + update qe on arrival
            for (int i = 0; i < longQ.size(); i++) {
                if (!inQ[i] && remaining[i] > 0 &&
                    longQ.get(i).getArrivalTime() <= currentTime) {
                    readyRR.add(i);
                    inQ[i] = true;
                }
            }
            int  bestSJF = findBestSJF(shortQ, sjfDone, currentTime);
            boolean runSJF = (q1Counter < 2 && bestSJF != -1);
            boolean runRR  = (!runSJF && !readyRR.isEmpty());
            if (!runSJF && !runRR && bestSJF != -1) runSJF = true;

            if (runSJF) {
                SRDQCloudlet proc  = shortQ.get(bestSJF);
                double       start = currentTime;
                currentTime += proc.getBurstMI();
                if (firstStart[proc.getCloudletId()] < 0)
                    firstStart[proc.getCloudletId()] = start;
                sjfDone[bestSJF] = true;
                finishedTotal++;
                q1Counter++;
                qe = SRDQBroker.updateThresholdOnCompletion(qe, proc.getBurstMI());
                results.add(new SRDQBroker.ScheduleResult(
                    proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                    "SJF", start, currentTime, "N/A"));

            } else if (runRR) {
                // Static quantum = current qe (no per-task formula)
                double quantum = Math.max(1, qe);
                int    idx     = readyRR.poll();
                SRDQCloudlet proc  = longQ.get(idx);
                double       start = currentTime;
                if (firstStart[proc.getCloudletId()] < 0)
                    firstStart[proc.getCloudletId()] = start;

                long exec = Math.min((long) Math.ceil(quantum), remaining[idx]);
                currentTime   += exec;
                remaining[idx] -= exec;

                for (int i = 0; i < longQ.size(); i++) {
                    if (!inQ[i] && remaining[i] > 0 &&
                        longQ.get(i).getArrivalTime() <= currentTime) {
                        readyRR.add(i); inQ[i] = true;
                    }
                }

                if (remaining[idx] > 0) {
                    readyRR.add(idx);
                } else {
                    inQ[idx] = false;
                    finishedTotal++;
                    qe = SRDQBroker.updateThresholdOnCompletion(qe, proc.getBurstMI());
                    results.add(new SRDQBroker.ScheduleResult(
                        proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                        "RR", firstStart[proc.getCloudletId()], currentTime,
                        String.format("%.1f", quantum)));
                }
                q1Counter = 0;

            } else {
                // Idle
                double next = Double.MAX_VALUE;
                for (int i = 0; i < shortQ.size(); i++)
                    if (!sjfDone[i])
                        next = Math.min(next, shortQ.get(i).getArrivalTime());
                for (int i = 0; i < longQ.size(); i++)
                    if (remaining[i] > 0)
                        next = Math.min(next, longQ.get(i).getArrivalTime());
                if (next == Double.MAX_VALUE) break;
                currentTime = Math.max(currentTime, next);
            }
        }
        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
        return results;
    }

    // ════════════════════════════════════════════════════════════════
    //  4. TSPBRR — Time Slice Priority Based Round Robin
    //     Tasks ordered by burst time (shortest = highest priority).
    //     Quantum for task i = ceil(B_i / sum(B_all) * totalBurst).
    //     Effectively: each task's quantum is proportional to its burst.
    // ════════════════════════════════════════════════════════════════

    public static List<SRDQBroker.ScheduleResult> runTSPBRR(List<SRDQCloudlet> cloudlets) {
        List<SRDQBroker.ScheduleResult> results = new ArrayList<>();
        if (cloudlets.isEmpty()) return results;

        long totalBurst = 0;
        for (SRDQCloudlet c : cloudlets) totalBurst += c.getBurstMI();
        int n = cloudlets.size();

        // Per-task quantum proportional to burst
        long[] quantumPerTask = new long[n];
        for (int i = 0; i < n; i++)
            quantumPerTask[i] = Math.max(1,
                (long) Math.ceil((double) cloudlets.get(i).getBurstMI() / n));

        long[]   remaining  = new long[n];
        double[] firstStart = new double[n];
        Arrays.fill(firstStart, -1);
        for (int i = 0; i < n; i++) remaining[i] = cloudlets.get(i).getBurstMI();

        // Priority queue ordered by burst time (shorter = first)
        LinkedList<Integer> readyQ   = new LinkedList<>();
        boolean[]           inQueue  = new boolean[n];
        boolean[]           finished = new boolean[n];

        double currentTime   = 0;
        int    finishedCount = 0;

        enqueueArrivedPriority(cloudlets, readyQ, inQueue, finished, remaining, currentTime);

        while (finishedCount < n) {
            if (readyQ.isEmpty()) {
                double next = nextArrival(cloudlets, finished);
                if (next == Double.MAX_VALUE) break;
                currentTime = next;
                enqueueArrivedPriority(cloudlets, readyQ, inQueue, finished, remaining, currentTime);
                continue;
            }
            int          idx   = readyQ.poll();
            SRDQCloudlet proc  = cloudlets.get(idx);
            double       start = currentTime;
            if (firstStart[idx] < 0) firstStart[idx] = start;

            long exec = Math.min(quantumPerTask[idx], remaining[idx]);
            currentTime   += exec;
            remaining[idx] -= exec;

            enqueueArrivedPriority(cloudlets, readyQ, inQueue, finished, remaining, currentTime);

            if (remaining[idx] > 0) {
                // Re-insert maintaining priority order (by original burst, then arrival)
                insertByPriority(readyQ, idx, cloudlets);
            } else {
                finished[idx] = true;
                inQueue[idx]  = false;
                finishedCount++;
                results.add(new SRDQBroker.ScheduleResult(
                    proc.getCloudletId(), proc.getArrivalTime(), proc.getBurstMI(),
                    "TSPBRR", firstStart[idx], currentTime,
                    String.valueOf(quantumPerTask[idx])));
            }
        }
        results.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));
        return results;
    }

    // ════════════════════════════════════════════════════════════════
    //  SHARED UTILITIES
    // ════════════════════════════════════════════════════════════════

    static double computeMedian(List<SRDQCloudlet> cloudlets) {
        long[] b = new long[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) b[i] = cloudlets.get(i).getBurstMI();
        Arrays.sort(b);
        int n = b.length;
        return (n % 2 == 1) ? b[n / 2] : (b[n / 2 - 1] + b[n / 2]) / 2.0;
    }

    private static int findBestSJF(List<SRDQCloudlet> q, boolean[] done, double t) {
        int best = -1;
        for (int i = 0; i < q.size(); i++) {
            if (!done[i] && q.get(i).getArrivalTime() <= t) {
                if (best == -1 || q.get(i).getBurstMI() < q.get(best).getBurstMI())
                    best = i;
            }
        }
        return best;
    }

    private static void enqueueArrived(List<SRDQCloudlet> cloudlets,
                                       LinkedList<Integer> q, boolean[] inQ,
                                       boolean[] done, double t) {
        for (int i = 0; i < cloudlets.size(); i++) {
            if (!done[i] && !inQ[i] && cloudlets.get(i).getArrivalTime() <= t) {
                q.add(i); inQ[i] = true;
            }
        }
    }

    private static void enqueueArrivedPriority(List<SRDQCloudlet> cloudlets,
                                               LinkedList<Integer> q, boolean[] inQ,
                                               boolean[] done, long[] remaining, double t) {
        for (int i = 0; i < cloudlets.size(); i++) {
            if (!done[i] && !inQ[i] && remaining[i] > 0 &&
                cloudlets.get(i).getArrivalTime() <= t) {
                insertByPriority(q, i, cloudlets);
                inQ[i] = true;
            }
        }
    }

    /** Insert index into readyQ ordered by burst time (shorter = front). */
    private static void insertByPriority(LinkedList<Integer> q, int idx,
                                         List<SRDQCloudlet> cloudlets) {
        long burst = cloudlets.get(idx).getBurstMI();
        int  pos   = 0;
        for (int existing : q) {
            if (cloudlets.get(existing).getBurstMI() > burst) break;
            pos++;
        }
        q.add(pos, idx);
    }

    private static double nextArrival(List<SRDQCloudlet> cloudlets, boolean[] done) {
        double next = Double.MAX_VALUE;
        for (int i = 0; i < cloudlets.size(); i++)
            if (!done[i]) next = Math.min(next, cloudlets.get(i).getArrivalTime());
        return next;
    }
}
