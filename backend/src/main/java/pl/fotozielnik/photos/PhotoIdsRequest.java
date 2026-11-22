package pl.fotozielnik.photos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Photos to move to / restore from the trash; ids of other users' photos are ignored. */
record PhotoIdsRequest(@NotNull @Size(min = 1, max = 500) List<@NotNull UUID> ids) {}
