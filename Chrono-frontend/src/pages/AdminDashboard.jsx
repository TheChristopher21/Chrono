import React, { useState, useEffect } from 'react'
import api from '../utils/api'
import Navbar from '../components/Navbar'
import VacationCalendar from '../components/VacationCalendar'
import '../styles/AdminDashboard.css'
import { useAuth } from '../context/AuthContext'

const AdminDashboard = () => {
    const { currentUser } = useAuth()
    const [timeTracks, setTimeTracks] = useState([])
    const [vacationRequests, setVacationRequests] = useState([])
    const [correctionRequests, setCorrectionRequests] = useState([])
    const [adminPassword, setAdminPassword] = useState('')
    const [expandedUserTimes, setExpandedUserTimes] = useState({})
    const [expandedUserVacations, setExpandedUserVacations] = useState({})
    const [editingTrack, setEditingTrack] = useState(null)
    const [editForm, setEditForm] = useState({
        newStart: '',
        newEnd: '',
        adminPassword: '',
        userPassword: ''
    })

    const fetchTimeTracks = async () => {
        try {
            const res = await api.get('/api/admin/timetracking/all')
            setTimeTracks(Array.isArray(res.data) ? res.data : [])
        } catch (err) {
            console.error("Error fetching time tracks", err)
        }
    }

    const fetchVacationRequests = async () => {
        try {
            const res = await api.get('/api/vacation/all')
            setVacationRequests(Array.isArray(res.data) ? res.data : [])
        } catch (err) {
            console.error("Error fetching vacation requests", err)
        }
    }

    const fetchCorrectionRequests = async () => {
        try {
            const res = await api.get('/api/correction/open')
            setCorrectionRequests(Array.isArray(res.data) ? res.data : [])
        } catch (err) {
            console.error("Error fetching correction requests", err)
        }
    }

    useEffect(() => {
        fetchTimeTracks()
        fetchVacationRequests()
        fetchCorrectionRequests()
    }, [])

    const userTimeGroups = timeTracks.reduce((acc, track) => {
        const username = track.username
        if (!acc[username]) acc[username] = []
        acc[username].push(track)
        return acc
    }, {})

    const userVacationGroups = vacationRequests.reduce((acc, vac) => {
        const username = vac.username
        if (!acc[username]) acc[username] = []
        acc[username].push(vac)
        return acc
    }, {})

    const toggleUserTimes = (username) => {
        setExpandedUserTimes(prev => ({ ...prev, [username]: !prev[username] }))
    }

    const toggleUserVacations = (username) => {
        setExpandedUserVacations(prev => ({ ...prev, [username]: !prev[username] }))
    }

    const handleEditClick = (track) => {
        setEditingTrack(track)
        const formatForInput = (dateStr) => {
            const date = new Date(dateStr)
            return date.toISOString().substring(0, 16)
        }
        setEditForm({
            newStart: formatForInput(track.startTime),
            newEnd: track.endTime ? formatForInput(track.endTime) : '',
            adminPassword: '',
            userPassword: ''
        })
    }

    const handleEditFormChange = (e) => {
        setEditForm({ ...editForm, [e.target.name]: e.target.value })
    }

    const handleEditSubmit = async (e) => {
        e.preventDefault()
        try {
            await api.put(`/api/admin/timetracking/${editingTrack.id}`, null, {
                params: {
                    newStart: editForm.newStart,
                    newEnd: editForm.newEnd,
                    adminPassword: editForm.adminPassword,
                    userPassword: editForm.userPassword
                }
            })
            setEditingTrack(null)
            fetchTimeTracks()
        } catch (err) {
            console.error("Error updating time track", err)
        }
    }

    const closeEditModal = () => {
        setEditingTrack(null)
    }

    const handleApproveCorrection = async (id) => {
        if (!adminPassword) {
            alert("Please enter your admin password")
            return
        }
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } })
            fetchCorrectionRequests()
            fetchTimeTracks()
        } catch (err) {
            console.error("Error approving correction", err)
        }
    }

    const handleDenyCorrection = async (id) => {
        try {
            await api.post(`/api/correction/deny/${id}`)
            fetchCorrectionRequests()
        } catch (err) {
            console.error("Error denying correction", err)
        }
    }

    const handleApproveVacation = async (id) => {
        if (!adminPassword) {
            alert("Please enter your admin password")
            return
        }
        try {
            await api.post(`/api/vacation/approve/${id}`, null, { params: { adminPassword } })
            fetchVacationRequests()
        } catch (err) {
            console.error("Error approving vacation", err)
        }
    }

    const handleDenyVacation = async (id) => {
        try {
            await api.post(`/api/vacation/deny/${id}`)
            fetchVacationRequests()
        } catch (err) {
            console.error("Error denying vacation", err)
        }
    }

    return (
        <div className="admin-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>Admin Dashboard</h2>
                <p>Welcome, {currentUser?.username}</p>
                <div className="admin-password">
                    <label>Admin Password:</label>
                    <input
                        type="password"
                        value={adminPassword}
                        onChange={(e) => setAdminPassword(e.target.value)}
                    />
                </div>
            </header>
            <section className="time-tracking-section">
                <h3>Time Tracking Overview</h3>
                {Object.keys(userTimeGroups).length === 0 ? (
                    <p>No time tracks found.</p>
                ) : (
                    <div className="user-groups">
                        {Object.keys(userTimeGroups).map(username => (
                            <div key={username} className="user-group">
                                <div className="user-group-header" onClick={() => toggleUserTimes(username)}>
                                    <h4>{username}</h4>
                                    <button>{expandedUserTimes[username] ? '-' : '+'}</button>
                                </div>
                                {expandedUserTimes[username] && (
                                    <ul>
                                        {userTimeGroups[username].map(track => (
                                            <li key={track.id}>
                                                <span className="entry-label">Start:</span> {new Date(track.startTime).toLocaleString()}<br />
                                                <span className="entry-label">End:</span> {track.endTime ? new Date(track.endTime).toLocaleString() : 'Ongoing'}<br />
                                                <span className="entry-label">Status:</span> {track.color}<br />
                                                <button onClick={() => handleEditClick(track)}>Edit</button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </section>
            <section className="vacation-section">
                <h3>Vacation Requests by User</h3>
                {Object.keys(userVacationGroups).length === 0 ? (
                    <p>No vacation requests found.</p>
                ) : (
                    <div className="vacation-groups">
                        {Object.keys(userVacationGroups).map(username => (
                            <div key={username} className="vacation-group">
                                <div className="vacation-group-header" onClick={() => toggleUserVacations(username)}>
                                    <h4>{username}</h4>
                                    <button>{expandedUserVacations[username] ? '-' : '+'}</button>
                                </div>
                                {expandedUserVacations[username] && (
                                    <ul>
                                        {userVacationGroups[username].map(vac => (
                                            <li key={vac.id}>
                                                <span className="entry-label">From:</span> {vac.startDate}<br />
                                                <span className="entry-label">To:</span> {vac.endDate}<br />
                                                <span className="entry-label">Status:</span>{' '}
                                                <span className={`status-badge ${vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending'}`}>
                          {vac.approved ? 'Approved' : vac.denied ? 'Denied' : 'Pending'}
                        </span>
                                                <div className="actions">
                                                    <button onClick={() => handleApproveVacation(vac.id)}>Approve</button>
                                                    <button onClick={() => handleDenyVacation(vac.id)}>Deny</button>
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        ))}
                    </div>
                )}
                <div className="calendar-section">
                    <h4>Vacation Calendar</h4>
                    <VacationCalendar vacationRequests={vacationRequests.filter(vac => vac.approved)} />
                </div>
            </section>
            <section className="correction-section">
                <h3>Open Correction Requests</h3>
                {correctionRequests.length === 0 ? (
                    <p>No correction requests found.</p>
                ) : (
                    <ul>
                        {correctionRequests.map(req => (
                            <li key={req.id}>
                                <span className="entry-label">User:</span> {req.username}<br />
                                <span className="entry-label">Original Start:</span> {req.originalStart ? new Date(req.originalStart).toLocaleString() : 'N/A'}<br />
                                <span className="entry-label">Original End:</span> {req.originalEnd ? new Date(req.originalEnd).toLocaleString() : 'N/A'}<br />
                                <span className="entry-label">Desired Start:</span> {new Date(req.desiredStart).toLocaleString()}<br />
                                <span className="entry-label">Desired End:</span> {new Date(req.desiredEnd).toLocaleString()}<br />
                                <span className="entry-label">Reason:</span> {req.reason}<br />
                                <div className="actions">
                                    <button onClick={() => handleApproveCorrection(req.id)}>Approve</button>
                                    <button onClick={() => handleDenyCorrection(req.id)}>Deny</button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </section>
            {editingTrack && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Edit Time Track</h3>
                        <form onSubmit={handleEditSubmit}>
                            <div className="form-group">
                                <label>New Start Time:</label>
                                <input
                                    type="datetime-local"
                                    name="newStart"
                                    value={editForm.newStart}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>New End Time:</label>
                                <input
                                    type="datetime-local"
                                    name="newEnd"
                                    value={editForm.newEnd}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Admin Password:</label>
                                <input
                                    type="password"
                                    name="adminPassword"
                                    value={editForm.adminPassword}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>User Password:</label>
                                <input
                                    type="password"
                                    name="userPassword"
                                    value={editForm.userPassword}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Save Changes</button>
                                <button type="button" onClick={closeEditModal}>Cancel</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    )
}

export default AdminDashboard
