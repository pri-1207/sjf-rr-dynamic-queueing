# SRDQ CloudSim Simulator

**A CloudSim 3.0.3 implementation of the SRDQ (Shortest Job First + Dynamic Round Robin) hybrid task scheduling algorithm.**

> **Reference:** Elmougy, S., Sarhan, S., & Joundy, M. (2017). *A novel hybrid of Shortest job first and round Robin with dynamic variable quantum time task scheduling technique.* Journal of Cloud Computing: Advances, Systems and Applications, 6(12).

---

## Table of Contents

1. [Overview](#overview)
2. [Algorithm Description](#algorithm-description)
3. [Project Structure](#project-structure)
4. [Infrastructure & Configuration](#infrastructure--configuration)
5. [Datasets](#datasets)
6. [Implementation Details](#implementation-details)
7. [Build & Run](#build--run)
8. [Simulation Results](#simulation-results)
9. [Key Design Decisions](#key-design-decisions)
10. [Known Limitations](#known-limitations)

---

## Overview

This project simulates the **SRDQ (SJF + Round Robin with Dynamic Quantum)** scheduling algorithm in a cloud computing environment using [CloudSim 3.0.3](http://www.cloudbus.org/cloudsim/). The simulation reproduces the experimental setup of the Elmougy et al. (2017) paper, evaluating the algorithm across **3 datasets** and **3 VM configurations (1, 2, 3 VMs)**, reporting per-cloudlet and average Turnaround Time (TAT), Waiting Time (WT), and Response Time (RT).

The SRDQ algorithm combines:
- **SJF (Shortest Job First)** for short tasks — minimizes waiting time for quick tasks.
- **Dynamic Round Robin** for long tasks — prevents starvation by interleaving long tasks with variable quantum sizes.

---

## Algorithm Description

### Core Idea

The ready queue is split into two sub-queues based on a dynamically computed threshold:

| Sub-queue | Tasks          | Policy              |
|-----------|----------------|---------------------|
| **Q1**    | Burst ≤ Median | SJF (non-preemptive) |
| **Q2**    | Burst > Median | Round Robin (dynamic quantum) |

### Step-by-Step

1. **Compute Threshold** — The median of all cloudlet burst times (in MI) is computed. Cloudlets with burst ≤ median go to **Q1 (SJF)**; those with burst > median go to **Q2 (RR)**.

2. **2:1 Interleaving** — The scheduler picks tasks in a **2 SJF : 1 RR** pattern:
   - Execute 2 SJF tasks (or fewer if Q1 is exhausted) → then 1 RR quantum slice → repeat.
   - `q1Counter` tracks how many SJF tasks have run since the last RR execution.

3. **Dynamic Quantum** — The RR quantum is not static. Before each RR slice, the quantum is recomputed as:
   ```
   quantum = ⌈ average remaining MI of all arrived, unfinished Q2 cloudlets ⌉
   ```
   This shrinks the quantum as tasks near completion, reducing overall waiting time.

4. **Arrival-Aware Scheduling** — The scheduler respects arrival times. If no task has arrived yet, it jumps forward in time to the next arrival event.

### Flowchart

```
START
  │
  ▼
Compute median threshold
  │
  ▼
Partition cloudlets → Q1 (SJF) | Q2 (RR)
  │
  ▼
┌─────────────────────────────────┐
│ MAIN LOOP (while tasks remain)  │
│                                 │
│  Enqueue arrived Q2 cloudlets   │
│       ↓                         │
│  q1Counter < 2 AND Q1 ready?    │
│    YES → Run SJF task           │
│           q1Counter++           │
│    NO  → Q2 ready?              │
│            YES → compute quantum│
│                  Run RR slice   │
│                  q1Counter = 0  │
│            NO  → advance time   │
└─────────────────────────────────┘
  │
  ▼
END: report TAT, WT, RT
```

---

## Project Structure

```
cloudsim-simulator/
├── pom.xml                                   # Maven build config
├── lib/
│   └── cloudsim-3.0.3.jar                   # CloudSim library (local)
└── src/main/java/com/scheduler/
    ├── Main.java                             # Simulation runner & output
    ├── SRDQBroker.java                       # Algorithm implementation (extends DatacenterBroker)
    └── SRDQCloudlet.java                     # Extended Cloudlet with SRDQ metadata
```

### File Responsibilities

#### `Main.java`
- Defines **infrastructure constants** (Host MIPS, RAM, storage, BW) from Table 6 of the paper.
- Defines the **3 datasets** (Table 7) as 2D arrays of `{burst, arrivalTime}`.
- For each `(dataset × vmCount)` combination:
  1. Initializes CloudSim
  2. Creates the Datacenter, Broker, VMs, and Cloudlets
  3. Calls `broker.runSRDQSchedule()` to compute scheduling order
  4. Binds cloudlets to VMs in **round-robin order** (based on SRDQ schedule)
  5. Starts the CloudSim simulation
  6. Reads actual CloudSim execution times from `getCloudletReceivedList()`
  7. Prints scheduling trace, per-cloudlet table, and summary averages

#### `SRDQBroker.java`
- Extends `DatacenterBroker` — the CloudSim entity managing VMs and cloudlets on behalf of a user.
- Implements the full SRDQ algorithm in `runSRDQSchedule()`.
- Produces:
  - `scheduleTrace`: ordered log of every scheduling decision
  - `results`: per-cloudlet metadata (queue type, quantum used, algorithm-computed start/finish)
- Helper methods:
  - `computeMedianThreshold()` — sorts burst times and finds median
  - `computeDynamicQuantum()` — computes avg remaining MI of active Q2 cloudlets
  - `findBestSJF()` — selects shortest-burst arrived Q1 cloudlet
  - `enqueueArrivedRR()` — adds newly arrived Q2 cloudlets to the ready queue

#### `SRDQCloudlet.java`
- Extends CloudSim's `Cloudlet` with:
  - `arrivalTime` — dataset-specified arrival (in MI-equivalent units)
  - `assignedQueue` — `"SJF"` or `"RR"`, set after threshold computation
  - `remaining` — remaining MI for RR slicing across multiple quanta
  - `quantumUsed` — last quantum value applied (for reporting)
  - `startTime`, `finishTime` — algorithm-computed times (for scheduling trace)

---

## Infrastructure & Configuration

These parameters replicate **Table 6** from the paper:

| Parameter         | Value      |
|-------------------|------------|
| Host MIPS         | 1000       |
| Host PEs          | 10         |
| Host RAM          | 2048 MB    |
| Host Storage      | 1,000,000  |
| Host Bandwidth    | 10,000     |
| VM MIPS           | 1000       |
| VM RAM            | 512 MB     |
| VM Bandwidth      | 1000       |
| VM Storage        | 10,000 MB  |
| VM PEs            | 1          |
| VM Scheduler      | `CloudletSchedulerSpaceShared` |
| VM Allocator      | `VmAllocationPolicySimple`     |
| Host Scheduler    | `VmSchedulerTimeShared`        |
| Architecture      | x86        |
| OS                | Linux      |
| Hypervisor        | Xen        |

---

## Datasets

Three datasets from **Table 7** of the paper are simulated. Each entry is `{burst (MI), arrival time}`:

### Dataset 1
| CID | Burst (MI) | Arrival |
|-----|-----------|---------|
| 0   | 49        | 0       |
| 1   | 98        | 1       |
| 2   | 143       | 2       |
| 3   | 187       | 3       |
| 4   | 244       | 4       |
| 5   | 252       | 4       |
| 6   | 199       | 4       |
| 7   | 67        | 5       |
| 8   | 83        | 3       |
| 9   | 75        | 6       |

### Dataset 2
| CID | Burst (MI) | Arrival |
|-----|-----------|---------|
| 0   | 251       | 0       |
| 1   | 177       | 1       |
| 2   | 152       | 2       |
| 3   | 299       | 3       |
| 4   | 47        | 4       |
| 5   | 84        | 5       |
| 6   | 244       | 3       |
| 7   | 124       | 3       |
| 8   | 55        | 4       |
| 9   | 180       | 6       |

### Dataset 3
| CID | Burst (MI) | Arrival |
|-----|-----------|---------|
| 0   | 33        | 0       |
| 1   | 201       | 1       |
| 2   | 98        | 2       |
| 3   | 116       | 3       |
| 4   | 11        | 4       |
| 5   | 100       | 5       |
| 6   | 33        | 6       |
| 7   | 78        | 7       |
| 8   | 18        | 4       |
| 9   | 64        | 8       |

---

## Implementation Details

### VM Distribution (Round-Robin)

After the SRDQ algorithm determines the **scheduling order** of cloudlets, they are distributed across available VMs in **round-robin fashion** based on that order:

```java
List<SRDQBroker.ScheduleResult> scheduledOrder = broker.getResults();
for (int i = 0; i < scheduledOrder.size(); i++) {
    int cloudletId = scheduledOrder.get(i).cloudletId;
    int vmIndex    = i % vmList.size();   // round-robin
    broker.bindCloudletToVm(cloudletId, vmList.get(vmIndex).getId());
}
```

This means the 1st-scheduled cloudlet goes to VM #0, the 2nd to VM #1, and so on, wrapping back to VM #0.

### Metrics Computation

The results table uses **actual CloudSim-measured execution times** (not the algorithm's sequential simulation), so results genuinely reflect parallel execution when multiple VMs are active.

CloudSim reports times in **seconds**. Since `VM_MIPS = 1000`, we convert:
```
time_in_MI = time_in_seconds × VM_MIPS
```

This gives TAT, WT, and RT in the same MI-based units as the arrival times and burst lengths.

### Metric Formulas

```
TAT (Turnaround Time) = Finish Time − Arrival Time
WT  (Waiting Time)    = TAT − Burst Time
RT  (Response Time)   = Start Time − Arrival Time
```

---

## Build & Run

### Prerequisites

- Java 8 or later
- Maven 3.x
- CloudSim 3.0.3 JAR in `lib/cloudsim-3.0.3.jar`

### First-Time Setup

Install the CloudSim JAR into your local Maven repository:

```bash
mvn install:install-file \
  -Dfile=lib/cloudsim-3.0.3.jar \
  -DgroupId=org.cloudbus.cloudsim \
  -DartifactId=cloudsim \
  -Dversion=3.0.3 \
  -Dpackaging=jar
```

### Compile

```bash
mvn compile
```

### Run

```bash
mvn exec:java
```

The simulation runs all **9 combinations** (3 datasets × 3 VM counts) and prints results to stdout.

---

## Simulation Results

### Summary: Average Metrics by Dataset and VM Count

All times are in MI-equivalent units.

#### Dataset 1

| VMs | Avg TAT   | Avg WT    | Avg RT    |
|-----|-----------|-----------|-----------|
| 1   | 969.10    | 829.40    | 811.60    |
| 2   | 603.60    | 463.90    | 436.60    |
| 3   | 514.80    | 375.10    | 327.80    |

#### Dataset 2

| VMs | Avg TAT   | Avg WT    | Avg RT    |
|-----|-----------|-----------|-----------|
| 1   | 1133.90   | 972.60    | 958.20    |
| 2   | 731.10    | 569.80    | 530.50    |
| 3   | 563.30    | 402.00    | 373.50    |

#### Dataset 3

| VMs | Avg TAT   | Avg WT    | Avg RT    |
|-----|-----------|-----------|-----------|
| 1   | 787.10    | 711.90    | 667.40    |
| 2   | 525.00    | 449.80    | 393.00    |
| 3   | 404.00    | 328.80    | 272.00    |

### Observation

Adding VMs consistently improves all three metrics. For Dataset 3:
- Going from 1→2 VMs improves Avg TAT by **~33%** (787 → 525)
- Going from 1→3 VMs improves Avg TAT by **~49%** (787 → 404)

This demonstrates that the SRDQ algorithm scales well with parallelism.

---

### Sample Scheduling Trace — Dataset 3 (any VM count)

The scheduling trace reflects the SRDQ algorithm's sequential decision logic (independent of VM count). Threshold = 71 MI; 5 cloudlets in SJF, 5 in RR.

```
[INIT] Threshold (median burst) = 71 MI
[INIT] Short Queue (SJF): 5 cloudlets | Long Queue (RR): 5 cloudlets
[t=0]    SJF -> Cloudlet #0  (burst=33)                      [q1Counter=1]
[t=33]   SJF -> Cloudlet #4  (burst=11)                      [q1Counter=2]
[t=44]   RR  -> Cloudlet #1  (burst=201, quantum=119, rem=82) [q1Counter=0]
[t=163]  SJF -> Cloudlet #8  (burst=18)                      [q1Counter=1]
[t=181]  SJF -> Cloudlet #6  (burst=33)                      [q1Counter=2]
[t=214]  RR  -> Cloudlet #2  (burst=98,  quantum=95,  rem=3)  [q1Counter=0]
[t=309]  SJF -> Cloudlet #9  (burst=64)                      [q1Counter=1]
[t=373]  RR  -> Cloudlet #3  (burst=116, quantum=76,  rem=40) [q1Counter=0]
[t=449]  RR  -> Cloudlet #5  (burst=100, quantum=61,  rem=39) [q1Counter=0]
[t=510]  RR  -> Cloudlet #7  (burst=78,  quantum=49,  rem=29) [q1Counter=0]
[t=559]  RR  -> Cloudlet #1  (burst=201, quantum=39,  rem=43) [q1Counter=0]
[t=598]  RR  -> Cloudlet #2  (burst=98,  quantum=31,  rem=0)  [q1Counter=0]
[t=601]  RR  -> Cloudlet #3  (burst=116, quantum=38,  rem=2)  [q1Counter=0]
[t=639]  RR  -> Cloudlet #5  (burst=100, quantum=29,  rem=10) [q1Counter=0]
[t=668]  RR  -> Cloudlet #7  (burst=78,  quantum=21,  rem=8)  [q1Counter=0]
[t=689]  RR  -> Cloudlet #1  (burst=201, quantum=16,  rem=27) [q1Counter=0]
[t=705]  RR  -> Cloudlet #3  (burst=116, quantum=12,  rem=0)  [q1Counter=0]
[t=707]  RR  -> Cloudlet #5  (burst=100, quantum=15,  rem=0)  [q1Counter=0]
[t=717]  RR  -> Cloudlet #7  (burst=78,  quantum=18,  rem=0)  [q1Counter=0]
[t=725]  RR  -> Cloudlet #1  (burst=201, quantum=27,  rem=0)  [q1Counter=0]
```

**Key observations from the trace:**
- The 2:1 interleaving is visible: two SJF tasks (#0, #4) run before the first RR slice.
- The dynamic quantum decreases over time as fewer long tasks remain (119 → 95 → 76 → ... → 12), showing the adaptive behavior.
- All cloudlets complete without starvation.

---

### Per-Cloudlet Results — Dataset 3, 3 VMs

| CID | Arrival | Burst(MI) | Queue | Start  | Finish | TAT    | WT     | RT     | Quantum |
|-----|---------|-----------|-------|--------|--------|--------|--------|--------|---------|
| 0   | 0       | 33        | SJF   | 100.00 | 210.00 | 210.00 | 177.00 | 100.00 | N/A     |
| 1   | 1       | 201       | RR    | 100.00 | 320.00 | 319.00 | 118.00 | 99.00  | 27      |
| 2   | 2       | 98        | RR    | 100.00 | 210.00 | 208.00 | 110.00 | 98.00  | 31      |
| 3   | 3       | 116       | RR    | 210.00 | 430.00 | 427.00 | 311.00 | 207.00 | 12      |
| 4   | 4       | 11        | SJF   | 320.00 | 430.00 | 426.00 | 415.00 | 316.00 | N/A     |
| 5   | 5       | 100       | RR    | 210.00 | 320.00 | 315.00 | 215.00 | 205.00 | 15      |
| 6   | 6       | 33        | SJF   | 430.00 | 540.00 | 534.00 | 501.00 | 424.00 | N/A     |
| 7   | 7       | 78        | RR    | 430.00 | 540.00 | 533.00 | 455.00 | 423.00 | 18      |
| 8   | 4       | 18        | SJF   | 320.00 | 430.00 | 426.00 | 408.00 | 316.00 | N/A     |
| 9   | 8       | 64        | SJF   | 540.00 | 650.00 | 642.00 | 578.00 | 532.00 | N/A     |

**Avg TAT: 404.00 | Avg WT: 328.80 | Avg RT: 272.00**

---

## Key Design Decisions

### 1. Broker-Level Scheduling
The SRDQ algorithm runs entirely inside `SRDQBroker`, which extends CloudSim's `DatacenterBroker`. All scheduling decisions (queue assignment, 2:1 interleaving, dynamic quantum) are made at the broker level before CloudSim's internal event loop begins. CloudSim is only used for accurate execution time measurement under parallel VM scenarios.

### 2. SpaceShared Cloudlet Scheduler
VMs use `CloudletSchedulerSpaceShared`, meaning each VM runs one cloudlet at a time. This is consistent with the paper's single-CPU model per VM, ensuring cloudlets on the same VM don't overlap.

### 3. Two-Phase Execution
The simulation has two distinct phases:
1. **Algorithm Phase** (`runSRDQSchedule`) — determines order and quantum values sequentially, produces the scheduling trace.
2. **CloudSim Phase** (`startSimulation`) — executes cloudlets across real VM resources in parallel, measures actual wall-clock finish times.

This separation allows the scheduling trace (which shows the SRDQ logic) to remain consistent across VM counts, while the metrics table reflects the true benefit of parallelism.

### 4. Unit Conversion
CloudSim internally works in seconds. Since `VM_MIPS = 1000`, one second of execution corresponds to 1000 MI of work. All metrics are converted to MI-equivalent units (`time_seconds × VM_MIPS`) so they are comparable with the paper's burst/arrival values.

---

## Known Limitations

- **Single host**: The datacenter uses one physical host. Adding multiple hosts would be needed to study host-level load balancing.
- **Homogeneous VMs**: All VMs are identical (same MIPS, RAM, BW). The algorithm does not account for heterogeneous VM capabilities.
- **CloudSim 3.0.3 is not on Maven Central**: The JAR must be manually installed into the local Maven repository (see [Build & Run](#build--run)).
- **Scheduling trace is always single-threaded**: The trace reflects sequential SRDQ logic and does not visually represent parallelism when multiple VMs are used.
