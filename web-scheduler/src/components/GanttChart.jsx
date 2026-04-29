import { motion } from 'framer-motion';

export default function GanttChart({ chart }) {
  if (!chart || chart.length === 0) return null;

  const totalTime = chart[chart.length - 1].end;

  return (
    <div className="gantt-wrapper">
      <div className="gantt-container">
        {chart.map((item, index) => {
          const duration = item.end - item.start;
          const widthPercentage = (duration / totalTime) * 100;
          
          return (
            <motion.div
              key={index}
              className="gantt-block"
              style={{ 
                width: `${Math.max(widthPercentage, 5)}%`, 
                backgroundColor: item.color,
                borderLeft: index === 0 ? 'none' : '1px solid rgba(255, 255, 255, 0.2)'
              }}
              initial={{ scaleX: 0, originX: 0 }}
              animate={{ scaleX: 1 }}
              transition={{ delay: index * 0.1, duration: 0.3 }}
            >
              <div className="gantt-pid">P{item.pid}</div>
              <div className="gantt-label">{item.queue}</div>
              
              {/* Time markers */}
              <span className="time-marker">{item.start}</span>
              {index === chart.length - 1 && (
                <span className="time-marker-end">{item.end}</span>
              )}
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}
