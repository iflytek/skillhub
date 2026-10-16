import { expect, test } from 'bun:test';
import { readableDate, formatCount, skillInitial, skillTone, validCount, validRating } from '../src/client/skill-presentation.js';

test('avatar fallback handles old records, whitespace, Chinese and Unicode without splitting characters', () => {
  expect(skillInitial('文档助手', 'writer')).toBe('文');
  expect(skillInitial('  review', 'fallback')).toBe('R');
  expect(skillInitial('  ', ' writer ')).toBe('W');
  expect(skillInitial(undefined, '')).toBe('?');
  expect(skillInitial('𠮷野')).toBe('𠮷');
  expect(skillTone('team/writer')).toBe(skillTone('team/writer'));
});
test('metrics preserve zero counts but omit missing, invalid and unrated values', () => {
  for (const value of [0, 8600, 13000]) expect(validCount(value)).toBe(true);
  for (const value of [undefined, null, -1, Infinity, 1.3, '0']) expect(validCount(value)).toBe(false);
  for (const value of [undefined, null, 0, -1, NaN, Infinity, 5.1]) expect(validRating(value)).toBe(false);
  expect(validRating(4.8)).toBe(true);
  expect(formatCount(8600)).toBe('8,600');
  expect(formatCount(13000)).toBe('1.3万');
});

test('publication dates are readable and tolerate missing or invalid older records', () => {
  expect(readableDate('2026-10-14T12:00:00Z')).toMatch(/2026.*10.*14/);
  expect(readableDate(undefined)).toBe('日期未知');
  expect(readableDate('not-a-date')).toBe('日期未知');
  expect(readableDate('')).toBe('日期未知');
  expect(readableDate('2026-10-14T12:00:00Z', true)).toMatch(/2026.*10.*14.*\d{2}:\d{2}/);
  expect(readableDate('not-a-date', true)).toBe('日期未知');
});
