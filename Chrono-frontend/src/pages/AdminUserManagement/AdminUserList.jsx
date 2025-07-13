// src/pages/AdminUserManagement/AdminUserList.jsx
import 'react';
import PropTypes from 'prop-types';

const AdminUserList = ({ users, t, handleEditUser, requestDeleteUser, handleProgramCard }) => {
    return (
        <section className="user-list">
            <h3>{t('userManagement.existingUsers', 'Vorhandene Benutzer')}</h3>
            {users.length === 0 ? (
                <p>{t("userManagement.noUsers", "Keine Benutzer vorhanden.")}</p>
            ) : (
                <table>
                    <thead>
                    <tr>
                        <th>{t("userManagement.username", "Benutzername")}</th>
                        <th>{t("userManagement.fullName", "Name")}</th>
                        <th>{t("userManagement.email", "E-Mail")}</th>
                        <th>{t("userManagement.role", "Rolle")}</th>
                        <th>{t("userManagement.workModel", "Arbeitsmodell")}</th>
                        <th>{t("userManagement.annualVacationDays", "Urlaubstage")}</th>
                        <th style={{ textAlign: 'right' }}>{t("userManagement.table.actions", "Aktionen")}</th>
                    </tr>
                    </thead>
                    <tbody>
                    {users.map((user) => (
                        <tr key={user.id}>
                            <td data-label={t("userManagement.username", "Benutzername")} style={{ borderLeft: `4px solid ${user.color || 'var(--c-muted)'}` }}>
                                {user.username}
                            </td>
                            <td data-label={t("userManagement.fullName", "Name")}>
                                {user.firstName} {user.lastName}
                            </td>
                            <td data-label={t("userManagement.email", "E-Mail")}>{user.email}</td>
                            <td data-label={t("userManagement.role", "Rolle")}>
                                {/* DTO liefert roles als List<String> */}
                                {user.roles?.[0]?.replace("ROLE_", "") || "USER"}
                            </td>
                            <td data-label={t("userManagement.workModel", "Arbeitsmodell")}>
                                {user.isHourly ? t('userTypes.hourly','Stündlich') : (user.isPercentage ? `${t('userTypes.percentage','Prozentual')} (${user.workPercentage}%)` : t('userTypes.standard','Standard'))}
                            </td>
                            <td data-label={t("userManagement.annualVacationDays", "Urlaubstage")}>{user.annualVacationDays ?? "-"}</td>
                            <td data-label={t("userManagement.table.actions", "Aktionen")} className="actions-cell">
                                <button onClick={() => handleEditUser(user)} className="action-button edit-action" title={t("userManagement.table.edit", "Bearbeiten")}
                                    aria-label={t('userManagement.table.edit', 'Bearbeiten')}>
                                    ✏️
                                </button>
                                <button onClick={() => handleProgramCard(user)} className="action-button program-action" title={t("userManagement.table.programCard", "Karte programmieren")}
                                    aria-label={t('userManagement.table.programCard', 'Karte programmieren')}>
                                    💳
                                </button>
                                <button onClick={() => requestDeleteUser(user)} className="action-button delete-action" title={t("userManagement.table.delete", "Löschen")}
                                    aria-label={t('userManagement.table.delete', 'Löschen')}>
                                    🗑️
                                </button>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            )}
        </section>
    );
};

AdminUserList.propTypes = {
    users: PropTypes.array.isRequired,
    t: PropTypes.func.isRequired,
    handleEditUser: PropTypes.func.isRequired,
    requestDeleteUser: PropTypes.func.isRequired, // Nimmt jetzt das User-Objekt
    handleProgramCard: PropTypes.func.isRequired
};

export default AdminUserList;