import React from 'react';
import PropTypes from 'prop-types';

const AdminUserList = ({ users, t, handleEditUser, requestDeleteUser, handleProgramCard }) => {
    return (
        <section className="user-list">
            {users.length === 0 ? (
                <p>{t("userManagement.noUsers")}</p>
            ) : (
                <table>
                    <thead>
                    <tr>
                        <th>{t("userManagement.username")}</th>
                        <th>
                            {t("userManagement.firstName")} {t("userManagement.lastName")}
                        </th>
                        <th>{t("userManagement.email")}</th>
                        <th>{t("userManagement.role")}</th>
                        <th>{t("userManagement.expectedWorkDays")}</th>
                        <th>{t("userManagement.breakDuration")}</th>
                        <th>{t("userManagement.annualVacationDays")}</th>
                        <th>{t("userManagement.table.actions")}</th>
                    </tr>
                    </thead>
                    <tbody>
                    {users.map((user) => (
                        <tr
                            key={user.id}
                            style={{ backgroundColor: user.color || "transparent" }}
                        >
                            <td>{user.username}</td>
                            <td>
                                {user.firstName} {user.lastName}
                            </td>
                            <td>{user.email}</td>

                            {/* Rollenanalyse */}
                            <td>
                                {user?.roles?.[0]?.roleName
                                    ? user.roles[0].roleName.replace("ROLE_", "")
                                    : "USER"}
                            </td>

                            <td>{user.expectedWorkDays ?? "-"}</td>
                            <td>{user.breakDuration ?? "-"}</td>
                            <td>{user.annualVacationDays ?? "-"}</td>

                            <td>
                                <button onClick={() => handleEditUser(user)}>
                                    {t("userManagement.table.edit")}
                                </button>
                                <button onClick={() => requestDeleteUser(user.id)}>
                                    {t("userManagement.table.delete")}
                                </button>
                                <button onClick={() => handleProgramCard(user)}>
                                    {t("userManagement.table.programCard")}
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
    requestDeleteUser: PropTypes.func.isRequired,
    handleProgramCard: PropTypes.func.isRequired
};

export default AdminUserList;
