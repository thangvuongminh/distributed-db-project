package com.hirono.bridge.repository.green;

import com.hirono.bridge.entity.green.GreenIdMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GreenIdMappingRepository extends JpaRepository<GreenIdMapping, String> {
  Optional<GreenIdMapping> findByBlueId(Long blueId);
}