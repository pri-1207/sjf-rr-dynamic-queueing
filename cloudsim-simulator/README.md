# SRDQ CloudSim Simulator

CloudSim 3.0.3 simulation of the **SRDQ** (SJF + Dynamic Round Robin) scheduling algorithm, based on:

> *"A novel hybrid of Shortest job first and round Robin with dynamic variable quantum time task scheduling technique"*
> — Elmougy, Sarhan & Joundy (2017)

---

## Prerequisites

- **Java 8** (JDK 1.8) or later
- **Maven 3.6+**
- **CloudSim 3.0.3 JAR** (not available on Maven Central — see below)

## Setup

1. Download CloudSim 3.0.3 from:
   https://github.com/Cloudsuite/cloudsim/releases
   or
   https://code.google.com/archive/p/cloudsim/downloads

2. Place the JAR at:
   cloudsim-simulator/lib/cloudsim-3.0.3.jar

3. Run: mvn compile
4. Run: mvn exec:java -Dexec.mainClass="com.scheduler.Main"

## What It Does

Runs the SRDQ scheduling algorithm across **3 datasets** (from Table 7 of the paper) and **3 VM configurations** (1, 2, and 3 VMs), producing:

- A scheduling trace showing the 2:1 SJF → RR interleaving pattern
- A per-cloudlet results table (CID, Arrival, Burst, Queue, Start, Finish, TAT, WT, RT, Quantum)
- Summary averages for TAT, WT, and RT

## Algorithm Summary

1. **Threshold** = median of all burst times → splits cloudlets into Short Queue (SJF) and Long Queue (Dynamic RR)
2. **Dynamic Quantum** = `ceil(avg remaining MI of arrived long-queue cloudlets)` — recalculated every round
3. **2:1 Interleaving** — run 2 SJF tasks, then 1 RR time slice, repeat
4. **Mid-execution arrivals** — newly arrived cloudlets are enqueued before re-queuing the current RR cloudlet

## Project Structure

```
cloudsim-simulator/
├── pom.xml                  # Maven config (system-scoped CloudSim JAR)
├── README.md                # This file
├── lib/                     # Place cloudsim-3.0.3.jar here
│   └── .gitkeep
└── src/main/java/com/scheduler/
    ├── Main.java            # Simulation runner (datasets, VM configs, output)
    ├── SRDQBroker.java      # Custom DatacenterBroker with SRDQ logic
    └── SRDQCloudlet.java    # Extended Cloudlet with arrival/queue/quantum fields
```
