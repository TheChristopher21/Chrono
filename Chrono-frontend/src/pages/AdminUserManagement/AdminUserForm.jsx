import 'react';
import PropTypes from 'prop-types';
import { STANDARD_COLORS, defaultWeeklySchedule } from './adminUserManagementUtils';

const AdminUserForm = ({
                           t,
                           isEditing,
                           userData,
                           setUserData,
                           onSubmit,
                           onCancel
                       }) => {
    const handleChange = (field, value) => {
        setUserData(prev => ({ ...prev, [field]: value }));
    };

    const handleScheduleCycleChange = (newCycle) => {
        let newSchedule = userData.weeklySchedule || [];
        if (newCycle > newSchedule.length) {
            const diff = newCycle - newSchedule.length;
            for (let i = 0; i < diff; i++) {
                newSchedule.push({ ...defaultWeeklySchedule });
            }
        } else {
            newSchedule = newSchedule.slice(0, newCycle);
        }
        setUserData({ ...userData, scheduleCycle: newCycle, weeklySchedule: newSchedule });
    };

    return (
        <section className="user-form">
            <h3>
                {isEditing ? t("userManagement.editUser") : t("userManagement.newUser")}
            </h3>

            <form onSubmit={onSubmit}>
                <input
                    type="text"
                    placeholder={t("userManagement.username")}
                    value={userData.username || ''}
                    onChange={(e) => handleChange('username', e.target.value)}
                    required
                />
                <input
                    type="text"
                    placeholder={t("userManagement.firstName")}
                    value={userData.firstName || ''}
                    onChange={(e) => handleChange('firstName', e.target.value)}
                    required
                />
                <input
                    type="text"
                    placeholder={t("userManagement.lastName")}
                    value={userData.lastName || ''}
                    onChange={(e) => handleChange('lastName', e.target.value)}
                    required
                />
                <input
                    type="email"
                    placeholder={t("userManagement.email")}
                    value={userData.email || ''}
                    onChange={(e) => handleChange('email', e.target.value)}
                    required
                />

                {!isEditing && (
                    <input
                        type="password"
                        placeholder={t("userManagement.password")}
                        value={userData.password || ''}
                        onChange={(e) => handleChange('password', e.target.value)}
                        required
                    />
                )}

                <div className="form-group">
                    <label>{t("userManagement.role")}:</label>
                    <select
                        value={userData.role || 'ROLE_USER'}
                        onChange={(e) => handleChange('role', e.target.value)}
                    >
                        <option value="ROLE_USER">User</option>
                        <option value="ROLE_ADMIN">Admin</option>
                    </select>
                </div>

                <div className="form-group">
                    <label>{t("userManagement.color")}</label>
                    <div className="color-picker">
                        {STANDARD_COLORS.map((c, idx) => (
                            <div
                                key={idx}
                                className={`color-swatch ${userData.color === c ? 'selected' : ''}`}
                                style={{ backgroundColor: c }}
                                onClick={() => handleChange('color', c)}
                            />
                        ))}
                    </div>
                </div>

                <div className="form-group">
                    <label>{t("userManagement.isHourly")}</label>
                    <input
                        type="checkbox"
                        checked={!!userData.isHourly}
                        onChange={(e) => handleChange('isHourly', e.target.checked)}
                    />
                </div>

                <div className="form-group">
                    <label>
                        <input
                            type="checkbox"
                            checked={!!userData.isPercentage}
                            onChange={(e) => handleChange('isPercentage', e.target.checked)}
                        />
                        {t("Prozentbasierte Zeiterfassung")}
                    </label>
                </div>

                {userData.isPercentage && (
                    <div className="form-group">
                        <label>{t("userManagement.workPercentage") || "Work Percentage"}:</label>
                        <input
                            type="number"
                            min="0"
                            max="100"
                            step="any"
                            placeholder="z.B. 10"
                            value={userData.workPercentage ?? ""}
                            onChange={(e) => {
                                const valStr = e.target.value;
                                const numeric = parseFloat(valStr.replace(",", ".")) || 0;
                                handleChange('workPercentage', numeric);
                            }}
                        />
                    </div>
                )}

                {/* Urlaubstage – immer anzeigen */}
                <div className="form-group">
                    <label>{t("Urlaubstage")}:</label>
                    <input
                        type="number"
                        step="any"
                        placeholder="z.B. 25 oder 25.5"
                        value={userData.annualVacationDays || ''}
                        onChange={(e) => handleChange('annualVacationDays', e.target.value)}
                    />
                </div>

                {/* Nur bei klassischer Zeiterfassung sichtbar */}
                {!userData.isPercentage && !userData.isHourly && (
                    <>
                        <div className="form-group">
                            <label>{t("userManagement.expectedWorkDays")}:</label>
                            <input
                                type="number"
                                step="any"
                                placeholder="z.B. 5 oder 4.5"
                                value={userData.expectedWorkDays || ''}
                                onChange={(e) => handleChange('expectedWorkDays', e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("userManagement.breakDuration")}:</label>
                            <input
                                type="number"
                                step="any"
                                placeholder="z.B. 30 oder 30.5"
                                value={userData.breakDuration || ''}
                                onChange={(e) => handleChange('breakDuration', e.target.value)}
                            />
                        </div>

                        <h4>{t("userManagement.scheduleConfig")}</h4>
                        <div className="form-group">
                            <label>{t("userManagement.cycleLength")}</label>
                            <input
                                type="number"
                                min="1"
                                value={userData.scheduleCycle || 1}
                                onChange={(e) => handleScheduleCycleChange(Number(e.target.value))}
                            />
                        </div>

                        <div className="weekly-schedule">
                            {userData.weeklySchedule?.map((week, idx) => (
                                <div key={idx} className="schedule-week">
                                    <h5>{t("userManagement.week")} {idx + 1}</h5>
                                    {['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map(dayKey => (
                                        <div key={dayKey}>
                                            <label>{t("days." + dayKey)}:</label>
                                            <input
                                                type="number"
                                                min="0"
                                                max="24"
                                                step="any"
                                                value={week[dayKey] || 0}
                                                onChange={(e) => {
                                                    const newVal = Number(e.target.value);
                                                    const newSchedule = [...userData.weeklySchedule];
                                                    newSchedule[idx] = { ...newSchedule[idx], [dayKey]: newVal };
                                                    setUserData({ ...userData, weeklySchedule: newSchedule });
                                                }}
                                            />
                                        </div>
                                    ))}
                                </div>
                            ))}
                        </div>
                    </>
                )}

                <button type="submit">
                    {isEditing ? t("userManagement.button.save") : t("userManagement.button.save")}
                </button>
                {isEditing && (
                    <button type="button" onClick={onCancel}>
                        {t("userManagement.button.cancel")}
                    </button>
                )}
            </form>
        </section>
    );
};

AdminUserForm.propTypes = {
    t: PropTypes.func.isRequired,
    isEditing: PropTypes.bool.isRequired,
    userData: PropTypes.object.isRequired,
    setUserData: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onCancel: PropTypes.func
};

export default AdminUserForm;
