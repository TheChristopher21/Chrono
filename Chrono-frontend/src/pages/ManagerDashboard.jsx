import  { useEffect, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import api from '../utils/api';

const ManagerDashboard = () => {
    const { currentUser } = useAuth();

    // Team-Users
    const [teamUsers, setTeamUsers] = useState([]);
    // Team TimeTracks
    const [teamTracks, setTeamTracks] = useState([]);
    // Team CorrectionRequests
    const [teamCorrections, setTeamCorrections] = useState([]);

    // Prüfe, ob der aktuelle User 'MANAGER' im Token hat
    const isManager = currentUser?.roles?.includes('MANAGER');

    useEffect(() => {
        if (isManager) {
            fetchTeamUsers();
            fetchTeamTracks();
            fetchTeamCorrections();
        }
    }, [isManager]);

    // 1) Team Users
    const fetchTeamUsers = () => {
        api.get('/manager/team/users')
            .then(res => {
                console.log('Manager: Team users:', res.data);
                setTeamUsers(res.data);
            })
            .catch(err => console.error('Error fetching team users:', err));
    };

    // 2) Team TimeTracks
    const fetchTeamTracks = () => {
        api.get('/manager/team/time')
            .then(res => {
                console.log('Manager: Team time tracks:', res.data);
                setTeamTracks(res.data);
            })
            .catch(err => console.error('Error fetching team time tracks:', err));
    };

    // 3) Team CorrectionRequests
    //  -> Du brauchst im Backend z.B. GET /manager/team/corrections
    //     das alle CorrectionRequests für die Team-Mitglieder zurückgibt.
    const fetchTeamCorrections = () => {
        api.get('/manager/team/corrections')
            .then(res => {
                console.log('Manager: Team corrections:', res.data);
                setTeamCorrections(res.data);
            })
            .catch(err => console.error('Error fetching team corrections:', err));
    };

    // Approve Correction
    //  -> Vorraussetzung: /api/correction/approve/{id}?managerPassword=... existiert
    const handleApproveCorrection = (correctionId) => {
        // optional: Manager-Passwort abfragen (double password logic)
        const managerPassword = prompt('Enter your Manager password to approve:');
        if (!managerPassword) return;

        api.post(`/correction/approve/${correctionId}?adminPassword=${managerPassword}`)
            .then(() => {
                alert('Correction request approved!');
                // Reload corrections
                fetchTeamCorrections();
            })
            .catch(err => {
                console.error(err);
                alert('Approve failed. Check console.');
            });
    };

    // Deny Correction
    const handleDenyCorrection = (correctionId) => {
        api.post(`/correction/deny/${correctionId}`)
            .then(() => {
                alert('Correction request denied!');
                // Reload corrections
                fetchTeamCorrections();
            })
            .catch(err => {
                console.error(err);
                alert('Deny failed. Check console.');
            });
    };

    return (
        <div>
            <h2>Manager Dashboard</h2>
            {isManager ? (
                <>
                    {/* -- SECTION 1: Team Users -- */}
                    <section>
                        <h3>Your Team Members</h3>
                        {teamUsers.length === 0 ? (
                            <p>No team users found.</p>
                        ) : (
                            <ul>
                                {teamUsers.map((user) => (
                                    <li key={user.id}>
                                        {user.firstName} {user.lastName} ({user.username})
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>

                    {/* -- SECTION 2: Team TimeTracks -- */}
                    <section style={{ marginTop: '1.5rem' }}>
                        <h3>Team Time Tracks</h3>
                        {teamTracks.length === 0 ? (
                            <p>No time entries found for your team.</p>
                        ) : (
                            <table border="1" cellPadding="6" style={{ borderCollapse: 'collapse' }}>
                                <thead>
                                <tr>
                                    <th>Username</th>
                                    <th>Start Time</th>
                                    <th>End Time</th>
                                    <th>Corrected</th>
                                    <th>Status Color</th>
                                </tr>
                                </thead>
                                <tbody>
                                {teamTracks.map((track, index) => (
                                    <tr key={index}>
                                        {/* Falls dein TimeTrackingResponse hat: userName, startTime, endTime, corrected, statusColor */}
                                        <td>{track.userName || '??'}</td>
                                        <td>{track.startTime || 'N/A'}</td>
                                        <td>{track.endTime || 'N/A'}</td>
                                        <td>{track.corrected ? 'Yes' : 'No'}</td>
                                        <td>{track.statusColor}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        )}
                    </section>

                    {/* -- SECTION 3: Team Correction Requests -- */}
                    <section style={{ marginTop: '1.5rem' }}>
                        <h3>Team Correction Requests</h3>
                        {teamCorrections.length === 0 ? (
                            <p>No correction requests from your team.</p>
                        ) : (
                            <table border="1" cellPadding="6" style={{ borderCollapse: 'collapse' }}>
                                <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>User</th>
                                    <th>Desired Start</th>
                                    <th>Desired End</th>
                                    <th>Reason</th>
                                    <th>Actions</th>
                                </tr>
                                </thead>
                                <tbody>
                                {teamCorrections.map((corr) => (
                                    <tr key={corr.id}>
                                        <td>{corr.id}</td>
                                        {/* Falls du in CorrectionRequest DTO userName mitgibst, hier ausgeben */}
                                        <td>{corr.userName || '??'}</td>
                                        <td>{corr.desiredStartTime}</td>
                                        <td>{corr.desiredEndTime}</td>
                                        <td>{corr.reason}</td>
                                        <td>
                                            {!corr.approved && !corr.denied && (
                                                <>
                                                    <button onClick={() => handleApproveCorrection(corr.id)}>Approve</button>
                                                    <button onClick={() => handleDenyCorrection(corr.id)} style={{ marginLeft: '0.5rem' }}>
                                                        Deny
                                                    </button>
                                                </>
                                            )}
                                            {corr.approved && <span style={{ color: 'green' }}>Approved</span>}
                                            {corr.denied && <span style={{ color: 'red' }}>Denied</span>}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        )}
                    </section>
                </>
            ) : (
                <p>You do not have manager permissions.</p>
            )}
        </div>
    );
};

export default ManagerDashboard;
