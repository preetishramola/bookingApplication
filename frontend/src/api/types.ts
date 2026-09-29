// Shapes returned by the Spring Boot API (see the DTOs in src/main/java/.../dto).

export interface Hotel {
  id: number;
  name: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  /** Average of verified reviews, or the initial rating while there are none. */
  rating?: number;
  reviewCount: number;
  description?: string;
  latitude?: number;
  longitude?: number;
  amenities: string[];
}

/** Geo and vibe search results. */
export interface HotelSearchResult {
  hotel: Hotel;
  distanceKm?: number;
  matchScore?: number;
}

export type RoomType = 'STANDARD' | 'DELUXE' | 'SUITE';

export interface Room {
  id: number;
  roomNumber: string;
  type: RoomType | string;
  pricePerNight: number;
}

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

export interface Booking {
  bookingId: number;
  roomId: number;
  hotelName: string;
  checkIn: string; // yyyy-mm-dd
  checkOut: string;
  totalAmount: number;
  status: BookingStatus;
  createdAt: string;
}

export interface Review {
  id: number;
  hotelId: number;
  hotelName: string;
  bookingId?: number; // only on the author's own reviews
  reviewerName: string;
  rating: number;
  comment?: string;
  stayMonth: string; // yyyy-mm
  createdAt: string;
  updatedAt?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type AddOn = 'BREAKFAST' | 'AIRPORT_PICKUP';
export type PaymentMethod = 'UPI' | 'CREDIT_CARD';

export interface HotelInput {
  name: string;
  address?: string;
  city: string;
  state?: string;
  country?: string;
  rating?: number;
  description?: string;
  latitude?: number;
  longitude?: number;
  amenities: string[];
}
