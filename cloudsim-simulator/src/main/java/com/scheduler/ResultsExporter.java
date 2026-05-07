package com.scheduler;

import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Exports simulation results to CSV files.
 *
 * Output files written to the results/ directory:
 *   detail_<dataset>_<vmCount>vm.csv  — per-cloudlet rows for ALL algorithms
 *   summary.csv                        — one row per (dataset × vmCount × algorithm)
 */
public class ResultsExporter {

    private static final String RESULTS_DIR = "results";
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    // ── Summary accumulator: keyed by "dataset|vmCount|algorithm" ──
    private final List<String[]> summaryRows = new ArrayList<>();

    public ResultsExporter() {
        try {
            Files.createDirectories(Paths.get(RESULTS_DIR));
        } catch (IOException e) {
            System.err.println("Could not create results/ directory: " + e.getMessage());
        }
    }

    /**
     * Write per-cloudlet detail CSV for one (dataset × vmCount) run.
     * Each algorithm is a separate block of rows, identified by the "Algorithm" column.
     *
     * @param datasetName  e.g. "Dataset 1"
     * @param vmCount      number of VMs
     * @param allResults   map from algorithm name → list of ScheduleResult
     */
    public void writeDetailCSV(String datasetName, int vmCount,
                               Map<String, List<SRDQBroker.ScheduleResult>> allResults) {
        String tag  = datasetName.replace(" ", "_") + "_" + vmCount + "vm";
        String path = RESULTS_DIR + "/detail_" + tag + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("Algorithm,CID,Arrival,Burst(MI),Queue,Start,Finish,TAT,WT,RT,QuantumUsed");
            for (Map.Entry<String, List<SRDQBroker.ScheduleResult>> entry : allResults.entrySet()) {
                String algoName = entry.getKey();
                for (SRDQBroker.ScheduleResult r : entry.getValue()) {
                    pw.printf("%s,%d,%d,%d,%s,%s,%s,%s,%s,%s,%s%n",
                        algoName,
                        r.cloudletId, r.arrivalTime, r.burstMI,
                        r.queue,
                        DF.format(r.startTime),  DF.format(r.finishTime),
                        DF.format(r.tat),        DF.format(r.wt),
                        DF.format(r.rt),         r.quantumUsed);
                }
            }
            System.out.println("  [CSV] Written: " + path);
        } catch (IOException e) {
            System.err.println("  [CSV] Error writing " + path + ": " + e.getMessage());
        }
    }

    /**
     * Accumulate one summary row from a list of results.
     */
    public void addSummaryRow(String datasetName, int vmCount, String algorithm,
                               List<SRDQBroker.ScheduleResult> results) {
        if (results.isEmpty()) return;
        double totalTAT = 0, totalWT = 0, totalRT = 0;
        for (SRDQBroker.ScheduleResult r : results) {
            totalTAT += r.tat;
            totalWT  += r.wt;
            totalRT  += r.rt;
        }
        int n = results.size();
        summaryRows.add(new String[]{
            datasetName, String.valueOf(vmCount), algorithm,
            DF.format(totalTAT / n),
            DF.format(totalWT  / n),
            DF.format(totalRT  / n)
        });
    }

    /**
     * Write the accumulated summary CSV.
     */
    public void writeSummaryCSV() {
        String path = RESULTS_DIR + "/summary.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("Dataset,VMs,Algorithm,AvgTAT,AvgWT,AvgRT");
            for (String[] row : summaryRows) {
                pw.println(String.join(",", row));
            }
            System.out.println("  [CSV] Written: " + path);
        } catch (IOException e) {
            System.err.println("  [CSV] Error writing " + path + ": " + e.getMessage());
        }
    }
}
