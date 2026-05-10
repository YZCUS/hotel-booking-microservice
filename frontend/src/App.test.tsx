import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = input.toString();
        if (url.includes('/api/v1/hotels?')) {
          return Promise.resolve(
            new Response(
              JSON.stringify({
                content: [
                  {
                    id: 'hotel-1',
                    name: 'Demo Hotel',
                    city: 'Taipei',
                    country: 'Taiwan',
                    starRating: 5,
                    minPrice: 120
                  }
                ],
                totalElements: 1,
                totalPages: 1,
                number: 0,
                size: 20
              }),
              { status: 200 }
            )
          );
        }

        if (url.includes('/api/v1/hotels/hotel-1/rooms')) {
          return Promise.resolve(
            new Response(
              JSON.stringify([
                {
                  id: 'room-1',
                  hotelId: 'hotel-1',
                  hotelName: 'Demo Hotel',
                  name: 'Deluxe Room',
                  capacity: 2,
                  pricePerNight: 120,
                  totalInventory: 10
                }
              ]),
              { status: 200 }
            )
          );
        }

        return Promise.resolve(new Response(JSON.stringify({ status: 'UP' }), { status: 200 }));
      })
    );
  });

  it('renders hotels and reservation controls', async () => {
    render(<App />);

    expect((await screen.findAllByText('Demo Hotel')).length).toBeGreaterThan(0);
    expect(await screen.findByText('Deluxe Room')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Register/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Book/i })).toBeDisabled();
  });
});
