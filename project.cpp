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

bool cmp(Process a, Process b) {
    return a.remaining < b.remaining;
}

int main() {
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

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
    vector<Process> ready;
    vector<Gantt> chart;

    while (completed < n) {
        // Add arrived processes
        for (int i = 0; i < n; i++) {
            if (p[i].arrival <= time && p[i].remaining > 0) {
                bool exists = false;
                for (auto &r : ready) {
                    if (r.pid == p[i].pid) {
                        exists = true;
                        break;
                    }
                }
                if (!exists)
                    ready.push_back(p[i]);
            }
        }

        if (ready.empty()) {
            time++;
            continue;
        }

        sort(ready.begin(), ready.end(), cmp);

        // Dynamic quantum = average remaining time
        int total = 0;
        for (auto &r : ready)
            total += r.remaining;

        int quantum = max(1, total / (int)ready.size());

        Process current = ready[0];
        ready.erase(ready.begin());

        int start_time = time;
        int exec_time = min(quantum, current.remaining);

        time += exec_time;
        current.remaining -= exec_time;

        // Record Gantt entry
        chart.push_back({current.pid, start_time, time});

        // Update original process
        for (int i = 0; i < n; i++) {
            if (p[i].pid == current.pid) {
                p[i].remaining = current.remaining;

                if (current.remaining == 0) {
                    completed++;
                    p[i].completion = time;
                    p[i].turnaround = p[i].completion - p[i].arrival;
                    p[i].waiting = p[i].turnaround - p[i].burst;
                }
                break;
            }
        }

        if (current.remaining > 0)
            ready.push_back(current);
    }

    // Process table
    cout << "\nPID\tAT\tBT\tCT\tTAT\tWT\n";
    for (auto &pr : p) {
        cout << pr.pid << "\t"
             << pr.arrival << "\t"
             << pr.burst << "\t"
             << pr.completion << "\t"
             << pr.turnaround << "\t"
             << pr.waiting << "\n";
    }

    // Gantt Chart
    cout << "\nGantt Chart:\n";

    for (auto &g : chart) {
        cout << "| P" << g.pid << " ";
    }
    cout << "|\n";

    cout << chart[0].start;
    for (auto &g : chart) {
        cout << "    " << g.end;
    }
    cout << "\n";

    return 0;
}