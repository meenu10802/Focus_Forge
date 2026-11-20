package com.focusforge.identity.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {
}
