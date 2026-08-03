'use strict';

const assert = require('assert');
const { normalizeSenderList } = require('../services/globalBlockedSenders');

assert.deepStrictEqual(normalizeSenderList([' GP ', 'gp', '', 'Promo']), ['GP', 'Promo']);
assert.deepStrictEqual(normalizeSenderList(null), []);
assert.deepStrictEqual(normalizeSenderList('x'), []);

console.log('global-blocked-senders.test.js PASS');
