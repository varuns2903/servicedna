package com.servicedna.graph.repository;

import com.servicedna.billing.domain.PlanType;
import com.servicedna.graph.domain.ObservedCall;
import com.servicedna.graph.domain.Protocol;
import com.servicedna.graph.domain.TargetKind;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ObservedCallRepository extends JpaRepository<ObservedCall, UUID> {

  Optional<ObservedCall>
      findByOrganizationIdAndBucketStartAndSourceServiceIdAndSourceOperationAndTargetKindAndTargetNameAndTargetOperationAndProtocol(
          UUID organizationId,
          OffsetDateTime bucketStart,
          UUID sourceServiceId,
          String sourceOperation,
          TargetKind targetKind,
          String targetName,
          String targetOperation,
          Protocol protocol);

  List<ObservedCall> findByOrganizationIdAndBucketStartGreaterThanEqual(
      UUID organizationId, OffsetDateTime since);

  @Modifying(clearAutomatically = true)
  @Query("delete from ObservedCall c where c.bucketStart < :cutoff")
  int deleteByBucketStartBefore(OffsetDateTime cutoff);

  @Modifying(clearAutomatically = true)
  @Query(
      "delete from ObservedCall c where c.bucketStart < :cutoff and c.organizationId in"
          + " (select sub.organization.id from Subscription sub where sub.planType = :plan)")
  int deleteForPlanOlderThan(PlanType plan, OffsetDateTime cutoff);
}
