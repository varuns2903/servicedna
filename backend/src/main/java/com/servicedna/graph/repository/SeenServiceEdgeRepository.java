package com.servicedna.graph.repository;

import com.servicedna.graph.domain.SeenServiceEdge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeenServiceEdgeRepository extends JpaRepository<SeenServiceEdge, SeenServiceEdge.Key> {}
