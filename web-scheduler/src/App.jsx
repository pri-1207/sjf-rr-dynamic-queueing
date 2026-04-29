import { useState, useMemo } from 'react';
import { Plus, Play, RefreshCw, Trash2, Cpu, BarChart3, Clock } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { runScheduler } from './utils/scheduler';
import GanttChart from './components/GanttChart';
import ProcessTable from './components/ProcessTable';

function App() {
  const [processes, setProcesses] = useState([
    { pid: 1, arrival: 0, burst: 3 },
    { pid: 2, arrival: 1, burst: 8 },
    { pid: 3, arrival: 2, burst: 5 },
    { pid: 4, arrival: 3, burst: 2 },
    { pid: 5, arrival: 4, burst: 12 },
  ]);

  const [newArrival, setNewArrival] = useState('');
  const [newBurst, setNewBurst] = useState('');
  const [results, setResults] = useState(null);

  const handleAddProcess = () => {
    if (newArrival === '' || newBurst === '') return;
    const pid = processes.length > 0 ? Math.max(...processes.map(p => p.pid)) + 1 : 1;
    setProcesses([...processes, { pid, arrival: parseInt(newArrival), burst: parseInt(newBurst) }]);
    setNewArrival('');
    setNewBurst('');
    setResults(null);
  };

  const handleRun = () => {
    const output = runScheduler(processes);
    setResults(output);
  };

  const handleReset = () => {
    setProcesses([]);
    setResults(null);
  };

  return (
    <div className="app-container">
      <header>
        <motion.h1 
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          Adaptive Scheduler
        </motion.h1>
        <p className="subtitle">SJF + Dynamic Round Robin with Adaptive Thresholding</p>
      </header>

      <div className="grid-layout">
        {/* Left Column: Input and Process List */}
        <div className="glass-card">
          <h2 style={{ marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Plus size={24} /> Add Processes
          </h2>
          
          <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem' }}>
            <div className="input-group" style={{ flex: 1 }}>
              <label className="input-label">Arrival Time</label>
              <input 
                type="number" 
                placeholder="0" 
                value={newArrival} 
                onChange={(e) => setNewArrival(e.target.value)} 
              />
            </div>
            <div className="input-group" style={{ flex: 1 }}>
              <label className="input-label">Burst Time</label>
              <input 
                type="number" 
                placeholder="5" 
                value={newBurst} 
                onChange={(e) => setNewBurst(e.target.value)} 
              />
            </div>
          </div>
          
          <button className="btn btn-primary" onClick={handleAddProcess} style={{ marginBottom: '2rem' }}>
            Add to Queue
          </button>

          <h3 style={{ marginBottom: '1rem' }}>Process List</h3>
          <ProcessTable processes={processes} results={results?.results} />
          
          <div style={{ display: 'flex', gap: '1rem', marginTop: '2rem' }}>
            <button className="btn btn-primary" onClick={handleRun} style={{ flex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}>
              <Play size={18} /> Calculate Schedule
            </button>
            <button className="btn btn-ghost" onClick={handleReset} style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}>
              <RefreshCw size={18} /> Reset
            </button>
          </div>
        </div>

        {/* Right Column: Visualization and Results */}
        <div className="glass-card">
          <h2 style={{ marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <BarChart3 size={24} /> Execution Summary
          </h2>

          {!results ? (
            <div style={{ textAlign: 'center', padding: '4rem 0', color: '#94a3b8' }}>
              <Cpu size={64} style={{ opacity: 0.2, marginBottom: '1rem' }} />
              <p>Add processes and click 'Calculate' to see the results.</p>
            </div>
          ) : (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <div style={{ marginBottom: '2rem' }}>
                <div style={{ color: '#94a3b8', marginBottom: '0.5rem' }}>Dynamic Threshold</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'white' }}>
                   Average Burst = {results.threshold}
                </div>
                <div style={{ fontSize: '0.9rem', color: '#94a3b8' }}>
                  (SJF for burst ≤ {results.threshold}, Dynamic RR for burst &gt; {results.threshold})
                </div>
              </div>

              <h3>Gantt Chart</h3>
              <GanttChart chart={results.gantt} />

              <div className="metrics-grid">
                <div className="metric-card">
                  <div className="metric-value">{results.avgTAT.toFixed(2)}</div>
                  <div className="metric-label">Avg Turnaround Time</div>
                </div>
                <div className="metric-card">
                  <div className="metric-value">{results.avgWT.toFixed(2)}</div>
                  <div className="metric-label">Avg Waiting Time</div>
                </div>
              </div>

              <div style={{ marginTop: '2rem', padding: '1rem', background: 'rgba(56, 189, 248, 0.1)', borderRadius: '0.75rem', border: '1px solid rgba(56, 189, 248, 0.2)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#38bdf8', marginBottom: '0.5rem' }}>
                   <Clock size={16} /> <strong>Scheduling Insight</strong>
                </div>
                <p style={{ fontSize: '0.9rem', color: 'rgba(255, 255, 255, 0.8)' }}>
                  The dynamic quantum for the Long Queue was automatically adjusted based on the average remaining burst times in each round to minimize waiting time for shorter-long processes.
                </p>
              </div>
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;
