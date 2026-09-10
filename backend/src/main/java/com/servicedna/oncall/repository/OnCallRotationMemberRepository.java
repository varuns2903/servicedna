package com.servicedna.oncall.repository;

import com.servicedna.oncall.domain.OnCallRotationMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnCallRotationMemberRepository extends JpaRepository<OnCallRotationMember, UUID> {
  List<OnCallRotationMember> findByRotationIdOrderByPositionAsc(UUID rotationId);

  void deleteByRotationId(UUID rotationId);
}
