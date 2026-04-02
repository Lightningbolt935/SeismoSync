const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: '*', // For development only
    }
});

app.get('/', (req, res) => {
    res.send('Shake Alert Signaling Server is running');
});

// A simple Map to store connected clients by role (victim or dashboard)
const clients = {
    dashboards: new Set(),
    victims: new Set(),
};

io.on('connection', (socket) => {
    console.log(`[Socket] User connected: ${socket.id}`);

    // Roles: 'dashboard' | 'victim'
    socket.on('register', (role) => {
        socket.role = role;
        if (role === 'dashboard') {
            clients.dashboards.add(socket.id);
            socket.join('dashboards');
            console.log(`[Socket] Dashboard registered: ${socket.id}`);
        } else if (role === 'victim') {
            clients.victims.add(socket.id);
            socket.join('victims');
            console.log(`[Socket] Victim registered: ${socket.id}`);
        }
    });

    // Handle generic WebRTC signaling data
    // Payload should include { to: socketId, type: 'offer'/'answer'/'candidate', ...data }
    socket.on('signal', (data) => {
        // If 'to' is specified, send direct to that socket.
        // Otherwise, if victim sends, broadcast to all dashboards.
        if (data.to) {
            io.to(data.to).emit('signal', { senderId: socket.id, ...data });
        } else if (socket.role === 'victim') {
            socket.to('dashboards').emit('signal', { senderId: socket.id, ...data });
        } else if (socket.role === 'dashboard') {
             socket.to('victims').emit('signal', { senderId: socket.id, ...data });
        }
    });
    
    // Explicit shake alert without wait for WebRTC data channel connection
    // Useful for instant delivery or fallback
    socket.on('shake_alert', (data) => {
       console.log(`[Alert] High severity shake detected from ${socket.id}. Forwarding to dashboards.`);
       socket.to('dashboards').emit('shake_alert', { senderId: socket.id, ...data });
    });

    socket.on('disconnect', () => {
        console.log(`[Socket] User disconnected: ${socket.id}`);
        if (socket.role === 'dashboard') clients.dashboards.delete(socket.id);
        if (socket.role === 'victim') clients.victims.delete(socket.id);
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Signaling Server listening on port ${PORT}`);
});
