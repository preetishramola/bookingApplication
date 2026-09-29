import { api } from './client';
import type {
  AddOn,
  Booking,
  Hotel,
  HotelInput,
  HotelSearchResult,
  Page,
  PaymentMethod,
  Review,
  Room,
  RoomType,
} from './types';

// ---- Auth ----
export const login = (email: string, password: string) =>
  api<{ token: string }>('/api/auth/login', { method: 'POST', body: { email, password } });

export const register = (name: string, email: string, password: string) =>
  api<string>('/api/auth/register', { method: 'POST', body: { name, email, password } });

// ---- Hotels & search ----
export const searchHotels = (params: { name?: string; city?: string; minRating?: number }) =>
  api<Hotel[]>('/api/hotels/search', { query: params });

export const vibeSearch = (q: string, limit = 12) =>
  api<HotelSearchResult[]>('/api/hotels/vibe-search', { query: { q, limit } });

export const nearbyHotels = (lat: number, lng: number, radiusKm: number, limit = 20) =>
  api<HotelSearchResult[]>('/api/hotels/nearby', { query: { lat, lng, radiusKm, limit } });

export const getHotel = (id: number) => api<Hotel>(`/api/hotels/${id}`);

export const getRooms = (hotelId: number) => api<Room[]>(`/api/hotels/${hotelId}/rooms`);

export const getAvailableRooms = (hotelId: number, checkIn: string, checkOut: string) =>
  api<Room[]>(`/api/hotels/${hotelId}/rooms/available`, { query: { checkIn, checkOut } });

// ---- Bookings ----
export const createBooking = (
  body: { roomId: number; checkIn: string; checkOut: string; addOns: AddOn[]; paymentMethod: PaymentMethod },
  idempotencyKey: string,
) => api<Booking>('/api/bookings', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } });

export const myBookings = () => api<Booking[]>('/api/bookings/me');

export const cancelBooking = (bookingId: number) => api<void>(`/api/bookings/${bookingId}`, { method: 'DELETE' });

// ---- Reviews ----
export const hotelReviews = (hotelId: number, page = 0, size = 5) =>
  api<Page<Review>>(`/api/hotels/${hotelId}/reviews`, { query: { page, size } });

export const myReviews = () => api<Review[]>('/api/reviews/me');

export const createReview = (bookingId: number, rating: number, comment: string) =>
  api<Review>(`/api/bookings/${bookingId}/review`, { method: 'POST', body: { rating, comment } });

export const updateReview = (reviewId: number, rating: number, comment: string) =>
  api<Review>(`/api/reviews/${reviewId}`, { method: 'PUT', body: { rating, comment } });

export const deleteReview = (reviewId: number) => api<void>(`/api/reviews/${reviewId}`, { method: 'DELETE' });

// ---- Admin ----
export const addHotel = (hotel: HotelInput) => api<Hotel>('/api/admin/hotels/add', { method: 'POST', body: hotel });

export const addRoom = (hotelId: number, room: { roomNumber: string; type: RoomType; pricePerNight: number }) =>
  api<Room>(`/api/admin/hotels/${hotelId}/rooms`, { method: 'POST', body: room });

export const refreshEmbeddings = (onlyMissing: boolean) =>
  api<{ embedded: number; failed: number; remaining: number }>('/api/admin/hotels/embeddings/refresh', {
    method: 'POST',
    query: { onlyMissing: String(onlyMissing) },
  });
