# Web Scheduler Dashboard 📊

This is the web-based visualization component of the **SJF + Dynamic Round Robin Scheduler** project. It allows users to input process data and visualize the scheduling algorithm's behavior through interactive Gantt charts and data tables.

## 🚀 Quick Start

### Prerequisites
- Node.js (v18+)
- npm or yarn

### Installation
```bash
npm install
```

### Development
```bash
npm run dev
```
The application will be available at `http://localhost:5173`.

### Production Build
```bash
npm run build
```

---

## 🐳 Docker Support

You can run the dashboard as a containerized application:

1. **Build the Image**:
   ```bash
   docker build -t scheduler-dashboard .
   ```

2. **Run the Container**:
   ```bash
   docker run -p 8080:80 scheduler-dashboard
   ```
Access the dashboard at `http://localhost:8080`.

---

## 🛠️ Technology Stack
- **Framework**: [React](https://reactjs.org/)
- **Build Tool**: [Vite](https://vitejs.dev/)
- **Styling**: Vanilla CSS with modern design principles.
- **Logic**: Ported C++ scheduling algorithm for real-time calculation in the browser.

---

## 🔗 Main Project
For the core algorithm details and C++ implementation, please refer to the [Root README](../README.md).
