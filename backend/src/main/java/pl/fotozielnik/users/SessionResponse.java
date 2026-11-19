package pl.fotozielnik.users;

import org.jspecify.annotations.Nullable;

/** Who is signed in; {@code user} is null when nobody is (a normal state, not an error). */
record SessionResponse(@Nullable MeResponse user) {}
