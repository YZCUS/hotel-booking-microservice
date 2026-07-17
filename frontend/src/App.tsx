import { useEffect, useMemo, useRef, useState } from 'react';
import {
  BedDouble,
  CalendarDays,
  Check,
  CircleAlert,
  HeartPulse,
  Loader2,
  LogIn,
  RefreshCw,
  Search,
  UserPlus,
  X
} from 'lucide-react';
import {
  cancelBooking,
  checkAvailability,
  createBooking,
  fetchHealth,
  fetchHotels,
  fetchRooms,
  fetchUserBookings,
  loginUser,
  registerUser
} from './api';
import type { Booking, HealthState, Hotel, JwtResponse, RoomType } from './types';
import './App.css';

type Notice = { tone: 'info' | 'error' | 'success'; text: string } | null;

const tomorrow = new Date();
tomorrow.setDate(tomorrow.getDate() + 7);
const twoDaysLater = new Date(tomorrow);
twoDaysLater.setDate(twoDaysLater.getDate() + 2);

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

const initialAuth = {
  email: 'demo@example.com',
  password: 'password123',
  fullName: 'Demo Guest',
  phone: '+1-555-0100'
};

function App() {
  const bookingAttemptRef = useRef<{ signature: string; key: string } | null>(null);
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [selectedHotel, setSelectedHotel] = useState<Hotel | null>(null);
  const [rooms, setRooms] = useState<RoomType[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [auth, setAuth] = useState<JwtResponse | null>(() => {
    const raw = localStorage.getItem('hotel-demo-auth');
    return raw ? (JSON.parse(raw) as JwtResponse) : null;
  });
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [health, setHealth] = useState<HealthState[]>([]);
  const [authForm, setAuthForm] = useState(initialAuth);
  const [bookingForm, setBookingForm] = useState({
    checkInDate: isoDate(tomorrow),
    checkOutDate: isoDate(twoDaysLater),
    guests: 2
  });
  const [available, setAvailable] = useState<boolean | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const [loading, setLoading] = useState({
    hotels: false,
    rooms: false,
    auth: false,
    booking: false,
    cancel: '',
    health: false,
    availability: false
  });

  const selectedRoom = useMemo(
    () => rooms.find((room) => room.id === selectedRoomId) ?? null,
    [rooms, selectedRoomId]
  );

  useEffect(() => {
    void loadHotels();
    void refreshHealth();
  }, []);

  useEffect(() => {
    if (auth?.token && auth.userId) {
      localStorage.setItem('hotel-demo-auth', JSON.stringify(auth));
      void loadBookings(auth.token, auth.userId);
    } else {
      localStorage.removeItem('hotel-demo-auth');
      setBookings([]);
    }
  }, [auth]);

  async function run<T>(key: keyof typeof loading, work: () => Promise<T>) {
    setLoading((current) => ({ ...current, [key]: true }));
    try {
      return await work();
    } finally {
      setLoading((current) => ({ ...current, [key]: false }));
    }
  }

  async function loadHotels() {
    await run('hotels', async () => {
      const data = await fetchHotels();
      setHotels(data);
      if (!selectedHotel && data.length > 0) {
        await chooseHotel(data[0]);
      }
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function chooseHotel(hotel: Hotel) {
    setSelectedHotel(hotel);
    setAvailable(null);
    await run('rooms', async () => {
      const data = await fetchRooms(hotel.id);
      setRooms(data);
      setSelectedRoomId(data[0]?.id ?? '');
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function refreshHealth() {
    await run('health', async () => {
      setHealth(await fetchHealth());
    });
  }

  async function submitRegister() {
    await run('auth', async () => {
      const response = await registerUser(authForm);
      setAuth(response);
      setNotice({ tone: 'success', text: `Signed in as ${response.email}` });
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function submitLogin() {
    await run('auth', async () => {
      const response = await loginUser({ email: authForm.email, password: authForm.password });
      setAuth(response);
      setNotice({ tone: 'success', text: `Signed in as ${response.email}` });
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function submitAvailability() {
    if (!selectedRoom) return;
    await run('availability', async () => {
      const result = await checkAvailability(
        selectedRoom.id,
        bookingForm.checkInDate,
        bookingForm.checkOutDate
      );
      setAvailable(result);
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function submitBooking() {
    if (!auth || !selectedRoom) {
      setNotice({ tone: 'error', text: 'Sign in and select a room first.' });
      return;
    }

    const input = {
      userId: auth.userId,
      roomTypeId: selectedRoom.id,
      checkInDate: bookingForm.checkInDate,
      checkOutDate: bookingForm.checkOutDate,
      guests: bookingForm.guests
    };
    const signature = JSON.stringify(input);
    if (bookingAttemptRef.current?.signature !== signature) {
      bookingAttemptRef.current = { signature, key: crypto.randomUUID() };
    }

    await run('booking', async () => {
      const booking = await createBooking(auth.token, bookingAttemptRef.current!.key, input);
      bookingAttemptRef.current = null;
      setBookings((current) => [booking, ...current]);
      setNotice({ tone: 'success', text: `Booking ${booking.id.slice(0, 8)} confirmed.` });
      setAvailable(null);
    }).catch((error) => setNotice({ tone: 'error', text: error.message }));
  }

  async function loadBookings(token: string, userId: string) {
    const data = await fetchUserBookings(token, userId).catch(() => []);
    setBookings(data);
  }

  async function submitCancel(bookingId: string) {
    if (!auth) return;
    setLoading((current) => ({ ...current, cancel: bookingId }));
    try {
      const cancelled = await cancelBooking(auth.token, bookingId);
      setBookings((current) => current.map((item) => (item.id === bookingId ? cancelled : item)));
      setNotice({ tone: 'success', text: `Booking ${bookingId.slice(0, 8)} cancelled.` });
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : 'Unable to cancel booking.' });
    } finally {
      setLoading((current) => ({ ...current, cancel: '' }));
    }
  }

  return (
    <main>
      <header className="topbar">
        <div>
          <p className="eyebrow">Microservices Demo</p>
          <h1>Hotel Reservations</h1>
        </div>
        <div className="session">
          {auth ? (
            <>
              <span>{auth.email}</span>
              <button className="iconButton" type="button" onClick={() => setAuth(null)} aria-label="Sign out">
                <X size={18} />
              </button>
            </>
          ) : (
            <span>Guest session</span>
          )}
        </div>
      </header>

      {notice && (
        <div className={`notice ${notice.tone}`} role="status">
          {notice.tone === 'error' ? <CircleAlert size={18} /> : <Check size={18} />}
          <span>{notice.text}</span>
          <button className="iconButton" type="button" onClick={() => setNotice(null)} aria-label="Dismiss">
            <X size={16} />
          </button>
        </div>
      )}

      <section className="healthStrip" aria-label="Service health">
        <div className="sectionTitle">
          <HeartPulse size={18} />
          <h2>Health</h2>
        </div>
        <div className="healthList">
          {health.map((item) => (
            <span key={item.name} className={`healthPill ${item.status.toLowerCase()}`}>
              {item.name}
              <strong>{item.status}</strong>
            </span>
          ))}
        </div>
        <button className="secondaryButton" type="button" onClick={refreshHealth} disabled={loading.health}>
          {loading.health ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
          Refresh
        </button>
      </section>

      <div className="layout">
        <section className="panel hotelsPanel">
          <div className="sectionTitle">
            <Search size={18} />
            <h2>Hotels</h2>
          </div>
          <div className="hotelList">
            {loading.hotels && <div className="empty">Loading hotels...</div>}
            {hotels.map((hotel) => (
              <button
                key={hotel.id}
                className={`hotelCard ${selectedHotel?.id === hotel.id ? 'selected' : ''}`}
                type="button"
                onClick={() => void chooseHotel(hotel)}
              >
                <span className="hotelName">{hotel.name}</span>
                <span className="muted">{hotel.city}, {hotel.country}</span>
                <span className="priceLine">
                  {hotel.starRating ?? 0} stars
                  {hotel.minPrice ? ` from $${hotel.minPrice}` : ''}
                </span>
              </button>
            ))}
          </div>
        </section>

        <section className="panel roomsPanel">
          <div className="sectionTitle">
            <BedDouble size={18} />
            <h2>Rooms</h2>
          </div>
          {selectedHotel && (
            <div className="hotelSummary">
              <h3>{selectedHotel.name}</h3>
              <p>{selectedHotel.description}</p>
            </div>
          )}
          <div className="roomGrid">
            {loading.rooms && <div className="empty">Loading rooms...</div>}
            {rooms.map((room) => (
              <label key={room.id} className={`roomCard ${selectedRoomId === room.id ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="room"
                  value={room.id}
                  checked={selectedRoomId === room.id}
                  onChange={() => {
                    setSelectedRoomId(room.id);
                    setAvailable(null);
                  }}
                />
                <span className="roomName">{room.name}</span>
                <span className="muted">Sleeps {room.capacity}</span>
                <strong>${room.pricePerNight}</strong>
              </label>
            ))}
          </div>
        </section>

        <section className="panel bookingPanel">
          <div className="sectionTitle">
            <CalendarDays size={18} />
            <h2>Reservation</h2>
          </div>
          <div className="formGrid">
            <label>
              Email
              <input
                value={authForm.email}
                onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })}
              />
            </label>
            <label>
              Password
              <input
                type="password"
                value={authForm.password}
                onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })}
              />
            </label>
            <label>
              Name
              <input
                value={authForm.fullName}
                onChange={(event) => setAuthForm({ ...authForm, fullName: event.target.value })}
              />
            </label>
            <label>
              Phone
              <input
                value={authForm.phone}
                onChange={(event) => setAuthForm({ ...authForm, phone: event.target.value })}
              />
            </label>
          </div>
          <div className="buttonRow">
            <button className="primaryButton" type="button" onClick={submitRegister} disabled={loading.auth}>
              {loading.auth ? <Loader2 className="spin" size={16} /> : <UserPlus size={16} />}
              Register
            </button>
            <button className="secondaryButton" type="button" onClick={submitLogin} disabled={loading.auth}>
              <LogIn size={16} />
              Login
            </button>
          </div>

          <div className="formGrid dates">
            <label>
              Check-in
              <input
                type="date"
                value={bookingForm.checkInDate}
                onChange={(event) => setBookingForm({ ...bookingForm, checkInDate: event.target.value })}
              />
            </label>
            <label>
              Check-out
              <input
                type="date"
                value={bookingForm.checkOutDate}
                onChange={(event) => setBookingForm({ ...bookingForm, checkOutDate: event.target.value })}
              />
            </label>
            <label>
              Guests
              <input
                type="number"
                min="1"
                max="10"
                value={bookingForm.guests}
                onChange={(event) => setBookingForm({ ...bookingForm, guests: Number(event.target.value) })}
              />
            </label>
          </div>
          <div className="buttonRow">
            <button
              className="secondaryButton"
              type="button"
              onClick={submitAvailability}
              disabled={!selectedRoom || loading.availability}
            >
              {loading.availability ? <Loader2 className="spin" size={16} /> : <Check size={16} />}
              Availability
            </button>
            <button className="primaryButton" type="button" onClick={submitBooking} disabled={!auth || !selectedRoom || loading.booking}>
              {loading.booking ? <Loader2 className="spin" size={16} /> : <CalendarDays size={16} />}
              Book
            </button>
          </div>
          {available !== null && (
            <div className={`availability ${available ? 'yes' : 'no'}`}>
              {available ? 'Available' : 'Unavailable'}
            </div>
          )}
        </section>
      </div>

      <section className="bookingsBand">
        <div className="sectionTitle">
          <CalendarDays size={18} />
          <h2>Bookings</h2>
        </div>
        <div className="bookingsGrid">
          {bookings.length === 0 && <div className="empty">No bookings for this session.</div>}
          {bookings.map((booking) => (
            <article key={booking.id} className="bookingCard">
              <div>
                <h3>{booking.id.slice(0, 8)}</h3>
                <p>{booking.checkInDate} to {booking.checkOutDate}</p>
              </div>
              <span className={`status ${booking.status.toLowerCase()}`}>{booking.status}</span>
              <strong>${booking.totalPrice}</strong>
              <button
                className="secondaryButton"
                type="button"
                disabled={!booking.canCancel || loading.cancel === booking.id}
                onClick={() => void submitCancel(booking.id)}
              >
                {loading.cancel === booking.id ? <Loader2 className="spin" size={16} /> : <X size={16} />}
                Cancel
              </button>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}

export default App;
