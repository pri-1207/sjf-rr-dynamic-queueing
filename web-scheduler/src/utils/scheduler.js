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
  const gantt = [];
  const results = [];

  // --- 1. Schedule Short Queue (SJF) ---
  const sjfProcs = [...shortQ].sort((a, b) => {
    if (a.arrival !== b.arrival) return a.arrival - b.arrival;
    return a.burst - b.burst;
  });

  const sjfDone = new Array(sjfProcs.length).fill(false);
  let sjfFinished = 0;

  while (sjfFinished < sjfProcs.length) {
    let best = -1;
    for (let i = 0; i < sjfProcs.length; i++) {
      if (!sjfDone[i] && sjfProcs[i].arrival <= time) {
        if (best === -1 || sjfProcs[i].burst < sjfProcs[best].burst) {
          best = i;
        }
      }
    }

    if (best === -1) {
      const nextArrival = Math.min(...sjfProcs.filter((_, i) => !sjfDone[i]).map(p => p.arrival));
      time = nextArrival;
      continue;
    }

    const start = time;
    const proc = sjfProcs[best];
    time += proc.burst;
    proc.remaining = 0;
    proc.completion = time;
    proc.turnaround = time - proc.arrival;
    proc.waiting = proc.turnaround - proc.burst;
    
    sjfDone[best] = true;
    sjfFinished++;
    
    gantt.push({
      pid: proc.pid,
      start,
      end: time,
      queue: 'SJF',
      color: 'hsl(210, 80%, 60%)' // Blueish
    });
    results.push(proc);
  }

  // --- 2. Schedule Long Queue (Dynamic RR) ---
  const rrProcs = [...longQ].sort((a, b) => a.arrival - b.arrival);
  const readyQ = [];
  const inQueue = new Array(rrProcs.length).fill(false);
  let rrFinished = 0;

  const enqueueArrivals = (currentTime) => {
    for (let i = 0; i < rrProcs.length; i++) {
      if (!inQueue[i] && rrProcs[i].remaining > 0 && rrProcs[i].arrival <= currentTime) {
        readyQ.push(i);
        inQueue[i] = true;
      }
    }
  };

  enqueueArrivals(time);

  while (rrFinished < rrProcs.length) {
    if (readyQ.length === 0) {
      const nextArrival = Math.min(...rrProcs.filter(p => p.remaining > 0).map(p => p.arrival));
      time = Math.max(time, nextArrival);
      enqueueArrivals(time);
      continue;
    }

    const quantum = computeDynamicQuantum(rrProcs);
    const idx = readyQ.shift();
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

    enqueueArrivals(time);

    if (proc.remaining > 0) {
      readyQ.push(idx);
    } else {
      rrFinished++;
      inQueue[idx] = false;
      proc.completion = time;
      proc.turnaround = time - proc.arrival;
      proc.waiting = proc.turnaround - proc.burst;
      results.push(proc);
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
