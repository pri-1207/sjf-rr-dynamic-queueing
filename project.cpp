#include <bits/stdc++.h>
using namespace std;

// ─── Data Structures ────────────────────────────────────────────────

struct Process {
    int pid;
    int arrival;
    int burst;
    int remaining;
    int completion;
    int waiting;
    int turnaround;
    int queue;        // 1 = Short (SJF), 2 = Long (Dynamic RR)
};

struct Gantt {
    int pid;
    int start;
    int end;
    string queueName; // label for the Gantt chart
};

// ─── Utility: compute dynamic threshold ─────────────────────────────
// Threshold = average burst time (rounded down).
// Processes with burst <= threshold  →  Short Queue  (SJF)
// Processes with burst >  threshold  →  Long  Queue  (Dynamic RR)

int computeThreshold(const vector<Process>& procs) {
    int sum = 0;
    for (auto& p : procs) sum += p.burst;
    return sum / (int)procs.size();          // integer average
}

// ─── Utility: compute dynamic quantum ───────────────────────────────
// Quantum = ceil( average of remaining burst times in the long queue )
// Recalculated each scheduling round so it adapts as processes finish.

int computeDynamicQuantum(const vector<Process>& longQ) {
    if (longQ.empty()) return 1;
    int sum = 0, cnt = 0;
    for (auto& p : longQ) {
        if (p.remaining > 0) {
            sum += p.remaining;
            cnt++;
        }
    }
    if (cnt == 0) return 1;
    return max(1, (sum + cnt - 1) / cnt);    // ceil(avg)
}

// ─── Pretty-print helpers ───────────────────────────────────────────

void printGanttChart(const vector<Gantt>& chart) {
    if (chart.empty()) return;

    // Top border
    for (auto& g : chart) {
        int width = max(5, (int)(" P" + to_string(g.pid) + " ").length() + 1);
        cout << "+";
        for (int i = 0; i < width; i++) cout << "-";
    }
    cout << "+\n";

    // Process IDs
    for (auto& g : chart) {
        int width = max(5, (int)(" P" + to_string(g.pid) + " ").length() + 1);
        string label = " P" + to_string(g.pid) + " ";
        int pad = width - (int)label.length();
        cout << "|" << label;
        for (int i = 0; i < pad; i++) cout << " ";
    }
    cout << "|\n";

    // Bottom border
    for (auto& g : chart) {
        int width = max(5, (int)(" P" + to_string(g.pid) + " ").length() + 1);
        cout << "+";
        for (int i = 0; i < width; i++) cout << "-";
    }
    cout << "+\n";

    // Time markers
    cout << chart[0].start;
    for (auto& g : chart) {
        int width = max(5, (int)(" P" + to_string(g.pid) + " ").length() + 1);
        string endStr = to_string(g.end);
        for (int i = 0; i < width + 1 - (int)endStr.length(); i++) cout << " ";
        cout << endStr;
    }
    cout << "\n";
}

// ════════════════════════════════════════════════════════════════════
//                          MAIN
// ════════════════════════════════════════════════════════════════════

int main() {
    // ── 1. Input ────────────────────────────────────────────────────
    int n;
    cout << "=============================================\n";
    cout << "   SJF + Dynamic RR  (Two-Queue Scheduler)\n";
    cout << "=============================================\n\n";
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> procs(n);
    for (int i = 0; i < n; i++) {
        procs[i].pid = i + 1;
        cout << "  P" << i + 1 << " Arrival Time : ";
        cin >> procs[i].arrival;
        cout << "  P" << i + 1 << " Burst   Time : ";
        cin >> procs[i].burst;
        procs[i].remaining = procs[i].burst;
    }

    // ── 2. Compute dynamic threshold & divide into queues ──────────
    int threshold = computeThreshold(procs);

    vector<Process> shortQ, longQ;
    for (auto& p : procs) {
        if (p.burst <= threshold) {
            p.queue = 1;
            shortQ.push_back(p);
        } else {
            p.queue = 2;
            longQ.push_back(p);
        }
    }

    cout << "\n---------------------------------------------\n";
    cout << "  Dynamic Threshold (avg burst) = " << threshold << "\n";
    cout << "---------------------------------------------\n";
    cout << "  Short Queue (burst <= " << threshold << ") [SJF]       : ";
    for (auto& p : shortQ) cout << "P" << p.pid << " ";
    cout << "\n";
    cout << "  Long  Queue (burst >  " << threshold << ") [Dynamic RR]: ";
    for (auto& p : longQ) cout << "P" << p.pid << " ";
    cout << "\n---------------------------------------------\n";

    // ── 3. Schedule Short Queue — Non-preemptive SJF ───────────────
    //    Sort by arrival, then by burst (SJF).

    sort(shortQ.begin(), shortQ.end(), [](const Process& a, const Process& b) {
        if (a.arrival != b.arrival) return a.arrival < b.arrival;
        return a.burst < b.burst;
    });

    int time = 0;
    vector<Gantt> ganttSJF;

    vector<bool> done(shortQ.size(), false);
    int finished = 0;
    while (finished < (int)shortQ.size()) {
        // Find shortest available job
        int best = -1;
        for (int i = 0; i < (int)shortQ.size(); i++) {
            if (!done[i] && shortQ[i].arrival <= time) {
                if (best == -1 || shortQ[i].burst < shortQ[best].burst)
                    best = i;
            }
        }
        if (best == -1) {
            // advance time to next arrival
            int nextArr = INT_MAX;
            for (int i = 0; i < (int)shortQ.size(); i++)
                if (!done[i]) nextArr = min(nextArr, shortQ[i].arrival);
            time = nextArr;
            continue;
        }
        int start = time;
        time += shortQ[best].burst;
        shortQ[best].remaining   = 0;
        shortQ[best].completion  = time;
        shortQ[best].turnaround  = time - shortQ[best].arrival;
        shortQ[best].waiting     = shortQ[best].turnaround - shortQ[best].burst;
        done[best] = true;
        finished++;
        ganttSJF.push_back({shortQ[best].pid, start, time, "SJF"});
    }

    // ── 4. Schedule Long Queue — Dynamic Round Robin ───────────────
    //    Processes in longQ start being eligible from max(their arrival, SJF-end)

    // Sort by arrival for the ready-queue logic
    sort(longQ.begin(), longQ.end(), [](const Process& a, const Process& b) {
        return a.arrival < b.arrival;
    });

    vector<Gantt> ganttRR;
    deque<int> readyQ;                 // indices into longQ
    vector<bool> inQueue(longQ.size(), false);
    int completedRR = 0;

    // Enqueue processes that have arrived by 'time'
    auto enqueueArrivals = [&](int upTo) {
        for (int i = 0; i < (int)longQ.size(); i++) {
            if (!inQueue[i] && longQ[i].remaining > 0 && longQ[i].arrival <= upTo) {
                readyQ.push_back(i);
                inQueue[i] = true;
            }
        }
    };

    enqueueArrivals(time);

    while (completedRR < (int)longQ.size()) {
        if (readyQ.empty()) {
            int nextArr = INT_MAX;
            for (int i = 0; i < (int)longQ.size(); i++)
                if (longQ[i].remaining > 0) nextArr = min(nextArr, longQ[i].arrival);
            time = max(time, nextArr);
            enqueueArrivals(time);
            continue;
        }

        // Compute dynamic quantum based on current remaining bursts
        int quantum = computeDynamicQuantum(longQ);

        int idx = readyQ.front();
        readyQ.pop_front();

        int start = time;
        int exec  = min(quantum, longQ[idx].remaining);
        time += exec;
        longQ[idx].remaining -= exec;

        ganttRR.push_back({longQ[idx].pid, start, time, "RR(q=" + to_string(quantum) + ")"});

        // Enqueue newly arrived processes before re-adding current
        enqueueArrivals(time);

        if (longQ[idx].remaining > 0) {
            readyQ.push_back(idx);
        } else {
            completedRR++;
            inQueue[idx] = false;
            longQ[idx].completion  = time;
            longQ[idx].turnaround  = time - longQ[idx].arrival;
            longQ[idx].waiting     = longQ[idx].turnaround - longQ[idx].burst;
        }
    }

    // ── 5. Merge results back & build combined Gantt chart ─────────
    // Write completion data back into the master array
    for (auto& sp : shortQ)
        for (auto& p : procs)
            if (p.pid == sp.pid) {
                p.completion  = sp.completion;
                p.turnaround  = sp.turnaround;
                p.waiting     = sp.waiting;
                p.queue       = 1;
            }
    for (auto& lp : longQ)
        for (auto& p : procs)
            if (p.pid == lp.pid) {
                p.completion  = lp.completion;
                p.turnaround  = lp.turnaround;
                p.waiting     = lp.waiting;
                p.queue       = 2;
            }

    vector<Gantt> combined;
    combined.insert(combined.end(), ganttSJF.begin(), ganttSJF.end());
    combined.insert(combined.end(), ganttRR.begin(),  ganttRR.end());

    // ── 6. Output ──────────────────────────────────────────────────

    // 6a. Process Table
    cout << "\n==================== RESULTS ====================\n";
    cout << "+-----+-----+-----+-------+-----+-----+-----------+\n";
    cout << "| PID |  AT |  BT |   CT  | TAT |  WT |   Queue   |\n";
    cout << "+-----+-----+-----+-------+-----+-----+-----------+\n";

    float totalTAT = 0, totalWT = 0;
    for (auto& p : procs) {
        string qLabel = (p.queue == 1) ? "SJF" : "Dyn RR";
        printf("| %3d | %3d | %3d | %5d | %3d | %3d | %-9s |\n",
               p.pid, p.arrival, p.burst, p.completion,
               p.turnaround, p.waiting, qLabel.c_str());
        totalTAT += p.turnaround;
        totalWT  += p.waiting;
    }

    cout << "+-----+-----+-----+-------+-----+-----+-----------+\n";
    printf("\n  Average Turnaround Time : %.2f\n", totalTAT / n);
    printf("  Average Waiting Time    : %.2f\n", totalWT / n);

    // 6b. Gantt Charts
    cout << "\n============ Gantt Chart: Short Queue (SJF) ============\n";
    printGanttChart(ganttSJF);

    cout << "\n========= Gantt Chart: Long Queue (Dynamic RR) =========\n";
    printGanttChart(ganttRR);

    cout << "\n============= Combined Gantt Chart =============\n";
    printGanttChart(combined);

    // 6c. Dynamic Quantum log for RR
    cout << "\n--- Dynamic Quantum Values Used (per RR slice) ---\n";
    for (auto& g : ganttRR) {
        printf("  [%2d - %2d]  P%-2d  %s\n", g.start, g.end, g.pid, g.queueName.c_str());
    }

    cout << "\n=================================================\n";

    return 0;
}