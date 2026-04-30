/**
 * Logic for SJF + Dynamic Round Robin scheduling
 */

export const computeThreshold = (processes) => {
  if (processes.length === 0) return 0;
  const sum = processes.reduce((acc, p) => acc + p.burst, 0);
  return Math.floor(sum / processes.length);
};

export const computeDynamicQuantum = (longQ) => {
  const remainingProcs = longQ.filter(p => p.remaining > 0);
  if (remainingProcs.length === 0) return 1;
  
  const sum = remainingProcs.reduce((acc, p) => acc + p.remaining, 0);
  const quantum = Math.ceil(sum / remainingProcs.length);
  return Math.max(1, quantum);
};

export const runScheduler = (inputProcesses) => {
  // Deep copy processes
  const processes = inputProcesses.map(p => ({
    ...p,
    remaining: p.burst,
    completion: 0,
    waiting: 0,
    turnaround: 0,
    queue: 0
  }));

  const threshold = computeThreshold(processes);
  
  // Divide into queues
  const shortQ = processes.filter(p => p.burst <= threshold).map(p => ({ ...p, queue: 1 }));
  const longQ = processes.filter(p => p.burst > threshold).map(p => ({ ...p, queue: 2 }));

  let time = 0;
  let q1Counter = 0;
  let finishedTotal = 0;
  const totalProcs = processes.length;
  const gantt = [];
  const results = [];

  // Prepare SJF processes
  const sjfProcs = [...shortQ].sort((a, b) => {
    if (a.arrival !== b.arrival) return a.arrival - b.arrival;
    return a.burst - b.burst;
  });
  const sjfDone = new Array(sjfProcs.length).fill(false);

  // Prepare RR processes
  const rrProcs = [...longQ].sort((a, b) => a.arrival - b.arrival);
  const readyQ_RR = [];
  const inQueue_RR = new Array(rrProcs.length).fill(false);

  const enqueueRR = (currentTime) => {
    for (let i = 0; i < rrProcs.length; i++) {
      if (!inQueue_RR[i] && rrProcs[i].remaining > 0 && rrProcs[i].arrival <= currentTime) {
        readyQ_RR.push(i);
        inQueue_RR[i] = true;
      }
    }
  };

  while (finishedTotal < totalProcs) {
    enqueueRR(time);

    let bestSJF = -1;
    for (let i = 0; i < sjfProcs.length; i++) {
      if (!sjfDone[i] && sjfProcs[i].arrival <= time) {
        if (bestSJF === -1 || sjfProcs[i].burst < sjfProcs[bestSJF].burst) {
          bestSJF = i;
        }
      }
    }

    let runSJF = false;
    let runRR = false;

    // Interleaving logic: Q1 priority for 2 tasks, then Q2 turn
    if (q1Counter < 2 && bestSJF !== -1) {
      runSJF = true;
    } else if (readyQ_RR.length > 0) {
      runRR = true;
    } else if (bestSJF !== -1) {
      runSJF = true;
    }

    if (runSJF) {
      const start = time;
      const proc = sjfProcs[bestSJF];
      time += proc.burst;
      proc.remaining = 0;
      proc.completion = time;
      proc.turnaround = time - proc.arrival;
      proc.waiting = proc.turnaround - proc.burst;
      
      sjfDone[bestSJF] = true;
      finishedTotal++;
      q1Counter++;
      
      gantt.push({
        pid: proc.pid,
        start,
        end: time,
        queue: 'SJF',
        color: 'hsl(210, 80%, 60%)' // Blueish
      });
      results.push(proc);
    } else if (runRR) {
      const quantum = computeDynamicQuantum(rrProcs);
      const idx = readyQ_RR.shift();
      const proc = rrProcs[idx];

      const start = time;
      const exec = Math.min(quantum, proc.remaining);
      time += exec;
      proc.remaining -= exec;

      gantt.push({
        pid: proc.pid,
        start,
        end: time,
        queue: `RR (q=${quantum})`,
        color: 'hsl(280, 70%, 60%)' // Purpleish
      });

      enqueueRR(time);

      if (proc.remaining > 0) {
        readyQ_RR.push(idx);
      } else {
        finishedTotal++;
        inQueue_RR[idx] = false;
        proc.completion = time;
        proc.turnaround = time - proc.arrival;
        proc.waiting = proc.turnaround - proc.burst;
        results.push(proc);
      }
      q1Counter = 0; // Reset after RR turn
    } else {
      // Idle: jump to next arrival
      const sjfRemaining = sjfProcs.filter((_, i) => !sjfDone[i]);
      const rrRemaining = rrProcs.filter(p => p.remaining > 0);
      
      let nextArrival = Infinity;
      if (sjfRemaining.length > 0) {
        nextArrival = Math.min(nextArrival, ...sjfRemaining.map(p => p.arrival));
      }
      if (rrRemaining.length > 0) {
        nextArrival = Math.min(nextArrival, ...rrRemaining.map(p => p.arrival));
      }
      
      if (nextArrival === Infinity) break;
      time = Math.max(time, nextArrival);
    }
  }


  return {
    results: results.sort((a, b) => a.pid - b.pid),
    gantt,
    threshold,
    avgTAT: results.reduce((acc, p) => acc + p.turnaround, 0) / results.length,
    avgWT: results.reduce((acc, p) => acc + p.waiting, 0) / results.length
  };
};
