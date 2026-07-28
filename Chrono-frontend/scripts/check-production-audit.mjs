import { spawnSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { extname, join } from 'node:path';

const ACCEPTED_ROUTER_ADVISORY = 'https://github.com/advisories/GHSA-qwww-vcr4-c8h2';
const ACCEPTED_ROUTER_VERSION = '7.18.2';
const SOURCE_EXTENSIONS = new Set(['.js', '.jsx', '.ts', '.tsx']);
const RSC_SERVER_MARKERS = [
    'createRequestHandler',
    'RSCStaticRouter',
    'routeRSCServerRequest',
    'RSCRouter',
    'ServerRouter',
    'unstable_RSC',
];

const npmExecutable = process.env.npm_execpath ? process.execPath : 'npm';
const npmArguments = process.env.npm_execpath
    ? [process.env.npm_execpath, 'audit', '--omit=dev', '--json']
    : ['audit', '--omit=dev', '--json'];
const audit = spawnSync(
    npmExecutable,
    npmArguments,
    { cwd: process.cwd(), encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 },
);

if (audit.error) {
    throw audit.error;
}

let report;
try {
    report = JSON.parse(audit.stdout);
} catch {
    process.stderr.write(audit.stderr || audit.stdout);
    throw new Error('npm audit did not return a valid JSON report.');
}

const vulnerabilities = Object.entries(report.vulnerabilities ?? {});
const unexpected = vulnerabilities.filter(([name, finding]) => {
    if (name === 'react-router') {
        return !(
            finding.severity === 'high'
            && finding.via.length === 1
            && finding.via[0]?.url === ACCEPTED_ROUTER_ADVISORY
        );
    }
    if (name === 'react-router-dom') {
        return !(
            finding.severity === 'high'
            && finding.via.length === 1
            && finding.via[0] === 'react-router'
        );
    }
    return true;
});

if (unexpected.length > 0) {
    for (const [name, finding] of unexpected) {
        console.error(`[audit] ${finding.severity}: ${name}`);
    }
    process.exit(1);
}

const routerPackage = JSON.parse(
    readFileSync(join(process.cwd(), 'node_modules', 'react-router', 'package.json'), 'utf8'),
);
if (routerPackage.version !== ACCEPTED_ROUTER_VERSION) {
    console.error(
        `[audit] The React Router exception only applies to ${ACCEPTED_ROUTER_VERSION}; `
        + `installed is ${routerPackage.version}.`,
    );
    process.exit(1);
}

const sourceFiles = [];
const collectSourceFiles = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            if (!['__tests__', 'node_modules'].includes(entry.name)) {
                collectSourceFiles(path);
            }
        } else if (
            SOURCE_EXTENSIONS.has(extname(entry.name))
            && !entry.name.includes('.test.')
            && !entry.name.includes('.spec.')
        ) {
            sourceFiles.push(path);
        }
    }
};
collectSourceFiles(join(process.cwd(), 'src'));

const rscUsage = [];
for (const file of sourceFiles) {
    const source = readFileSync(file, 'utf8');
    for (const marker of RSC_SERVER_MARKERS) {
        if (source.includes(marker)) {
            rscUsage.push(`${file}: ${marker}`);
        }
    }
}

if (rscUsage.length > 0) {
    console.error('[audit] The accepted React Router RSC advisory is applicable:');
    rscUsage.forEach((usage) => console.error(`  ${usage}`));
    process.exit(1);
}

if (vulnerabilities.length === 0 && audit.status === 0) {
    console.log('[audit] No known production dependency vulnerabilities.');
} else {
    console.warn(
        `[audit] Accepted ${ACCEPTED_ROUTER_ADVISORY} for browser-only React Router `
        + `${ACCEPTED_ROUTER_VERSION}; Chrono contains no RSC server APIs.`,
    );
}
