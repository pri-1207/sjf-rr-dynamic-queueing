export default function ProcessTable({ processes, results = [] }) {
  const getResult = (pid) => results.find(r => r.pid === pid);

  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>PID</th>
            <th>Arrival</th>
            <th>Burst</th>
            {results.length > 0 && (
              <>
                <th>CT</th>
                <th>TAT</th>
                <th>WT</th>
                <th>Queue</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {processes.map((p) => {
            const res = getResult(p.pid);
            return (
              <tr key={p.pid} className="fade-in">
                <td><strong>P{p.pid}</strong></td>
                <td>{p.arrival}</td>
                <td>{p.burst}</td>
                {res && (
                  <>
                    <td>{res.completion}</td>
                    <td>{res.turnaround}</td>
                    <td>{res.waiting}</td>
                    <td>
                      <span className={`badge ${res.queue === 1 ? 'badge-sjf' : 'badge-rr'}`}>
                        {res.queue === 1 ? 'SJF' : 'Dyn RR'}
                      </span>
                    </td>
                  </>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
