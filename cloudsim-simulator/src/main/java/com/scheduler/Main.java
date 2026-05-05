package com.scheduler;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * SRDQ CloudSim Simulation Runner.
 *
 * Runs the SRDQ scheduling algorithm across 3 datasets (from Table 7 of
 * the paper) and 3 VM configurations (1, 2, 3 VMs).
 *
 * Infrastructure parameters are from Table 6 of:
 *   Elmougy, Sarhan & Joundy (2017). "A novel hybrid of Shortest job first
 *   and round Robin with dynamic variable quantum time task scheduling technique."
 */
public class Main {

    // ─── Infrastructure Constants (Table 6) ────────────────────────
    private static final int HOST_MIPS     = 1000;
    private static final int HOST_PES      = 10;
    private static final int HOST_RAM      = 2048;    // MB
    private static final long HOST_STORAGE = 1_000_000;
    private static final int HOST_BW       = 10_000;

    private static final int VM_MIPS       = 1000;
    private static final int VM_RAM        = 512;     // MB
    private static final int VM_BW         = 1000;
    private static final long VM_SIZE      = 10_000;  // MB
    private static final int VM_PES        = 1;

    private static final String ARCH       = "x86";
    private static final String OS         = "Linux";
    private static final String VMM        = "Xen";

    // ─── Datasets (Table 7) ────────────────────────────────────────
    // Each entry: {burst (MI), arrival time}

    private static final int[][] DATASET_1 = {
        {49, 0}, {98, 1}, {143, 2}, {187, 3},
        {244, 4}, {252, 4}, {199, 4}, {67, 5},
        {83, 3}, {75, 6}
    };

    private static final int[][] DATASET_2 = {
        {251, 0}, {177, 1}, {152, 2}, {299, 3},
        {47, 4}, {84, 5}, {244, 3}, {124, 3},
        {55, 4}, {180, 6}
    };

    private static final int[][] DATASET_3 = {
        {33, 0}, {201, 1}, {98, 2}, {116, 3},
        {11, 4}, {100, 5}, {33, 6}, {78, 7},
        {18, 4}, {64, 8}
    };

    // ════════════════════════════════════════════════════════════════
    //  MAIN
    // ════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     SRDQ CloudSim Simulator — Elmougy et al. (2017)        ║");
        System.out.println("║     SJF + Dynamic Round Robin with 2:1 Interleaving         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int[][][] datasets = { DATASET_1, DATASET_2, DATASET_3 };
        String[] datasetNames = { "Dataset 1", "Dataset 2", "Dataset 3" };
        int[] vmCounts = { 1, 2, 3 };

        for (int d = 0; d < datasets.length; d++) {
            for (int vmCount : vmCounts) {
                runSimulation(datasets[d], datasetNames[d], vmCount);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  SIMULATION RUNNER
    // ════════════════════════════════════════════════════════════════

    private static void runSimulation(int[][] dataset, String datasetName, int numVMs) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  %s  |  VMs: %d%n", datasetName, numVMs);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // ── 1. Initialize CloudSim ─────────────────────────────
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;
            CloudSim.init(numUsers, calendar, traceFlag);

            // ── 2. Create Datacenter ───────────────────────────────
            Datacenter datacenter = createDatacenter("SRDQ_Datacenter");

            // ── 3. Create Broker ───────────────────────────────────
            SRDQBroker broker = new SRDQBroker("SRDQBroker");
            int brokerId = broker.getId();

            // ── 4. Create VMs ──────────────────────────────────────
            List<Vm> vmList = createVMs(brokerId, numVMs);
            broker.submitVmList(vmList);

            // ── 5. Create Cloudlets from dataset ───────────────────
            List<SRDQCloudlet> cloudletList = createCloudlets(dataset, brokerId);

            // ── 6. Run SRDQ Algorithm (broker-level) ───────────────
            broker.runSRDQSchedule(cloudletList, VM_MIPS);

            // ── 7. Submit cloudlets to CloudSim and bind to VMs ────
            List<Cloudlet> baseList = new ArrayList<Cloudlet>(cloudletList);
            broker.submitCloudletList(baseList);
            for (SRDQCloudlet cl : cloudletList) {
                broker.bindCloudletToVm(cl.getCloudletId(), vmList.get(0).getId());
            }

            // ── 8. Start Simulation ────────────────────────────────
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // ── 9. Print Results ───────────────────────────────────
            printScheduleTrace(broker);
            printResultsTable(broker);
            printSummary(broker);

        } catch (Exception e) {
            System.err.println("Simulation error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════
    //  FACTORY METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Creates a Datacenter with a single host matching Table 6 parameters.
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        // Create PEs (Processing Elements)
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(HOST_MIPS)));
        }

        // Create Host
        Host host = new Host(
            0,
            new RamProvisionerSimple(HOST_RAM),
            new BwProvisionerSimple(HOST_BW),
            HOST_STORAGE,
            peList,
            new VmSchedulerTimeShared(peList)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        // Datacenter characteristics
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            ARCH, OS, VMM,
            hostList,
            10.0,     // time zone
            3.0,      // cost per sec
            0.05,     // cost per mem
            0.001,    // cost per storage
            0.0       // cost per bw
        );

        return new Datacenter(
            name,
            characteristics,
            new VmAllocationPolicySimple(hostList),
            new LinkedList<Storage>(),
            0
        );
    }

    /**
     * Creates VMs with CloudletSchedulerSpaceShared (per the paper).
     */
    private static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new Vm(
                i, brokerId,
                VM_MIPS, VM_PES,
                VM_RAM, VM_BW, VM_SIZE,
                VMM,
                new CloudletSchedulerSpaceShared()
            );
            vmList.add(vm);
        }
        return vmList;
    }

    /**
     * Creates SRDQCloudlets from a dataset array.
     * Each entry: {burst (MI), arrival time}.
     */
    private static List<SRDQCloudlet> createCloudlets(int[][] dataset, int brokerId) {
        List<SRDQCloudlet> list = new ArrayList<>();
        UtilizationModel utilizationFull = new UtilizationModelFull();

        for (int i = 0; i < dataset.length; i++) {
            int burst   = dataset[i][0];
            int arrival = dataset[i][1];

            SRDQCloudlet cloudlet = new SRDQCloudlet(
                i,                 // cloudlet ID (0-based)
                burst,             // length in MI
                VM_PES,            // PEs required
                300,               // file size
                300,               // output size
                utilizationFull,   // CPU utilization
                utilizationFull,   // RAM utilization
                utilizationFull,   // BW utilization
                arrival
            );
            cloudlet.setUserId(brokerId);
            list.add(cloudlet);
        }

        return list;
    }

    // ════════════════════════════════════════════════════════════════
    //  OUTPUT METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Prints the scheduling trace showing 2:1 interleaving.
     */
    private static void printScheduleTrace(SRDQBroker broker) {
        System.out.println();
        System.out.println("┌─── Scheduling Trace ───────────────────────────────────────┐");
        for (String line : broker.getScheduleTrace()) {
            System.out.println("  " + line);
        }
        System.out.println("└────────────────────────────────────────────────────────────┘");
    }

    /**
     * Prints the per-cloudlet results table.
     */
    private static void printResultsTable(SRDQBroker broker) {
        System.out.println();
        System.out.println("┌─── Per-Cloudlet Results ──────────────────────────────────────────────────────────────┐");
        System.out.printf("│ %-5s │ %-7s │ %-9s │ %-5s │ %-7s │ %-7s │ %-7s │ %-7s │ %-7s │ %-12s │%n",
            "CID", "Arrival", "Burst(MI)", "Queue", "Start", "Finish", "TAT", "WT", "RT", "Quantum Used");
        System.out.println("├───────┼─────────┼───────────┼───────┼─────────┼─────────┼─────────┼─────────┼─────────┼──────────────┤");

        DecimalFormat df = new DecimalFormat("0.00");

        for (SRDQBroker.ScheduleResult r : broker.getResults()) {
            System.out.printf("│ %-5d │ %-7d │ %-9d │ %-5s │ %-7s │ %-7s │ %-7s │ %-7s │ %-7s │ %-12s │%n",
                r.cloudletId,
                r.arrivalTime,
                r.burstMI,
                r.queue,
                df.format(r.startTime),
                df.format(r.finishTime),
                df.format(r.tat),
                df.format(r.wt),
                df.format(r.rt),
                r.quantumUsed);
        }

        System.out.println("└───────┴─────────┴───────────┴───────┴─────────┴─────────┴─────────┴─────────┴─────────┴──────────────┘");
    }

    /**
     * Prints summary averages (TAT, WT, RT) and the threshold used.
     */
    private static void printSummary(SRDQBroker broker) {
        List<SRDQBroker.ScheduleResult> results = broker.getResults();
        if (results.isEmpty()) {
            System.out.println("  No results to summarize.");
            return;
        }

        double totalTAT = 0, totalWT = 0, totalRT = 0;
        for (SRDQBroker.ScheduleResult r : results) {
            totalTAT += r.tat;
            totalWT  += r.wt;
            totalRT  += r.rt;
        }

        int n = results.size();
        DecimalFormat df = new DecimalFormat("0.00");

        System.out.println();
        System.out.printf("  Threshold (median): %d MI%n", broker.getThreshold());
        System.out.printf("  Average TAT: %s | Average WT: %s | Average RT: %s%n",
            df.format(totalTAT / n),
            df.format(totalWT / n),
            df.format(totalRT / n));
    }
}
