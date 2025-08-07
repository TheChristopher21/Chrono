package com.chrono.chrono.services;

import com.chrono.chrono.entities.Task;
import com.chrono.chrono.repositories.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;

    public List<Task> getTasks(Long projectId) {
        if (projectId != null) {
            return taskRepository.findByProjectId(projectId);
        }
        return taskRepository.findAll();
    }
}
