package com.chrono.chrono.services;

import com.chrono.chrono.entities.Project;
import com.chrono.chrono.repositories.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ProjectService {
    @Autowired
    private ProjectRepository projectRepository;

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public List<Project> findAllByCompanyId(Long companyId) {
        return projectRepository.findByCustomerCompanyId(companyId);
    }

    public List<Project> findAllByCompanyIdOrdered(Long companyId) {
        return projectRepository.findByCustomerCompanyIdOrderByNameAsc(companyId);
    }

    public List<Project> findRootProjects(Long companyId) {
        return projectRepository.findByCustomerCompanyIdAndParentIsNullOrderByNameAsc(companyId);
    }

    public List<Project> findChildren(Long parentId) {
        return projectRepository.findByParentId(parentId);
    }

    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    public Project save(Project project) {
        return projectRepository.save(project);
    }

    public void deleteById(Long id) {
        projectRepository.deleteById(id);
    }

    public Set<Long> collectProjectAndDescendantIds(Project root) {
        if (root == null || root.getId() == null) {
            return Set.of();
        }
        if (root.getCustomer() == null || root.getCustomer().getCompany() == null) {
            return Set.of(root.getId());
        }
        List<Project> all = projectRepository.findByCustomerCompanyId(root.getCustomer().getCompany().getId());
        return collectProjectAndDescendantIds(root.getId(), all);
    }

    public Set<Long> collectProjectAndDescendantIds(Long projectId, Long companyId) {
        if (projectId == null || companyId == null) {
            return Set.of();
        }
        List<Project> all = projectRepository.findByCustomerCompanyId(companyId);
        return collectProjectAndDescendantIds(projectId, all);
    }

    private Set<Long> collectProjectAndDescendantIds(Long projectId, List<Project> all) {
        Map<Long, List<Project>> childrenMap = new HashMap<>();
        for (Project p : all) {
            Long parentId = p.getParent() != null ? p.getParent().getId() : null;
            childrenMap.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(p);
        }
        Set<Long> ids = new HashSet<>();
        collectRecursive(projectId, childrenMap, ids);
        return ids;
    }

    private void collectRecursive(Long currentId, Map<Long, List<Project>> childrenMap, Set<Long> target) {
        if (currentId == null || target.contains(currentId)) {
            return;
        }
        target.add(currentId);
        List<Project> children = childrenMap.get(currentId);
        if (children == null) {
            return;
        }
        for (Project child : children) {
            collectRecursive(child.getId(), childrenMap, target);
        }
    }
}
