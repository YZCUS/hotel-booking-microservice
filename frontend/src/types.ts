export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type RoomType = {
  id: string;
  hotelId: string;
  hotelName: string;
  name: string;
  description?: string;
  capacity: number;
  pricePerNight: number;
  totalInventory: number;
  availableRooms?: number;
  isAvailable?: boolean;
};

export type Hotel = {
  id: string;
  name: string;
  description?: string;
  address?: string;
  city?: string;
  country?: string;
  starRating?: number;
  amenities?: string[];
  roomTypes?: RoomType[];
  favoriteCount?: number;
  minPrice?: number;
  maxPrice?: number;
};

export type JwtResponse = {
  token: string;
  type: string;
  userId: string;
  email: string;
};

export type Booking = {
  id: string;
  userId: string;
  roomTypeId: string;
  checkInDate: string;
  checkOutDate: string;
  guests: number;
  totalPrice: number;
  status: string;
  numberOfNights?: number;
  canCancel?: boolean;
};

export type HealthState = {
  name: string;
  path: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
};
