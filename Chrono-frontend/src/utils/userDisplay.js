const DEMO_NAME_FALLBACKS = {
    demo: 'Demo Manager',
    anna: 'Anna Fischer',
    ben: 'Ben Keller',
    carla: 'Carla Meier',
    david: 'David Lenz',
};

const asTrimmedString = (value) => {
    if (value === null || value === undefined) return '';
    return String(value).trim();
};

export const getUserFullName = (user) => {
    if (!user || typeof user !== 'object') return '';
    return [user.firstName, user.lastName]
        .map(asTrimmedString)
        .filter(Boolean)
        .join(' ')
        .trim();
};

const titleCaseToken = (value) => {
    const normalized = asTrimmedString(value).replace(/[-_.]+/g, ' ');
    if (!normalized) return '';
    return normalized
        .split(/\s+/)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
        .join(' ');
};

export const getDemoUsernameDisplayName = (username) => {
    const normalized = asTrimmedString(username);
    const match = normalized.match(/^demo_[a-f0-9]{8,}(?:_(.+))?$/i);
    if (!match) return '';

    const demoKey = asTrimmedString(match[1] || 'demo').toLowerCase();
    return DEMO_NAME_FALLBACKS[demoKey] || titleCaseToken(demoKey) || DEMO_NAME_FALLBACKS.demo;
};

const findUserByUsername = (username, users) => {
    const normalizedUsername = asTrimmedString(username);
    if (!normalizedUsername || !Array.isArray(users)) return null;
    return users.find((user) => user?.username === normalizedUsername) || null;
};

export const getUserDisplayName = (userOrUsername, users = [], fallback = '') => {
    const isUserObject = userOrUsername && typeof userOrUsername === 'object';
    const username = isUserObject
        ? asTrimmedString(userOrUsername.username)
        : asTrimmedString(userOrUsername);
    const user = isUserObject ? userOrUsername : findUserByUsername(username, users);

    const fullName = getUserFullName(user);
    if (fullName) return fullName;

    const demoName = getDemoUsernameDisplayName(username);
    if (demoName) return demoName;

    return username || asTrimmedString(fallback);
};

export const getUserSearchText = (userOrUsername, users = []) => {
    const isUserObject = userOrUsername && typeof userOrUsername === 'object';
    const user = isUserObject ? userOrUsername : findUserByUsername(userOrUsername, users);
    const username = isUserObject
        ? asTrimmedString(userOrUsername.username)
        : asTrimmedString(userOrUsername);

    return [
        getUserDisplayName(user || username, users),
        username,
        user?.email,
        user?.firstName,
        user?.lastName,
        user?.department,
    ]
        .map(asTrimmedString)
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
};
