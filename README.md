# SJF + Dynamic Round Robin Scheduler 🚀

A high-performance, dual-queue process scheduling system that combines the efficiency of **Shortest Job First (SJF)** with the fairness of **Dynamic Round Robin (RR)**. This project features a core C++ implementation and a modern React-based web dashboard for real-time visualization.

[![C++](https://img.shields.io/badge/C++-00599C?style=for-the-badge&logo=c%2B%2B&logoColor=white)](https://isocpp.org/)
[![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)](https://reactjs.org/)
[![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

---

## 🧠 Scheduling Algorithm Architecture

The scheduler implements a sophisticated multi-level queue logic designed to minimize average waiting time while preventing starvation.

### 1. Dynamic Thresholding
At the start of each simulation, the system calculates a **Dynamic Threshold** based on the average burst time of all incoming processes:
$$\text{Threshold} = \lfloor \frac{\sum \text{Burst Times}}{N} \rfloor$$
- **Short Queue (Q1)**: Processes with $\text{Burst Time} \le \text{Threshold}$.
- **Long Queue (Q2)**: Processes with $\text{Burst Time} > \text{Threshold}$.

### 2. Multi-Queue Execution Logic
- **Queue 1 (SJF)**: Uses the **Shortest Job First** (Non-Preemptive) algorithm to quickly clear short-duration tasks.
- **Queue 2 (Dynamic RR)**: Uses a **Dynamic Time Quantum** Round Robin approach. The quantum is recalculated every round:
  $$\text{Quantum} = \lceil \text{Average Remaining Burst Time of Q2} \rceil$$

### 3. Interleaved Scheduling (Priority Management)
To ensure system responsiveness and prevent Q2 processes from waiting indefinitely, the scheduler follows a strict rotation:
- Execute **2 tasks** from the Short Queue (SJF).
- Execute **1 task slice** from the Long Queue (Dynamic RR).
- *Note: If a queue is empty, the scheduler dynamically adjusts to the available tasks.*

---

## 🛠️ Project Structure

```text
.
├── project.cpp           # Core C++ Scheduling Logic
├── input.txt             # Sample process input data
└── web-scheduler/        # React Visualization Dashboard
    ├── src/              # Frontend components & logic
    ├── Dockerfile        # Containerization setup
    └── package.json      # Frontend dependencies
```

---

## 🚀 Getting Started

### Backend (C++ CLI)
1. **Compile**:
   ```bash
   g++ project.cpp -o scheduler
   ```
2. **Run**:
   ```bash
   ./scheduler
   ```
3. **Input**: Enter the number of processes, followed by their Arrival Time (AT) and Burst Time (BT) when prompted.

### Frontend (Web Dashboard)
The web dashboard provides a beautiful interface to visualize the Gantt charts and performance metrics.

1. **Install Dependencies**:
   ```bash
   cd web-scheduler
   npm install
   ```
2. **Start Development Server**:
   ```bash
   npm run dev
   ```
3. **Docker (Optional)**:
   ```bash
   docker build -t scheduler-web .
   docker run -p 3000:80 scheduler-web
   ```

---

## 📊 Performance Metrics
The system automatically generates comprehensive reports including:
- **Arrival Time (AT)**, **Burst Time (BT)**, and **Completion Time (CT)**.
- **Turnaround Time (TAT)** and **Waiting Time (WT)** for every process.
- **Average TAT** and **Average WT** for the entire batch.
- **Gantt Charts**: Visual representation of the execution flow for both individual queues and the combined system.

---

## 🎨 Visualization Features
- **Real-time Gantt Chart**: Dynamic rendering of process execution blocks.
- **Process Table**: Interactive data grid showing scheduling results.
- **Responsive Design**: Optimized for both desktop and mobile viewing.

---

## 📜 License
This project is open-source and available under the [MIT License](LICENSE).
