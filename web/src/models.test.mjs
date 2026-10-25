import { test } from 'node:test';
import assert from 'node:assert/strict';
import { setTaskStatus, toggleComplete, validateTaskTitle } from './models.js';

test('rejects blank and overly long titles', () => {
  assert.equal(validateTaskTitle('  '), 'Add a task title to continue.');
  assert.equal(validateTaskTitle('x'.repeat(121)), 'Keep the title to 120 characters or fewer.');
});

test('accepts a short title', () => assert.equal(validateTaskTitle('Take a short walk'), null));

test('task status changes are clear and reversible', () => {
  const task = { id: 't', title: 'Walk', status: 'planned' };
  assert.equal(setTaskStatus(task, 'skipped').status, 'skipped');
  assert.equal(toggleComplete(task).status, 'completed');
  assert.equal(toggleComplete({ ...task, status: 'completed' }).status, 'planned');
});
