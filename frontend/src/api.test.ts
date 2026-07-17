import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createBooking, fetchHealth } from './api';

describe('createBooking', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('sends the caller-provided idempotency key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'booking-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    await createBooking('jwt-token', 'attempt-123', {
      userId: 'user-1',
      roomTypeId: 'room-1',
      checkInDate: '2026-08-01',
      checkOutDate: '2026-08-03',
      guests: 2
    });

    expect(fetchMock).toHaveBeenCalledOnce();
    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(options.headers);
    expect(headers.get('Idempotency-Key')).toBe('attempt-123');
    expect(headers.get('Authorization')).toBe('Bearer jwt-token');
  });

  it('checks internal services through the gateway', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    await fetchHealth();

    const urls = fetchMock.mock.calls.map(([url]) => String(url));
    expect(urls).toContain('http://localhost:8080/health/user-service');
    expect(urls).toContain('http://localhost:8080/health/notification-service');
    expect(urls.some((url) => /localhost:808[1-5]/.test(url))).toBe(false);
  });
});
