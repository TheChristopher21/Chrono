package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.repositories.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void collectProjectAndDescendantIds_returnsOnlyRootWhenCompanyMissing() {
        Project project = new Project();
        project.setId(5L);

        Set<Long> ids = projectService.collectProjectAndDescendantIds(project);

        assertEquals(Set.of(5L), ids);
        verify(projectRepository, never()).findByCustomerCompanyId(any());
    }

    @Test
    void collectProjectAndDescendantIds_includesAllNestedChildren() {
        Company company = new Company();
        company.setId(1L);

        Customer customer = new Customer();
        customer.setCompany(company);

        Project root = new Project();
        root.setId(1L);
        root.setCustomer(customer);

        Project child = new Project();
        child.setId(2L);
        child.setCustomer(customer);
        child.setParent(root);

        Project grandChild = new Project();
        grandChild.setId(3L);
        grandChild.setCustomer(customer);
        grandChild.setParent(child);

        Project sibling = new Project();
        sibling.setId(4L);
        sibling.setCustomer(customer);

        when(projectRepository.findByCustomerCompanyId(company.getId()))
                .thenReturn(List.of(root, child, grandChild, sibling));

        Set<Long> result = projectService.collectProjectAndDescendantIds(root);

        assertEquals(Set.of(1L, 2L, 3L), result);
        verify(projectRepository).findByCustomerCompanyId(company.getId());
    }

    @Test
    void collectProjectAndDescendantIdsWithIds_delegatesToRepository() {
        Company company = new Company();
        company.setId(10L);

        Project root = new Project();
        root.setId(10L);

        Project child = new Project();
        child.setId(11L);
        child.setParent(root);

        when(projectRepository.findByCustomerCompanyId(company.getId()))
                .thenReturn(List.of(root, child));

        Set<Long> ids = projectService.collectProjectAndDescendantIds(10L, company.getId());

        assertEquals(Set.of(10L, 11L), ids);
        verify(projectRepository).findByCustomerCompanyId(company.getId());
    }

    @Test
    void findByIdDelegatesToRepository() {
        Project project = new Project();
        project.setId(42L);
        when(projectRepository.findById(42L)).thenReturn(Optional.of(project));

        Optional<Project> result = projectService.findById(42L);

        assertTrue(result.isPresent());
        assertEquals(project, result.get());
        verify(projectRepository).findById(42L);
    }
}
