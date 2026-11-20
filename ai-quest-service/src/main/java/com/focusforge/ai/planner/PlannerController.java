package com.focusforge.ai.planner;

import com.focusforge.ai.planner.PlanDtos.PlanResponse;
import com.focusforge.ai.planner.PlanDtos.QuestlineRequest;
import com.focusforge.ai.planner.PlanDtos.SplitRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only; the gateway never routes /internal/**. */
@RestController
@RequestMapping("/internal/ai")
public class PlannerController {

    private final QuestPlanner planner;

    public PlannerController(QuestPlanner planner) {
        this.planner = planner;
    }

    @PostMapping("/questline")
    public PlanResponse questline(@Valid @RequestBody QuestlineRequest req) {
        return planner.questline(req.goal());
    }

    @PostMapping("/split")
    public PlanResponse split(@Valid @RequestBody SplitRequest req) {
        return planner.split(req.taskTitle(), req.taskDescription(), req.goal());
    }
}
