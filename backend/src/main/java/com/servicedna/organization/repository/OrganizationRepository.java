package com.servicedna.organization.repository;

import com.servicedna.organization.domain.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
