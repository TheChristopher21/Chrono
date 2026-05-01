// src/pages/AdminUserManagement/AdminUserForm.jsx
import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { STANDARD_COLORS } from './adminUserManagementUtils';
import {
    ACCESS_MANAGE,
    ACCESS_NONE,
    ACCESS_VIEW,
    buildDefaultPagePermissions,
    getAccessChoicesForPage,
    getPermissionSectionsForRole,
    normalizeAccessLevel,
} from '../../utils/pageAccess.js';

const DAY_KEYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];

const DAY_FALLBACKS = {
    monday: 'Montag',
    tuesday: 'Dienstag',
    wednesday: 'Mittwoch',
    thursday: 'Donnerstag',
    friday: 'Freitag',
    saturday: 'Samstag',
    sunday: 'Sonntag',
};

const ROLE_LABELS = {
    ROLE_USER: 'Mitarbeiter',
    ROLE_ADMIN: 'Admin',
    ROLE_PAYROLL_ADMIN: 'Payroll Admin',
    ROLE_SUPERADMIN: 'Superadmin',
};

const AdminUserForm = ({
                           t,
                           isEditing,
                           userData,
                           originalUser,
                           setUserData,
                           onSubmit,
                           onCancel,
                           onScheduleCycleChange,
                           onWeeklyScheduleDayChange
                       }) => {
    const [activeTab, setActiveTab] = useState('profile');
    const [wizardStepIndex, setWizardStepIndex] = useState(0);
    const [permissionSearch, setPermissionSearch] = useState('');
    const [showOnlyCustomPermissions, setShowOnlyCustomPermissions] = useState(false);
    const [showColorPalette, setShowColorPalette] = useState(false);
    const [submitAttempted, setSubmitAttempted] = useState(false);

    useEffect(() => {
        setActiveTab('profile');
        setWizardStepIndex(0);
        setPermissionSearch('');
        setShowOnlyCustomPermissions(false);
        setShowColorPalette(false);
        setSubmitAttempted(false);
    }, [isEditing]);

    const handleChange = (field, value) => {
        setUserData(field, value);
    };

    const handleCheckboxChange = (field, checked) => {
        setUserData(field, checked);
    };

    const selectedRole = userData.roles?.[0] || 'ROLE_USER';
    const selectedRoleLabel = ROLE_LABELS[selectedRole] || selectedRole.replace('ROLE_', '');
    const permissionSections = getPermissionSectionsForRole(selectedRole, userData.companyFeatureKeys);
    const roleDefaultPermissions = buildDefaultPagePermissions(selectedRole, userData.companyFeatureKeys);

    const handleRoleChange = (e) => {
        handleChange('roles', [e.target.value]);
    };

    const applyRolePreset = (roleName) => {
        handleChange('roles', [roleName]);
    };

    const handlePermissionChange = (pageKey, nextAccessLevel) => {
        handleChange('pagePermissions', {
            ...(userData.pagePermissions || {}),
            [pageKey]: nextAccessLevel,
        });
    };

    const resetPermissionsToRole = () => {
        handleChange('pagePermissions', buildDefaultPagePermissions(selectedRole, userData.companyFeatureKeys));
    };

    const getAccessLabel = (accessLevel) => {
        switch (accessLevel) {
            case ACCESS_MANAGE:
                return 'Verwalten';
            case ACCESS_VIEW:
                return 'Ansehen';
            case ACCESS_NONE:
            default:
                return 'Kein Zugriff';
        }
    };

    const hasValue = (value) => value !== null && value !== undefined && String(value).trim() !== '';

    const requiredChecks = [
        { key: 'username', label: t('userManagement.username', 'Benutzername'), complete: hasValue(userData.username) },
        { key: 'password', label: t('userManagement.password', 'Passwort'), complete: isEditing || hasValue(userData.password) },
        { key: 'firstName', label: t('userManagement.firstName', 'Vorname'), complete: hasValue(userData.firstName) },
        { key: 'lastName', label: t('userManagement.lastName', 'Nachname'), complete: hasValue(userData.lastName) },
        { key: 'email', label: t('userManagement.email', 'E-Mail'), complete: hasValue(userData.email) },
        { key: 'mobilePhone', label: t('userManagement.mobilePhone', 'Handynummer'), complete: hasValue(userData.mobilePhone) },
        { key: 'personnelNumber', label: t('userManagement.personnelNumber', 'Personalnummer'), complete: hasValue(userData.personnelNumber) },
        { key: 'country', label: t('userManagement.country', 'Land'), complete: hasValue(userData.country) },
        {
            key: 'taxClass',
            label: t('userManagement.taxClass', 'Steuerklasse'),
            complete: userData.country !== 'DE' || hasValue(userData.taxClass),
        },
        {
            key: 'hourlyWage',
            label: t('userManagement.hourlyWage', 'Stundenlohn'),
            complete: !userData.isHourly || hasValue(userData.hourlyWage),
        },
        {
            key: 'monthlySalary',
            label: t('userManagement.monthlySalary', 'Monatslohn'),
            complete: userData.isHourly || hasValue(userData.monthlySalary),
        },
    ];

    const missingRequired = requiredChecks.filter((item) => !item.complete);
    const completionPercent = Math.round(((requiredChecks.length - missingRequired.length) / requiredChecks.length) * 100);
    const isActive = userData.active !== false && userData.enabled !== false && userData.deleted !== true;
    const fullName = [userData.firstName, userData.lastName].filter(Boolean).join(' ').trim() || userData.username || 'Neuer Benutzer';
    const initials = (
        `${userData.firstName?.[0] || ''}${userData.lastName?.[0] || ''}`.trim()
        || userData.username?.slice(0, 2)
        || 'NB'
    ).toUpperCase();
    const workModelLabel = userData.isHourly
        ? t('userTypes.hourly', 'Stundenlohn')
        : userData.isPercentage
            ? `${t('userTypes.percentage', 'Prozentual')} ${userData.workPercentage || 100}%`
            : t('userTypes.standard', 'Standard');
    const payrollStatus = userData.isHourly
        ? (hasValue(userData.hourlyWage) ? 'Aktiv' : 'Offen')
        : (hasValue(userData.monthlySalary) ? 'Aktiv' : 'Offen');

    const flattenedPages = permissionSections.flatMap((section) => section.pages);
    const customPermissionCount = flattenedPages.filter((page) => (
        normalizeAccessLevel(userData.pagePermissions?.[page.key])
        !== normalizeAccessLevel(roleDefaultPermissions?.[page.key])
    )).length;

    const filteredPermissionSections = permissionSections
        .map((section) => {
            const query = permissionSearch.trim().toLowerCase();
            const pages = section.pages.filter((page) => {
                const currentAccess = normalizeAccessLevel(userData.pagePermissions?.[page.key]);
                const defaultAccess = normalizeAccessLevel(roleDefaultPermissions?.[page.key]);
                const isCustom = currentAccess !== defaultAccess;
                const matchesQuery = !query
                    || page.label.toLowerCase().includes(query)
                    || page.description.toLowerCase().includes(query)
                    || section.group.toLowerCase().includes(query);

                return matchesQuery && (!showOnlyCustomPermissions || isCustom);
            });

            return { ...section, pages };
        })
        .filter((section) => section.pages.length > 0);

    const changedFieldSummaries = useMemo(() => {
        if (!originalUser || !isEditing) {
            return [];
        }

        const normalize = (value) => {
            if (Array.isArray(value) || (value && typeof value === 'object')) {
                return JSON.stringify(value ?? null);
            }
            return String(value ?? '');
        };

        const changeCandidates = [
            {
                label: t('userManagement.role', 'Rolle'),
                before: ROLE_LABELS[originalUser.roles?.[0]] || originalUser.roles?.[0],
                after: selectedRoleLabel,
            },
            {
                label: t('userManagement.fullName', 'Name'),
                before: [originalUser.firstName, originalUser.lastName].filter(Boolean).join(' '),
                after: fullName,
            },
            { label: t('userManagement.email', 'E-Mail'), before: originalUser.email, after: userData.email },
            { label: t('userManagement.mobilePhone', 'Handynummer'), before: originalUser.mobilePhone, after: userData.mobilePhone },
            { label: t('userManagement.personnelNumber', 'Personalnummer'), before: originalUser.personnelNumber, after: userData.personnelNumber },
            { label: t('userManagement.monthlySalary', 'Monatslohn'), before: originalUser.monthlySalary, after: userData.monthlySalary },
            { label: t('userManagement.hourlyWage', 'Stundenlohn'), before: originalUser.hourlyRate, after: userData.hourlyWage },
            { label: t('userManagement.annualVacationDays', 'Urlaubstage'), before: originalUser.annualVacationDays, after: userData.annualVacationDays },
            { label: t('userManagement.breakDuration', 'Pausendauer'), before: originalUser.breakDuration, after: userData.breakDuration },
            { label: t('userManagement.dailyWorkHours', 'Tagessoll'), before: originalUser.dailyWorkHours, after: userData.dailyWorkHours },
            { label: t('userManagement.expectedWorkDays', 'Arbeitstage'), before: originalUser.expectedWorkDays, after: userData.expectedWorkDays },
            { label: t('userManagement.workModel', 'Arbeitsmodell'), before: originalUser.isHourly ? 'Stundenlohn' : (originalUser.isPercentage ? 'Prozentual' : 'Standard'), after: workModelLabel },
            { label: t('userManagement.pageAccessSummary', 'Seitenrechte'), before: originalUser.pagePermissions, after: userData.pagePermissions },
        ];

        return changeCandidates.filter((item) => normalize(item.before) !== normalize(item.after));
    }, [fullName, isEditing, originalUser, selectedRoleLabel, t, userData, workModelLabel]);

    const wizardSteps = [
        { id: 'account', label: 'Konto' },
        { id: 'access', label: 'Rechte' },
        { id: 'profile', label: 'Stammdaten' },
        { id: 'payroll', label: 'Payroll' },
        { id: 'worktime', label: 'Arbeitszeit' },
        { id: 'preview', label: 'Vorschau' },
    ];
    const currentWizardStep = wizardSteps[wizardStepIndex];
    const isLastWizardStep = wizardStepIndex === wizardSteps.length - 1;

    const editTabs = [
        { id: 'profile', label: 'Profil' },
        { id: 'access', label: 'Zugriff' },
        { id: 'permissions', label: 'Rechte' },
        { id: 'payroll', label: 'Payroll' },
        { id: 'worktime', label: 'Arbeitszeit' },
    ];

    const goToNextWizardStep = () => {
        setWizardStepIndex((current) => Math.min(current + 1, wizardSteps.length - 1));
    };

    const goToPreviousWizardStep = () => {
        setWizardStepIndex((current) => Math.max(current - 1, 0));
    };

    const handleFormSubmit = (event) => {
        if (!isEditing && !isLastWizardStep) {
            event.preventDefault();
            goToNextWizardStep();
            return;
        }

        if (missingRequired.length > 0) {
            event.preventDefault();
            setSubmitAttempted(true);
            return;
        }

        onSubmit(event);
    };

    const renderLabel = (id, translationKey, defaultText, { required = false, hint } = {}) => (
        <label htmlFor={id}>
            <span>
                {t(translationKey, defaultText)}
                {required ? <span className="required-indicator" aria-label={t('userManagement.requiredField', 'Pflichtfeld')}>*</span> : null}
            </span>
            {hint ? <span className="label-hint">{hint}</span> : null}
        </label>
    );

    const renderInputField = ({
                                  id,
                                  translationKey,
                                  defaultText,
                                  type = 'text',
                                  value,
                                  onChange,
                                  required = false,
                                  hint,
                                  className = '',
                                  ...inputProps
                              }) => (
        <div className={`form-field ${className}`}>
            {renderLabel(id, translationKey, defaultText, { required, hint })}
            <input
                id={id}
                type={type}
                value={value ?? ''}
                onChange={onChange}
                required={required}
                {...inputProps}
            />
        </div>
    );

    const renderSelectField = ({
                                   id,
                                   translationKey,
                                   defaultText,
                                   value,
                                   onChange,
                                   required = false,
                                   hint,
                                   children,
                                   className = '',
                               }) => (
        <div className={`form-field ${className}`}>
            {renderLabel(id, translationKey, defaultText, { required, hint })}
            <select id={id} value={value ?? ''} onChange={onChange} required={required}>
                {children}
            </select>
        </div>
    );

    const renderCheckboxField = ({ id, label, checked, onChange, disabled = false, description }) => (
        <div className="toggle-card">
            <input
                type="checkbox"
                id={id}
                checked={checked}
                disabled={disabled}
                onChange={onChange}
            />
            <label htmlFor={id}>
                <span>{label}</span>
                {description ? <small>{description}</small> : null}
            </label>
        </div>
    );

    const FormSection = ({ title, description, children, actions }) => (
        <section className="form-section">
            <div className="section-heading">
                <div>
                    <h4>{title}</h4>
                    {description ? <p>{description}</p> : null}
                </div>
                {actions ? <div className="section-actions">{actions}</div> : null}
            </div>
            <div className="admin-user-form-grid">
                {children}
            </div>
        </section>
    );

    FormSection.propTypes = {
        title: PropTypes.string.isRequired,
        description: PropTypes.string,
        children: PropTypes.node.isRequired,
        actions: PropTypes.node,
    };

    FormSection.defaultProps = {
        description: '',
        actions: null,
    };

    const renderRoleSelect = () => renderSelectField({
        id: 'roles',
        translationKey: 'userManagement.role',
        defaultText: 'Rolle',
        value: selectedRole,
        onChange: handleRoleChange,
        required: true,
        children: (
            <>
                <option value="ROLE_USER">Mitarbeiter</option>
                <option value="ROLE_ADMIN">Admin</option>
                <option value="ROLE_PAYROLL_ADMIN">Payroll Admin</option>
                <option value="ROLE_SUPERADMIN">Superadmin</option>
            </>
        ),
    });

    const renderAccountFields = ({ includePresetCards = false } = {}) => (
        <>
            {includePresetCards ? (
                <div className="role-preset-grid full-width">
                    {[
                        { role: 'ROLE_USER', title: 'Mitarbeiter', text: 'Zeiterfassung, Urlaub und eigene Daten.' },
                        { role: 'ROLE_PAYROLL_ADMIN', title: 'Payroll Admin', text: 'Lohnbereiche und abrechnungsnahe Daten.' },
                        { role: 'ROLE_ADMIN', title: 'Admin', text: 'Team, Rechte und operative Verwaltung.' },
                    ].map((preset) => (
                        <button
                            key={preset.role}
                            type="button"
                            className={`role-preset-card ${selectedRole === preset.role ? 'selected' : ''}`}
                            onClick={() => applyRolePreset(preset.role)}
                        >
                            <span>{preset.title}</span>
                            <small>{preset.text}</small>
                        </button>
                    ))}
                </div>
            ) : null}

            {renderInputField({
                id: 'username',
                translationKey: 'userManagement.username',
                defaultText: 'Benutzername',
                value: userData.username,
                onChange: (e) => handleChange('username', e.target.value),
                required: true,
                disabled: isEditing,
            })}

            {!isEditing ? renderInputField({
                id: 'password',
                translationKey: 'userManagement.password',
                defaultText: 'Passwort',
                type: 'password',
                value: userData.password,
                onChange: (e) => handleChange('password', e.target.value),
                required: true,
                hint: t('userManagement.passwordHint', 'nur fuer neue Benutzer'),
            }) : null}

            {renderInputField({
                id: 'firstName',
                translationKey: 'userManagement.firstName',
                defaultText: 'Vorname',
                value: userData.firstName,
                onChange: (e) => handleChange('firstName', e.target.value),
                required: true,
            })}

            {renderInputField({
                id: 'lastName',
                translationKey: 'userManagement.lastName',
                defaultText: 'Nachname',
                value: userData.lastName,
                onChange: (e) => handleChange('lastName', e.target.value),
                required: true,
            })}

            {renderInputField({
                id: 'email',
                translationKey: 'userManagement.email',
                defaultText: 'E-Mail',
                type: 'email',
                value: userData.email,
                onChange: (e) => handleChange('email', e.target.value),
                required: true,
                className: 'form-field-wide',
            })}

            {renderInputField({
                id: 'mobilePhone',
                translationKey: 'userManagement.mobilePhone',
                defaultText: 'Handynummer',
                type: 'tel',
                value: userData.mobilePhone,
                onChange: (e) => handleChange('mobilePhone', e.target.value),
                required: true,
            })}

            {renderRoleSelect()}
        </>
    );

    const renderColorPicker = () => (
        <div className="form-field form-field-wide color-control">
            <span className="form-label-text">
                {t('userManagement.color', 'Farbe')}
                <span className="label-hint">{t('userManagement.colorHint', 'fuer Kalender und Auswertungen')}</span>
            </span>
            <div className="color-compact-row">
                <span className="selected-color-dot" style={{ backgroundColor: userData.color || STANDARD_COLORS[0] }} />
                <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => setShowColorPalette((open) => !open)}
                >
                    Farbe ändern
                </button>
                <input
                    type="color"
                    value={userData.color || STANDARD_COLORS[0]}
                    onChange={(e) => handleChange('color', e.target.value)}
                    aria-label="Benutzerfarbe frei wählen"
                />
            </div>
            {showColorPalette ? (
                <div className="color-popover">
                    {STANDARD_COLORS.map((color) => (
                        <button
                            key={color}
                            type="button"
                            className={`color-swatch ${userData.color === color ? 'selected' : ''}`}
                            style={{ backgroundColor: color }}
                            onClick={() => {
                                handleChange('color', color);
                                setShowColorPalette(false);
                            }}
                            aria-label={`Farbe ${color} auswählen`}
                        />
                    ))}
                </div>
            ) : null}
        </div>
    );

    const renderProfileFields = () => (
        <>
            <FormSection
                title={t('userManagement.section.personalData', 'Persönliche Angaben')}
                description={t('userManagement.section.personalDataHint', 'Grunddaten fuer Stammdaten und Dokumente.')}
            >
                {renderInputField({
                    id: 'firstName',
                    translationKey: 'userManagement.firstName',
                    defaultText: 'Vorname',
                    value: userData.firstName,
                    onChange: (e) => handleChange('firstName', e.target.value),
                    required: true,
                })}
                {renderInputField({
                    id: 'lastName',
                    translationKey: 'userManagement.lastName',
                    defaultText: 'Nachname',
                    value: userData.lastName,
                    onChange: (e) => handleChange('lastName', e.target.value),
                    required: true,
                })}
                {renderInputField({
                    id: 'birthDate',
                    translationKey: 'userManagement.birthDate',
                    defaultText: 'Geburtsdatum',
                    type: 'date',
                    value: userData.birthDate,
                    onChange: (e) => handleChange('birthDate', e.target.value),
                    pattern: '\\d{4}-\\d{2}-\\d{2}',
                })}
                {renderInputField({
                    id: 'civilStatus',
                    translationKey: 'userManagement.civilStatus',
                    defaultText: 'Zivilstand',
                    value: userData.civilStatus,
                    onChange: (e) => handleChange('civilStatus', e.target.value),
                    hint: t('userManagement.optionalField', 'optional'),
                })}
                {renderInputField({
                    id: 'children',
                    translationKey: 'userManagement.children',
                    defaultText: 'Kinder',
                    type: 'number',
                    min: '0',
                    value: userData.children ?? 0,
                    onChange: (e) => handleChange('children', e.target.value === '' ? 0 : parseInt(e.target.value, 10)),
                    hint: t('userManagement.optionalField', 'optional'),
                })}
                {renderColorPicker()}
            </FormSection>

            <FormSection
                title={t('userManagement.section.contact', 'Kontakt & Adresse')}
                description={t('userManagement.section.contactHint', 'Wird fuer Benachrichtigungen und Unterlagen verwendet.')}
            >
                {renderInputField({
                    id: 'email',
                    translationKey: 'userManagement.email',
                    defaultText: 'E-Mail',
                    type: 'email',
                    value: userData.email,
                    onChange: (e) => handleChange('email', e.target.value),
                    required: true,
                    className: 'form-field-wide',
                })}
                {renderInputField({
                    id: 'mobilePhone',
                    translationKey: 'userManagement.mobilePhone',
                    defaultText: 'Handynummer',
                    type: 'tel',
                    value: userData.mobilePhone,
                    onChange: (e) => handleChange('mobilePhone', e.target.value),
                    required: true,
                })}
                {renderInputField({
                    id: 'landlinePhone',
                    translationKey: 'userManagement.landlinePhone',
                    defaultText: 'Festnetz',
                    type: 'tel',
                    value: userData.landlinePhone,
                    onChange: (e) => handleChange('landlinePhone', e.target.value),
                    hint: t('userManagement.optionalField', 'optional'),
                })}
                {renderInputField({
                    id: 'address',
                    translationKey: 'userManagement.address',
                    defaultText: 'Adresse',
                    value: userData.address,
                    onChange: (e) => handleChange('address', e.target.value),
                    hint: t('userManagement.optionalField', 'optional'),
                    className: 'form-field-wide',
                })}
            </FormSection>
        </>
    );

    const renderAccessFields = ({ includeLogin = true } = {}) => (
        <>
            <FormSection
                title={t('userManagement.section.access', 'Konto & Zugriff')}
                description={t('userManagement.section.accessHint', 'Steuert Login-Daten, Rolle und Sichtbarkeit in Zeitübersichten.')}
            >
                {includeLogin ? renderInputField({
                    id: 'username',
                    translationKey: 'userManagement.username',
                    defaultText: 'Benutzername',
                    value: userData.username,
                    onChange: (e) => handleChange('username', e.target.value),
                    required: true,
                    disabled: isEditing,
                }) : null}
                {renderRoleSelect()}
                <div className="readonly-status">
                    <span>Status</span>
                    <strong>{isActive ? 'Aktiv' : 'Inaktiv'}</strong>
                </div>
                <div className="readonly-status">
                    <span>Login erlaubt</span>
                    <strong>{isActive ? 'Ja' : 'Nein'}</strong>
                </div>
                {Array.isArray(userData.roles) && (
                    userData.roles.includes('ROLE_ADMIN')
                    || userData.roles.includes('ROLE_SUPERADMIN')
                    || userData.roles.includes('ROLE_PAYROLL_ADMIN')
                ) ? (
                    <div className="time-tracking-toggle-group form-field-wide">
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
                ) : null}
            </FormSection>
        </>
    );

    const renderPermissionFields = () => (
        <FormSection
            title={t('userManagement.section.pageAccess', 'Rechte')}
            description={
                selectedRole === 'ROLE_SUPERADMIN'
                    ? t('userManagement.section.pageAccessSuperadminHint', 'Superadmins behalten immer vollen Zugriff auf alle Bereiche.')
                    : `Rolle = Vorlage. Rechte sind einzelne Abweichungen davon. Aktuell ${customPermissionCount} Abweichung(en).`
            }
            actions={selectedRole !== 'ROLE_SUPERADMIN' ? (
                <button type="button" className="button-secondary button-small" onClick={resetPermissionsToRole}>
                    Rollenrechte übernehmen
                </button>
            ) : null}
        >
            {selectedRole === 'ROLE_SUPERADMIN' ? (
                <div className="permission-empty full-width">
                    {t('userManagement.superadminFullAccess', 'Für Superadmins werden alle Seiten und Aktionen automatisch freigegeben.')}
                </div>
            ) : (
                <div className="permissions-workspace full-width">
                    <div className="permission-toolbar">
                        <input
                            type="search"
                            value={permissionSearch}
                            onChange={(e) => setPermissionSearch(e.target.value)}
                            placeholder="Modul suchen"
                            aria-label="Rechte nach Modul suchen"
                        />
                        <label className="compact-check">
                            <input
                                type="checkbox"
                                checked={showOnlyCustomPermissions}
                                onChange={(e) => setShowOnlyCustomPermissions(e.target.checked)}
                            />
                            Nur Abweichungen
                        </label>
                        <span className="soft-status-pill">{customPermissionCount} Abweichungen</span>
                    </div>

                    <div className="permission-list">
                        {filteredPermissionSections.length === 0 ? (
                            <div className="permission-empty">Keine Rechte für diese Auswahl.</div>
                        ) : filteredPermissionSections.map((section) => (
                            <div key={section.group} className="permission-section">
                                <h5>{section.group}</h5>
                                {section.pages.map((page) => {
                                    const currentAccess = normalizeAccessLevel(userData.pagePermissions?.[page.key]);
                                    const defaultAccess = normalizeAccessLevel(roleDefaultPermissions?.[page.key]);
                                    const isCustom = currentAccess !== defaultAccess;
                                    const choices = getAccessChoicesForPage(page.key);

                                    return (
                                        <div key={page.key} className={`permission-item ${isCustom ? 'customized' : ''}`}>
                                            <div>
                                                <strong>{page.label}</strong>
                                                <span>{page.description}</span>
                                            </div>
                                            <select
                                                id={`permission-${page.key}`}
                                                value={currentAccess}
                                                onChange={(event) => handlePermissionChange(page.key, event.target.value)}
                                                aria-label={`${page.label} Zugriff`}
                                            >
                                                {choices.map((choice) => (
                                                    <option key={choice} value={choice}>
                                                        {getAccessLabel(choice)}
                                                    </option>
                                                ))}
                                            </select>
                                        </div>
                                    );
                                })}
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </FormSection>
    );

    const renderEmploymentFields = () => (
        <>
            <FormSection
                title={t('userManagement.section.employment', 'Beschäftigung')}
                description={t('userManagement.section.employmentHint', 'Organisatorische Daten und landesspezifische Angaben fuer die Lohnabrechnung.')}
            >
                {renderInputField({
                    id: 'personnelNumber',
                    translationKey: 'userManagement.personnelNumber',
                    defaultText: 'Personalnummer',
                    value: userData.personnelNumber,
                    onChange: (e) => handleChange('personnelNumber', e.target.value),
                    pattern: '[0-9]{1,10}',
                    required: true,
                    hint: t('userManagement.personnelNumberHint', 'laut Lohnsystem'),
                })}
                {renderInputField({
                    id: 'entryDate',
                    translationKey: 'userManagement.entryDate',
                    defaultText: 'Eintrittsdatum',
                    type: 'date',
                    pattern: '\\d{4}-\\d{2}-\\d{2}',
                    value: userData.entryDate,
                    onChange: (e) => handleChange('entryDate', e.target.value),
                })}
                {renderInputField({
                    id: 'department',
                    translationKey: 'userManagement.department',
                    defaultText: 'Abteilung',
                    value: userData.department,
                    onChange: (e) => handleChange('department', e.target.value),
                    hint: t('userManagement.optionalField', 'optional'),
                })}
                {isEditing ? renderInputField({
                    id: 'employmentModelEffectiveFrom',
                    translationKey: 'userManagement.employmentModelEffectiveFrom',
                    defaultText: 'Änderung gültig ab',
                    type: 'date',
                    pattern: '\\d{4}-\\d{2}-\\d{2}',
                    value: userData.employmentModelEffectiveFrom,
                    onChange: (e) => handleChange('employmentModelEffectiveFrom', e.target.value),
                    hint: t('userManagement.employmentModelEffectiveFromHint', 'bei Modell- oder Sollstundenwechsel'),
                }) : null}
                {renderSelectField({
                    id: 'country',
                    translationKey: 'userManagement.country',
                    defaultText: 'Land',
                    value: userData.country || 'DE',
                    onChange: (e) => handleChange('country', e.target.value),
                    required: true,
                    hint: t('userManagement.countryHint', 'steuert landesspezifische Felder'),
                    children: (
                        <>
                            <option value="DE">Deutschland</option>
                            <option value="CH">Schweiz</option>
                        </>
                    ),
                })}
            </FormSection>
        </>
    );

    const renderPayrollFields = () => (
        <>
            {renderEmploymentFields()}
            <FormSection
                title="Lohnmodell"
                description="Monatslohn, Stundenlohn oder prozentuale Zeiterfassung festlegen."
            >
                <div className="toggle-row full-width">
                    {renderCheckboxField({
                        id: 'isHourly',
                        label: t('userManagement.isHourly', 'Stundenbasiert abrechnen'),
                        checked: !!userData.isHourly,
                        onChange: (e) => handleCheckboxChange('isHourly', e.target.checked),
                        description: 'Stundenlohn mit flexibler Erfassung.',
                    })}
                    {renderCheckboxField({
                        id: 'isPercentage',
                        label: t('userManagement.percentageTracking', 'Prozentbasierte Zeiterfassung'),
                        checked: !!userData.isPercentage,
                        disabled: !!userData.isHourly,
                        onChange: (e) => handleCheckboxChange('isPercentage', e.target.checked),
                        description: 'Pensum statt fixer Wochenplan.',
                    })}
                </div>

                {userData.isHourly ? renderInputField({
                    id: 'hourlyWage',
                    translationKey: 'userManagement.hourlyWage',
                    defaultText: 'Stundenlohn (Brutto)',
                    type: 'number',
                    step: '0.01',
                    min: '0',
                    value: userData.hourlyWage,
                    onChange: (e) => handleChange('hourlyWage', e.target.value ? parseFloat(e.target.value) : null),
                    placeholder: 'z.B. 25.00',
                    required: true,
                }) : renderInputField({
                    id: 'monthlySalary',
                    translationKey: 'userManagement.monthlySalary',
                    defaultText: 'Monatslohn (Brutto)',
                    type: 'number',
                    step: '0.01',
                    min: '0',
                    value: userData.monthlySalary,
                    onChange: (e) => handleChange('monthlySalary', e.target.value ? parseFloat(e.target.value) : null),
                    placeholder: 'z.B. 4500.00',
                    required: true,
                })}

                {userData.isPercentage && !userData.isHourly ? renderInputField({
                    id: 'workPercentage',
                    translationKey: 'userManagement.workPercentage',
                    defaultText: 'Arbeitspensum (%)',
                    type: 'number',
                    min: '1',
                    max: '100',
                    step: '1',
                    value: userData.workPercentage,
                    onChange: (e) => handleChange('workPercentage', e.target.value ? parseInt(e.target.value, 10) : null),
                    placeholder: '1-100',
                }) : null}
            </FormSection>

            <FormSection
                title="Bank & Versicherung"
                description="Zahlungs- und Versicherungsdaten fuer die Abrechnung."
            >
                {renderInputField({
                    id: 'bankAccount',
                    translationKey: 'userManagement.bankAccount',
                    defaultText: 'Bankverbindung',
                    value: userData.bankAccount,
                    onChange: (e) => handleChange('bankAccount', e.target.value),
                    hint: t('userManagement.bankAccountHint', 'IBAN fuer die Lohnzahlung'),
                    className: 'form-field-wide',
                })}
                {renderInputField({
                    id: 'healthInsurance',
                    translationKey: 'userManagement.healthInsurance',
                    defaultText: 'Krankenkasse',
                    value: userData.healthInsurance,
                    onChange: (e) => handleChange('healthInsurance', e.target.value),
                    hint: t('userManagement.healthInsuranceHint', 'optional'),
                    className: 'form-field-wide',
                })}
            </FormSection>

            {userData.country === 'DE' ? renderGermanyFields() : null}
            {userData.country === 'CH' ? renderSwitzerlandFields() : null}
        </>
    );

    const renderGermanyFields = () => (
        <FormSection title={t('userManagement.section.germany', 'Zusatzangaben fuer Deutschland')}>
            {renderInputField({
                id: 'taxClass',
                translationKey: 'userManagement.taxClass',
                defaultText: 'Steuerklasse',
                value: userData.taxClass,
                onChange: (e) => handleChange('taxClass', e.target.value),
                pattern: '[A-Za-z0-9]+',
                required: true,
                hint: t('userManagement.taxClassHint', 'laut ELStAM'),
            })}
            {renderInputField({
                id: 'socialSecurityNumber',
                translationKey: 'userManagement.socialSecurityNumber.de',
                defaultText: 'Sozialversicherungsnummer',
                value: userData.socialSecurityNumber,
                onChange: (e) => handleChange('socialSecurityNumber', e.target.value),
                hint: t('userManagement.socialSecurityNumberDeHint', 'optional'),
            })}
            {renderInputField({
                id: 'religion',
                translationKey: 'userManagement.religion.de',
                defaultText: 'Religion',
                value: userData.religion,
                onChange: (e) => handleChange('religion', e.target.value),
                hint: t('userManagement.religionDeHint', 'optional'),
            })}
            {renderSelectField({
                id: 'federalState',
                translationKey: 'userManagement.federalState',
                defaultText: 'Bundesland',
                value: userData.federalState,
                onChange: (e) => handleChange('federalState', e.target.value),
                hint: t('userManagement.optionalField', 'optional'),
                children: (
                    <>
                        <option value="">{t('userManagement.selectOption', 'Bitte wählen')}</option>
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
                    </>
                ),
            })}
            <div className="toggle-row full-width">
                {renderCheckboxField({
                    id: 'churchTax',
                    label: t('userManagement.churchTax', 'Kirchensteuerpflichtig'),
                    checked: !!userData.churchTax,
                    onChange: (e) => handleCheckboxChange('churchTax', e.target.checked),
                })}
            </div>
            {renderInputField({
                id: 'gkvAdditionalRate',
                translationKey: 'userManagement.gkvAdditionalRate',
                defaultText: 'GKV-Zusatzbeitrag',
                type: 'number',
                step: '0.0005',
                min: '0',
                max: '0.03',
                value: userData.gkvAdditionalRate,
                onChange: (e) => handleChange('gkvAdditionalRate', e.target.value ? parseFloat(e.target.value) : null),
                placeholder: 'z. B. 0.0125',
            })}
        </FormSection>
    );

    const renderSwitzerlandFields = () => (
        <FormSection title={t('userManagement.section.switzerland', 'Zusatzangaben fuer die Schweiz')}>
            {renderInputField({
                id: 'tarifCode',
                translationKey: 'userManagement.tarifCode',
                defaultText: 'Tarifcode',
                value: userData.tarifCode,
                onChange: (e) => handleChange('tarifCode', e.target.value),
                hint: t('userManagement.tarifCodeHint', 'Quellensteuer-Code'),
            })}
            {renderInputField({
                id: 'canton',
                translationKey: 'userManagement.canton',
                defaultText: 'Kanton',
                value: userData.canton,
                onChange: (e) => handleChange('canton', e.target.value),
                hint: t('userManagement.optionalField', 'optional'),
            })}
            {renderInputField({
                id: 'socialSecurityNumber',
                translationKey: 'userManagement.socialSecurityNumber.ch',
                defaultText: 'AHV-Nr.',
                value: userData.socialSecurityNumber,
                onChange: (e) => handleChange('socialSecurityNumber', e.target.value),
                hint: t('userManagement.socialSecurityNumberChHint', 'optional'),
            })}
            {renderInputField({
                id: 'religion',
                translationKey: 'userManagement.religion.ch',
                defaultText: 'Religion',
                value: userData.religion,
                onChange: (e) => handleChange('religion', e.target.value),
                hint: t('userManagement.religionChHint', 'optional'),
            })}
        </FormSection>
    );

    const renderWorkTimeFields = () => (
        <>
            <FormSection
                title={t('userManagement.section.generalSettings', 'Regeln')}
                description="Urlaub, Pausen und Solltage liegen direkt bei der Arbeitszeit."
            >
                {renderInputField({
                    id: 'annualVacationDays',
                    translationKey: 'userManagement.annualVacationDays',
                    defaultText: 'Urlaubstage/Jahr',
                    type: 'number',
                    step: '0.5',
                    min: '0',
                    value: userData.annualVacationDays === null || userData.annualVacationDays === undefined ? '' : userData.annualVacationDays,
                    onChange: (e) => handleChange('annualVacationDays', e.target.value ? parseFloat(e.target.value) : null),
                    placeholder: 'z.B. 25',
                })}
                {renderInputField({
                    id: 'breakDuration',
                    translationKey: 'userManagement.breakDuration',
                    defaultText: 'Standard Pausendauer (Min)',
                    type: 'number',
                    min: '0',
                    value: userData.breakDuration === null || userData.breakDuration === undefined ? '' : userData.breakDuration,
                    onChange: (e) => handleChange('breakDuration', e.target.value ? parseInt(e.target.value, 10) : null),
                    placeholder: 'z.B. 30',
                })}
                {!userData.isHourly ? renderInputField({
                    id: 'expectedWorkDays',
                    translationKey: 'userManagement.expectedWorkDays',
                    defaultText: 'Erwartete Arbeitstage/Woche',
                    type: 'number',
                    step: '0.5',
                    min: '0',
                    max: '7',
                    value: userData.expectedWorkDays === null || userData.expectedWorkDays === undefined ? '' : userData.expectedWorkDays,
                    onChange: (e) => handleChange('expectedWorkDays', e.target.value ? parseFloat(e.target.value) : null),
                    placeholder: 'z.B. 5',
                }) : null}
            </FormSection>

            <FormSection
                title={t('userManagement.scheduleConfig', 'Wochenplan & Sollzeiten')}
                description={userData.isHourly || userData.isPercentage ? 'Für dieses Modell wird kein fixer Wochenplan gepflegt.' : 'Tagesziele, Zyklus und Wochenplan kompakt bearbeiten.'}
            >
                {userData.isHourly || userData.isPercentage ? (
                    <div className="permission-empty full-width">
                        Arbeitsmodell: {workModelLabel}. Die Sollzeiten werden ueber das gewählte Modell berechnet.
                    </div>
                ) : (
                    <>
                        {renderInputField({
                            id: 'dailyWorkHours',
                            translationKey: 'userManagement.dailyWorkHours',
                            defaultText: 'Standard Tagessoll (Std)',
                            type: 'number',
                            step: '0.01',
                            min: '0',
                            value: userData.dailyWorkHours === null || userData.dailyWorkHours === undefined ? '' : userData.dailyWorkHours,
                            onChange: (e) => handleChange('dailyWorkHours', e.target.value ? parseFloat(e.target.value) : null),
                            placeholder: 'z.B. 8.5',
                        })}
                        {renderInputField({
                            id: 'scheduleEffectiveDate',
                            translationKey: 'userManagement.scheduleEffectiveDate',
                            defaultText: 'Plan gültig ab',
                            type: 'date',
                            value: userData.scheduleEffectiveDate || '',
                            onChange: (e) => handleChange('scheduleEffectiveDate', e.target.value),
                        })}
                        {renderInputField({
                            id: 'scheduleCycle',
                            translationKey: 'userManagement.cycleLength',
                            defaultText: 'Zykluslänge (Wochen)',
                            type: 'number',
                            min: '1',
                            value: userData.scheduleCycle || 1,
                            onChange: (e) => onScheduleCycleChange(Number(e.target.value)),
                        })}
                        <div className="work-week-grid full-width">
                            {(userData.weeklySchedule || []).map((week, weekIdx) => (
                                <div key={weekIdx} className="work-week-card">
                                    <h5>{t('userManagement.week', 'Woche')} {weekIdx + 1}</h5>
                                    {DAY_KEYS.map((dayKey) => {
                                        const hours = Number(week?.[dayKey] ?? 0);
                                        const barWidth = `${Math.min(100, Math.max(0, hours / 10 * 100))}%`;

                                        return (
                                            <div key={dayKey} className="week-day-row">
                                                <label htmlFor={`schedule-${weekIdx}-${dayKey}`}>
                                                    {t(`days.${dayKey}`, DAY_FALLBACKS[dayKey])}
                                                </label>
                                                <div className="day-hours-bar" aria-hidden="true">
                                                    <span style={{ width: barWidth }} />
                                                </div>
                                                <input
                                                    type="number"
                                                    id={`schedule-${weekIdx}-${dayKey}`}
                                                    min="0"
                                                    max="24"
                                                    step="0.01"
                                                    value={week[dayKey] !== null && week[dayKey] !== undefined ? week[dayKey] : ''}
                                                    placeholder="Std."
                                                    onChange={(e) => onWeeklyScheduleDayChange(weekIdx, dayKey, e.target.value)}
                                                />
                                            </div>
                                        );
                                    })}
                                </div>
                            ))}
                        </div>
                    </>
                )}
            </FormSection>
        </>
    );

    const renderPreview = () => (
        <div className="preview-panel">
            <div className="preview-main">
                <span className="avatar-preview" style={{ borderColor: userData.color || STANDARD_COLORS[0] }}>
                    {initials}
                </span>
                <div>
                    <h4>{fullName}</h4>
                    <p>{userData.email || 'Keine E-Mail'} · {selectedRoleLabel} · {userData.country || '-'}</p>
                </div>
            </div>
            <div className="preview-grid">
                <span><strong>Personalnummer</strong>{userData.personnelNumber || '-'}</span>
                <span><strong>Payroll</strong>{payrollStatus}</span>
                <span><strong>Arbeitsmodell</strong>{workModelLabel}</span>
                <span><strong>Rechte</strong>{customPermissionCount} Abweichungen</span>
            </div>
            {missingRequired.length > 0 ? (
                <div className="missing-panel">
                    <strong>Fehlende Pflichtangaben</strong>
                    <ul>
                        {missingRequired.map((item) => <li key={item.key}>{item.label}</li>)}
                    </ul>
                </div>
            ) : (
                <div className="ready-panel">Alle Pflichtangaben sind vorhanden.</div>
            )}
        </div>
    );

    const renderCreateStepContent = () => {
        switch (currentWizardStep.id) {
            case 'account':
                return (
                    <FormSection
                        title="Konto anlegen"
                        description="Erst die wichtigsten Angaben erfassen, danach Rechte und Payroll sauber ergänzen."
                    >
                        {renderAccountFields({ includePresetCards: true })}
                    </FormSection>
                );
            case 'access':
                return (
                    <>
                        {renderAccessFields({ includeLogin: false })}
                        {renderPermissionFields()}
                    </>
                );
            case 'profile':
                return renderProfileFields();
            case 'payroll':
                return renderPayrollFields();
            case 'worktime':
                return renderWorkTimeFields();
            case 'preview':
            default:
                return renderPreview();
        }
    };

    const renderEditTabContent = () => {
        switch (activeTab) {
            case 'access':
                return renderAccessFields();
            case 'permissions':
                return renderPermissionFields();
            case 'payroll':
                return renderPayrollFields();
            case 'worktime':
                return renderWorkTimeFields();
            case 'profile':
            default:
                return renderProfileFields();
        }
    };

    const renderSummarySidebar = () => (
        <aside className="user-summary-panel">
            <div className="summary-avatar" style={{ backgroundColor: userData.color || STANDARD_COLORS[0] }}>
                {initials}
            </div>
            <h4>{fullName}</h4>
            <p>{userData.email || userData.username || 'Noch keine Kontaktdaten'}</p>

            <dl>
                <div>
                    <dt>Rolle</dt>
                    <dd>{selectedRoleLabel}</dd>
                </div>
                <div>
                    <dt>Status</dt>
                    <dd>{isActive ? 'Aktiv' : 'Inaktiv'}</dd>
                </div>
                <div>
                    <dt>Personalnr.</dt>
                    <dd>{userData.personnelNumber || '-'}</dd>
                </div>
                <div>
                    <dt>Land</dt>
                    <dd>{userData.country || '-'}</dd>
                </div>
                <div>
                    <dt>Payroll</dt>
                    <dd>{payrollStatus}</dd>
                </div>
                <div>
                    <dt>Arbeitszeit</dt>
                    <dd>{workModelLabel}</dd>
                </div>
            </dl>

            <div className="completion-box">
                <div>
                    <span>Vollständigkeit</span>
                    <strong>{completionPercent}%</strong>
                </div>
                <div className="completion-track">
                    <span style={{ width: `${completionPercent}%` }} />
                </div>
            </div>

            <div className="missing-summary">
                <strong>{missingRequired.length > 0 ? 'Fehlende Angaben' : 'Pflichtangaben komplett'}</strong>
                {missingRequired.length > 0 ? (
                    <ul>
                        {missingRequired.slice(0, 4).map((item) => <li key={item.key}>{item.label}</li>)}
                    </ul>
                ) : (
                    <p>Bereit zum Speichern.</p>
                )}
            </div>
        </aside>
    );

    return (
        <section className={`user-form modern-user-form ${isEditing ? 'edit-mode' : 'create-mode'}`}>
            <form onSubmit={handleFormSubmit}>
                <div className="user-profile-header">
                    <div className="profile-header-main">
                        <span className="profile-avatar" style={{ backgroundColor: userData.color || STANDARD_COLORS[0] }}>
                            {initials}
                        </span>
                        <div>
                            <p className="eyebrow">{isEditing ? 'Benutzer bearbeiten' : 'Neuen Benutzer erstellen'}</p>
                            <h3>{isEditing ? fullName : 'Benutzerkonto einrichten'}</h3>
                            <p>
                                {isEditing
                                    ? `${userData.email || 'Keine E-Mail'} · Personalnummer ${userData.personnelNumber || '-'} · Rolle: ${selectedRoleLabel}`
                                    : 'Konto, Rolle, Stammdaten, Payroll und Arbeitszeit geführt erfassen.'}
                            </p>
                        </div>
                    </div>
                    <div className="profile-header-actions">
                        {isEditing ? (
                            <>
                                <button type="button" className="button-secondary" onClick={() => setActiveTab('access')}>
                                    Rolle ändern
                                </button>
                                <button type="button" className="button-secondary" onClick={() => setActiveTab('payroll')}>
                                    Arbeitsmodell
                                </button>
                                <button type="submit" className="button-primary" disabled={missingRequired.length > 0}>
                                    Speichern
                                </button>
                            </>
                        ) : (
                            <span className="wizard-progress-label">
                                Schritt {wizardStepIndex + 1} von {wizardSteps.length}
                            </span>
                        )}
                        <button type="button" onClick={onCancel} className="button-secondary">
                            Abbrechen
                        </button>
                    </div>
                </div>

                {!isEditing ? (
                    <div className="create-wizard-shell">
                        <div className="wizard-steps" aria-label="Erstellungsschritte">
                            {wizardSteps.map((step, index) => (
                                <button
                                    key={step.id}
                                    type="button"
                                    className={`${index === wizardStepIndex ? 'active' : ''} ${index < wizardStepIndex ? 'done' : ''}`}
                                    onClick={() => setWizardStepIndex(index)}
                                >
                                    <span>{index + 1}</span>
                                    {step.label}
                                </button>
                            ))}
                        </div>
                        <div className="wizard-content">
                            {renderCreateStepContent()}
                        </div>
                    </div>
                ) : (
                    <div className="user-edit-shell">
                        {renderSummarySidebar()}
                        <div className="user-detail-panel">
                            <div className="user-tabs" role="tablist" aria-label="Benutzerbereiche">
                                {editTabs.map((tab) => (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        role="tab"
                                        aria-selected={activeTab === tab.id}
                                        className={activeTab === tab.id ? 'active' : ''}
                                        onClick={() => setActiveTab(tab.id)}
                                    >
                                        {tab.label}
                                    </button>
                                ))}
                            </div>
                            <div className="tab-content">
                                {renderEditTabContent()}
                            </div>
                        </div>
                    </div>
                )}

                {(submitAttempted && missingRequired.length > 0) ? (
                    <div className="missing-panel sticky-warning">
                        <strong>Vor dem Speichern fehlen noch:</strong>
                        <span>{missingRequired.map((item) => item.label).join(', ')}</span>
                    </div>
                ) : null}

                <div className="sticky-save-bar">
                    <div>
                        {isEditing ? (
                            <>
                                <strong>
                                    {changedFieldSummaries.length > 0
                                        ? `${changedFieldSummaries.length} ungespeicherte Änderungen`
                                        : 'Keine ungespeicherten Änderungen'}
                                </strong>
                                {changedFieldSummaries.length > 0 ? (
                                    <span>{changedFieldSummaries.slice(0, 3).map((item) => item.label).join(', ')}</span>
                                ) : (
                                    <span>Pflichtangaben: {missingRequired.length === 0 ? 'vollständig' : `${missingRequired.length} offen`}</span>
                                )}
                            </>
                        ) : (
                            <>
                                <strong>{isLastWizardStep ? 'Benutzer wird vorbereitet' : currentWizardStep.label}</strong>
                                <span>{missingRequired.length === 0 ? 'Alle Pflichtangaben vorhanden' : `${missingRequired.length} Pflichtangaben offen`}</span>
                            </>
                        )}
                    </div>
                    <div className="sticky-actions">
                        {!isEditing && wizardStepIndex > 0 ? (
                            <button type="button" className="button-secondary" onClick={goToPreviousWizardStep}>
                                Zurück
                            </button>
                        ) : null}
                        <button type="button" onClick={onCancel} className="button-secondary">
                            Abbrechen
                        </button>
                        {!isEditing && !isLastWizardStep ? (
                            <button type="button" className="button-primary" onClick={goToNextWizardStep}>
                                Weiter
                            </button>
                        ) : (
                            <button type="submit" className="button-primary" disabled={missingRequired.length > 0}>
                                {isEditing
                                    ? t('userManagement.button.saveChanges', 'Änderungen speichern')
                                    : t('userManagement.button.createUser', 'Benutzer erstellen')}
                            </button>
                        )}
                    </div>
                </div>
            </form>
        </section>
    );
};

AdminUserForm.propTypes = {
    t: PropTypes.func.isRequired,
    isEditing: PropTypes.bool.isRequired,
    userData: PropTypes.object.isRequired,
    originalUser: PropTypes.object,
    setUserData: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    onScheduleCycleChange: PropTypes.func.isRequired,
    onWeeklyScheduleDayChange: PropTypes.func.isRequired,
};

AdminUserForm.defaultProps = {
    originalUser: null,
};

export default AdminUserForm;
