#include <bits/stdc++.h>
using namespace std;

struct Process {
    int pid;
    int arrival;
    int burst;
    int remaining;
    int completion;
    int waiting;
    int turnaround;
};

struct Gantt {
    int pid;
    int start;
    int end;
};

int main() {
    int n, quantum;
    cout << "Enter number of processes: ";
    cin >> n;

    cout << "Enter time quantum for Round Robin: ";
    cin >> quantum;

    vector<Process> p(n);

    for (int i = 0; i < n; i++) {
        cout << "Process " << i + 1 << " Arrival Time: ";
        cin >> p[i].arrival;
        cout << "Process " << i + 1 << " Burst Time: ";
        cin >> p[i].burst;

        p[i].pid = i + 1;
        p[i].remaining = p[i].burst;
    }

    int time = 0, completed = 0;
    vector<Gantt> chart;
    vector<bool> inQueue(n, false);
    deque<int> ready; // stores indices into p[]

    // Add processes arriving at time 0 (sorted by burst for SJF priority)
    vector<int> initialArrivals;
    for (int i = 0; i < n; i++) {
        if (p[i].arrival <= 0) {
            initialArrivals.push_back(i);
        }
    }
    // Sort initial arrivals by burst time (SJF)
    sort(initialArrivals.begin(), initialArrivals.end(), [&](int a, int b) {
        return p[a].remaining < p[b].remaining;
    });
    for (int idx : initialArrivals) {
        ready.push_back(idx);
        inQueue[idx] = true;
    }

    while (completed < n) {
        // If ready queue is empty, jump time to next arrival
        if (ready.empty()) {
            int nextArrival = INT_MAX;
            for (int i = 0; i < n; i++) {
                if (p[i].remaining > 0 && p[i].arrival > time)
                    nextArrival = min(nextArrival, p[i].arrival);
            }
            time = nextArrival;

            // Add all processes arriving at this time
            vector<int> newArrivals;
            for (int i = 0; i < n; i++) {
                if (p[i].arrival <= time && p[i].remaining > 0 && !inQueue[i]) {
                    newArrivals.push_back(i);
                }
            }
            sort(newArrivals.begin(), newArrivals.end(), [&](int a, int b) {
                return p[a].remaining < p[b].remaining;
            });
            for (int idx : newArrivals) {
                ready.push_back(idx);
                inQueue[idx] = true;
            }
            continue;
        }

        // Pick the process with shortest remaining time from the ready queue (SJF)
        int bestPos = 0;
        for (int i = 1; i < (int)ready.size(); i++) {
            if (p[ready[i]].remaining < p[ready[bestPos]].remaining) {
                bestPos = i;
            }
        }

        int idx = ready[bestPos];
        ready.erase(ready.begin() + bestPos);

        int start_time = time;
        int exec_time = min(quantum, p[idx].remaining);

        time += exec_time;
        p[idx].remaining -= exec_time;

        // Record Gantt entry
        chart.push_back({p[idx].pid, start_time, time});

        // Add newly arrived processes BEFORE re-adding current (RR fairness)
        vector<int> newArrivals;
        for (int i = 0; i < n; i++) {
            if (p[i].arrival <= time && p[i].remaining > 0 && !inQueue[i] && i != idx) {
                newArrivals.push_back(i);
            }
        }
        // Sort new arrivals by remaining time (SJF ordering)
        sort(newArrivals.begin(), newArrivals.end(), [&](int a, int b) {
            return p[a].remaining < p[b].remaining;
        });
        for (int id : newArrivals) {
            ready.push_back(id);
            inQueue[id] = true;
        }

        // If current process is not finished, add it back to the queue (RR behavior)
        if (p[idx].remaining > 0) {
            ready.push_back(idx);
        } else {
            // Process completed
            completed++;
            inQueue[idx] = false;
            p[idx].completion = time;
            p[idx].turnaround = p[idx].completion - p[idx].arrival;
            p[idx].waiting = p[idx].turnaround - p[idx].burst;
        }
    }

    // Process table
    cout << "\n+-----+-----+-----+-----+-----+-----+\n";
    cout << "| PID |  AT |  BT |  CT | TAT |  WT |\n";
    cout << "+-----+-----+-----+-----+-----+-----+\n";
    float totalTAT = 0, totalWT = 0;
    for (auto &pr : p) {
        printf("| %3d | %3d | %3d | %3d | %3d | %3d |\n",
               pr.pid, pr.arrival, pr.burst, pr.completion, pr.turnaround, pr.waiting);
        totalTAT += pr.turnaround;
        totalWT += pr.waiting;
    }
    cout << "+-----+-----+-----+-----+-----+-----+\n";

    printf("\nAverage Turnaround Time: %.2f\n", totalTAT / n);
    printf("Average Waiting Time:    %.2f\n", totalWT / n);

    // Gantt Chart
    cout << "\nGantt Chart:\n";

    // Top border
    for (auto &g : chart) {
        int width = max(4, (int)to_string(g.pid).length() + 3);
        cout << "+";
        for (int i = 0; i < width; i++) cout << "-";
    }
    cout << "+\n";

    // Process IDs
    for (auto &g : chart) {
        int width = max(4, (int)to_string(g.pid).length() + 3);
        string label = " P" + to_string(g.pid) + " ";
        int pad = width - label.length();
        cout << "|" << label;
        for (int i = 0; i < pad; i++) cout << " ";
    }
    cout << "|\n";

    // Bottom border
    for (auto &g : chart) {
        int width = max(4, (int)to_string(g.pid).length() + 3);
        cout << "+";
        for (int i = 0; i < width; i++) cout << "-";
    }
    cout << "+\n";

    // Time markers
    cout << chart[0].start;
    for (auto &g : chart) {
        int width = max(4, (int)to_string(g.pid).length() + 3);
        string endStr = to_string(g.end);
        for (int i = 0; i < width + 1 - (int)endStr.length(); i++) cout << " ";
        cout << endStr;
    }
    cout << "\n";

    return 0;
}