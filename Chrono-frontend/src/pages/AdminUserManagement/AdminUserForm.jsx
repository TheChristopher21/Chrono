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

    const handleChange = (field, value) => {
        setUserData(field, value);
    };

    const handleCheckboxChange = (field, checked) => {
        setUserData(field, checked);
    };

    const handleRoleChange = (e) => {
        handleChange("roles", [e.target.value]);
    };

    const isAdminRole = Array.isArray(userData.roles)
        && (userData.roles.includes('ROLE_ADMIN') || userData.roles.includes('ROLE_SUPERADMIN'));

    const requiredLabelText = t("userManagement.requiredField", "Pflichtfeld");
    const optionalLabelText = t("userManagement.optionalField", "optional");

    const renderLabel = (id, translationKey, defaultText, { required = false, hint } = {}) => (
        <label htmlFor={id}>
            {t(translationKey, defaultText)}
            {hint ? <span className="label-hint"> ({hint})</span> : null}
            {required ? <span className="required-indicator"> ({requiredLabelText})</span> : null}
        </label>
    );

    return (
        <section className="user-form">
            <h3>
                {isEditing ? t("userManagement.editUser", "Benutzer bearbeiten") : t("userManagement.newUser", "Neuen Benutzer anlegen")}
            </h3>

            <form onSubmit={onSubmit} className="admin-user-form-grid">
                {/* Sektion: Basisinformationen */}
                <h4 className="form-section-title full-width">{t('userManagement.section.basicInfo', 'Basisinformationen')}</h4>

                <div className="form-group full-width form-group-heading">
                    <h5>{t('userManagement.section.access', 'Zugang & Rollen')}</h5>
                    <p className="form-group-description">
                        {t('userManagement.section.accessHint', 'Steuert Login-Daten und Berechtigungen.')}
                    </p>
                </div>

                <div className="form-group">
                    {renderLabel("username", "userManagement.username", "Benutzername", { required: true })}
                    <input
                        id="username"
                        type="text"
                        value={userData.username || ""}
                        onChange={(e) => handleChange("username", e.target.value)}
                        required
                        disabled={isEditing}
                    />
                </div>

                {!isEditing && (
                    <div className="form-group">
                        {renderLabel("password", "userManagement.password", "Passwort", {
                            required: true,
                            hint: t("userManagement.passwordHint", "nur für neue Benutzer:innen"),
                        })}
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
                    {renderLabel("roles", "userManagement.role", "Rolle", { required: true })}
                    <select
                        id="roles"
                        value={userData.roles?.[0] || "ROLE_USER"}
                        onChange={handleRoleChange}
                        required
                    >
                        <option value="ROLE_USER">User</option>
                        <option value="ROLE_ADMIN">Admin</option>
                        <option value="ROLE_SUPERADMIN">Superadmin</option>
                    </select>
                </div>

                {isAdminRole && (
                    <div className="form-group time-tracking-toggle-group">
                        <span className="form-label-text">
                            {t('userManagement.includeInTimeTrackingLabel', 'In Zeiterfassung & Übersichten anzeigen')}
                        </span>
                        <button
                            type="button"
                            className={`time-tracking-toggle-button ${userData.includeInTimeTracking !== false ? 'active' : ''}`}
                            onClick={() => handleChange('includeInTimeTracking', !(userData.includeInTimeTracking !== false))}
                            aria-pressed={userData.includeInTimeTracking !== false}
                        >
                            {userData.includeInTimeTracking !== false
                                ? t('userManagement.includeInTimeTrackingEnabled', 'Eingeschlossen in Zeitübersichten')
                                : t('userManagement.includeInTimeTrackingDisabled', 'Von Zeitübersichten ausgeschlossen')}
                        </button>
                        <p className="form-group-description">
                            {t('userManagement.includeInTimeTrackingHint', 'Admins ohne Arbeitszeiterfassung werden in Wochenansichten und Salden nicht angezeigt.')}
                        </p>
                    </div>
                )}

                <div className="form-group full-width">
                    <span className="form-label-text">
                        {t("userManagement.color", "Farbe")}
                        <span className="label-hint">
                            {" "}
                            ({t("userManagement.colorHint", "für Kalender & Auswertungen (optional)")})
                        </span>
                    </span>
                    <div className="color-picker">
                        {STANDARD_COLORS.map((c, idx) => (
                            <div
                                key={idx}
                                className={`color-swatch ${userData.color === c ? "selected" : ""}`}
                                style={{ backgroundColor: c }}
                                onClick={() => handleChange("color", c)}
                                role="button"
                                tabIndex={0}
                                onKeyPress={(e) => e.key === "Enter" && handleChange("color", c)}
                                aria-label={`Farbe ${c} auswählen`}
                            />
                        ))}
                    </div>
                </div>

                <div className="form-group full-width form-group-heading">
                    <h5>{t("userManagement.section.personalData", "Persönliche Angaben")}</h5>
                    <p className="form-group-description">
                        {t("userManagement.section.personalDataHint", "Grunddaten für Stammdaten und Dokumente.")}
                    </p>
                </div>

                <div className="form-group">
                    {renderLabel("firstName", "userManagement.firstName", "Vorname", { required: true })}
                    <input
                        id="firstName"
                        type="text"
                        value={userData.firstName || ""}
                        onChange={(e) => handleChange("firstName", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    {renderLabel("lastName", "userManagement.lastName", "Nachname", { required: true })}
                    <input
                        id="lastName"
                        type="text"
                        value={userData.lastName || ""}
                        onChange={(e) => handleChange("lastName", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    {renderLabel("birthDate", "userManagement.birthDate", "Geburtsdatum")}
                    <input
                        id="birthDate"
                        type="date"
                        pattern="\d{4}-\d{2}-\d{2}"
                        value={userData.birthDate || ""}
                        onChange={(e) => handleChange("birthDate", e.target.value)}
                    />
                </div>
                <div className="form-group">
                    {renderLabel("civilStatus", "userManagement.civilStatus", "Zivilstand", { hint: optionalLabelText })}
                    <input
                        id="civilStatus"
                        type="text"
                        value={userData.civilStatus || ""}
                        onChange={(e) => handleChange("civilStatus", e.target.value)}
                    />
                </div>
                <div className="form-group">
                    {renderLabel("children", "userManagement.children", "Kinder", { hint: optionalLabelText })}
                    <input
                        id="children"
                        type="number"
                        min="0"
                        value={userData.children ?? 0}
                        onChange={(e) => handleChange("children", parseInt(e.target.value, 10))}
                    />
                </div>

                <div className="form-group full-width form-group-heading">
                    <h5>{t("userManagement.section.contact", "Kontakt & Adresse")}</h5>
                    <p className="form-group-description">
                        {t("userManagement.section.contactHint", "Wird für Benachrichtigungen und Unterlagen verwendet.")}
                    </p>
                </div>

                <div className="form-group">
                    {renderLabel("email", "userManagement.email", "E-Mail", { required: true })}
                    <input
                        id="email"
                        type="email"
                        value={userData.email || ""}
                        onChange={(e) => handleChange("email", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    {renderLabel("mobilePhone", "userManagement.mobilePhone", "Handynummer", { required: true })}
                    <input
                        id="mobilePhone"
                        type="tel"
                        value={userData.mobilePhone || ""}
                        onChange={(e) => handleChange("mobilePhone", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    {renderLabel("landlinePhone", "userManagement.landlinePhone", "Festnetz", { hint: optionalLabelText })}
                    <input
                        id="landlinePhone"
                        type="tel"
                        value={userData.landlinePhone || ""}
                        onChange={(e) => handleChange("landlinePhone", e.target.value)}
                    />
                </div>
                <div className="form-group full-width">
                    {renderLabel("address", "userManagement.address", "Adresse", { hint: optionalLabelText })}
                    <input
                        id="address"
                        type="text"
                        value={userData.address || ""}
                        onChange={(e) => handleChange("address", e.target.value)}
                    />
                </div>

                <div className="form-group full-width form-group-heading">
                    <h5>{t("userManagement.section.employment", "Beschäftigung & Payroll")}</h5>
                    <p className="form-group-description">
                        {t(
                            "userManagement.section.employmentHint",
                            "Organisatorische Daten und landesspezifische Angaben für die Lohnabrechnung."
                        )}
                    </p>
                </div>

                <div className="form-group">
                    {renderLabel("personnelNumber", "userManagement.personnelNumber", "Personalnummer", {
                        required: true,
                        hint: t(
                            "userManagement.personnelNumberHint",
                            "laut Lohnsystem, nur Ziffern (max. 10, z. B. 4711)"
                        ),
                    })}
                    <input
                        id="personnelNumber"
                        type="text"
                        pattern="[0-9]{1,10}"
                        value={userData.personnelNumber || ""}
                        onChange={(e) => handleChange("personnelNumber", e.target.value)}
                        required
                    />
                </div>
                <div className="form-group">
                    {renderLabel("entryDate", "userManagement.entryDate", "Eintrittsdatum", {
                        hint: t(
                            "userManagement.entryDateHint",
                            "Startdatum im Format JJJJ-MM-TT (optional, z. B. 2024-01-01)"
                        ),
                    })}
                    <input
                        id="entryDate"
                        type="date"
                        pattern="\d{4}-\d{2}-\d{2}"
                        value={userData.entryDate || ""}
                        onChange={(e) => handleChange("entryDate", e.target.value)}
                    />
                </div>
                <div className="form-group">
                    {renderLabel("department", "userManagement.department", "Abteilung", { hint: optionalLabelText })}
                    <input
                        id="department"
                        type="text"
                        value={userData.department || ""}
                        onChange={(e) => handleChange("department", e.target.value)}
                    />
                </div>
                <div className="form-group">
                    {renderLabel("country", "userManagement.country", "Land", {
                        required: true,
                        hint: t("userManagement.countryHint", "steuert länderspezifische Felder"),
                    })}
                    <select
                        id="country"
                        value={userData.country || "DE"}
                        onChange={(e) => handleChange("country", e.target.value)}
                        required
                    >
                        <option value="DE">DE</option>
                        <option value="CH">CH</option>
                    </select>
                </div>
                <div className="form-group full-width">
                    {renderLabel("bankAccount", "userManagement.bankAccount", "Bankverbindung", {
                        hint: t(
                            "userManagement.bankAccountHint",
                            "IBAN für die Lohnzahlung (z. B. DE89 3704 0044 0532 0130 00)"
                        ),
                    })}
                    <input
                        id="bankAccount"
                        type="text"
                        value={userData.bankAccount || ""}
                        onChange={(e) => handleChange("bankAccount", e.target.value)}
                    />
                </div>
                <div className="form-group full-width">
                    {renderLabel("healthInsurance", "userManagement.healthInsurance", "Krankenkasse", {
                        hint: t(
                            "userManagement.healthInsuranceHint",
                            "Name der Krankenkasse für die Abrechnung (optional, z. B. TK)"
                        ),
                    })}
                    <input
                        id="healthInsurance"
                        type="text"
                        value={userData.healthInsurance || ""}
                        onChange={(e) => handleChange("healthInsurance", e.target.value)}
                    />
                </div>

                {/* Conditional Fields based on Country */}
                {userData.country === 'DE' && (
                    <>
                        <div className="form-group full-width form-group-subheading">
                            <h6>{t("userManagement.section.germany", "Zusatzangaben für Deutschland")}</h6>
                        </div>
                        <div className="form-group">
                            {renderLabel("taxClass", "userManagement.taxClass", "Steuerklasse", {
                                required: true,
                                hint: t("userManagement.taxClassHint", "laut ELStAM, z. B. I oder III"),
                            })}
                            <input
                                id="taxClass"
                                type="text"
                                pattern="[A-Za-z0-9]+"
                                value={userData.taxClass || ""}
                                onChange={(e) => handleChange("taxClass", e.target.value)}
                                required
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel(
                                "socialSecurityNumber",
                                "userManagement.socialSecurityNumber.de",
                                "Sozialversicherungsnummer",
                                {
                                    hint: t(
                                        "userManagement.socialSecurityNumberDeHint",
                                        "11 Stellen ohne Leerzeichen (optional, z. B. 12345678901)"
                                    ),
                                }
                            )}
                            <input
                                id="socialSecurityNumber"
                                type="text"
                                value={userData.socialSecurityNumber || ""}
                                onChange={(e) => handleChange("socialSecurityNumber", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("religion", "userManagement.religion.de", "Religion (für Kirchensteuer)", {
                                hint: t(
                                    "userManagement.religionDeHint",
                                    "laut Meldedaten (optional, z. B. römisch-katholisch)"
                                ),
                            })}
                            <input
                                id="religion"
                                type="text"
                                value={userData.religion || ""}
                                onChange={(e) => handleChange("religion", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("federalState", "userManagement.federalState", "Bundesland", {
                                hint: optionalLabelText,
                            })}
                            <select
                                id="federalState"
                                value={userData.federalState || ""}
                                onChange={(e) => handleChange("federalState", e.target.value)}
                            >
                                <option value="">{t("userManagement.selectOption", "– bitte wählen –")}</option>
                                <option value="BW">Baden-Württemberg</option>
                                <option value="BY">Bayern</option>
                                <option value="BE">Berlin</option>
                                <option value="BB">Brandenburg</option>
                                <option value="HB">Bremen</option>
                                <option value="HH">Hamburg</option>
                                <option value="HE">Hessen</option>
                                <option value="MV">Mecklenburg-Vorpommern</option>
                                <option value="NI">Niedersachsen</option>
                                <option value="NW">Nordrhein-Westfalen</option>
                                <option value="RP">Rheinland-Pfalz</option>
                                <option value="SL">Saarland</option>
                                <option value="SN">Sachsen</option>
                                <option value="ST">Sachsen-Anhalt</option>
                                <option value="SH">Schleswig-Holstein</option>
                                <option value="TH">Thüringen</option>
                            </select>
                        </div>
                        <div className="form-group form-group-checkbox">
                            <input
                                type="checkbox"
                                id="churchTax"
                                checked={!!userData.churchTax}
                                onChange={(e) => handleCheckboxChange("churchTax", e.target.checked)}
                            />
                            <label htmlFor="churchTax">{t("userManagement.churchTax", "Kirchensteuerpflichtig")}</label>
                        </div>
                        <div className="form-group">
                            {renderLabel(
                                "gkvAdditionalRate",
                                "userManagement.gkvAdditionalRate",
                                "GKV-Zusatzbeitrag (AN-Hälfte)",
                                {
                                    hint: t(
                                        "userManagement.gkvAdditionalRateHint",
                                        "Dezimalwert laut Krankenkasse (optional, z. B. 0.0125)"
                                    ),
                                }
                            )}
                            <input
                                id="gkvAdditionalRate"
                                type="number"
                                step="0.0005"
                                min="0"
                                max="0.03"
                                value={userData.gkvAdditionalRate ?? ""}
                                onChange={(e) =>
                                    handleChange("gkvAdditionalRate", e.target.value ? parseFloat(e.target.value) : null)
                                }
                                placeholder="z. B. 0.0125"
                            />
                        </div>
                    </>
                )}

                {userData.country === 'CH' && (
                    <>
                        <div className="form-group full-width form-group-subheading">
                            <h6>{t("userManagement.section.switzerland", "Zusatzangaben für die Schweiz")}</h6>
                        </div>
                        <div className="form-group">
                            {renderLabel("tarifCode", "userManagement.tarifCode", "Tarifcode", {
                                hint: t(
                                    "userManagement.tarifCodeHint",
                                    "Quellensteuer-Code vom Lohnausweis, z. B. A0"
                                ),
                            })}
                            <input
                                id="tarifCode"
                                type="text"
                                value={userData.tarifCode || ""}
                                onChange={(e) => handleChange("tarifCode", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("canton", "userManagement.canton", "Kanton", { hint: optionalLabelText })}
                            <input
                                id="canton"
                                type="text"
                                value={userData.canton || ""}
                                onChange={(e) => handleChange("canton", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel(
                                "socialSecurityNumber",
                                "userManagement.socialSecurityNumber.ch",
                                "AHV-Nr.",
                                {
                                    hint: t(
                                        "userManagement.socialSecurityNumberChHint",
                                        "Sozialversicherungsnummer (optional, 13-stellig, z. B. 756.1234.5678.97)"
                                    ),
                                }
                            )}
                            <input
                                id="socialSecurityNumber"
                                type="text"
                                value={userData.socialSecurityNumber || ""}
                                onChange={(e) => handleChange("socialSecurityNumber", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("religion", "userManagement.religion.ch", "Religion", {
                                hint: t(
                                    "userManagement.religionChHint",
                                    "für Quellensteuer (optional, z. B. römisch-katholisch)"
                                ),
                            })}
                            <input
                                id="religion"
                                type="text"
                                value={userData.religion || ""}
                                onChange={(e) => handleChange("religion", e.target.value)}
                            />
                        </div>
                    </>
                )}

                {/* Sektion: Arbeitsmodell */}
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
                        disabled={!!userData.isHourly} // Deaktivieren, wenn isHourly true ist
                    />
                    <label htmlFor="isPercentage">{t("userManagement.percentageTracking", "Prozentbasierte Zeiterfassung")}</label>
                </div>

                {userData.isHourly && (
                    <div className="form-group">
                        {renderLabel("hourlyWage", "userManagement.hourlyWage", "Stundenlohn (Brutto)", {
                            required: true,
                            hint: t(
                                "userManagement.hourlyWageHint",
                                "Bruttobetrag in Landeswährung, z. B. 25.00"
                            ),
                        })}
                        <input
                            id="hourlyWage"
                            type="number"
                            step="0.01"
                            min="0"
                            value={userData.hourlyWage ?? ""}
                            onChange={(e) => handleChange("hourlyWage", e.target.value ? parseFloat(e.target.value) : null)}
                            placeholder="z.B. 25.00"
                            required
                        />
                    </div>
                )}

                {!userData.isHourly && (
                    <div className="form-group">
                        {renderLabel("monthlySalary", "userManagement.monthlySalary", "Monatslohn (Brutto)", {
                            required: true,
                            hint: t(
                                "userManagement.monthlySalaryHint",
                                "Bruttogehalt pro Monat in Landeswährung, z. B. 4500.00"
                            ),
                        })}
                        <input
                            id="monthlySalary"
                            type="number"
                            step="0.01"
                            min="0"
                            value={userData.monthlySalary ?? ""}
                            onChange={(e) => handleChange("monthlySalary", e.target.value ? parseFloat(e.target.value) : null)}
                            placeholder="z.B. 4500.00"
                            required
                        />
                    </div>
                )}

                {userData.isPercentage && !userData.isHourly && (
                    <div className="form-group">
                        {renderLabel("workPercentage", "userManagement.workPercentage", "Arbeitspensum (%)", {
                            hint: t(
                                "userManagement.workPercentageHint",
                                "Beschäftigungsgrad für die Lohnberechnung, z. B. 60"
                            ),
                        })}
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


                {/* Sektion: Allgemeine Einstellungen */}
                <h4 className="form-section-title full-width">{t('userManagement.section.generalSettings', 'Allgemeine Einstellungen')}</h4>

                <div className="form-group">
                    {renderLabel("annualVacationDays", "userManagement.annualVacationDays", "Urlaubstage/Jahr", {
                        hint: t(
                            "userManagement.annualVacationDaysHint",
                            "für die Urlaubsberechnung, ganze oder halbe Tage"
                        ),
                    })}
                    <input
                        id="annualVacationDays"
                        type="number" step="0.5" min="0"
                        value={userData.annualVacationDays === null || userData.annualVacationDays === undefined ? "" : userData.annualVacationDays}
                        onChange={(e) => handleChange("annualVacationDays", e.target.value ? parseFloat(e.target.value) : null)}
                        placeholder="z.B. 25"
                    />
                </div>
                <div className="form-group">
                    {renderLabel("breakDuration", "userManagement.breakDuration", "Standard Pausendauer (Min)", {
                        hint: t(
                            "userManagement.breakDurationHint",
                            "für automatische Pausenabzüge, Minuten pro Tag"
                        ),
                    })}
                    <input
                        id="breakDuration"
                        type="number" min="0"
                        value={userData.breakDuration === null || userData.breakDuration === undefined ? "" : userData.breakDuration}
                        onChange={(e) => handleChange("breakDuration", e.target.value ? parseInt(e.target.value, 10) : null)}
                        placeholder="z.B. 30"
                    />
                </div>

                {!userData.isHourly && (
                    <div className="form-group">
                        {renderLabel(
                            "expectedWorkDays",
                            "userManagement.expectedWorkDays",
                            "Erw. Arbeitstage/Woche",
                            {
                                hint: t(
                                    "userManagement.expectedWorkDaysHint",
                                    "typische Arbeitstage zur Sollstundenberechnung"
                                ),
                            }
                        )}
                        <input
                            id="expectedWorkDays"
                            type="number" step="0.5" min="0" max="7"
                            value={userData.expectedWorkDays === null || userData.expectedWorkDays === undefined ? "" : userData.expectedWorkDays}
                            onChange={(e) => handleChange("expectedWorkDays", e.target.value ? parseFloat(e.target.value) : null)}
                            placeholder="z.B. 5"
                        />
                    </div>
                )}


                {/* Sektion: Wochenplan-Konfiguration */}
                <div className="full-width">
                    <h4 className="form-section-title">{t("userManagement.scheduleConfig", "Wochenplan & Sollzeiten")}</h4>
                    <div className="admin-user-form-grid"> {/* Nested grid for this section's own layout */}
                        <div className="form-group">
                            {renderLabel("dailyWorkHours", "userManagement.dailyWorkHours", "Standard Tagessoll (Std)", {
                                hint: t(
                                    "userManagement.dailyWorkHoursHint",
                                    "Sollstunden pro Arbeitstag für Zeitkonten"
                                ),
                            })}
                            <input
                                id="dailyWorkHours"
                                type="number" step="0.01" min="0"
                                value={userData.dailyWorkHours === null || userData.dailyWorkHours === undefined ? "" : userData.dailyWorkHours}
                                onChange={(e) => handleChange("dailyWorkHours", e.target.value ? parseFloat(e.target.value) : null)}
                                placeholder="z.B. 8.5"
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("scheduleEffectiveDate", "userManagement.scheduleEffectiveDate", "Plan gültig ab", {
                                hint: t(
                                    "userManagement.scheduleEffectiveDateHint",
                                    "Datum, ab dem der Wochenplan angewendet wird (z. B. 2024-01-01)"
                                ),
                            })}
                            <input
                                type="date"
                                id="scheduleEffectiveDate"
                                value={userData.scheduleEffectiveDate || ''}
                                onChange={(e) => handleChange("scheduleEffectiveDate", e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            {renderLabel("scheduleCycle", "userManagement.cycleLength", "Zykluslänge (Wochen)", {
                                hint: t(
                                    "userManagement.cycleLengthHint",
                                    "Anzahl Wochen, bevor der Plan von vorn beginnt (z. B. 4)"
                                ),
                            })}
                            <input
                                type="number"
                                id="scheduleCycle"
                                min="1"
                                value={userData.scheduleCycle || 1}
                                onChange={(e) => onScheduleCycleChange(Number(e.target.value))}
                            />
                        </div>
                    </div>
                    <div className="schedule-week-container full-width">
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


                {/* Sektion: Formular-Aktionen */}
                <div className="form-actions full-width">
                    <button type="submit" className="button-primary">
                        {isEditing ? t("userManagement.button.saveChanges", "Änderungen speichern") : t("userManagement.button.createUser", "Benutzer erstellen")}
                    </button>
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