package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.routing.RoutingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoutingProfileRepository extends JpaRepository<RoutingProfile, UUID> { Optional<RoutingProfile> findByCode(String code); }
