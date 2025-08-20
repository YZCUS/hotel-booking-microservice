-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Service schemas
CREATE SCHEMA IF NOT EXISTS user_svc;
CREATE SCHEMA IF NOT EXISTS hotel_svc;
CREATE SCHEMA IF NOT EXISTS booking_svc;
CREATE SCHEMA IF NOT EXISTS search_svc;

-- Users (user-service)
CREATE TABLE IF NOT EXISTS user_svc.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Hotels (hotel-service)
CREATE TABLE IF NOT EXISTS hotel_svc.hotels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    address VARCHAR(500),
    city VARCHAR(100),
    country VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    star_rating INTEGER CHECK (star_rating >= 1 AND star_rating <= 5),
    amenities JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Room types (hotel-service)
CREATE TABLE IF NOT EXISTS hotel_svc.room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id UUID REFERENCES hotel_svc.hotels(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    capacity INTEGER NOT NULL,
    price_per_night DECIMAL(10, 2) NOT NULL,
    total_inventory INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Room inventory (booking-service) - NO cross-service foreign key
CREATE TABLE IF NOT EXISTS booking_svc.room_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type_id UUID NOT NULL, -- Removed FK constraint - validate in application
    date DATE NOT NULL,
    available_rooms INTEGER NOT NULL,
    UNIQUE(room_type_id, date)
);

-- Bookings (booking-service) - NO cross-service foreign keys
CREATE TABLE IF NOT EXISTS booking_svc.bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL, -- Removed FK constraint - validate in application
    room_type_id UUID NOT NULL, -- Removed FK constraint - validate in application
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    guests INTEGER NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    room_number VARCHAR(20),
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User favorites (hotel-service) - NO cross-service foreign key
CREATE TABLE IF NOT EXISTS hotel_svc.user_favorites (
    user_id UUID NOT NULL, -- Removed FK constraint - validate in application
    hotel_id UUID REFERENCES hotel_svc.hotels(id) ON DELETE CASCADE, -- Keep this FK as it's same service
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, hotel_id)
);

-- Search history (search-service) - NO cross-service foreign key
CREATE TABLE IF NOT EXISTS search_svc.search_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID, -- Removed FK constraint - validate in application (nullable for anonymous)
    search_query TEXT,
    search_filters JSONB,
    results_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON user_svc.users(email);
CREATE INDEX IF NOT EXISTS idx_users_active ON user_svc.users(is_active);

CREATE INDEX IF NOT EXISTS idx_hotels_city ON hotel_svc.hotels(city);
CREATE INDEX IF NOT EXISTS idx_hotels_country ON hotel_svc.hotels(country);
CREATE INDEX IF NOT EXISTS idx_hotels_rating ON hotel_svc.hotels(star_rating);
CREATE INDEX IF NOT EXISTS idx_hotels_location ON hotel_svc.hotels(latitude, longitude);

CREATE INDEX IF NOT EXISTS idx_room_types_hotel_id ON hotel_svc.room_types(hotel_id);
CREATE INDEX IF NOT EXISTS idx_room_types_price ON hotel_svc.room_types(price_per_night);

CREATE INDEX IF NOT EXISTS idx_room_inventory_date ON booking_svc.room_inventory(date);
CREATE INDEX IF NOT EXISTS idx_room_inventory_room_type_date ON booking_svc.room_inventory(room_type_id, date);

CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON booking_svc.bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_dates ON booking_svc.bookings(check_in_date, check_out_date);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON booking_svc.bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_room_type ON booking_svc.bookings(room_type_id);

CREATE INDEX IF NOT EXISTS idx_user_favorites_user_id ON hotel_svc.user_favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_user_favorites_hotel_id ON hotel_svc.user_favorites(hotel_id);

-- Sample data
INSERT INTO hotel_svc.hotels (id, name, description, address, city, country, latitude, longitude, star_rating, amenities) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Grand Hotel Taipei', 'Luxury hotel in the heart of Taipei', '123 Main St, Xinyi District', 'Taipei', 'Taiwan', 25.0330, 121.5654, 5, '["WiFi", "Pool", "Gym", "Spa", "Restaurant"]'),
    ('550e8400-e29b-41d4-a716-446655440002', 'City Business Hotel', 'Modern business hotel with conference facilities', '456 Business Ave, Zhongshan District', 'Taipei', 'Taiwan', 25.0478, 121.5319, 4, '["WiFi", "Conference Room", "Restaurant", "Gym"]'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Budget Inn Express', 'Affordable accommodation for budget travelers', '789 Budget St, Wanhua District', 'Taipei', 'Taiwan', 25.0375, 121.4999, 3, '["WiFi", "24h Reception"]')
ON CONFLICT (id) DO NOTHING;

INSERT INTO hotel_svc.room_types (id, hotel_id, name, description, capacity, price_per_night, total_inventory) VALUES
    ('660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'Deluxe Room', 'Spacious room with city view', 2, 150.00, 20),
    ('660e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'Executive Suite', 'Luxury suite with separate living area', 4, 300.00, 5),
    ('660e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002', 'Standard Room', 'Comfortable standard accommodation', 2, 80.00, 30),
    ('660e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440003', 'Economy Room', 'Basic room for budget travelers', 2, 40.00, 15)
ON CONFLICT (id) DO NOTHING;

-- Initialize room inventory for next 30 days
INSERT INTO booking_svc.room_inventory (room_type_id, date, available_rooms)
SELECT 
    rt.id,
    CURRENT_DATE + INTERVAL '1 day' * generate_series(0, 29),
    rt.total_inventory
FROM hotel_svc.room_types rt
ON CONFLICT (room_type_id, date) DO NOTHING;