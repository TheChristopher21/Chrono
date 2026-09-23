-- Keep the paginated room plan bounded for large properties; reuse V22 room feature data.
create index idx_pms_room_property_active_number on pms_rooms (property_id, active, floor, room_number);
