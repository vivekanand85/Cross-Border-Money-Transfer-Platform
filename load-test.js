import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m', target: 50 },
    { duration: '20s', target: 0 },
  ],
};

const BASE_URL = 'http://host.docker.internal:8082';

export default function () {
  const idempotencyKey = `loadtest-${__VU}-${__ITER}-${Date.now()}`;

  const initPayload = JSON.stringify({
    idempotencyKey: idempotencyKey,
    amount: 10000,
    currency: 'INR',
    sourceAccountId: '5687d64a-bf01-44b4-837a-f02235f457ea',
    destAccountId: 'e8307db8-6dc7-431a-a4ac-fda1b2c29bba',
    payoutMode: 'BANK',
  });

const initRes = http.post(`${BASE_URL}/api/v1/transfers/initiate`, initPayload, {    headers: { 'Content-Type': 'application/json' },
});

  console.log(`Status: ${initRes.status}, Body: ${initRes.body}`);
  check(initRes, { 'initiate status 200/201': (r) => r.status === 200 || r.status === 201 });

  const transferId = JSON.parse(initRes.body).id;

  sleep(0.5);

  const screenRes = http.post(`${BASE_URL}/api/v1/transfers/${transferId}/screen`);
  check(screenRes, { 'screen status 200': (r) => r.status === 200 });

  sleep(1);
}