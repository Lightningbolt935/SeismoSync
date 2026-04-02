import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle } from 'react-leaflet';
import { io } from 'socket.io-client';
import 'leaflet/dist/leaflet.css';
import './App.css';
import L from 'leaflet';

// Fix Leaflet marker icons not loading correctly in React
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// Custom icons based on severity
const createIcon = (color) => {
  return new L.Icon({
    iconUrl: `https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-${color}.png`,
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41]
  });
};

const icons = {
  severe: createIcon('red'),
  moderate: createIcon('orange'),
  mild: createIcon('green')
};

const SOCKET_SERVER_URL = 'http://localhost:3000';

function App() {
  const [socket, setSocket] = useState(null);
  const [connected, setConnected] = useState(false);
  const [alerts, setAlerts] = useState([]);

  useEffect(() => {
    // Initialize Socket Connection
    const newSocket = io(SOCKET_SERVER_URL);
    setSocket(newSocket);

    newSocket.on('connect', () => {
      console.log('Connected to signaling server');
      setConnected(true);
      newSocket.emit('register', 'dashboard');
    });

    newSocket.on('disconnect', () => {
      console.log('Disconnected from signaling server');
      setConnected(false);
    });

    // Listen for shake alerts (explicit events or WebRTC data fallback)
    newSocket.on('shake_alert', (data) => {
      console.log('Received shake alert', data);
      
      const newAlert = {
        id: Date.now().toString(),
        senderId: data.senderId,
        coords: { lat: data.lat, lng: data.lng },
        severity: data.severity || 'severe',
        timestamp: new Date().toLocaleTimeString(),
        rawSensor: data.rawSensor || null
      };

      setAlerts(prev => [newAlert, ...prev]);
    });

    return () => newSocket.close();
  }, []);

  // Center of US / World starting point
  const mapCenter = [39.8283, -98.5795];

  return (
    <div className="dashboard-container">
      {/* Sidebar Panel */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <h1 className="title">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{color: 'var(--accent-orange)'}}>
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>
            </svg>
            Shake Alert
          </h1>
          <div className="connection-status">
            <span className={`status-dot ${connected ? 'connected' : ''}`}></span>
            {connected ? 'Real-time System Active' : 'Connecting to Server...'}
          </div>
        </div>

        <div className="alerts-container">
          {alerts.length === 0 ? (
            <div className="empty-state">
              No recent activity. Monitoring for seismic events...
            </div>
          ) : (
            alerts.map(alert => (
              <div key={alert.id} className={`alert-card ${alert.severity}`}>
                <div className="alert-header">
                  <span className="alert-badget">{alert.severity}</span>
                  <span className="time">{alert.timestamp}</span>
                </div>
                <div className="alert-details">
                  <p><strong>Victim ID:</strong> {alert.senderId.slice(0,6)}...</p>
                  <p><strong>Location:</strong> {alert.coords.lat.toFixed(4)}, {alert.coords.lng.toFixed(4)}</p>
                </div>
              </div>
            ))
          )}
        </div>
      </aside>

      {/* Map View */}
      <main className="map-container">
        <MapContainer center={mapCenter} zoom={4} style={{ height: '100%', width: '100%' }}>
          {/* Dark themed map tiles (CartoDB Dark Matter) */}
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          />
          
          {alerts.map(alert => (
            <div key={alert.id}>
              <Circle 
                center={[alert.coords.lat, alert.coords.lng]}
                pathOptions={{ 
                  color: alert.severity === 'severe' ? '#ef4444' : alert.severity === 'moderate' ? '#f97316' : '#10b981',
                  fillColor: alert.severity === 'severe' ? '#ef4444' : alert.severity === 'moderate' ? '#f97316' : '#10b981',
                  fillOpacity: 0.15,
                  weight: 2,
                  dashArray: '5, 5'
                }}
                radius={20000} // 20km radius circle for visual effect
              />
              <Marker 
                position={[alert.coords.lat, alert.coords.lng]}
                icon={icons[alert.severity] || icons.severe}
              >
                <Popup>
                  <strong>Shake Alert Detected</strong><br/>
                  Severity: {alert.severity}<br/>
                  Time: {alert.timestamp}<br/>
                  <button style={{marginTop:'8px', padding:'4px 8px', background:'var(--accent-blue)', color:'white', border:'none', borderRadius:'4px', cursor:'pointer'}}>Connect Audio</button>
                </Popup>
              </Marker>
            </div>
          ))}
        </MapContainer>
      </main>
    </div>
  );
}

export default App;
