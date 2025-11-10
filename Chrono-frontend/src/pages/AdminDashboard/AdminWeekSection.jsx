// src/pages/AdminDashboard/AdminWeekSection.jsx
import React, {
    useState,
    useMemo,
    useEffect,
    useRef,
    useCallback,
    forwardRef,
    useImperativeHandle,
} from "react";
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";
import api from "../../utils/api"; // Assuming api is configured
import { useAuth } from "../../context/AuthContext"; // Assuming useAuth is available
import { useNotification } from "../../context/NotificationContext"; // Assuming useNotification is available

import {
    formatLocalDateYMD,
    formatDate, // Your existing formatDate
    formatTime, // <--- HIER HINZUFÜGEN
    getExpectedHoursForDay,
    computeDailyDiff,
    minutesToHHMM,
    calculateWeeklyActualMinutes,
    calculateWeeklyExpectedMinutes,
    // isLateTime, // isLateTime might be used internally by formatTimeEntryForDisplay or similar
    getDetailedGlobalProblemIndicators,
    getMondayOfWeek,
    addDays,
    selectTrackableUsers,
} from "./adminDashboardUtils"; // Ensure this path is correct
import {parseISO} from "date-fns"; // Make sure date-fns is installed
import { sortEntries } from '../../utils/timeUtils';

const HOLIDAY_OPTIONS_LOCAL_STORAGE_KEY = 'adminDashboard_holidayOptions_v1';
const HIDDEN_USERS_LOCAL_STORAGE_PREFIX = 'adminDashboard_hiddenUsers_v3';
const MONTH_RANGE_SETTINGS_LOCAL_STORAGE_PREFIX = 'adminDashboard_monthRangeSettings_v1';

const DEFAULT_MONTH_RANGE_SETTINGS = {
    mode: 'calendar',
    customStartDay: 1,
    manualStart: '',
    manualEnd: '',
};

const isBrowserEnvironment = () => typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';

const areSetsEqual = (setA, setB) => {
    if (setA === setB) return true;
    if (!setA || !setB || setA.size !== setB.size) return false;
    for (const value of setA) {
        if (!setB.has(value)) return false;
    }
    return true;
};

const parseHiddenUsersFromStorage = (rawValue) => {
    if (!rawValue) return new Set();
    try {
        const parsed = JSON.parse(rawValue);
        if (Array.isArray(parsed)) return new Set(parsed);
        return new Set();
    } catch (error) {
        console.error('Error parsing hidden users from localStorage:', error);
        return new Set();
    }
};

const normalizeHolidayOptionsList = (optionsList) => {
    if (!Array.isArray(optionsList)) return [];
    return optionsList
        .map(option => {
            if (!option) return null;
            try {
                const normalizedDate = typeof option.holidayDate === 'string'
                    ? option.holidayDate
                    : formatLocalDateYMD(new Date(option.holidayDate));
                if (!normalizedDate) return null;
                return { ...option, holidayDate: normalizedDate };
            } catch (error) {
                console.error('Error normalizing holiday option entry:', error, option);
                return null;
            }
        })
        .filter(Boolean);
};

const getIsoDatesForWeek = (mondayDate) => {
    if (!(mondayDate instanceof Date) || Number.isNaN(mondayDate.getTime())) return [];
    const mondayClone = new Date(mondayDate.getTime());
    mondayClone.setHours(0, 0, 0, 0);
    const isoDates = [];
    for (let i = 0; i < 7; i += 1) {
        const day = new Date(mondayClone.getTime());
        day.setDate(mondayClone.getDate() + i);
        isoDates.push(formatLocalDateYMD(day));
    }
    return isoDates;
};

const areHolidayOptionRecordsEqual = (optionA, optionB) => {
    if (optionA === optionB) return true;
    if (!optionA || !optionB) return false;
    const keys = new Set([...Object.keys(optionA), ...Object.keys(optionB)]);
    for (const key of keys) {
        if (optionA[key] !== optionB[key]) return false;
    }
    return true;
};

const parseHolidayOptionsFromStorage = (rawValue) => {
    if (!rawValue) return {};
    try {
        const parsed = JSON.parse(rawValue);
        if (!parsed || typeof parsed !== 'object') return {};
        return Object.entries(parsed).reduce((acc, [username, dateMap]) => {
            if (dateMap && typeof dateMap === 'object') {
                acc[username] = Object.entries(dateMap).reduce((userAcc, [dateKey, option]) => {
                    if (option && typeof option === 'object' && typeof option.holidayDate === 'string') {
                        userAcc[dateKey] = option;
                    }
                    return userAcc;
                }, {});
            }
            return acc;
        }, {});
    } catch (error) {
        console.error('Error parsing stored holiday options from localStorage:', error);
        return {};
    }
};


const clampMonthStartDay = (value) => {
    if (value === null || value === undefined) return 1;
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return 1;
    const wholeNumber = Math.trunc(numeric);
    if (Number.isNaN(wholeNumber) || wholeNumber < 1) return 1;
    if (wholeNumber > 31) return 31;
    return wholeNumber;
};

const getDaysInMonth = (year, monthIndex) => {
    return new Date(year, monthIndex + 1, 0).getDate();
};

const parseMonthRangeSettingsFromStorage = (rawValue) => {
    const defaults = { ...DEFAULT_MONTH_RANGE_SETTINGS };
    if (!rawValue) return defaults;
    try {
        const parsed = JSON.parse(rawValue);
        const mode = ['calendar', 'customCycle', 'manual'].includes(parsed?.mode)
            ? parsed.mode
            : defaults.mode;
        const customStartDay = clampMonthStartDay(parsed?.customStartDay);
        const manualStart = typeof parsed?.manualStart === 'string' ? parsed.manualStart : defaults.manualStart;
        const manualEnd = typeof parsed?.manualEnd === 'string' ? parsed.manualEnd : defaults.manualEnd;
        return {
            mode,
            customStartDay,
            manualStart,
            manualEnd,
        };
    } catch (error) {
        console.error('Error parsing month range settings from localStorage:', error);
        return defaults;
    }
};

const getCustomCycleStartDate = (referenceDate, startDay) => {
    if (!(referenceDate instanceof Date) || Number.isNaN(referenceDate.getTime())) {
        return null;
    }
    const normalizedDay = clampMonthStartDay(startDay);
    let year = referenceDate.getFullYear();
    let monthIndex = referenceDate.getMonth();
    const dayOfMonth = referenceDate.getDate();

    if (dayOfMonth < normalizedDay) {
        monthIndex -= 1;
        if (monthIndex < 0) {
            monthIndex = 11;
            year -= 1;
        }
    }

    const daysInMonth = getDaysInMonth(year, monthIndex);
    const clampedDay = Math.min(normalizedDay, daysInMonth);
    const startDate = new Date(year, monthIndex, clampedDay);
    startDate.setHours(0, 0, 0, 0);
    return startDate;
};

const getCustomCycleRangeForDate = (referenceDate, startDay) => {
    const startDate = getCustomCycleStartDate(referenceDate, startDay);
    if (!startDate) {
        return { startIso: '', endIso: '' };
    }
    const nextCycleBase = addDays(startDate, 40);
    const nextCycleStart = getCustomCycleStartDate(nextCycleBase, startDay);
    if (!nextCycleStart) {
        return { startIso: '', endIso: '' };
    }
    const endDate = addDays(nextCycleStart, -1);
    endDate.setHours(0, 0, 0, 0);
    return {
        startIso: formatLocalDateYMD(startDate),
        endIso: formatLocalDateYMD(endDate),
    };
};

const computeMonthRangeBoundaries = (referenceDate, mode, customStartDay, manualStart, manualEnd) => {
    if (mode === 'manual') {
        return {
            startIso: manualStart || '',
            endIso: manualEnd || '',
        };
    }

    if (mode === 'customCycle') {
        return getCustomCycleRangeForDate(referenceDate, customStartDay);
    }

    return {
        startIso: getMonthRangeStartIso(referenceDate),
        endIso: getMonthRangeEndIso(referenceDate),
    };
};

const getMonthRangeStartIso = (referenceDate) => {
    if (!(referenceDate instanceof Date) || Number.isNaN(referenceDate.getTime())) {
        return '';
    }
    const start = new Date(referenceDate.getTime());
    start.setDate(1);
    start.setHours(0, 0, 0, 0);
    return formatLocalDateYMD(start);
};

const getMonthRangeEndIso = (referenceDate) => {
    if (!(referenceDate instanceof Date) || Number.isNaN(referenceDate.getTime())) {
        return '';
    }
    const end = new Date(referenceDate.getTime());
    end.setMonth(end.getMonth() + 1);
    end.setDate(0);
    end.setHours(0, 0, 0, 0);
    return formatLocalDateYMD(end);
};

const EMPTY_PROBLEM_INDICATORS = {
    missingEntriesCount: 0,
    incompleteDaysCount: 0,
    autoCompletedUncorrectedCount: 0,
    holidayPendingCount: 0,
    problematicDays: [],
};

const sortUserCollection = (collection, sortConfig) => {
    const items = Array.isArray(collection) ? [...collection] : [];
    if (!sortConfig?.key) {
        return items;
    }

    items.sort((a, b) => {
        if (!a || !b) return 0;
        let valA = a[sortConfig.key];
        let valB = b[sortConfig.key];

        if (sortConfig.key === 'problemIndicators') {
            const totalA = (a.problemIndicators?.missingEntriesCount || 0)
                + (a.problemIndicators?.incompleteDaysCount || 0)
                + (a.problemIndicators?.autoCompletedUncorrectedCount || 0)
                + (a.problemIndicators?.holidayPendingCount || 0);
            const totalB = (b.problemIndicators?.missingEntriesCount || 0)
                + (b.problemIndicators?.incompleteDaysCount || 0)
                + (b.problemIndicators?.autoCompletedUncorrectedCount || 0)
                + (b.problemIndicators?.holidayPendingCount || 0);
            valA = totalA;
            valB = totalB;
        } else if (typeof valA === 'string' && typeof valB === 'string') {
            valA = valA.toLowerCase();
            valB = valB.toLowerCase();
        }

        if (valA < valB) return sortConfig.direction === 'ascending' ? -1 : 1;
        if (valA > valB) return sortConfig.direction === 'ascending' ? 1 : -1;
        return 0;
    });

    return items;
};


const ISSUE_FILTER_KEYS = {
    missing: 'missingEntriesCount',
    incomplete: 'incompleteDaysCount',
    autoCompleted: 'autoCompletedUncorrectedCount',
    holidayPending: 'holidayPendingCount',
};

const DEFAULT_ISSUE_FILTER_STATE = {
    missing: true,
    incomplete: true,
    autoCompleted: true,
    holidayPending: true,
};


const AdminWeekSection = forwardRef(({
                                         t,
                                         weekDates,
                                         selectedMonday,
                                         handlePrevWeek,
                                         handleNextWeek,
                                         handleWeekJump,
                                         handleCurrentWeek,
                                         onFocusProblemWeek,
                                         dailySummariesForWeekSection, // This will be DailyTimeSummaryDTO[]
                                         allVacations,
                                         allSickLeaves,
                                         allHolidays, // This is now holidaysByCanton
                                         users,
                                         defaultExpectedHours,
                                         openEditModal, // This will pass the list of entries now
                                         openPrintUserModal,
                                         rawUserTrackingBalances, // Consider renaming if structure changed
                                         openNewEntryModal, // For creating entries for a day from scratch
                                         onDataReloadNeeded,
                                         onIssueSummaryChange,
                                         showSmartOverview = true,
                                         onQuickFixQueueChange = () => {},
                                     }, ref) => {
    const { notify } = useNotification();
    const { currentUser } = useAuth();

    const browserHasStorage = isBrowserEnvironment();
    const hiddenUsersStorageKey = useMemo(
        () => `${HIDDEN_USERS_LOCAL_STORAGE_PREFIX}_${currentUser?.username || 'anonymous'}`,
        [currentUser?.username]
    );

    const [searchTerm, setSearchTerm] = useState("");
    const [detailedUser, setDetailedUser] = useState(null); // Username of the user whose details are expanded
    const [sortConfig, setSortConfig] = useState({ key: 'username', direction: 'ascending' });
    const [focusedProblem, setFocusedProblem] = useState({ username: null, dateIso: null, type: null });
    const [showOnlyIssues, setShowOnlyIssues] = useState(false);
    const [issueTypeFilters, setIssueTypeFilters] = useState(() => ({ ...DEFAULT_ISSUE_FILTER_STATE }));

    const readHiddenUsersFromStorage = useCallback(() => {
        if (!browserHasStorage) return new Set();
        return parseHiddenUsersFromStorage(window.localStorage.getItem(hiddenUsersStorageKey));
    }, [browserHasStorage, hiddenUsersStorageKey]);

    const [hiddenUsers, setHiddenUsers] = useState(() => readHiddenUsersFromStorage());
    const [showHiddenUsersManager, setShowHiddenUsersManager] = useState(false);
    const detailSectionRef = useRef(null); // For scrolling to problem
    const sectionRef = useRef(null);

    // State for holiday options for the currently detailed percentage user
    const [currentUserHolidayOptions, setCurrentUserHolidayOptions] = useState([]);
    const [holidayOptionsByUser, setHolidayOptionsByUser] = useState(() => {
        if (!browserHasStorage) return {};
        return parseHolidayOptionsFromStorage(window.localStorage.getItem(HOLIDAY_OPTIONS_LOCAL_STORAGE_KEY));
    });

    const monthRangeSettingsStorageKey = `${MONTH_RANGE_SETTINGS_LOCAL_STORAGE_PREFIX}_${currentUser?.username || 'anonymous'}`;

    const [activeTab, setActiveTab] = useState('week');
    const [monthRangeMode, setMonthRangeMode] = useState(DEFAULT_MONTH_RANGE_SETTINGS.mode);
    const [customMonthStartDay, setCustomMonthStartDay] = useState(DEFAULT_MONTH_RANGE_SETTINGS.customStartDay);
    const [manualMonthRangeStart, setManualMonthRangeStart] = useState(DEFAULT_MONTH_RANGE_SETTINGS.manualStart);
    const [manualMonthRangeEnd, setManualMonthRangeEnd] = useState(DEFAULT_MONTH_RANGE_SETTINGS.manualEnd);
    const [monthSortConfig, setMonthSortConfig] = useState({ key: 'username', direction: 'ascending' });

    const {
        trackableUsers,
        reactivatedOptOutUsernames,
        allUsersOptedOut,
    } = useMemo(

        () => selectTrackableUsers(users),
        [users]
    );

    useEffect(() => {
        if (!browserHasStorage) return;
        try {
            const stored = window.localStorage.getItem(monthRangeSettingsStorageKey);
            if (!stored) return;
            const parsed = parseMonthRangeSettingsFromStorage(stored);
            setMonthRangeMode(parsed.mode);
            setCustomMonthStartDay(parsed.customStartDay);
            setManualMonthRangeStart(parsed.manualStart);
            setManualMonthRangeEnd(parsed.manualEnd);
        } catch (error) {
            console.error('Error restoring month range settings:', error);
        }
    }, [browserHasStorage, monthRangeSettingsStorageKey]);

    useEffect(() => {
        if (!browserHasStorage) return;
        try {
            const payload = JSON.stringify({
                mode: monthRangeMode,
                customStartDay: customMonthStartDay,
                manualStart: manualMonthRangeStart,
                manualEnd: manualMonthRangeEnd,
            });
            window.localStorage.setItem(monthRangeSettingsStorageKey, payload);
        } catch (error) {
            console.error('Error saving month range settings to localStorage:', error);
        }
    }, [browserHasStorage, monthRangeSettingsStorageKey, monthRangeMode, customMonthStartDay, manualMonthRangeStart, manualMonthRangeEnd]);


    // Fetch holiday options when a percentage user's details are expanded for the current week
    const fetchHolidayOptionsForUser = useCallback(async (username, mondayDate) => {
        const userConf = trackableUsers.find(u => u.username === username);
        if (userConf && userConf.isPercentage) {
            try {
                const response = await api.get('/api/admin/user-holiday-options/week', {
                    params: { username: username, mondayInWeek: formatLocalDateYMD(mondayDate) }
                });
                const normalizedOptions = normalizeHolidayOptionsList(response.data);
                const weekDatesToReset = getIsoDatesForWeek(mondayDate);

                setHolidayOptionsByUser(prev => {
                    const prevForUser = prev[username] ? { ...prev[username] } : {};
                    const updatedForUser = { ...prevForUser };
                    let userChanged = false;
                    const optionDateSet = new Set(normalizedOptions.map(opt => opt.holidayDate).filter(Boolean));

                    weekDatesToReset.forEach(dateKey => {
                        if (!optionDateSet.has(dateKey) && updatedForUser[dateKey]) {
                            delete updatedForUser[dateKey];
                            userChanged = true;
                        }
                    });

                    normalizedOptions.forEach(option => {
                        if (!option?.holidayDate) return;
                        if (!areHolidayOptionRecordsEqual(updatedForUser[option.holidayDate], option)) {
                            updatedForUser[option.holidayDate] = option;
                            userChanged = true;
                        }
                    });

                    if (!userChanged) return prev;

                    const next = { ...prev };
                    if (Object.keys(updatedForUser).length > 0) {
                        next[username] = updatedForUser;
                    } else {
                        delete next[username];
                    }
                    return next;
                });

            } catch (error) {
                console.error("Error fetching holiday options for user's week:", error);
                setCurrentUserHolidayOptions([]); // Reset on error
            }
        } else {
            setCurrentUserHolidayOptions([]); // Not a percentage user or no user
        }
    }, [trackableUsers]);

    useEffect(() => {
        if (detailedUser) {
            fetchHolidayOptionsForUser(detailedUser, selectedMonday);
        } else {
            setCurrentUserHolidayOptions([]); // Clear when no user details are open
        }
    }, [detailedUser, selectedMonday, fetchHolidayOptionsForUser]);

    useEffect(() => {
        if (!detailedUser) return;
        const storedOptionsForUser = holidayOptionsByUser[detailedUser];
        const sortedOptions = storedOptionsForUser
            ? Object.values(storedOptionsForUser).sort((a, b) => a.holidayDate.localeCompare(b.holidayDate))
            : [];
        setCurrentUserHolidayOptions(sortedOptions);
    }, [detailedUser, holidayOptionsByUser]);

    useEffect(() => {
        if (!browserHasStorage) return;
        const storedHiddenUsers = readHiddenUsersFromStorage();
        setHiddenUsers(prev => (areSetsEqual(prev, storedHiddenUsers) ? prev : storedHiddenUsers));
    }, [browserHasStorage, readHiddenUsersFromStorage]);

    useEffect(() => {
        if (!browserHasStorage) return;
        try {
            window.localStorage.setItem(hiddenUsersStorageKey, JSON.stringify(Array.from(hiddenUsers)));
        } catch (error) {
            console.error('Error persisting hidden users to localStorage:', error);
        }
    }, [browserHasStorage, hiddenUsersStorageKey, hiddenUsers]);

    useEffect(() => {
        if (!browserHasStorage) return;
        try {
            window.localStorage.setItem(HOLIDAY_OPTIONS_LOCAL_STORAGE_KEY, JSON.stringify(holidayOptionsByUser));
        } catch (error) {
            console.error('Error persisting holiday options to localStorage:', error);
        }
    }, [browserHasStorage, holidayOptionsByUser]);

    useEffect(() => {
        if (!selectedMonday || trackableUsers.length === 0) return;
        const percentageUsers = trackableUsers.filter(user => user?.isPercentage);
        if (percentageUsers.length === 0) return;

        const isoDatesForWeek = getIsoDatesForWeek(selectedMonday);
        if (isoDatesForWeek.length === 0) return;

        let cancelled = false;

        const preloadHolidayOptions = async () => {
            const requests = percentageUsers.map(user =>
                api.get('/api/admin/user-holiday-options/week', {
                    params: { username: user.username, mondayInWeek: formatLocalDateYMD(selectedMonday) }
                })
                    .then(response => ({ username: user.username, options: normalizeHolidayOptionsList(response.data) }))
                    .catch(error => {
                        console.error(`Error preloading holiday options for ${user.username}:`, error);
                        return { username: user.username, options: null };
                    })
            );

            const results = await Promise.all(requests);
            if (cancelled) return;

            setHolidayOptionsByUser(prev => {
                let hasChanges = false;
                const next = { ...prev };

                results.forEach(({ username, options }) => {
                    if (!username || options === null) return;

                    const prevForUser = prev[username] ? { ...prev[username] } : {};
                    const updatedForUser = { ...prevForUser };
                    let userChanged = false;
                    const optionDateSet = new Set(options.map(opt => opt.holidayDate).filter(Boolean));

                    isoDatesForWeek.forEach(dateKey => {
                        if (!optionDateSet.has(dateKey) && updatedForUser[dateKey]) {
                            delete updatedForUser[dateKey];
                            userChanged = true;
                        }
                    });

                    options.forEach(option => {
                        if (!option?.holidayDate) return;
                        if (!areHolidayOptionRecordsEqual(updatedForUser[option.holidayDate], option)) {
                            updatedForUser[option.holidayDate] = option;
                            userChanged = true;
                        }
                    });

                    if (userChanged) {
                        if (Object.keys(updatedForUser).length > 0) {
                            next[username] = updatedForUser;
                        } else {
                            delete next[username];
                        }
                        hasChanges = true;
                    }
                });

                return hasChanges ? next : prev;
            });
        };

        preloadHolidayOptions();

        return () => {
            cancelled = true;
        };
    }, [selectedMonday, trackableUsers]);

    const resolvedMonthRange = useMemo(
        () => computeMonthRangeBoundaries(
            selectedMonday,
            monthRangeMode,
            customMonthStartDay,
            manualMonthRangeStart,
            manualMonthRangeEnd,
        ),
        [selectedMonday, monthRangeMode, customMonthStartDay, manualMonthRangeStart, manualMonthRangeEnd]
    );

    const monthRangeStart = resolvedMonthRange.startIso;
    const monthRangeEnd = resolvedMonthRange.endIso;
    const monthRangeIsManual = monthRangeMode === 'manual';
    const monthRangeIsCustomCycle = monthRangeMode === 'customCycle';
    const hasCustomMonthSettings = monthRangeMode !== DEFAULT_MONTH_RANGE_SETTINGS.mode
        || customMonthStartDay !== DEFAULT_MONTH_RANGE_SETTINGS.customStartDay
        || manualMonthRangeStart !== DEFAULT_MONTH_RANGE_SETTINGS.manualStart
        || manualMonthRangeEnd !== DEFAULT_MONTH_RANGE_SETTINGS.manualEnd;

    const monthRangeDates = useMemo(() => {
        if (!monthRangeStart || !monthRangeEnd) return [];
        if (monthRangeStart > monthRangeEnd) return [];
        try {
            const startDate = parseISO(monthRangeStart);
            const endDate = parseISO(monthRangeEnd);
            if (!startDate || !endDate || Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
                return [];
            }
            startDate.setHours(0, 0, 0, 0);
            endDate.setHours(0, 0, 0, 0);
            if (startDate.getTime() > endDate.getTime()) return [];
            const dates = [];
            for (let cursor = new Date(startDate.getTime()); cursor.getTime() <= endDate.getTime(); cursor.setDate(cursor.getDate() + 1)) {
                dates.push(new Date(cursor.getTime()));
            }
            return dates;
        } catch (error) {
            console.error('Error constructing month range dates:', error);
            return [];
        }
    }, [monthRangeStart, monthRangeEnd]);

    const monthRangeIsValid = monthRangeDates.length > 0;
    const monthRangeLabel = useMemo(() => {
        if (!monthRangeStart || !monthRangeEnd) return '';
        return `${formatDate(monthRangeStart)} – ${formatDate(monthRangeEnd)}`;
    }, [monthRangeStart, monthRangeEnd]);

    const userAnalytics = useMemo(() => {
        const currentWeekIsoDates = weekDates.map(date => formatLocalDateYMD(date));
        const currentWeekIsoSet = new Set(currentWeekIsoDates);

        // dailySummariesForWeekSection is now a list of DailyTimeSummaryDTO
        return trackableUsers
            .map((user) => {
                const userConfig = user; // UserDTO from backend
                // Get all DailyTimeSummaryDTOs for this user (can be for multiple days/weeks if AdminDashboard fetches more)
                const allUserSummariesList = dailySummariesForWeekSection.filter(s => s.username === user.username);

                // Create a map of summaries for the current display week for easy lookup
                const userDayMapCurrentWeek = {};
                weekDates.forEach(date => {
                    const isoDate = formatLocalDateYMD(date);
                    const summaryForDay = allUserSummariesList.find(s => s.date === isoDate);
                    userDayMapCurrentWeek[isoDate] = summaryForDay ||
                        { date: isoDate, username: user.username, entries: [], workedMinutes: 0, breakMinutes: 0, needsCorrection: false, primaryTimes: {isOpen: false, firstStartTime: null, lastEndTime: null}, dailyNote: null };
                });

                const userApprovedVacations = allVacations.filter(vac => vac.username === user.username && vac.approved);
                const userCurrentSickLeaves = allSickLeaves.filter(sl => sl.username === user.username);

                // Get holidays for the user's canton, or general if not specified
                const userCantonKey = userConfig.companyCantonAbbreviation || 'GENERAL';
                const holidaysForThisUserYearObj = allHolidays[userCantonKey] || allHolidays['GENERAL']; // allHolidays is now holidaysByCanton
                const holidaysForThisUserYear = holidaysForThisUserYearObj?.data || {}; // Ensure data field is accessed

                const weeklyActualMinutes = calculateWeeklyActualMinutes(Object.values(userDayMapCurrentWeek));

                const storedHolidayOptions = holidayOptionsByUser[user.username] || {};
                const allHolidayOptionsForUser = Object.values(storedHolidayOptions);
                const holidayOptionsForThisUserInThisWeek = allHolidayOptionsForUser
                    .filter(opt => opt?.holidayDate && currentWeekIsoSet.has(opt.holidayDate))
                    .sort((a, b) => a.holidayDate.localeCompare(b.holidayDate));


                const weeklyExpectedMinutes = calculateWeeklyExpectedMinutes(
                    userConfig, weekDates, defaultExpectedHours,
                    userApprovedVacations, userCurrentSickLeaves, holidaysForThisUserYear,
                    holidayOptionsForThisUserInThisWeek // Pass options here
                );

                const currentWeekOvertimeMinutes = weeklyActualMinutes - weeklyExpectedMinutes;
                // Use rawUserTrackingBalances or fetch/calculate cumulativeBalance differently if needed
                const cumulativeBalanceRecord = rawUserTrackingBalances.find(b => b.username === user.username);
                const cumulativeBalanceMinutes = cumulativeBalanceRecord ? cumulativeBalanceRecord.trackingBalance : (userConfig.trackingBalanceInMinutes ?? 0);


                // Use all summaries of the user for global problem indicators
                const problemIndicators = getDetailedGlobalProblemIndicators(
                    allUserSummariesList, // Pass all summaries for this user
                    userApprovedVacations, userConfig, defaultExpectedHours,
                    userCurrentSickLeaves, holidaysForThisUserYear,
                    allHolidayOptionsForUser // Pass relevant holiday options collected so far
                );

                return {
                    username: user.username,
                    userColor: /^#[0-9A-F]{6}$/i.test(userConfig.color || "") ? userConfig.color : "#007BFF", // Default color
                    weeklyActualMinutes,
                    weeklyExpectedMinutes,
                    currentWeekOvertimeMinutes,
                    cumulativeBalanceMinutes,
                    problemIndicators,
                    userConfig, // Pass the full UserDTO
                    userDayMap: userDayMapCurrentWeek, // Summaries for the currently displayed week
                    userApprovedVacations,
                    userCurrentSickLeaves, // Pass sick leaves for display
                    holidayOptions: allHolidayOptionsForUser,
                };
            });
    }, [trackableUsers, dailySummariesForWeekSection, allVacations, allSickLeaves, allHolidays, weekDates, defaultExpectedHours, rawUserTrackingBalances, holidayOptionsByUser]);

    const userAnalyticsMap = useMemo(() => {
        const map = new Map();
        userAnalytics.forEach(entry => {
            if (entry?.username) {
                map.set(entry.username, entry);
            }
        });
        return map;
    }, [userAnalytics]);

    const monthlyUserAnalytics = useMemo(() => {
        if (!Array.isArray(trackableUsers) || trackableUsers.length === 0) return [];
        if (!monthRangeIsValid) return [];

        return trackableUsers.map(user => {
            const baseData = userAnalyticsMap.get(user.username) || null;
            const userConfig = baseData?.userConfig || user;
            const allUserSummariesList = dailySummariesForWeekSection.filter(summary => summary.username === user.username);
            const monthSummaries = allUserSummariesList.filter(summary => summary.date >= monthRangeStart && summary.date <= monthRangeEnd);
            const monthlyActualMinutes = monthSummaries.reduce((acc, summary) => acc + (summary?.workedMinutes || 0), 0);

            const userApprovedVacations = allVacations.filter(vac => vac.username === user.username && vac.approved);
            const userCurrentSickLeaves = allSickLeaves.filter(sl => sl.username === user.username);
            const userCantonKey = userConfig?.companyCantonAbbreviation || 'GENERAL';
            const holidaysForThisUserYear = allHolidays[userCantonKey]?.data || allHolidays['GENERAL']?.data || {};
            const storedHolidayOptions = holidayOptionsByUser[user.username] || {};
            const allHolidayOptionsForUser = Object.values(storedHolidayOptions);

            const monthlyExpectedMinutes = monthRangeDates.reduce((acc, dateObj) => {
                const isoDate = formatLocalDateYMD(dateObj);
                const holidayOptionForDay = allHolidayOptionsForUser.find(opt => opt?.holidayDate === isoDate);
                const expectedHours = getExpectedHoursForDay(
                    dateObj,
                    userConfig,
                    defaultExpectedHours,
                    holidaysForThisUserYear,
                    userApprovedVacations,
                    userCurrentSickLeaves,
                    holidayOptionForDay
                );
                return acc + Math.round((expectedHours || 0) * 60);
            }, 0);

            const cumulativeBalanceMinutes = baseData?.cumulativeBalanceMinutes ?? (user.trackingBalanceInMinutes ?? 0);
            const problemIndicators = baseData?.problemIndicators || EMPTY_PROBLEM_INDICATORS;
            const userColor = baseData?.userColor || (/^#[0-9A-F]{6}$/i.test(user.color || "") ? user.color : "#007BFF");

            return {
                ...(baseData || {
                    username: user.username,
                    userConfig,
                    weeklyActualMinutes: 0,
                    weeklyExpectedMinutes: 0,
                    currentWeekOvertimeMinutes: 0,
                }),
                username: user.username,
                userColor,
                userApprovedVacations,
                userCurrentSickLeaves,
                cumulativeBalanceMinutes,
                problemIndicators,
                monthlyActualMinutes,
                monthlyExpectedMinutes,
                monthlyOvertimeMinutes: monthlyActualMinutes - monthlyExpectedMinutes,
            };
        });
    }, [trackableUsers, userAnalyticsMap, monthRangeIsValid, dailySummariesForWeekSection, monthRangeStart, monthRangeEnd, allVacations, allSickLeaves, allHolidays, holidayOptionsByUser, monthRangeDates, defaultExpectedHours]);


    const issueSummary = useMemo(() => {
        const summary = {
            missing: 0,
            incomplete: 0,
            autoCompleted: 0,
            holidayPending: 0,
            totalWithIssue: 0,
        };

        userAnalytics.forEach(userData => {
            const indicators = userData.problemIndicators || {};
            let hasAny = false;
            if ((indicators.missingEntriesCount || 0) > 0) {
                summary.missing += 1;
                hasAny = true;
            }
            if ((indicators.incompleteDaysCount || 0) > 0) {
                summary.incomplete += 1;
                hasAny = true;
            }
            if ((indicators.autoCompletedUncorrectedCount || 0) > 0) {
                summary.autoCompleted += 1;
                hasAny = true;
            }
            if ((indicators.holidayPendingCount || 0) > 0) {
                summary.holidayPending += 1;
                hasAny = true;
            }
            if (hasAny) {
                summary.totalWithIssue += 1;
            }
        });

        return summary;
    }, [userAnalytics]);

    const lastIssueSummaryRef = useRef(issueSummary);

    useEffect(() => {
        const previousSummary = lastIssueSummaryRef.current;
        const summaryKeys = ['missing', 'incomplete', 'autoCompleted', 'holidayPending', 'totalWithIssue'];
        const hasMeaningfulChanges = !previousSummary || summaryKeys.some((key) => {
            const previousValue = previousSummary?.[key] ?? 0;
            const nextValue = issueSummary?.[key] ?? 0;
            return previousValue !== nextValue;
        });

        if (hasMeaningfulChanges && typeof onIssueSummaryChange === 'function') {
            onIssueSummaryChange(issueSummary);
        }

        lastIssueSummaryRef.current = issueSummary;
    }, [issueSummary, onIssueSummaryChange]);

    const filterUserCollection = useCallback((collection) => {
        if (!Array.isArray(collection)) return [];
        const normalizedSearch = searchTerm.trim().toLowerCase();
        const activeFilters = Object.entries(issueTypeFilters)
            .filter(([, enabled]) => enabled)
            .map(([key]) => key);

        return collection.filter(userData => {
            if (!userData?.username) return false;
            if (normalizedSearch && !userData.username.toLowerCase().includes(normalizedSearch)) {
                return false;
            }
            if (hiddenUsers.has(userData.username)) {
                return false;
            }
            if (!showOnlyIssues) {
                return true;
            }

            const indicators = userData.problemIndicators || {};
            const hasAnyIssue = Object.values(ISSUE_FILTER_KEYS).some(key => (indicators[key] || 0) > 0);
            if (!hasAnyIssue) {
                return false;
            }

            if (activeFilters.length === 0) {
                return true;
            }

            return activeFilters.some(filterKey => {
                const indicatorKey = ISSUE_FILTER_KEYS[filterKey];
                return indicatorKey ? (indicators[indicatorKey] || 0) > 0 : false;
            });
        });
    }, [searchTerm, hiddenUsers, showOnlyIssues, issueTypeFilters]);

    const processedUserData = useMemo(
        () => filterUserCollection(userAnalytics),
        [userAnalytics, filterUserCollection]
    );

    const processedMonthlyUserData = useMemo(
        () => filterUserCollection(monthlyUserAnalytics),
        [monthlyUserAnalytics, filterUserCollection]
    );

    const sortedUserData = useMemo(
        () => sortUserCollection(processedUserData, sortConfig),
        [processedUserData, sortConfig]
    );

    const sortedMonthlyUserData = useMemo(
        () => sortUserCollection(processedMonthlyUserData, monthSortConfig),
        [processedMonthlyUserData, monthSortConfig]
    );

    const requestSort = (key) => {
        let direction = 'ascending';
        if (sortConfig.key === key && sortConfig.direction === 'ascending') {
            direction = 'descending';
        }
        setSortConfig({ key, direction });
    };

    const getSortIndicator = (key) => {
        if (sortConfig.key === key) {
            return sortConfig.direction === 'ascending' ? ' ▲' : ' ▼';
        }
        return '';
    };

    const requestMonthSort = (key) => {
        let direction = 'ascending';
        if (monthSortConfig.key === key && monthSortConfig.direction === 'ascending') {
            direction = 'descending';
        }
        setMonthSortConfig({ key, direction });
    };

    const getMonthSortIndicator = (key) => {
        if (monthSortConfig.key === key) {
            return monthSortConfig.direction === 'ascending' ? ' ▲' : ' ▼';
        }
        return '';
    };

    const handleMonthRangeModeSelect = useCallback((value) => {
        const nextMode = ['calendar', 'customCycle', 'manual'].includes(value) ? value : 'calendar';
        if (nextMode === 'manual') {
            setManualMonthRangeStart((prev) => prev || monthRangeStart || getMonthRangeStartIso(selectedMonday));
            setManualMonthRangeEnd((prev) => prev || monthRangeEnd || getMonthRangeEndIso(selectedMonday));
        }
        setMonthRangeMode(nextMode);
    }, [monthRangeEnd, monthRangeStart, selectedMonday]);

    const handleMonthRangeStartChange = (value) => {
        setMonthRangeMode('manual');
        setManualMonthRangeStart(value);
    };

    const handleMonthRangeEndChange = (value) => {
        setMonthRangeMode('manual');
        setManualMonthRangeEnd(value);
    };

    const handleCustomMonthStartDayChange = (value) => {
        const sanitized = clampMonthStartDay(value);
        setCustomMonthStartDay(sanitized);
    };

    const resetMonthRangeToDefault = useCallback(() => {
        setMonthRangeMode(DEFAULT_MONTH_RANGE_SETTINGS.mode);
        setCustomMonthStartDay(DEFAULT_MONTH_RANGE_SETTINGS.customStartDay);
        setManualMonthRangeStart(DEFAULT_MONTH_RANGE_SETTINGS.manualStart);
        setManualMonthRangeEnd(DEFAULT_MONTH_RANGE_SETTINGS.manualEnd);
    }, []);

    const renderProblemIndicatorsCell = (userData) => {
        const indicators = userData?.problemIndicators || EMPTY_PROBLEM_INDICATORS;
        const hasProblems = (indicators.missingEntriesCount || 0) > 0
            || (indicators.incompleteDaysCount || 0) > 0
            || (indicators.autoCompletedUncorrectedCount || 0) > 0
            || (indicators.holidayPendingCount || 0) > 0;

        if (!hasProblems) {
            return <span role="img" aria-label={t('adminDashboard.noIssues', 'Keine Probleme')} className="text-green-500">✅</span>;
        }

        return (
            <div className="flex gap-1 items-center justify-center">
                {indicators.missingEntriesCount > 0 && (
                    <span
                        title={`${indicators.missingEntriesCount} ${t('adminDashboard.problemTooltips.missingEntries', 'Tag(e) ohne Eintrag')}`}
                        onClick={() => handleProblemIndicatorClick(userData.username, "missing")}
                        className="problem-icon cursor-pointer"
                        role="button"
                        tabIndex={0}
                        onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "missing")}
                    >
                        ❗
                    </span>
                )}
                {indicators.incompleteDaysCount > 0 && (
                    <span
                        title={`${indicators.incompleteDaysCount} ${t('adminDashboard.problemTooltips.incompleteDays', 'Tag(e) unvollständig (z.B. fehlendes Ende)')}`}
                        onClick={() => handleProblemIndicatorClick(userData.username, "any_incomplete")}
                        className="problem-icon cursor-pointer"
                        role="button"
                        tabIndex={0}
                        onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "any_incomplete")}
                    >
                        ⚠️
                    </span>
                )}
                {indicators.autoCompletedUncorrectedCount > 0 && (
                    <span
                        title={`${indicators.autoCompletedUncorrectedCount}${t('adminDashboard.problemTooltips.autoCompletedDaysUncorrected', ' Tag(e) automatisch beendet & unkorrigiert')}`}
                        onClick={() => handleProblemIndicatorClick(userData.username, "auto_completed")}
                        className="problem-icon auto-completed-icon cursor-pointer"
                        role="button"
                        tabIndex={0}
                        onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "auto_completed")}
                    >
                        🤖
                    </span>
                )}
                {indicators.holidayPendingCount > 0 && (
                    <span
                        title={`${indicators.holidayPendingCount} ${t('adminDashboard.problemTooltips.holidayPending', 'Feiertagsoption(en) ausstehend')}`}
                        onClick={() => handleProblemIndicatorClick(userData.username, "holiday_pending_decision")}
                        className="problem-icon holiday-pending-icon cursor-pointer"
                        role="button"
                        tabIndex={0}
                        onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "holiday_pending_decision")}
                    >
                        🎉❓
                    </span>
                )}
            </div>
        );
    };

    const activeSortedData = activeTab === 'week' ? sortedUserData : sortedMonthlyUserData;

    const toggleIssueTypeFilter = (filterKey) => {
        setIssueTypeFilters(prev => ({
            ...prev,
            [filterKey]: !prev[filterKey],
        }));
    };

    const resetIssueTypeFilters = () => {
        setIssueTypeFilters({ ...DEFAULT_ISSUE_FILTER_STATE });
    };

    const issueFilterButtons = useMemo(() => ([
        {
            key: 'missing',
            label: t('adminDashboard.issueFilters.missing', 'Fehlende Stempel (keine Zeiten)'),

            count: issueSummary.missing,
            icon: '❗',
        },
        {
            key: 'incomplete',
            label: t('adminDashboard.issueFilters.incomplete', 'Unvollständige Tage (z.B. Ende fehlt)'),

            count: issueSummary.incomplete,
            icon: '⚠️',
        },
        {
            key: 'autoCompleted',
            label: t('adminDashboard.issueFilters.autoCompleted', 'Automatisch beendet (noch prüfen)'),

            count: issueSummary.autoCompleted,
            icon: '🤖',
        },
        {
            key: 'holidayPending',
            label: t('adminDashboard.issueFilters.holidayPending', 'Feiertag offen (Entscheid fehlt)'),

            count: issueSummary.holidayPending,
            icon: '🎉',
        },
    ]), [issueSummary, t]);

    const activeIssueFilterCount = Object.values(issueTypeFilters).filter(Boolean).length;

    const quickFixMeta = useMemo(() => ({
        missing: {
            label: t('adminDashboard.smartOverview.quickFix.labels.missing', 'Fehlende Stempel'),
            icon: '❗',
            filterKey: 'missing',
            problemType: 'missing',
            priority: 1,
        },
        incomplete: {
            label: t('adminDashboard.smartOverview.quickFix.labels.incomplete', 'Unvollständige Tage'),
            icon: '⚠️',
            filterKey: 'incomplete',
            problemType: 'any_incomplete',
            priority: 2,
        },
        autoCompleted: {
            label: t('adminDashboard.smartOverview.quickFix.labels.autoCompleted', 'Automatisch beendet'),
            icon: '🤖',
            filterKey: 'autoCompleted',
            problemType: 'auto_completed',
            priority: 3,
        },
        holidayPending: {
            label: t('adminDashboard.smartOverview.quickFix.labels.holidayPending', 'Feiertag offen'),
            icon: '🎉❓',
            filterKey: 'holidayPending',
            problemType: 'holiday_pending_decision',
            priority: 4,
        },
    }), [t]);

    const smartOverviewCards = useMemo(() => {
        const totalUsersCount = Array.isArray(trackableUsers) ? trackableUsers.length : 0;
        const stats = {
            activeUsers: 0,
            negativeBalanceUsers: 0,
            missingDays: 0,
            incompleteDays: 0,
            autoCompletedDays: 0,
        };

        userAnalytics.forEach(userData => {
            if (!userData) return;
            const expectedMinutes = userData.weeklyExpectedMinutes || 0;
            const actualMinutes = userData.weeklyActualMinutes || 0;
            if (expectedMinutes > 0 || actualMinutes > 0) {
                stats.activeUsers += 1;
            }
            if ((userData.cumulativeBalanceMinutes || 0) < 0) {
                stats.negativeBalanceUsers += 1;
            }
            const indicators = userData.problemIndicators || {};
            stats.missingDays += indicators.missingEntriesCount || 0;
            stats.incompleteDays += indicators.incompleteDaysCount || 0;
            stats.autoCompletedDays += indicators.autoCompletedUncorrectedCount || 0;
        });

        const totalCorrections = stats.missingDays + stats.incompleteDays + stats.autoCompletedDays;
        const activeRatio = totalUsersCount > 0
            ? `${stats.activeUsers}/${totalUsersCount}`
            : `${stats.activeUsers}`;

        return [
            {
                key: 'active',
                accent: 'accent-primary',
                title: t('adminDashboard.smartOverview.cards.active.title', 'Aktive Personen'),
                value: stats.activeUsers,
                description: `${activeRatio} ${t('adminDashboard.smartOverview.cards.active.subtitle', 'mit Sollzeit in dieser Woche')}`,
            },
            {
                key: 'issues',
                accent: 'accent-warning',
                title: t('adminDashboard.smartOverview.cards.issues.title', 'Problemfälle'),
                value: issueSummary.totalWithIssue,
                description: t('adminDashboard.smartOverview.cards.issues.subtitle', 'Benutzer mit Handlungsbedarf'),
            },
            {
                key: 'corrections',
                accent: 'accent-info',
                title: t('adminDashboard.smartOverview.cards.corrections.title', 'Offene Korrekturen'),
                value: totalCorrections,
                description: t('adminDashboard.smartOverview.cards.corrections.subtitle', 'Tage prüfen (fehlend/unklar)'),
            },
            {
                key: 'negative',
                accent: 'accent-danger',
                title: t('adminDashboard.smartOverview.cards.negative.title', 'Negative Salden'),
                value: stats.negativeBalanceUsers,
                description: t('adminDashboard.smartOverview.cards.negative.subtitle', 'Personen unter Soll'),
            },
        ];
    }, [trackableUsers, userAnalytics, issueSummary, t]);

    const quickIssueQueue = useMemo(() => {
        const queue = [];

        userAnalytics.forEach(userData => {
            if (!userData?.problemIndicators) return;
            const indicators = userData.problemIndicators;
            const priorityOrder = [
                { count: indicators.missingEntriesCount || 0, meta: quickFixMeta.missing },
                { count: indicators.incompleteDaysCount || 0, meta: quickFixMeta.incomplete },
                { count: indicators.autoCompletedUncorrectedCount || 0, meta: quickFixMeta.autoCompleted },
                { count: indicators.holidayPendingCount || 0, meta: quickFixMeta.holidayPending },
            ];

            const firstWithIssue = priorityOrder.find(item => item.count > 0 && item.meta);
            if (firstWithIssue) {
                queue.push({
                    username: userData.username,
                    count: firstWithIssue.count,
                    ...firstWithIssue.meta,
                });
            }
        });

        queue.sort((a, b) => {
            if (a.priority !== b.priority) return a.priority - b.priority;
            if (b.count !== a.count) return b.count - a.count;
            return a.username.localeCompare(b.username);
        });

        return queue.slice(0, 6);
    }, [userAnalytics, quickFixMeta]);

    useEffect(() => {
        if (typeof onQuickFixQueueChange === 'function') {
            onQuickFixQueueChange(quickIssueQueue);
        }
    }, [onQuickFixQueueChange, quickIssueQueue]);

    const scrollSectionIntoView = useCallback(() => {
        if (sectionRef.current) {
            sectionRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, []);

    const setFiltersExclusive = useCallback((filterKey) => {
        if (!filterKey || filterKey === 'all') {
            setIssueTypeFilters({ ...DEFAULT_ISSUE_FILTER_STATE });
            return;
        }

        if (Object.prototype.hasOwnProperty.call(DEFAULT_ISSUE_FILTER_STATE, filterKey)) {
            const nextState = { ...DEFAULT_ISSUE_FILTER_STATE };
            Object.keys(nextState).forEach(key => {
                nextState[key] = key === filterKey;
            });
            setIssueTypeFilters(nextState);
        }
    }, []);

    const toggleDetails = (username) => {
        const newDetailedUser = detailedUser === username ? null : username;
        setDetailedUser(newDetailedUser);
        if (!newDetailedUser) { // If closing details, reset focused problem
            setFocusedProblem({ username: null, dateIso: null, type: null });
        }
    };

    const handleProblemIndicatorClick = (username, problemType, dateIso = null) => {
        let targetDateIso = dateIso;
        // If no specific date is provided, find the first problematic day of that type for the user
        if (!targetDateIso) {
            const userProcData = processedUserData.find(ud => ud.username === username);
            if (userProcData?.problemIndicators?.problematicDays?.length > 0) {
                const problemDays = userProcData.problemIndicators.problematicDays;
                const firstProblemOfType = problemDays.find(p =>
                    p.type === problemType ||
                    (problemType === 'any_incomplete' && p.type.startsWith('incomplete_')) ||
                    (problemType === 'auto_completed' && (p.type === 'auto_completed_uncorrected' || p.type === 'auto_completed_incomplete_uncorrected')) ||
                    (problemType === 'any_problem' && !['holiday_pending_decision'].includes(p.type)) // Exclude pending holiday for generic 'any_problem'
                );
                if (firstProblemOfType) {
                    targetDateIso = firstProblemOfType.dateIso;
                } else if (problemType === 'holiday_pending_decision') {
                    // Specifically look for holiday_pending_decision if that was clicked
                    const firstHolidayPending = problemDays.find(p => p.type === 'holiday_pending_decision');
                    if (firstHolidayPending) targetDateIso = firstHolidayPending.dateIso;
                } else if (problemDays.length > 0) {
                    // Fallback to first problem if specific type not found but problems exist
                    targetDateIso = problemDays[0].dateIso;
                }
            }
        }

        if (targetDateIso) {
            const problemDate = parseISO(targetDateIso); // Use date-fns parseISO
            const problemWeekMonday = getMondayOfWeek(problemDate);

            // Check if the week needs to change
            if (problemWeekMonday.getTime() !== selectedMonday.getTime()) {
                if (onFocusProblemWeek) { // Call the prop function to change the week
                    onFocusProblemWeek(problemDate);
                }
            }
            // Set user and problem details
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: targetDateIso, type: problemType });
        } else {
            // If no specific problem day found, just open details for the user
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: null, type: problemType });
        }
    };

    useEffect(() => {
        let highlightTimeoutId = null;
        if (focusedProblem.username && focusedProblem.dateIso && detailSectionRef.current) {
            const elementId = `day-card-${focusedProblem.username}-${focusedProblem.dateIso}`;
            const elementToScrollTo = detailSectionRef.current.querySelector(`#${elementId}`);

            if (elementToScrollTo) {
                elementToScrollTo.scrollIntoView({ behavior: 'smooth', block: 'center' }); // 'center' might be better

                let highlightClass = 'highlight-problem-generic'; // Default or general problem
                if (focusedProblem.type === 'auto_completed_uncorrected' || focusedProblem.type === 'auto_completed_incomplete_uncorrected') {
                    highlightClass = 'highlight-autocompleted';
                } else if (focusedProblem.type === 'holiday_pending_decision') {
                    highlightClass = 'highlight-holiday-pending';
                } else if (focusedProblem.type === 'missing') {
                    highlightClass = 'highlight-missing-entry';
                } else if (focusedProblem.type.startsWith('incomplete_')) {
                    highlightClass = 'highlight-incomplete-day';
                }

                elementToScrollTo.classList.add(highlightClass);
                highlightTimeoutId = setTimeout(() => {
                    elementToScrollTo.classList.remove(highlightClass);
                }, 3000); // Highlight for 3 seconds
            }
        }
        return () => clearTimeout(highlightTimeoutId); // Cleanup timeout on unmount or if focusedProblem changes
    }, [focusedProblem]); // Re-run when focusedProblem changes


    const handleHideUser = (usernameToHide) => {
        setHiddenUsers(prev => new Set(prev).add(usernameToHide));
        if(detailedUser === usernameToHide) setDetailedUser(null); // Close details if hidden
    };
    const handleUnhideUser = (usernameToUnhide) => {
        setHiddenUsers(prev => {
            const next = new Set(prev);
            next.delete(usernameToUnhide);
            return next;
        });
    };
    const handleUnhideAllUsers = () => {
        setHiddenUsers(new Set());
        setShowHiddenUsersManager(false);
    };

    // State for sick leave deletion modal
    const [showDeleteSickLeaveModal, setShowDeleteSickLeaveModal] = useState(false);
    const [sickLeaveToDelete, setSickLeaveToDelete] = useState(null);

    const openDeleteSickLeaveConfirmationModal = (sickLeaveEntry) => {
        setSickLeaveToDelete(sickLeaveEntry);
        setShowDeleteSickLeaveModal(true);
    };

    const handleDeleteSickLeave = async () => {
        if (!sickLeaveToDelete || !currentUser?.username) {
            notify(t('errors.genericError', 'Ein Fehler ist aufgetreten.'), 'error');
            return;
        }
        try {
            // Assuming adminUsername is required for deletion by the backend
            await api.delete(`/api/sick-leave/${sickLeaveToDelete.id}`, {
                // params: { adminUsername: currentUser.username } // If backend needs it
            });
            notify(t('adminDashboard.sickLeaveDeleteSuccess', 'Krankmeldung erfolgreich gelöscht.'), 'success');
            setShowDeleteSickLeaveModal(false);
            setSickLeaveToDelete(null);
            if (onDataReloadNeeded) onDataReloadNeeded(); // Reload data
            // If detailedUser is affected, re-fetch their holiday options as sick leave impacts expected hours
            if (detailedUser === sickLeaveToDelete.username) {
                fetchHolidayOptionsForUser(detailedUser, selectedMonday);
            }
        } catch (err) {
            console.error("Error deleting sick leave:", err);
            notify(t('errors.deleteError', 'Fehler beim Löschen der Krankmeldung:') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleHolidayOptionChange = async (username, dateIso, newOptionValue) => {
        try {
            const response = await api.post('/api/admin/user-holiday-options', null, {
                params: { username, date: dateIso, option: newOptionValue }
            });
            notify(t('adminDashboard.holidayOptionUpdateSuccess', 'Feiertagsoption erfolgreich aktualisiert.'), 'success');
            const normalizedDateIso = typeof dateIso === 'string' ? dateIso : formatLocalDateYMD(new Date(dateIso));

            setHolidayOptionsByUser(prev => {
                const previousForUser = prev[username] ? { ...prev[username] } : {};
                const updatedOptionData = response?.data && typeof response.data === 'object'
                    ? {
                        ...response.data,
                        holidayDate: typeof response.data.holidayDate === 'string'
                            ? response.data.holidayDate
                            : normalizedDateIso,
                        holidayHandlingOption: newOptionValue,
                    }
                    : {
                        ...(previousForUser[normalizedDateIso] || {}),
                        holidayDate: normalizedDateIso,
                        holidayHandlingOption: newOptionValue,
                        username,
                    };
                previousForUser[normalizedDateIso] = updatedOptionData;
                return { ...prev, [username]: previousForUser };
            });
            if (detailedUser === username) {
                setCurrentUserHolidayOptions(prev => {
                    const updated = prev.some(opt => opt.holidayDate === normalizedDateIso)
                        ? prev.map(opt => opt.holidayDate === normalizedDateIso
                            ? { ...opt, holidayHandlingOption: newOptionValue }
                            : opt)
                        : [...prev, {
                            holidayDate: normalizedDateIso,
                            holidayHandlingOption: newOptionValue,
                            username,
                            ...(response?.data && typeof response.data === 'object' ? response.data : {}),
                        }];
                    return updated.sort((a, b) => a.holidayDate.localeCompare(b.holidayDate));
                });
            }
            // Re-fetch options for the current detailed user if it matches
            if (detailedUser === username) {
                fetchHolidayOptionsForUser(username, selectedMonday);
            }
            // Reload all data as this might affect balances
            if (onDataReloadNeeded) {
                onDataReloadNeeded();
            }
        } catch (error) {
            console.error("Error updating holiday option:", error);
            notify(t('errors.holidayOptionUpdateError', 'Fehler beim Aktualisieren der Feiertagsoption:') + (error.response?.data?.message || error.message), 'error');
        }
    };

    useImperativeHandle(ref, () => ({
        focusIssueType(filterKey) {
            setSearchTerm('');
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
            setShowOnlyIssues(true);
            setFiltersExclusive(filterKey);
            scrollSectionIntoView();
        },
        showOnlyIssuesView() {
            setSearchTerm('');
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
            setShowOnlyIssues(true);
            scrollSectionIntoView();
        },
        resetIssueFilters() {
            setSearchTerm('');
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
            setShowOnlyIssues(false);
            setIssueTypeFilters({ ...DEFAULT_ISSUE_FILTER_STATE });
            scrollSectionIntoView();
        },
        focusUser(username) {
            if (!username) return;
            setHiddenUsers(prev => {
                if (!prev.has(username)) return prev;
                const next = new Set(prev);
                next.delete(username);
                return next;
            });
            setShowOnlyIssues(false);
            setSearchTerm(username);
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: null, type: 'direct_focus' });
            scrollSectionIntoView();
        },
        focusNegativeBalances() {
            setSearchTerm('');
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
            setShowOnlyIssues(false);
            setSortConfig({ key: 'cumulativeBalanceMinutes', direction: 'ascending' });
            scrollSectionIntoView();
        },
        focusPositiveBalances() {
            setSearchTerm('');
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
            setShowOnlyIssues(false);
            setSortConfig({ key: 'cumulativeBalanceMinutes', direction: 'descending' });
            scrollSectionIntoView();
        },
    }), [scrollSectionIntoView, setFiltersExclusive]);


    return (
        <> {/* Added scoped-dashboard here */}
            <section ref={sectionRef} className="week-section content-section">
                <div className="section-header-controls">
                    <h3>
                        {activeTab === 'week'
                            ? t("adminDashboard.timeTrackingCurrentWeek", "Zeiterfassung Aktuelle Woche")
                            : t("adminDashboard.timeTrackingRangeTitle", "Zeiterfassung Zeitraum")}
                    </h3>
                    {activeTab === 'week' ? (
                        <div className="week-navigation">
                            <button onClick={handlePrevWeek} aria-label={t('adminDashboard.prevWeek', 'Vorige Woche')}>←</button>
                            <input
                                type="date"
                                value={formatLocalDateYMD(selectedMonday)}
                                onChange={handleWeekJump}
                                aria-label={t('adminDashboard.jumpToDate', 'Datum auswählen')}
                            />
                            <button onClick={handleNextWeek} aria-label={t('adminDashboard.nextWeek', 'Nächste Woche')}>→</button>
                            <button onClick={handleCurrentWeek} aria-label={t('adminDashboard.currentWeek', 'Aktuelle Woche')}>
                                {t('currentWeek', 'Aktuelle Woche')}
                            </button>
                        </div>
                    ) : (
                        <div className="month-range-controls">
                            <label className="month-range-field month-range-mode">
                                <span>{t('adminDashboard.monthView.modeLabel', 'Zeitraum-Modus')}</span>
                                <select
                                    value={monthRangeMode}
                                    onChange={(e) => handleMonthRangeModeSelect(e.target.value)}
                                    aria-label={t('adminDashboard.monthView.modeLabel', 'Zeitraum-Modus')}
                                >
                                    <option value="calendar">{t('adminDashboard.monthView.modeCalendar', 'Kalendermonat')}</option>
                                    <option value="customCycle">{t('adminDashboard.monthView.modeCustomCycle', 'Benutzerdefinierte Monatsgrenzen')}</option>
                                    <option value="manual">{t('adminDashboard.monthView.modeManual', 'Manuell wählen')}</option>
                                </select>
                            </label>
                            {monthRangeIsCustomCycle && (
                                <label className="month-range-field month-range-custom-day">
                                    <span>{t('adminDashboard.monthView.customStartLabel', 'Starttag im Monat')}</span>
                                    <input
                                        type="number"
                                        min="1"
                                        max="31"
                                        value={customMonthStartDay}
                                        onChange={(e) => handleCustomMonthStartDayChange(e.target.value)}
                                        aria-label={t('adminDashboard.monthView.customStartLabel', 'Starttag im Monat')}
                                    />
                                </label>
                            )}
                            <label className="month-range-field">
                                <span>{t('adminDashboard.monthView.startLabel', 'Startdatum')}</span>
                                <input
                                    type="date"
                                    value={monthRangeStart}
                                    onChange={(e) => handleMonthRangeStartChange(e.target.value)}
                                    aria-label={t('adminDashboard.monthView.startLabel', 'Startdatum')}
                                    disabled={!monthRangeIsManual}
                                />
                            </label>
                            <label className="month-range-field">
                                <span>{t('adminDashboard.monthView.endLabel', 'Enddatum')}</span>
                                <input
                                    type="date"
                                    value={monthRangeEnd}
                                    onChange={(e) => handleMonthRangeEndChange(e.target.value)}
                                    aria-label={t('adminDashboard.monthView.endLabel', 'Enddatum')}
                                    disabled={!monthRangeIsManual}
                                />
                            </label>
                            <button
                                type="button"
                                className="month-range-reset"
                                onClick={resetMonthRangeToDefault}
                                disabled={!hasCustomMonthSettings}
                            >
                                {t('adminDashboard.monthView.resetRange', 'Monatsgrenzen zurücksetzen')}
                            </button>
                        </div>
                    )}
                </div>


                {trackableUsers.length === 0 && allUsersOptedOut && (
                    <div className="mt-3 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700">
                        {t(
                            'adminDashboard.weekView.allOptedOutWarning',
                            'Alle bekannten Benutzer sind derzeit von den Zeitübersichten ausgeschlossen. Passe die Einstellung „In Zeiterfassung anzeigen“ in der Benutzerverwaltung an, um diese Ansicht wieder zu füllen.'
                        )}
                    </div>
                )}

                <div className="timeframe-tab-bar">
                    <button
                        type="button"
                        className={`timeframe-tab ${activeTab === 'week' ? 'active' : ''}`}
                        onClick={() => setActiveTab('week')}
                    >
                        {t('adminDashboard.weekTabLabel', 'Wochenansicht')}
                    </button>
                    <button
                        type="button"
                        className={`timeframe-tab ${activeTab === 'month' ? 'active' : ''}`}
                        onClick={() => setActiveTab('month')}
                    >
                        {t('adminDashboard.monthTabLabel', 'Monatsansicht')}
                    </button>
                </div>

                {activeTab === 'week' && showSmartOverview && (
                <div className="smart-week-overview" role="region" aria-label={t('adminDashboard.smartOverview.title', 'Wochenüberblick')}>
                    <div className="smart-overview-header">
                        <div>
                            <h4>{t('adminDashboard.smartOverview.title', 'Wochenüberblick')}</h4>
                            <p>{t('adminDashboard.smartOverview.subtitle', 'Direkter Blick auf wichtige Kennzahlen und offene Themen.')}</p>
                        </div>
                        <button
                            type="button"
                            className="smart-overview-issues-toggle"
                            onClick={() => {
                                scrollSectionIntoView();
                                setShowOnlyIssues(true);
                                setFiltersExclusive('all');
                            }}
                            disabled={issueSummary.totalWithIssue === 0}
                        >
                            {t('adminDashboard.smartOverview.showIssuesButton', 'Problemfälle anzeigen')}
                            {issueSummary.totalWithIssue > 0 && ` (${issueSummary.totalWithIssue})`}
                        </button>
                    </div>
                    <div className="smart-overview-layout">
                        <div className="smart-cards-grid">
                            {smartOverviewCards.map(card => (
                                <div key={card.key} className={`smart-overview-card ${card.accent}`}>
                                    <span className="card-title">{card.title}</span>
                                    <span className="card-value">{card.value}</span>
                                    <span className="card-description">{card.description}</span>
                                </div>
                            ))}
                        </div>
                        <div className="smart-quick-fix-panel">
                            <div className="smart-quick-fix-header">
                                <h5>{t('adminDashboard.smartOverview.quickFix.title', 'Schnellkorrekturen')}</h5>
                                <p>{t('adminDashboard.smartOverview.quickFix.subtitle', 'Spring direkt zu den wichtigsten Problemen.')}</p>
                            </div>
                            {quickIssueQueue.length === 0 ? (
                                <p className="smart-quick-fix-empty">{t('adminDashboard.smartOverview.quickFix.empty', 'Aktuell keine offenen Problemfälle – alles im grünen Bereich!')}</p>
                            ) : (
                                <ul className="smart-quick-fix-list">
                                    {quickIssueQueue.map(item => (
                                        <li key={`${item.username}-${item.filterKey}`} className="smart-quick-fix-list-item">
                                            <button
                                                type="button"
                                                className="smart-quick-fix-item"
                                                onClick={() => {
                                                    scrollSectionIntoView();
                                                    if (hiddenUsers.has(item.username)) {
                                                        handleUnhideUser(item.username);
                                                    }
                                                    setShowHiddenUsersManager(false);
                                                    setShowOnlyIssues(true);
                                                    setFiltersExclusive(item.filterKey);
                                                    handleProblemIndicatorClick(item.username, item.problemType);
                                                }}
                                            >
                                                <span className="smart-quick-fix-icon" aria-hidden="true">{item.icon}</span>
                                                <span className="smart-quick-fix-label">
                                                    <strong>{item.username}</strong>
                                                    <span>{`${item.count}× ${item.label}`}</span>
                                                </span>
                                                <span className="smart-quick-fix-cta">{t('adminDashboard.smartOverview.quickFix.action', 'Öffnen')}</span>
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                    </div>
                </div>
                )}

                {activeTab === 'month' && (
                    <div className="month-range-summary">
                        <p>
                            <strong>{t('adminDashboard.monthView.currentRangeTitle', 'Aktueller Zeitraum')}:</strong>{' '}
                            {monthRangeIsValid
                                ? monthRangeLabel
                                : t('adminDashboard.monthView.noRangeSelected', 'Bitte gültigen Zeitraum auswählen.')}
                        </p>
                        <p className="month-range-hint">
                            {monthRangeIsCustomCycle
                                ? t(
                                    'adminDashboard.monthView.customCycleHint',
                                    'Automatischer Zeitraum: ab dem {day}. eines Monats bis zum Vortag des Folgemonats.',
                                    { day: customMonthStartDay }
                                )
                                : monthRangeIsManual
                                    ? t(
                                        'adminDashboard.monthView.manualHint',
                                        'Manueller Zeitraum – Start und Ende bleiben unverändert, bis erneut angepasst.'
                                    )
                                    : t(
                                        'adminDashboard.monthView.adjustHint',
                                        'Standard: 1. bis letzter Tag des Monats. Wähle „Benutzerdefinierte Monatsgrenzen“ oder „Manuell“, um z. B. vom 26. bis 26. auszuwerten.'
                                    )}
                        </p>
                    </div>
                )}

                <div className="user-search-controls">
                    <div className="user-search-row">
                        <input
                            type="text"
                            placeholder={t("adminDashboard.searchUser", "Benutzer suchen...")}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="user-search-input"
                        />
                        {trackableUsers.length > 0 && ( // Only show if there are users to manage
                            <button
                                type="button"
                                onClick={() => setShowHiddenUsersManager(!showHiddenUsersManager)}
                                className="manage-hidden-users-button text-sm"
                                title={t('adminDashboard.manageHiddenUsersTooltip', 'Ausgeblendete Benutzer verwalten')}
                            >
                                {showHiddenUsersManager
                                    ? t('adminDashboard.hideHiddenUsersList', 'Liste verbergen')
                                    : t('adminDashboard.showHiddenUsersList', 'Ausgeblendete zeigen')}
                                ({hiddenUsers.size})

                            </button>
                        )}
                        <button
                            type="button"
                            className={`issue-filter-toggle ${showOnlyIssues ? 'active' : ''}`}
                            onClick={() => setShowOnlyIssues(prev => !prev)}
                            aria-pressed={showOnlyIssues}
                        >
                            {showOnlyIssues
                                ? t('adminDashboard.issueFilters.showAll', 'Alle anzeigen')
                                : t('adminDashboard.issueFilters.onlyIssues', 'Nur Problemfälle')}
                            {issueSummary.totalWithIssue > 0 && ` (${issueSummary.totalWithIssue})`}
                        </button>
                    </div>
                    {showOnlyIssues && (
                        <div
                            className="issue-type-filter-group"
                            role="group"
                            aria-label={t('adminDashboard.issueFilters.groupLabel', 'Problemtypen filtern')}
                        >
                            {issueFilterButtons.map(filter => (
                                <button
                                    type="button"
                                    key={filter.key}
                                    className={`issue-type-pill ${issueTypeFilters[filter.key] ? 'active' : ''}`}
                                    onClick={() => toggleIssueTypeFilter(filter.key)}
                                    aria-pressed={issueTypeFilters[filter.key]}
                                >
                                    <span className="pill-icon" aria-hidden="true">{filter.icon}</span>
                                    <span className="pill-label">{filter.label}</span>
                                    <span className="pill-count">{filter.count}</span>
                                </button>
                            ))}
                            <button
                                type="button"
                                className="issue-type-pill reset-pill"
                                onClick={resetIssueTypeFilters}
                                disabled={activeIssueFilterCount === Object.keys(DEFAULT_ISSUE_FILTER_STATE).length}
                            >
                                {t('adminDashboard.issueFilters.reset', 'Alle Typen')}
                            </button>
                        </div>
                    )}
                </div>

                {showHiddenUsersManager && (
                    <div className="hidden-users-manager card-style p-3 my-2 bg-gray-50 rounded shadow">
                        <h4 className="text-sm font-semibold mb-1">{t('adminDashboard.hiddenUsersTitle', 'Ausgeblendete Benutzer')}</h4>
                        {hiddenUsers.size === 0 ? (
                            <p className="text-xs italic">{t('adminDashboard.noHiddenUsers', 'Aktuell sind keine Benutzer ausgeblendet.')}</p>
                        ) : (
                            <>
                                <ul className="hidden-users-list list-disc list-inside ml-1 text-xs">
                                    {Array.from(hiddenUsers).sort().map(username => (
                                        <li key={username} className="flex justify-between items-center py-0.5">
                                            <span>{username}</span>
                                            <button onClick={() => handleUnhideUser(username)} className="action-button unhide-button text-xs p-0.5 bg-blue-100 hover:bg-blue-200 rounded">
                                                {t('adminDashboard.unhideUser', 'Einblenden')}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                                <button onClick={handleUnhideAllUsers} className="action-button unhide-all-button mt-2 text-xs p-1 bg-gray-200 hover:bg-gray-300 rounded">
                                    {t('adminDashboard.unhideAllUsers', 'Alle einblenden')}
                                </button>
                            </>
                        )}
                    </div>
                )}


                {activeTab === 'month' && !monthRangeIsValid ? (
                    <p className="no-data-message italic text-gray-600 p-4 text-center">
                        {t('adminDashboard.monthView.invalidRange', 'Bitte wählen Sie einen gültigen Zeitraum.')}
                    </p>
                ) : (activeSortedData.length === 0 && !showHiddenUsersManager ? (
                    <p className="no-data-message italic text-gray-600 p-4 text-center">
                        {activeTab === 'week'
                            ? (hiddenUsers.size > 0 && searchTerm.trim() === ""
                                ? t("adminDashboard.allVisibleUsersHiddenOrNoData", "Alle sichtbaren Benutzer sind ausgeblendet oder es sind keine Daten für die aktuelle Woche vorhanden.")
                                : (searchTerm.trim() === "" ? t("adminDashboard.noUserDataForWeek", "Keine Benutzerdaten für diese Woche.") : t("adminDashboard.noMatch", "Keine Benutzer entsprechen der Suche.")))
                            : (searchTerm.trim() === "" ? t('adminDashboard.monthView.noUserData', 'Keine Benutzerdaten für diesen Zeitraum.') : t('adminDashboard.noMatch', 'Keine Benutzer entsprechen der Suche.'))}
                    </p>
                ) : (
                    activeTab === 'week' ? (
                        <div className="table-responsive-wrapper">
                            <table className="admin-week-table">
                                <thead>
                                <tr>
                                    <th onClick={() => requestSort('username')} className="sortable-header th-user">
                                        {t('user', 'Benutzer')} {getSortIndicator('username')}
                                    </th>
                                    <th onClick={() => requestSort('weeklyActualMinutes')} className="sortable-header th-numeric">
                                        {t('actualHours', 'Ist (Wo)')} {getSortIndicator('weeklyActualMinutes')}
                                    </th>
                                    <th onClick={() => requestSort('weeklyExpectedMinutes')} className="sortable-header th-numeric">
                                        {t('expectedHours', 'Soll (Wo)')} {getSortIndicator('weeklyExpectedMinutes')}
                                    </th>
                                    <th onClick={() => requestSort('currentWeekOvertimeMinutes')} className="sortable-header th-numeric">
                                        {t('balanceWeek', 'Saldo (Wo)')} {getSortIndicator('currentWeekOvertimeMinutes')}
                                    </th>
                                    <th onClick={() => requestSort('cumulativeBalanceMinutes')} className="sortable-header th-numeric">
                                        {t('balanceTotal', 'Gesamtsaldo')} {getSortIndicator('cumulativeBalanceMinutes')}
                                    </th>
                                    <th onClick={() => requestSort('problemIndicators')} className="sortable-header th-center">
                                        {t('issues', 'Probleme')} {getSortIndicator('problemIndicators')}
                                    </th>
                                    <th className="th-actions">{t('actions', 'Aktionen')}</th>
                                </tr>
                                </thead>
                                <tbody>
                                {sortedUserData.map((userData) => (
                                    <React.Fragment key={userData.username}>
                                        <tr className={`user-row ${detailedUser === userData.username ? "user-row-detailed" : ""} ${hiddenUsers.has(userData.username) ? "user-row-hidden" : ""}`}>
                                            <td data-label={t('user', 'Benutzer')} className="td-user" style={{ borderLeft: `4px solid ${userData.userColor}` }}>{userData.username}</td>
                                            <td data-label={t('actualHours', 'Ist (Wo)')} className="td-numeric">{minutesToHHMM(userData.weeklyActualMinutes)}</td>
                                            <td data-label={t('expectedHours', 'Soll (Wo)')} className="td-numeric">{minutesToHHMM(userData.weeklyExpectedMinutes)}</td>
                                            <td data-label={t('balanceWeek', 'Saldo (Wo)')} className={`td-numeric ${userData.currentWeekOvertimeMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.currentWeekOvertimeMinutes)}</td>
                                            <td data-label={t('balanceTotal', 'Gesamtsaldo')} className={`td-numeric ${userData.cumulativeBalanceMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.cumulativeBalanceMinutes)}</td>
                                            <td data-label={t('issues', 'Probleme')} className="problem-indicators-cell td-center">
                                                {renderProblemIndicatorsCell(userData)}
                                            </td>
                                            <td data-label={t('actions', 'Aktionen')} className="actions-cell">
                                                <button onClick={() => toggleDetails(userData.username)} className="action-button details-toggle-button" title={detailedUser === userData.username ? t('hideDetails', 'Details Ausblenden') : t('showDetails', 'Details Anzeigen')} aria-expanded={detailedUser === userData.username}>
                                                    {detailedUser === userData.username ? '📂' : '📄'}
                                                </button>
                                                <button onClick={() => openPrintUserModal(userData.username)} className="action-button print-user-button" title={t('printButtonUser', 'Zeiten dieses Benutzers drucken')}>
                                                    🖨️
                                                </button>
                                                <button onClick={() => handleHideUser(userData.username)} className="action-button hide-user-row-button" title={t('hideUserInTable', 'Diesen Benutzer in der Tabelle ausblenden')}>
                                                    🙈
                                                </button>
                                            </td>
                                        </tr>
                                        {detailedUser === userData.username && (
                                            <tr className="user-detail-row">
                                                <td colSpan="7" ref={detailSectionRef}>
                                                    <div className="admin-week-display-detail p-2 bg-slate-50 rounded-b-md shadow-inner">
                                                        <div className="user-weekly-balance-detail text-xs mb-2 font-medium">
                                                            <span>{t('balanceTotal', 'Gesamtsaldo')}: {minutesToHHMM(userData.cumulativeBalanceMinutes)}</span>
                                                            <span className="mx-2">|</span>
                                                            <span>{t('balanceWeek', 'Saldo (akt. Woche)')}: {minutesToHHMM(userData.currentWeekOvertimeMinutes)}</span>
                                                        </div>
                                                        <div className="admin-days-grid">
                                                            {weekDates.map((d) => {
                                                                const isoDate = formatLocalDateYMD(d);
                                                                const dailySummary = userData.userDayMap[isoDate];
                                                                const userCantonKeyForDay = userData.userConfig.companyCantonAbbreviation || 'GENERAL';
                                                                const holidaysDataForDay = allHolidays[userCantonKeyForDay]?.data || allHolidays['GENERAL']?.data || {};
                                                                const holidayNameOnThisDay = holidaysDataForDay[isoDate];
                                                                const holidayOptionForThisDay = currentUserHolidayOptions.find(opt => opt.holidayDate === isoDate);

                                                                const expectedMinsToday = Math.round(getExpectedHoursForDay(d, userData.userConfig, defaultExpectedHours, holidaysDataForDay, userData.userApprovedVacations, userData.userCurrentSickLeaves, holidayOptionForThisDay) * 60);
                                                                const actualMinsToday = dailySummary?.workedMinutes || 0;
                                                                const diffMinsToday = actualMinsToday - expectedMinsToday;

                                                                const isFocused = focusedProblem.username === userData.username && focusedProblem.dateIso === isoDate;
                                                                let cardClass = `admin-day-card ${isFocused ? (focusedProblem.type.includes('auto_completed') ? 'highlight-autocompleted' : (focusedProblem.type === 'holiday_pending_decision' ? 'highlight-holiday-pending' : 'focused-problem')) : ''}`;
                                                                if (dailySummary?.needsCorrection && !isFocused) cardClass += ' auto-completed-day-card';

                                                                const vacationOnThisDay = userData.userApprovedVacations.find(vac => isoDate >= vac.startDate && isoDate <= vac.endDate);
                                                                const sickOnThisDay = userData.userCurrentSickLeaves.find(sick => isoDate >= sick.startDate && isoDate <= sick.endDate);

                                                                let dayCardContent;
                                                                if (holidayNameOnThisDay) {
                                                                    cardClass += ' admin-day-card-holiday';
                                                                    const currentHolidayHandling = holidayOptionForThisDay?.holidayHandlingOption || 'PENDING_DECISION';
                                                                    dayCardContent = (
                                                                        <>
                                                                            <p className="holiday-indicator text-xs">🎉 {t('holiday', 'Feiertag')}: {holidayNameOnThisDay}</p>
                                                                            {userData.userConfig.isPercentage && (
                                                                                <div className="holiday-handling-select mt-1">
                                                                                    <label htmlFor={`holiday-opt-${isoDate}-${userData.username}`} className="text-xs block mb-0.5">{t('adminDashboard.holidayOptionLabel', 'Option:')}</label>
                                                                                    <select
                                                                                        id={`holiday-opt-${isoDate}-${userData.username}`}
                                                                                        value={currentHolidayHandling}
                                                                                        onChange={(e) => handleHolidayOptionChange(userData.username, isoDate, e.target.value)}
                                                                                        className={`text-xs p-1 border rounded ${isFocused && focusedProblem.type === 'holiday_pending_decision' ? 'highlight-select' : ''}`}
                                                                                    >
                                                                                        <option value="PENDING_DECISION">{t('adminDashboard.holidayOption.pending', 'Ausstehend')}</option>
                                                                                        <option value="DEDUCT_FROM_WEEKLY_TARGET">{t('adminDashboard.holidayOption.deduct', 'Soll reduzieren')}</option>
                                                                                        <option value="DO_NOT_DEDUCT_FROM_WEEKLY_TARGET">{t('adminDashboard.holidayOption.doNotDeduct', 'Soll nicht reduzieren')}</option>
                                                                                    </select>
                                                                                </div>
                                                                            )}
                                                                        </>
                                                                    );
                                                                } else if (vacationOnThisDay) {
                                                                    cardClass += ' admin-day-card-vacation';
                                                                    if (vacationOnThisDay.companyVacation && dailySummary && dailySummary.entries && dailySummary.entries.length > 0) {
                                                                        dayCardContent = (
                                                                            <>
                                                                                <p className="vacation-indicator text-xs">🏖️ {t('adminDashboard.onVacation', 'Im Urlaub')}{vacationOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}{vacationOnThisDay.usesOvertime ? ` (${t('adminDashboard.overtimeVacationShort', 'ÜS')})` : ''}</p>
                                                                                <div className="admin-day-card-header justify-between items-start mb-1">
                                                                                    <div className="text-xs">
                                                                                        {!userData.userConfig.isHourly && <span className="expected-hours">({t('expectedTimeShort', 'Soll')}: {minutesToHHMM(expectedMinsToday)})</span>}
                                                                                        {!userData.userConfig.isHourly && <span className={`daily-diff ml-1 ${diffMinsToday < 0 ? 'text-red-600' : 'text-green-600'}`}>({t('diffTimeShort', 'Diff')}: {minutesToHHMM(diffMinsToday)})</span>}
                                                                                        {dailySummary.needsCorrection && <span className="auto-completed-tag ml-1 text-red-600 font-bold" title={t('adminDashboard.needsCorrectionTooltip', 'Automatisch beendet und unkorrigiert')}>KORR?</span>}
                                                                                    </div>
                                                                                    <button className="edit-day-button text-xs py-0.5 px-1 bg-gray-200 hover:bg-gray-300 rounded" onClick={() => openEditModal(userData.username, d, dailySummary)}>
                                                                                        {t("adminDashboard.editButton", "Bearb.")}
                                                                                    </button>
                                                                                </div>
                                                                                <ul className="time-entry-list-condensed text-xs">
                                                                                    {sortEntries(dailySummary.entries).map(entry => {
                                                                                        let typeLabel = entry.punchType;
                                                                                        try {
                                                                                            typeLabel = t(`punchTypes.${entry.punchType}`, entry.punchType);
                                                                                        } catch (e) { /* Fallback */ }

                                                                                        let sourceIndicator = '';
                                                                                        if (entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser) {
                                                                                            sourceIndicator = t('adminDashboard.entrySource.autoSuffix', ' (Auto)');
                                                                                        } else if (entry.source === 'ADMIN_CORRECTION') {
                                                                                            sourceIndicator = t('adminDashboard.entrySource.adminSuffix', ' (AdmK)');
                                                                                        } else if (entry.source === 'USER_CORRECTION') {
                                                                                            sourceIndicator = t('adminDashboard.entrySource.userSuffix', ' (UsrK)');
                                                                                        } else if (entry.source === 'MANUAL_IMPORT') {
                                                                                            sourceIndicator = t('adminDashboard.entrySource.importSuffix', ' (Imp)');
                                                                                        }

                                                                                        return (
                                                                                            <li key={entry.id || entry.key} className="py-0.5">
                                                                                                {`${typeLabel}: ${formatTime(entry.entryTimestamp)}${sourceIndicator}`}
                                                                                            </li>
                                                                                        );
                                                                                    })}
                                                                                </ul>
                                                                                <p className="text-xs mt-1">
                                                                                    <strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(actualMinsToday)} | <strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(dailySummary.breakMinutes)}
                                                                                </p>
                                                                                {dailySummary.dailyNote && <p className="text-xs mt-1 italic">📝 {dailySummary.dailyNote}</p>}
                                                                            </>
                                                                        );
                                                                    } else {
                                                                        dayCardContent = (
                                                                            <>
                                                                                <p className="vacation-indicator text-xs">🏖️ {t('adminDashboard.onVacation', 'Im Urlaub')}{vacationOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}{vacationOnThisDay.usesOvertime ? ` (${t('adminDashboard.overtimeVacationShort', 'ÜS')})` : ''}</p>
                                                                                {vacationOnThisDay.halfDay && !userData.userConfig.isHourly && (
                                                                                    <p className="text-xs">{t('adminDashboard.halfDayNote', 'Halbtägiger Urlaub – Restzeiten prüfen.')}</p>
                                                                                )}
                                                                            </>
                                                                        );
                                                                    }
                                                                } else if (sickOnThisDay) {
                                                                    cardClass += ' admin-day-card-sick';
                                                                    dayCardContent = (
                                                                        <>
                                                                            <p className="sick-indicator text-xs">🤒 {t('adminDashboard.onSickLeave', 'Krank gemeldet')}{sickOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}</p>
                                                                            {dailySummary && dailySummary.entries && dailySummary.entries.length > 0 && (
                                                                                <p className="text-xs">{t('adminDashboard.sickWithEntries', 'Zeiten vorhanden – bitte prüfen.')}</p>
                                                                            )}
                                                                        </>
                                                                    );
                                                                } else if (!dailySummary || !dailySummary.entries || dailySummary.entries.length === 0) {
                                                                    let showNewEntryButton = expectedMinsToday > 0
                                                                        || (userData.userConfig.isPercentage && (d.getDay() >= 1 && d.getDay() <= (userData.userConfig.expectedWorkDays || 5)))
                                                                        || (userData.userConfig.isHourly && (d.getDay() >= 1 && d.getDay() <= 5));
                                                                    const effectiveDate = userData.userConfig.scheduleEffectiveDate ? parseISO(userData.userConfig.scheduleEffectiveDate) : null;
                                                                    if (effectiveDate && d < effectiveDate) {
                                                                        showNewEntryButton = false;
                                                                    }
                                                                    dayCardContent = (
                                                                        <>
                                                                            <p className="no-entries text-xs italic">{t('adminDashboard.noEntries')}</p>
                                                                            {showNewEntryButton && (
                                                                                <button className="edit-day-button new-entry text-xs py-0.5 px-1 mt-1 bg-blue-500 hover:bg-blue-600 text-white rounded" onClick={() => openNewEntryModal(userData.username, d)}>
                                                                                    {t('adminDashboard.newEntryButton', 'Neuer Eintrag')}
                                                                                </button>
                                                                            )}
                                                                        </>
                                                                    );
                                                                } else {
                                                                    dayCardContent = (
                                                                        <>
                                                                            <div className="admin-day-card-header justify-between items-start mb-1">
                                                                                <div className="text-xs">
                                                                                    {!userData.userConfig.isHourly && <span className="expected-hours">({t('expectedTimeShort', 'Soll')}: {minutesToHHMM(expectedMinsToday)})</span>}
                                                                                    {!userData.userConfig.isHourly && <span className={`daily-diff ml-1 ${diffMinsToday < 0 ? 'text-red-600' : 'text-green-600'}`}>({t('diffTimeShort', 'Diff')}: {minutesToHHMM(diffMinsToday)})</span>}
                                                                                    {dailySummary.needsCorrection && <span className="auto-completed-tag ml-1 text-red-600 font-bold" title={t('adminDashboard.needsCorrectionTooltip', 'Automatisch beendet und unkorrigiert')}>KORR?</span>}
                                                                                </div>
                                                                                <button className="edit-day-button text-xs py-0.5 px-1 bg-gray-200 hover:bg-gray-300 rounded" onClick={() => openEditModal(userData.username, d, dailySummary)}>
                                                                                    {t("adminDashboard.editButton", "Bearb.")}
                                                                                </button>
                                                                            </div>
                                                                            <ul className="time-entry-list-condensed text-xs">
                                                                                {sortEntries(dailySummary.entries).map(entry => {
                                                                                    let typeLabel = entry.punchType;
                                                                                    try {
                                                                                        typeLabel = t(`punchTypes.${entry.punchType}`, entry.punchType);
                                                                                    } catch (e) { /* Fallback */ }

                                                                                    let sourceIndicator = '';
                                                                                    if (entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser) {
                                                                                        sourceIndicator = t('adminDashboard.entrySource.autoSuffix', ' (Auto)');
                                                                                    } else if (entry.source === 'ADMIN_CORRECTION') {
                                                                                        sourceIndicator = t('adminDashboard.entrySource.adminSuffix', ' (AdmK)');
                                                                                    } else if (entry.source === 'USER_CORRECTION') {
                                                                                        sourceIndicator = t('adminDashboard.entrySource.userSuffix', ' (UsrK)');
                                                                                    } else if (entry.source === 'MANUAL_IMPORT') {
                                                                                        sourceIndicator = t('adminDashboard.entrySource.importSuffix', ' (Imp)');
                                                                                    }

                                                                                    return (
                                                                                        <li key={entry.id || entry.key} className="py-0.5">
                                                                                            {`${typeLabel}: ${formatTime(entry.entryTimestamp)}${sourceIndicator}`}
                                                                                        </li>
                                                                                    );
                                                                                })}
                                                                            </ul>
                                                                            <p className="text-xs mt-1">
                                                                                <strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(actualMinsToday)} | <strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(dailySummary.breakMinutes)}
                                                                            </p>
                                                                            {dailySummary.dailyNote && <p className="text-xs mt-1 italic">📝 {dailySummary.dailyNote}</p>}
                                                                        </>
                                                                    );
                                                                }

                                                                return (
                                                                    <div id={`day-card-${userData.username}-${isoDate}`} key={isoDate} className={`${cardClass} p-2 border rounded shadow-sm bg-white`}>
                                                                        <div className="admin-day-card-header-date font-semibold text-sm mb-1 text-gray-700">
                                                                            {d.toLocaleDateString("de-DE", { weekday: "short" }).toUpperCase()}, {formatDate(d)}
                                                                            {userData.userConfig.isPercentage && holidayNameOnThisDay && holidayOptionForThisDay?.holidayHandlingOption === 'PENDING_DECISION' && (
                                                                                <span className="holiday-pending-icon-small ml-1 text-orange-500 animate-pulse" title={t('adminDashboard.holidayOptionPendingTooltip', 'Feiertagsoption ausstehend')}>❓</span>
                                                                            )}
                                                                        </div>
                                                                        <div className="admin-day-content">
                                                                            {dayCardContent}
                                                                        </div>
                                                                    </div>
                                                                );
                                                            })}
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                    </React.Fragment>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <div className="table-responsive-wrapper">
                            <table className="admin-week-table">
                                <thead>
                                <tr>
                                    <th onClick={() => requestMonthSort('username')} className="sortable-header th-user">
                                        {t('user', 'Benutzer')} {getMonthSortIndicator('username')}
                                    </th>
                                    <th onClick={() => requestMonthSort('monthlyActualMinutes')} className="sortable-header th-numeric">
                                        {t('adminDashboard.monthView.actualHours', 'Ist (Zeitraum)')} {getMonthSortIndicator('monthlyActualMinutes')}
                                    </th>
                                    <th onClick={() => requestMonthSort('monthlyExpectedMinutes')} className="sortable-header th-numeric">
                                        {t('adminDashboard.monthView.expectedHours', 'Soll (Zeitraum)')} {getMonthSortIndicator('monthlyExpectedMinutes')}
                                    </th>
                                    <th onClick={() => requestMonthSort('monthlyOvertimeMinutes')} className="sortable-header th-numeric">
                                        {t('adminDashboard.monthView.balanceRange', 'Saldo (Zeitraum)')} {getMonthSortIndicator('monthlyOvertimeMinutes')}
                                    </th>
                                    <th onClick={() => requestMonthSort('cumulativeBalanceMinutes')} className="sortable-header th-numeric">
                                        {t('balanceTotal', 'Gesamtsaldo')} {getMonthSortIndicator('cumulativeBalanceMinutes')}
                                    </th>
                                    <th onClick={() => requestMonthSort('problemIndicators')} className="sortable-header th-center">
                                        {t('issues', 'Probleme')} {getMonthSortIndicator('problemIndicators')}
                                    </th>
                                    <th className="th-actions">{t('actions', 'Aktionen')}</th>
                                </tr>
                                </thead>
                                <tbody>
                                {sortedMonthlyUserData.map((userData) => (
                                    <tr key={userData.username} className={`user-row ${hiddenUsers.has(userData.username) ? "user-row-hidden" : ""}`}>
                                        <td data-label={t('user', 'Benutzer')} className="td-user" style={{ borderLeft: `4px solid ${userData.userColor}` }}>{userData.username}</td>
                                        <td data-label={t('adminDashboard.monthView.actualHours', 'Ist (Zeitraum)')} className="td-numeric">{minutesToHHMM(userData.monthlyActualMinutes)}</td>
                                        <td data-label={t('adminDashboard.monthView.expectedHours', 'Soll (Zeitraum)')} className="td-numeric">{minutesToHHMM(userData.monthlyExpectedMinutes)}</td>
                                        <td data-label={t('adminDashboard.monthView.balanceRange', 'Saldo (Zeitraum)')} className={`td-numeric ${userData.monthlyOvertimeMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.monthlyOvertimeMinutes)}</td>
                                        <td data-label={t('balanceTotal', 'Gesamtsaldo')} className={`td-numeric ${userData.cumulativeBalanceMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.cumulativeBalanceMinutes)}</td>
                                        <td data-label={t('issues', 'Probleme')} className="problem-indicators-cell td-center">
                                            {renderProblemIndicatorsCell(userData)}
                                        </td>
                                        <td data-label={t('actions', 'Aktionen')} className="actions-cell">
                                            <button onClick={() => openPrintUserModal(userData.username)} className="action-button print-user-button" title={t('printButtonUser', 'Zeiten dieses Benutzers drucken')}>
                                                🖨️
                                            </button>
                                            <button onClick={() => handleHideUser(userData.username)} className="action-button hide-user-row-button" title={t('hideUserInTable', 'Diesen Benutzer in der Tabelle ausblenden')}>
                                                🙈
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )
                ))}

            </section>
            {/* Modal for deleting sick leave */}
            {showDeleteSickLeaveModal && sickLeaveToDelete && (
                <ModalOverlay visible className="bg-black bg-opacity-50">
                    <div className="modal-content delete-confirmation-modal bg-white p-6 rounded-lg shadow-xl max-w-md w-full">
                        <h3 className="text-lg font-semibold mb-4">{t('adminDashboard.deleteSickLeaveConfirmTitle', 'Krankmeldung löschen bestätigen')}</h3>
                        <p className="mb-4 text-sm">
                            {t('adminDashboard.deleteSickLeaveConfirmMessage', 'Möchten Sie die Krankmeldung für')}
                            <strong> {sickLeaveToDelete.username} </strong>
                            ({formatDate(parseISO(sickLeaveToDelete.startDate))} - {formatDate(parseISO(sickLeaveToDelete.endDate))})
                            {sickLeaveToDelete.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}
                            {t('adminDashboard.deleteSickLeaveIrreversible', ' wirklich löschen? Das Tagessoll und der Saldo werden neu berechnet.')}
                        </p>
                        <div className="modal-buttons flex justify-end gap-3">
                            <button onClick={handleDeleteSickLeave} className="button-danger bg-red-500 hover:bg-red-600 text-white">
                                {t('delete', 'Ja, löschen')}
                            </button>
                            <button onClick={() => setShowDeleteSickLeaveModal(false)} className="button-cancel bg-gray-300 hover:bg-gray-400">
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </div>
                </ModalOverlay>
            )}
        </>
    );
});

AdminWeekSection.propTypes = {
    t: PropTypes.func.isRequired,
    weekDates: PropTypes.arrayOf(PropTypes.instanceOf(Date)).isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    handlePrevWeek: PropTypes.func.isRequired,
    handleNextWeek: PropTypes.func.isRequired,
    handleWeekJump: PropTypes.func.isRequired,
    handleCurrentWeek: PropTypes.func.isRequired,
    onFocusProblemWeek: PropTypes.func.isRequired,
    dailySummariesForWeekSection: PropTypes.arrayOf(
        PropTypes.shape({ // This should match DailyTimeSummaryDTO
            username: PropTypes.string.isRequired,
            date: PropTypes.string.isRequired, // YYYY-MM-DD
            workedMinutes: PropTypes.number.isRequired,
            breakMinutes: PropTypes.number.isRequired,
            entries: PropTypes.arrayOf( // This should match TimeTrackingEntryDTO
                PropTypes.shape({
                    id: PropTypes.number, // Can be null for new entries before saving
                    entryTimestamp: PropTypes.string.isRequired, // ISO String like "2023-10-26T08:00:00"
                    punchType: PropTypes.oneOf(['START', 'ENDE']).isRequired,
                    source: PropTypes.string, // e.g., 'NFC_SCAN', 'MANUAL_PUNCH', 'SYSTEM_AUTO_END', 'ADMIN_CORRECTION'
                    correctedByUser: PropTypes.bool, // Indicates if an admin or system correction was then user-corrected
                    systemGeneratedNote: PropTypes.string,
                    key: PropTypes.string // Client-side key for React lists, optional
                })
            ).isRequired,
            needsCorrection: PropTypes.bool, // If SYSTEM_AUTO_END happened and not user corrected
            primaryTimes: PropTypes.shape({ // For quick access to main start/end
                firstStartTime: PropTypes.string, // HH:mm or null
                lastEndTime: PropTypes.string,    // HH:mm or null
                isOpen: PropTypes.bool.isRequired // True if the last punch of the day was START
            }),
            dailyNote: PropTypes.string
        })
    ).isRequired,
    allVacations: PropTypes.arrayOf(
        PropTypes.shape({
            username: PropTypes.string,
            startDate: PropTypes.string.isRequired, // YYYY-MM-DD
            endDate: PropTypes.string.isRequired,   // YYYY-MM-DD
            approved: PropTypes.bool,
            halfDay: PropTypes.bool,
            usesOvertime: PropTypes.bool
        })
    ).isRequired,
    allSickLeaves: PropTypes.arrayOf(
        PropTypes.shape({
            username: PropTypes.string.isRequired,
            startDate: PropTypes.string.isRequired, // YYYY-MM-DD
            endDate: PropTypes.string.isRequired,   // YYYY-MM-DD
            halfDay: PropTypes.bool,
            comment: PropTypes.string
        })
    ).isRequired,
    allHolidays: PropTypes.objectOf( // Keyed by canton (e.g., "SG", "GENERAL")
        PropTypes.shape({
            data: PropTypes.objectOf(PropTypes.string).isRequired, // Key: "YYYY-MM-DD", Value: "Holiday Name"
            year: PropTypes.number.isRequired
        })
    ).isRequired,
    users: PropTypes.arrayOf(
        PropTypes.shape({ // Matches UserDTO more closely
            username: PropTypes.string.isRequired,
            trackingBalanceInMinutes: PropTypes.number,
            isPercentage: PropTypes.bool,
            isHourly: PropTypes.bool,
            expectedWorkDays: PropTypes.number,
            workPercentage: PropTypes.number,
            dailyWorkHours: PropTypes.number,
            companyCantonAbbreviation: PropTypes.string,
            color: PropTypes.string,
            scheduleEffectiveDate: PropTypes.string, // YYYY-MM-DD
            // other user fields...
        })
    ).isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired,
    rawUserTrackingBalances: PropTypes.arrayOf(PropTypes.shape({
        username: PropTypes.string.isRequired,
        trackingBalance: PropTypes.number.isRequired, // Assuming this structure
    })),
    openNewEntryModal: PropTypes.func.isRequired,
    onDataReloadNeeded: PropTypes.func.isRequired,
    onIssueSummaryChange: PropTypes.func,
    showSmartOverview: PropTypes.bool,
    onQuickFixQueueChange: PropTypes.func,
};

AdminWeekSection.displayName = 'AdminWeekSection';

export default AdminWeekSection;