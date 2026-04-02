import { useState, useEffect, useRef } from 'react';
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
  
  // WebRTC States
  const [incomingOffers, setIncomingOffers] = useState({});
  const [activeCalls, setActiveCalls] = useState({});
  const peersRef = useRef({});

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

    // Listen for shake alerts
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

    // Listen for WebRTC signals (Offers, answers, ICE candidates)
    newSocket.on('signal', async (data) => {
      const senderId = data.senderId;
      if (data.type === 'offer') {
        console.log(`🎙️ Incoming Audio Offer from Victim: ${senderId}`);
        setIncomingOffers(prev => ({ ...prev, [senderId]: data.sdp }));
      } else if (data.type === 'candidate') {
        if (peersRef.current[senderId]) {
          try {
             await peersRef.current[senderId].addIceCandidate(new RTCIceCandidate(data.candidate));
          } catch(e) {
             console.error('Error adding ICE candidate', e);
          }
        } else {
          // The victim sends ICE candidates immediately after the offer. If we haven't clicked ACCEPT yet, queue them.
          if (!window.candidateQueue) window.candidateQueue = {};
          if (!window.candidateQueue[senderId]) window.candidateQueue[senderId] = [];
          window.candidateQueue[senderId].push(data.candidate);
        }
      }
    });

    return () => newSocket.close();
  }, []);

  const acceptAudio = async (senderId) => {
    const offerSdp = incomingOffers[senderId];
    if (!offerSdp || !socket) return;

    try {
      // 1. Get Rescuer Microphone
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      
      // 2. Init WebRTC Peer Connection & Store immediately
      const pc = new RTCPeerConnection({
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }]
      });
      peersRef.current[senderId] = pc;

      pc.oniceconnectionstatechange = () => {
         console.log(`[WebRTC ICE State] -> ${pc.iceConnectionState}`);
         if (pc.iceConnectionState === 'failed') {
            alert('Audio tunnel blocked by network firewall. WebRTC requires clear UDP traffic.');
         }
      };

      pc.onsignalingstatechange = () => {
         console.log(`[WebRTC Signal State] -> ${pc.signalingState}`);
      };

      // 3. Add local tracks
      stream.getTracks().forEach(track => pc.addTrack(track, stream));

      // 4. Send ICE candidates back to victim
      pc.onicecandidate = (e) => {
        if (e.candidate) {
          console.log(`[WebRTC] Emitting dashboard ICE candidate`);
          socket.emit('signal', { to: senderId, type: 'candidate', candidate: e.candidate });
        }
      };

      // 5. Play incoming victim audio instantly!
      pc.ontrack = (event) => {
        console.log("🔊 WebRTC track successfully bound to UI!");
        const audioElement = document.getElementById(`audio-${senderId}`);
        if (audioElement && event.streams && event.streams[0]) {
          audioElement.srcObject = event.streams[0];
          audioElement.play().then(() => console.log('Audio playback running'))
                           .catch(e => console.error("Could not auto-play audio:", e));
        } else {
           console.error("Audio Element missing from DOM!");
        }
      };

      // 6. Complete Handshake
      await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: offerSdp }));
      
      // Inject all queued ICE candidates from the victim that arrived while we were waiting to click ACCEPT
      if (window.candidateQueue && window.candidateQueue[senderId]) {
        for (const candidate of window.candidateQueue[senderId]) {
           await pc.addIceCandidate(new RTCIceCandidate(candidate));
        }
        window.candidateQueue[senderId] = [];
      }

      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      // 7. Send Answer
      socket.emit('signal', { to: senderId, type: 'answer', sdp: answer.sdp });
      
      setActiveCalls(prev => ({...prev, [senderId]: true}));

    } catch (err) {
      console.error("Audio connection failed", err);
      alert("Microphone connection failed. Please check browser permissions.");
    }
  };

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
            {connected ? 'Global System Active' : 'Connecting to Node...'}
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
                  
                  {/* Real-time WebRTC Audio Context */}
                  {incomingOffers[alert.senderId] && !activeCalls[alert.senderId] && (
                    <div style={{ marginTop: '8px', color: '#10b981', fontWeight: '600', animation: 'pulse 2s infinite' }}>
                      🎙️ Victim sending audio feed!
                    </div>
                  )}
                  {activeCalls[alert.senderId] && (
                    <div style={{ marginTop: '8px', color: '#3b82f6', fontWeight: '600' }}>
                      🔊 Audio Bridge Active
                    </div>
                  )}
                </div>
                {/* Safe DOM insertion for WebRTC audio elements */}
                <audio id={`audio-${alert.senderId}`} autoPlay playsInline style={{ display: 'none' }} />
              </div>
            ))
          )}
        </div>
      </aside>

      {/* Map View */}
      <main className="map-container">
        <MapContainer center={mapCenter} zoom={4} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          />
          
          {alerts.map(alert => (
            <React.Fragment key={alert.id}>
              <Circle 
                center={[alert.coords.lat, alert.coords.lng]}
                pathOptions={{ 
                  color: alert.severity === 'severe' ? '#ef4444' : alert.severity === 'moderate' ? '#f97316' : '#10b981',
                  fillColor: alert.severity === 'severe' ? '#ef4444' : alert.severity === 'moderate' ? '#f97316' : '#10b981',
                  fillOpacity: 0.15,
                  weight: 2,
                  dashArray: '5, 5'
                }}
                radius={20000}
              />
              <Marker 
                position={[alert.coords.lat, alert.coords.lng]}
                icon={icons[alert.severity] || icons.severe}
              >
                <Popup>
                  <strong>Entity: {alert.senderId.slice(0,6)}</strong><br/>
                  Severity: <span style={{color: 'var(--accent-red)'}}>{alert.severity.toUpperCase()}</span><br/>
                  Time: {alert.timestamp}<br/>
                  
                  {incomingOffers[alert.senderId] ? (
                    activeCalls[alert.senderId] ? (
                      <button disabled style={{marginTop:'8px', padding:'6px 12px', background:'#475569', color:'white', border:'none', borderRadius:'6px'}}>🔊 Connection Live</button>
                    ) : (
                      <button 
                        onClick={() => acceptAudio(alert.senderId)}
                        style={{marginTop:'8px', padding:'6px 12px', background:'#3b82f6', color:'white', border:'none', borderRadius:'6px', cursor:'pointer', fontWeight: 'bold', boxShadow: '0 0 10px rgba(59, 130, 246, 0.5)'}}>
                        📞 INCOMING FEED... ACCEPT
                      </button>
                    )
                  ) : (
                    <div>
                      <button disabled style={{marginTop:'8px', padding:'6px 12px', background:'#1e293b', border:'1px solid #475569', color:'#94a3b8', borderRadius:'6px'}}>No Audio Data</button>
                      <button 
                        onClick={() => socket.emit('signal', { to: alert.senderId, type: 'poke_audio' })}
                        style={{marginTop:'8px', marginLeft: '6px', padding:'6px 12px', background:'var(--accent-orange)', color:'white', border:'none', borderRadius:'6px', cursor:'pointer', fontWeight: 'bold'}}>
                        Request Audio Test
                      </button>
                    </div>
                  )}
                </Popup>
              </Marker>
            </React.Fragment>
          ))}
        </MapContainer>
      </main>
    </div>
  );
}

export default App;
