package com.servicedna.catalog.repository;

import com.servicedna.catalog.domain.ServiceOperation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ServiceOperationRepository extends JpaRepository<ServiceOperation, UUID> {
  List<ServiceOperation> findByServiceIdOrderByName(UUID serviceId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from ServiceOperation o where o.serviceId = :serviceId")
  void deleteByServiceId(UUID serviceId);
}
