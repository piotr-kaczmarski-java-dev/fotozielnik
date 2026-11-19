package pl.fotozielnik.users;

import java.util.List;

record UserPage(List<UserResponse> items, int page, int size, long totalItems) {}
