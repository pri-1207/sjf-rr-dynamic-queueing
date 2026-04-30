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

    // 1. Calculate widths and labels
    vector<int> widths;
    vector<string> labels;
    for (auto& g : chart) {
        string label = " P" + to_string(g.pid);
        if (!g.queueName.empty()) {
            if (g.queueName.find("RR") != string::npos) label += "(RR)";
            else label += "(SJF)";
        }
        labels.push_back(label);
        widths.push_back(max(7, (int)label.length() + 2));
    }

    // 2. Top border
    for (int w : widths) {
        cout << "+";
        for (int i = 0; i < w; i++) cout << "-";
    }
    cout << "+\n";

    // 3. Labels
    for (int i = 0; i < (int)chart.size(); i++) {
        int pad = widths[i] - (int)labels[i].length();
        cout << "|" << labels[i];
        for (int j = 0; j < pad; j++) cout << " ";
    }
    cout << "|\n";

    // 4. Bottom border
    for (int w : widths) {
        cout << "+";
        for (int i = 0; i < w; i++) cout << "-";
    }
    cout << "+\n";

    // 5. Time markers
    cout << chart[0].start;
    for (int i = 0; i < (int)chart.size(); i++) {
        string endStr = to_string(chart[i].end);
        for (int j = 0; j < widths[i] + 1 - (int)endStr.length(); j++) cout << " ";
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

    // ── 3. Unified Scheduler — Interleaved Q1 (SJF) and Q2 (RR) ──────
    // Requirement: Run 1 task from Q2 after every 2 tasks from Q1.

    int time = 0;
    int q1Counter = 0;
    int finishedTotal = 0;
    int totalProcs = (int)procs.size();

    vector<Gantt> ganttSJF, ganttRR;
    deque<int> readyQ_RR;                 // indices into longQ
    vector<bool> inQueue_RR(longQ.size(), false);
    vector<bool> done_SJF(shortQ.size(), false);

    // Sort shortQ by arrival for easier access if needed, but the SJF search handles it
    sort(shortQ.begin(), shortQ.end(), [](const Process& a, const Process& b) {
        if (a.arrival != b.arrival) return a.arrival < b.arrival;
        return a.burst < b.burst;
    });

    // Sort longQ by arrival initially for the ready queue logic
    sort(longQ.begin(), longQ.end(), [](const Process& a, const Process& b) {
        return a.arrival < b.arrival;
    });

    auto enqueueRR = [&](int upTo) {
        for (int i = 0; i < (int)longQ.size(); i++) {
            if (!inQueue_RR[i] && longQ[i].remaining > 0 && longQ[i].arrival <= upTo) {
                readyQ_RR.push_back(i);
                inQueue_RR[i] = true;
            }
        }
    };

    while (finishedTotal < totalProcs) {
        // Enqueue arrivals in RR
        enqueueRR(time);

        // Find shortest available SJF job
        int bestSJF = -1;
        for (int i = 0; i < (int)shortQ.size(); i++) {
            if (!done_SJF[i] && shortQ[i].arrival <= time) {
                if (bestSJF == -1 || shortQ[i].burst < shortQ[bestSJF].burst)
                    bestSJF = i;
            }
        }

        bool runSJF = false;
        bool runRR = false;

        // Decision logic: Q1 gets priority until 2 tasks are run, then Q2 gets a turn if available
        if (q1Counter < 2 && bestSJF != -1) {
            runSJF = true;
        } else if (!readyQ_RR.empty()) {
            runRR = true;
        } else if (bestSJF != -1) {
            runSJF = true;
        }

        if (runSJF) {
            int start = time;
            time += shortQ[bestSJF].burst;
            shortQ[bestSJF].remaining = 0;
            shortQ[bestSJF].completion = time;
            shortQ[bestSJF].turnaround = time - shortQ[bestSJF].arrival;
            shortQ[bestSJF].waiting = shortQ[bestSJF].turnaround - shortQ[bestSJF].burst;
            done_SJF[bestSJF] = true;
            finishedTotal++;
            q1Counter++;
            ganttSJF.push_back({shortQ[bestSJF].pid, start, time, "SJF"});
        } else if (runRR) {
            int quantum = computeDynamicQuantum(longQ);
            int idx = readyQ_RR.front();
            readyQ_RR.pop_front();

            int start = time;
            int exec = min(quantum, longQ[idx].remaining);
            time += exec;
            longQ[idx].remaining -= exec;

            ganttRR.push_back({longQ[idx].pid, start, time, "RR(q=" + to_string(quantum) + ")"});

            // Enqueue arrivals during this slice
            enqueueRR(time);

            if (longQ[idx].remaining > 0) {
                readyQ_RR.push_back(idx);
            } else {
                finishedTotal++;
                inQueue_RR[idx] = false;
                longQ[idx].completion = time;
                longQ[idx].turnaround = time - longQ[idx].arrival;
                longQ[idx].waiting = longQ[idx].turnaround - longQ[idx].burst;
            }
            q1Counter = 0; // Reset counter after RR slice
        } else {
            // Idle: jump to next arrival
            int nextArr = INT_MAX;
            for (int i = 0; i < (int)shortQ.size(); i++)
                if (!done_SJF[i]) nextArr = min(nextArr, shortQ[i].arrival);
            for (int i = 0; i < (int)longQ.size(); i++)
                if (longQ[i].remaining > 0) nextArr = min(nextArr, longQ[i].arrival);

            if (nextArr == INT_MAX) break;
            time = max(time, nextArr);
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
    sort(combined.begin(), combined.end(), [](const Gantt& a, const Gantt& b) {
        return a.start < b.start;
    });

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