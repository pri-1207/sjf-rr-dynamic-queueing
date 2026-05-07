package com.scheduler;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

/**
 * SRDQ CloudSim Simulation Runner.
 *
 * For each (dataset × vmCount) combination:
 *   1. Runs SRDQ via CloudSim (actual parallel VM execution)
 *   2. Runs SJF, RR, SRSQ, TSPBRR as analytical schedulers
 *   3. Prints a comparison table
 *   4. Exports results/detail_*.csv and results/summary.csv
 *
 * Reference: Elmougy, Sarhan & Joundy (2017).
 */
public class Main {

    // ─── Infrastructure Constants (Table 6) ────────────────────────
    private static final int    HOST_MIPS     = 1000;
    private static final int    HOST_PES      = 10;
    private static final int    HOST_RAM      = 2048;
    private static final long   HOST_STORAGE  = 1_000_000;
    private static final int    HOST_BW       = 10_000;

    static final int    VM_MIPS       = 1000;
    private static final int    VM_RAM        = 512;
    private static final int    VM_BW         = 1000;
    private static final long   VM_SIZE       = 10_000;
    private static final int    VM_PES        = 1;

    private static final String ARCH          = "x86";
    private static final String OS            = "Linux";
    private static final String VMM           = "Xen";

    // ─── Datasets (Table 7) — {burst (MI), arrival time} ──────────
    private static final int[][] DATASET_1 = {
        {49,0},{98,1},{143,2},{187,3},{244,4},{252,4},{199,4},{67,5},{83,3},{75,6}
    };
    private static final int[][] DATASET_2 = {
        {251,0},{177,1},{152,2},{299,3},{47,4},{84,5},{244,3},{124,3},{55,4},{180,6}
    };
    private static final int[][] DATASET_3 = {
        {33,0},{201,1},{98,2},{116,3},{11,4},{100,5},{33,6},{78,7},{18,4},{64,8}
    };

    // ═══════════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     SRDQ CloudSim Simulator — Elmougy et al. (2017)        ║");
        System.out.println("║  SJF + Dynamic Round Robin | Comparison: SJF/RR/SRSQ/TSPBRR║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int[][][]  datasets     = {DATASET_1, DATASET_2, DATASET_3};
        String[]   datasetNames = {"Dataset 1", "Dataset 2", "Dataset 3"};
        int[]      vmCounts     = {1, 2, 3};

        ResultsExporter exporter = new ResultsExporter();

        for (int d = 0; d < datasets.length; d++) {
            for (int vmCount : vmCounts) {
                runAll(datasets[d], datasetNames[d], vmCount, exporter);
            }
        }

        exporter.writeSummaryCSV();
        System.out.println("\nDone. Check results/ for CSV files.");
    }

    // ═══════════════════════════════════════════════════════════════
    //  PER-RUN ORCHESTRATOR
    // ═══════════════════════════════════════════════════════════════

    private static void runAll(int[][] dataset, String datasetName,
                               int numVMs, ResultsExporter exporter) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  %s  |  VMs: %d%n", datasetName, numVMs);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── Fresh cloudlet list for SRDQ (CloudSim run) ────────────
        List<SRDQCloudlet> srdqCloudlets = buildCloudlets(dataset, 0 /* brokerId placeholder */);

        // ── Run SRDQ via CloudSim ──────────────────────────────────
        List<SRDQBroker.ScheduleResult> srdqResults =
            runSRDQWithCloudSim(srdqCloudlets, dataset, datasetName, numVMs);

        // ── Run comparison algorithms (analytical, 1-VM equivalent) ─
        List<SRDQCloudlet> analyticalCl = buildCloudlets(dataset, 0);
        List<SRDQBroker.ScheduleResult> sjfResults    = AlgorithmRunner.runSJF(analyticalCl);

        analyticalCl = buildCloudlets(dataset, 0);
        List<SRDQBroker.ScheduleResult> rrResults     = AlgorithmRunner.runRR(analyticalCl);

        analyticalCl = buildCloudlets(dataset, 0);
        List<SRDQBroker.ScheduleResult> srsqResults   = AlgorithmRunner.runSRSQ(analyticalCl);

        analyticalCl = buildCloudlets(dataset, 0);
        List<SRDQBroker.ScheduleResult> tspbrrResults = AlgorithmRunner.runTSPBRR(analyticalCl);

        // ── Build ordered results map ──────────────────────────────
        Map<String, List<SRDQBroker.ScheduleResult>> allResults = new LinkedHashMap<>();
        allResults.put("SRDQ",    srdqResults);
        allResults.put("SRSQ",    srsqResults);
        allResults.put("SJF",     sjfResults);
        allResults.put("RR",      rrResults);
        allResults.put("TSPBRR",  tspbrrResults);

        // ── Print comparison table ─────────────────────────────────
        printComparisonTable(allResults);

        // ── Export CSV ─────────────────────────────────────────────
        exporter.writeDetailCSV(datasetName, numVMs, allResults);
        for (Map.Entry<String, List<SRDQBroker.ScheduleResult>> e : allResults.entrySet()) {
            exporter.addSummaryRow(datasetName, numVMs, e.getKey(), e.getValue());
        }

        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLOUDSIM RUNNER (SRDQ only)
    // ═══════════════════════════════════════════════════════════════

    private static List<SRDQBroker.ScheduleResult> runSRDQWithCloudSim(
            List<SRDQCloudlet> cloudlets, int[][] dataset,
            String datasetName, int numVMs) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);
            createDatacenter("SRDQ_Datacenter");
            SRDQBroker broker = new SRDQBroker("SRDQBroker");
            int brokerId = broker.getId();

            // Re-set userId on cloudlets now that we have brokerId
            for (SRDQCloudlet cl : cloudlets) cl.setUserId(brokerId);

            List<Vm> vmList = createVMs(brokerId, numVMs);
            broker.submitVmList(vmList);

            // Run SRDQ algorithm
            broker.runSRDQSchedule(cloudlets, VM_MIPS);

            // Print scheduling trace
            printScheduleTrace(broker);

            // Bind cloudlets to VMs in scheduled order (round-robin)
            List<Cloudlet> baseList = new ArrayList<>(cloudlets);
            broker.submitCloudletList(baseList);
            List<SRDQBroker.ScheduleResult> scheduledOrder = broker.getResults();
            for (int i = 0; i < scheduledOrder.size(); i++) {
                broker.bindCloudletToVm(scheduledOrder.get(i).cloudletId,
                                        vmList.get(i % vmList.size()).getId());
            }

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Map actual CloudSim times back to MI units
            Map<Integer, Cloudlet> actualTimes = new HashMap<>();
            for (Cloudlet cl : broker.getCloudletReceivedList())
                actualTimes.put(cl.getCloudletId(), cl);

            // Build result list using actual CloudSim times
            List<SRDQBroker.ScheduleResult> finalResults = new ArrayList<>();
            Map<Integer, String> queueMap   = new HashMap<>();
            Map<Integer, String> quantumMap = new HashMap<>();
            for (SRDQBroker.ScheduleResult r : broker.getResults()) {
                queueMap.put(r.cloudletId, r.queue);
                quantumMap.put(r.cloudletId, r.quantumUsed);
            }

            for (SRDQCloudlet cl : cloudlets) {
                int      cid    = cl.getCloudletId();
                Cloudlet actual = actualTimes.get(cid);
                double   start  = (actual != null) ? actual.getExecStartTime() * VM_MIPS : 0;
                double   finish = (actual != null) ? actual.getFinishTime()     * VM_MIPS : 0;
                finalResults.add(new SRDQBroker.ScheduleResult(
                    cid, cl.getArrivalTime(), cl.getBurstMI(),
                    queueMap.getOrDefault(cid, "?"),
                    start, finish,
                    quantumMap.getOrDefault(cid, "N/A")));
            }
            finalResults.sort((a, b) -> Integer.compare(a.cloudletId, b.cloudletId));

            // Print SRDQ per-cloudlet table
            printResultsTable("SRDQ (CloudSim actual)", finalResults);
            printSummaryLine(finalResults);

            return finalResults;

        } catch (Exception e) {
            System.err.println("CloudSim error: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLOUDSIM FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    private static Datacenter createDatacenter(String name) throws Exception {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < HOST_PES; i++)
            peList.add(new Pe(i, new PeProvisionerSimple(HOST_MIPS)));

        Host host = new Host(0,
            new RamProvisionerSimple(HOST_RAM),
            new BwProvisionerSimple(HOST_BW),
            HOST_STORAGE, peList,
            new VmSchedulerTimeShared(peList));

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        DatacenterCharacteristics dc = new DatacenterCharacteristics(
            ARCH, OS, VMM, hostList, 10.0, 3.0, 0.05, 0.001, 0.0);

        return new Datacenter(name, dc,
            new VmAllocationPolicySimple(hostList),
            new LinkedList<Storage>(), 0);
    }

    private static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++)
            vmList.add(new Vm(i, brokerId, VM_MIPS, VM_PES,
                VM_RAM, VM_BW, VM_SIZE, VMM,
                new CloudletSchedulerSpaceShared()));
        return vmList;
    }

    /** Creates a fresh list of SRDQCloudlets from a dataset array. */
    private static List<SRDQCloudlet> buildCloudlets(int[][] dataset, int brokerId) {
        List<SRDQCloudlet> list = new ArrayList<>();
        UtilizationModel full = new UtilizationModelFull();
        for (int i = 0; i < dataset.length; i++) {
            SRDQCloudlet cl = new SRDQCloudlet(i, dataset[i][0], VM_PES,
                300, 300, full, full, full, dataset[i][1]);
            cl.setUserId(brokerId);
            list.add(cl);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    //  OUTPUT HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static void printScheduleTrace(SRDQBroker broker) {
        System.out.println();
        System.out.println("┌─── SRDQ Scheduling Trace ─────────────────────────────────────────────────────┐");
        for (String line : broker.getScheduleTrace())
            System.out.println("  " + line);
        System.out.println("└───────────────────────────────────────────────────────────────────────────────┘");
    }

    private static void printResultsTable(String title,
                                          List<SRDQBroker.ScheduleResult> results) {
        DecimalFormat df = new DecimalFormat("0.00");
        System.out.println();
        System.out.println("┌─── " + title + " ─────────────────────────────────────────────────────────────┐");
        System.out.printf("│ %-5s │ %-7s │ %-9s │ %-6s │ %-7s │ %-7s │ %-7s │ %-7s │ %-7s │%n",
            "CID","Arrival","Burst(MI)","Queue","Start","Finish","TAT","WT","RT");
        System.out.println("├───────┼─────────┼───────────┼────────┼─────────┼─────────┼─────────┼─────────┼─────────┤");
        for (SRDQBroker.ScheduleResult r : results) {
            System.out.printf("│ %-5d │ %-7d │ %-9d │ %-6s │ %-7s │ %-7s │ %-7s │ %-7s │ %-7s │%n",
                r.cloudletId, r.arrivalTime, r.burstMI, r.queue,
                df.format(r.startTime), df.format(r.finishTime),
                df.format(r.tat), df.format(r.wt), df.format(r.rt));
        }
        System.out.println("└───────┴─────────┴───────────┴────────┴─────────┴─────────┴─────────┴─────────┴─────────┘");
    }

    private static void printSummaryLine(List<SRDQBroker.ScheduleResult> results) {
        if (results.isEmpty()) return;
        double tat = 0, wt = 0, rt = 0;
        for (SRDQBroker.ScheduleResult r : results) { tat += r.tat; wt += r.wt; rt += r.rt; }
        int n = results.size();
        DecimalFormat df = new DecimalFormat("0.00");
        System.out.printf("  Avg TAT: %s | Avg WT: %s | Avg RT: %s%n",
            df.format(tat/n), df.format(wt/n), df.format(rt/n));
    }

    private static void printComparisonTable(
            Map<String, List<SRDQBroker.ScheduleResult>> allResults) {
        DecimalFormat df = new DecimalFormat("0.00");
        System.out.println();
        System.out.println("┌─── Algorithm Comparison (Avg Metrics) ────────────────────────────────┐");
        System.out.printf("│ %-8s │ %-10s │ %-10s │ %-10s │%n",
            "Algorithm", "Avg TAT", "Avg WT", "Avg RT");
        System.out.println("├──────────┼────────────┼────────────┼────────────┤");
        for (Map.Entry<String, List<SRDQBroker.ScheduleResult>> e : allResults.entrySet()) {
            List<SRDQBroker.ScheduleResult> res = e.getValue();
            if (res.isEmpty()) {
                System.out.printf("│ %-8s │ %-10s │ %-10s │ %-10s │%n",
                    e.getKey(), "N/A", "N/A", "N/A");
                continue;
            }
            double tat = 0, wt = 0, rt = 0;
            for (SRDQBroker.ScheduleResult r : res) { tat += r.tat; wt += r.wt; rt += r.rt; }
            int n = res.size();
            System.out.printf("│ %-8s │ %-10s │ %-10s │ %-10s │%n",
                e.getKey(), df.format(tat/n), df.format(wt/n), df.format(rt/n));
        }
        System.out.println("└──────────┴────────────┴────────────┴────────────┘");
    }
}
