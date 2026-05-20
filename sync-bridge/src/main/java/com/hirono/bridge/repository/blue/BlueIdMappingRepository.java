package com.hirono.bridge.repository.blue;

import com.hirono.bridge.entity.blue.BlueIdMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlueIdMappingRepository extends JpaRepository<BlueIdMapping, Long> {
  Optional<BlueIdMapping> findByGreenUuid(String greenUuid);
}