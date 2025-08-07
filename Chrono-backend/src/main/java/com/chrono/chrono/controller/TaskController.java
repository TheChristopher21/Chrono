package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Task;
import com.chrono.chrono.services.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping
    public List<Task> getTasks(@RequestParam(value = "projectId", required = false) Long projectId) {
        return taskService.getTasks(projectId);
    }
}
