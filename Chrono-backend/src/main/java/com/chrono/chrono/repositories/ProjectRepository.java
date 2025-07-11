package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
