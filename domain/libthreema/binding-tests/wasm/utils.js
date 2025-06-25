import * as process from 'node:process';

import {init as initLibthreema} from '../../build/wasm/nodejs/libthreema.js';

export function assert(condition, message) {
    if (!condition) {
        throw new Error(`Assertion failed, message: ${message}`);
    }
}

export async function init() {
    const logTag = '[libthreema]';
    initLibthreema(
        {handle: (info) => console.error('PANIC!', info)},
        {
            debug: console.debug.bind(console, logTag),
            info: console.info.bind(console, logTag),
            warn: console.warn.bind(console, logTag),
            error: console.error.bind(console, logTag),
        },
        'debug',
    );
}

/**
 * Run a test and log information about passing or failure. Returns whether the
 * test passed.
 */
export function runTest(testFunction, name) {
    console.info('> 📝 Test', name);

    try {
        testFunction();
    } catch (error) {
        console.info('> ❌ Test', name, 'failed');
        console.error(error);
        return false;
    }

    console.info('> ✅ Test', name, 'passed');
    return true;
}

/**
 * Evaluate all tests results from {@link runTest} and exit the process
 * accordingly.
 */
export function evaluateTestResults(testResults) {
    const results = testResults.reduce(
        (results, passed) => {
            if (passed) {
                ++results.passed;
            } else {
                ++results.failed;
            }
            return results;
        },
        {passed: 0, failed: 0},
    );

    console.info(
        `${results.passed} tests passed, ${results.failed} tests failed`,
    );
    process.exit(results.failed === 0 ? 0 : 1);
}
