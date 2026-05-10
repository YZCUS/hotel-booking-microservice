import type { Booking, HealthState, Hotel, JwtResponse, Page, RoomType } from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const gatewayOrigin = API_BASE_URL.replace(/\/api\/v1\/?$/, '');

type RequestOptions = RequestInit & {
  token?: string | null;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(detail || `${response.status} ${response.statusText}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export async function fetchHotels(): Promise<Hotel[]> {
  const page = await request<Page<Hotel>>('/hotels?size=20&sort=starRating,desc');
  return page.content;
}

export async function fetchRooms(hotelId: string): Promise<RoomType[]> {
  return request<RoomType[]>(`/hotels/${hotelId}/rooms`);
}

export async function checkAvailability(
  roomTypeId: string,
  checkInDate: string,
  checkOutDate: string
): Promise<boolean> {
  const params = new URLSearchParams({
    roomTypeId,
    checkInDate,
    checkOutDate,
    rooms: '1'
  });
  return request<boolean>(`/inventory/check-availability?${params.toString()}`);
}

export async function registerUser(input: {
  email: string;
  password: string;
  fullName: string;
  phone: string;
}): Promise<JwtResponse> {
  return request<JwtResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function loginUser(input: { email: string; password: string }): Promise<JwtResponse> {
  return request<JwtResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function createBooking(
  token: string,
  input: {
    userId: string;
    roomTypeId: string;
    checkInDate: string;
    checkOutDate: string;
    guests: number;
  }
): Promise<Booking> {
  return request<Booking>('/bookings', {
    method: 'POST',
    token,
    body: JSON.stringify(input)
  });
}

export async function fetchUserBookings(token: string, userId: string): Promise<Booking[]> {
  const page = await request<Page<Booking>>(`/bookings/user/${userId}?size=10&sort=createdAt,desc`, { token });
  return page.content;
}

export async function cancelBooking(token: string, bookingId: string): Promise<Booking> {
  return request<Booking>(`/bookings/${bookingId}/cancel`, {
    method: 'PUT',
    token
  });
}

export async function fetchHealth(): Promise<HealthState[]> {
  const services: HealthState[] = [
    { name: 'Gateway', path: `${gatewayOrigin}/actuator/health`, status: 'UNKNOWN' },
    { name: 'User', path: 'http://localhost:8081/actuator/health', status: 'UNKNOWN' },
    { name: 'Hotel', path: 'http://localhost:8082/actuator/health', status: 'UNKNOWN' },
    { name: 'Booking', path: 'http://localhost:8083/actuator/health', status: 'UNKNOWN' },
    { name: 'Search', path: 'http://localhost:8084/actuator/health', status: 'UNKNOWN' },
    { name: 'Notification', path: 'http://localhost:8085/actuator/health', status: 'UNKNOWN' }
  ];

  return Promise.all(
    services.map(async (service) => {
      try {
        const response = await fetch(service.path);
        if (!response.ok) {
          return { ...service, status: 'DOWN' as const };
        }
        const payload = (await response.json()) as { status?: string };
        return { ...service, status: payload.status === 'UP' ? 'UP' : 'UNKNOWN' };
      } catch {
        return { ...service, status: 'DOWN' as const };
      }
    })
  );
}
