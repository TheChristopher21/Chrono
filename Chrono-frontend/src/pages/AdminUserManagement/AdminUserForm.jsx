// src/pages/AdminUserManagement/AdminUserForm.jsx
import 'react';
import PropTypes from 'prop-types';
import { STANDARD_COLORS } from './adminUserManagementUtils';

const AdminUserForm = ({
                           t,
                           isEditing,
                           userData,
                           setUserData, // Dies ist jetzt handleFormChange aus der Parent-Komponente
                           onSubmit,
                           onCancel,
                           onScheduleCycleChange,
                           onWeeklyScheduleDayChange
                       }) => {

    // Direkte Verwendung der setUserData-Prop, die auf handleFormChange zeigt
    const handleChange = (field, value) => {
        setUserData(field, value);
    };

    const handleCheckboxChange = (field, checked) => {
        setUserData(field, checked);
    };

    const handleRoleChange = (e) => {
        // userData.roles ist ein Array von Strings im DTO, das Formular zeigt aber nur eine Rolle an.
        // Wir senden ein Array mit der ausgewählten Rolle.
        handleChange("roles", [e.target.value]);
    };

    return (
        <section className="user-form">
            <h3>
                {isEditing ? t("userManagement.editUser", "Benutzer bearbeiten") : t("userManagement.newUser", "Neuen Benutzer anlegen")}
            </h3>

            <form onSubmit={onSubmit}>
                {/* Basisinformationen - Spannen sich über 3 Spalten im Desktop-Layout */}
                <h4 className="form-section-title full-width">{t('userManagement.section.basicInfo', 'Basisinformationen')}</h4>

                <div className="form-group">
                    <label htmlFor="username">{t("userManagement.username", "Benutzername")}:</label>
                    <input
                        id="username"
                        type="text"
                        value={userData.username || ""}
                        onChange={(e) => handleChange("username", e.target.value)}
                        required
                        disabled={isEditing} // Benutzername im Edit-Modus nicht änderbar
                    />
                </div>

                {!isEditing && (
                    <div className="form-group">
                        <label htmlFor="password">{t("userManagement.password", "Passwort")}:</label>
                        <input
                            id="password"
                            type="password"
                            value={userData.password || ""}
                            onChange={(e) => handleChange("password", e.target.value)}
                            required={!isEditing}
                        />
                    </div>
                )}
                <div className="form-group">
                    <label htmlFor="firstName">{t("userManagement.firstName", "Vorname")}:</label>
                    <input
                        id="firstName"
                        type="text"
                        value={userData.firstName || ""}
                        onChange={(e) => handleChange("firstName", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="lastName">{t("userManagement.lastName", "Nachname")}:</label>
                    <input
                        id="lastName"
                        type="text"
                        value={userData.lastName || ""}
                        onChange={(e) => handleChange("lastName", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="email">{t("userManagement.email", "E-Mail")}:</label>
                    <input
                        id="email"
                        type="email"
                        value={userData.email || ""}
                        onChange={(e) => handleChange("email", e.target.value)}
                        required
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="roles">{t("userManagement.role", "Rolle")}:</label>
                    <select
                        id="roles"
                        value={userData.roles?.[0] || "ROLE_USER"}
                        onChange={handleRoleChange}
                    >
                        <option value="ROLE_USER">User</option>
                        <option value="ROLE_ADMIN">Admin</option>
                        {/* <option value="ROLE_SUPERADMIN">Super Admin</option> // Falls SUPERADMIN hier auch setzbar sein soll */}
                    </select>
                </div>

                <div className="form-group full-width"> {/* Color Picker über volle Breite */}
                    <label>{t("userManagement.color", "Farbe")}:</label>
                    <div className="color-picker">
                        {STANDARD_COLORS.map((c, idx) => (
                            <div
                                key={idx}
                                className={`color-swatch ${userData.color === c ? "selected" : ""}`}
                                style={{ backgroundColor: c }}
                                onClick={() => handleChange("color", c)}
                                role="button"
                                tabIndex={0}
                                onKeyPress={(e) => e.key === 'Enter' && handleChange("color", c)}
                                aria-label={`Farbe ${c} auswählen`}
                            />
                        ))}
                    </div>
                </div>

                {/* Arbeitsmodell - Spannen sich über 3 Spalten */}
                <h4 className="form-section-title full-width">{t('userManagement.section.workModel', 'Arbeitsmodell')}</h4>
                <div className="form-group form-group-checkbox">
                    <input
                        type="checkbox"
                        id="isHourly"
                        checked={!!userData.isHourly}
                        onChange={(e) => handleCheckboxChange("isHourly", e.target.checked)}
                    />
                    <label htmlFor="isHourly">{t("userManagement.isHourly", "Stundenbasiert abrechnen")}</label>
                </div>
                <div className="form-group form-group-checkbox">
                    <input
                        type="checkbox"
                        id="isPercentage"
                        checked={!!userData.isPercentage}
                        onChange={(e) => handleCheckboxChange("isPercentage", e.target.checked)}
                    />
                    <label htmlFor="isPercentage">{t("userManagement.percentageTracking", "Prozentbasierte Zeiterfassung")}</label>
                </div>
                {userData.isPercentage && !userData.isHourly && (
                    <div className="form-group">
                        <label htmlFor="workPercentage">{t("userManagement.workPercentage", "Arbeitspensum (%)")}:</label>
                        <input
                            id="workPercentage"
                            type="number"
                            min="1" max="100" step="1"
                            value={userData.workPercentage ?? ""}
                            onChange={(e) => handleChange("workPercentage", e.target.value ? parseInt(e.target.value, 10) : null)}
                            placeholder="1-100"
                        />
                    </div>
                )}

                {/* Allgemeine Einstellungen */}
                <h4 className="form-section-title full-width">{t('userManagement.section.generalSettings', 'Allgemeine Einstellungen')}</h4>
                <div className="form-group">
                    <label htmlFor="annualVacationDays">{t("userManagement.annualVacationDays", "Urlaubstage/Jahr")}:</label>
                    <input
                        id="annualVacationDays"
                        type="number" step="0.5" min="0"
                        value={userData.annualVacationDays === null || userData.annualVacationDays === undefined ? "" : userData.annualVacationDays}
                        onChange={(e) => handleChange("annualVacationDays", e.target.value ? parseFloat(e.target.value) : null)}
                        placeholder="z.B. 25"
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="breakDuration">{t("userManagement.breakDuration", "Standard Pausendauer (Min)")}:</label>
                    <input
                        id="breakDuration"
                        type="number" min="0"
                        value={userData.breakDuration === null || userData.breakDuration === undefined ? "" : userData.breakDuration}
                        onChange={(e) => handleChange("breakDuration", e.target.value ? parseInt(e.target.value, 10) : null)}
                        placeholder="z.B. 30"
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="trackingBalanceInMinutes">{t('userManagement.balanceMinutes', 'Überstundensaldo (Minuten)')}:</label>
                    <input
                        type="number"
                        id="trackingBalanceInMinutes"
                        value={userData.trackingBalanceInMinutes === null || userData.trackingBalanceInMinutes === undefined ? "" : userData.trackingBalanceInMinutes}
                        onChange={(e) => handleChange('trackingBalanceInMinutes', e.target.value ? parseInt(e.target.value, 10) : 0)}
                        placeholder={t('userManagement.balanceMinutesPlaceholder', 'z.B. 120 oder -60')}
                    />
                </div>


                {/* Wochenplan-Konfiguration (nur für Standard-User) */}
                {!userData.isPercentage && !userData.isHourly && (
                    <div className="weekly-schedule-config full-width">
                        <h4 className="form-section-title">{t("userManagement.scheduleConfig", "Wochenplan & Sollzeiten")}</h4>
                        <div className="form-group">
                            <label htmlFor="dailyWorkHours">{t("userManagement.dailyWorkHours", "Standard Tagessoll (Std)")}:</label>
                            <input
                                id="dailyWorkHours"
                                type="number" step="0.01" min="0"
                                value={userData.dailyWorkHours === null || userData.dailyWorkHours === undefined ? "" : userData.dailyWorkHours}
                                onChange={(e) => handleChange("dailyWorkHours", e.target.value ? parseFloat(e.target.value) : null)}
                                placeholder="z.B. 8.5"
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="expectedWorkDays">{t("userManagement.expectedWorkDays", "Erw. Arbeitstage/Woche")}:</label>
                            <input
                                id="expectedWorkDays"
                                type="number" step="0.5" min="0" max="7"
                                value={userData.expectedWorkDays === null || userData.expectedWorkDays === undefined ? "" : userData.expectedWorkDays}
                                onChange={(e) => handleChange("expectedWorkDays", e.target.value ? parseFloat(e.target.value) : null)}
                                placeholder="z.B. 5"
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="scheduleEffectiveDate">{t("userManagement.scheduleEffectiveDate", "Plan gültig ab")}:</label>
                            <input
                                type="date"
                                id="scheduleEffectiveDate"
                                value={userData.scheduleEffectiveDate || ''}
                                onChange={(e) => handleChange("scheduleEffectiveDate", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="scheduleCycle">{t("userManagement.cycleLength", "Zykluslänge (Wochen)")}:</label>
                            <input
                                type="number"
                                id="scheduleCycle"
                                min="1"
                                value={userData.scheduleCycle || 1}
                                onChange={(e) => onScheduleCycleChange(Number(e.target.value))}
                            />
                        </div>

                        <div className="schedule-week-container">
                            {(userData.weeklySchedule || []).map((week, weekIdx) => (
                                <div key={weekIdx} className="schedule-week">
                                    <h5>{t("userManagement.week", "Woche")} {weekIdx + 1}</h5>
                                    {["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"].map((dayKey) => (
                                        <div key={dayKey} className="day-input-group">
                                            <label htmlFor={`schedule-${weekIdx}-${dayKey}`}>{t(`days.${dayKey}`)}:</label>
                                            <input
                                                type="number"
                                                id={`schedule-${weekIdx}-${dayKey}`}
                                                min="0" max="24" step="0.01"
                                                value={week[dayKey] !== null && week[dayKey] !== undefined ? week[dayKey] : ""}
                                                placeholder="Std."
                                                onChange={(e) => onWeeklyScheduleDayChange(weekIdx, dayKey, e.target.value)}
                                            />
                                        </div>
                                    ))}
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                <div className="form-actions full-width">
                    <button type="submit" className="button-primary">
                        {isEditing ? t("userManagement.button.saveChanges", "Änderungen speichern") : t("userManagement.button.createUser", "Benutzer erstellen")}
                    </button>
                    {/* Cancel-Button wird jetzt in AdminUserManagementPage gerendert, wenn editingUser existiert */}
                    <button type="button" onClick={onCancel} className="button-secondary">
                        {t("userManagement.button.cancel", "Abbrechen")}
                    </button>
                </div>
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
    onCancel: PropTypes.func.isRequired,
    onScheduleCycleChange: PropTypes.func.isRequired,
    onWeeklyScheduleDayChange: PropTypes.func.isRequired,
};

export default AdminUserForm;