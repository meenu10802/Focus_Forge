package com.focusforge.quest.quest;

import com.focusforge.quest.quest.QuestDtos.GenerateQuestRequest;
import com.focusforge.quest.quest.QuestDtos.QuestDto;
import com.focusforge.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService quests;

    public QuestController(QuestService quests) {
        this.quests = quests;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestDto generate(@CurrentUser UUID userId, @Valid @RequestBody GenerateQuestRequest req) {
        return quests.generate(userId, req.goal());
    }

    @GetMapping
    public List<QuestDto> list(@CurrentUser UUID userId) {
        return quests.list(userId);
    }

    @GetMapping("/{questId}")
    public QuestDto get(@CurrentUser UUID userId, @PathVariable UUID questId) {
        return quests.get(userId, questId);
    }
}
