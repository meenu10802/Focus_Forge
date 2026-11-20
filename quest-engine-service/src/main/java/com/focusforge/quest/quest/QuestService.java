package com.focusforge.quest.quest;

import com.focusforge.quest.ai.AiQuestClient;
import com.focusforge.quest.quest.QuestDtos.QuestDto;
import com.focusforge.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class QuestService {

    private final QuestRepository quests;
    private final AiQuestClient ai;
    private final TransactionTemplate tx;
    private final int listLimit;

    public QuestService(QuestRepository quests, AiQuestClient ai, TransactionTemplate tx,
                        @Value("${focus.quests.list-limit:20}") int listLimit) {
        this.quests = quests;
        this.ai = ai;
        this.tx = tx;
        this.listLimit = listLimit;
    }

    /**
     * The (slow) AI call happens OUTSIDE the transaction: holding a DB
     * connection for 10+ seconds while an LLM thinks would starve the pool.
     */
    public QuestDto generate(UUID userId, String goal) {
        String cleanGoal = goal.strip();
        AiQuestClient.Plan plan = ai.questline(cleanGoal);

        return tx.execute(status -> {
            var quest = new Quest(userId, cleanGoal, clip(plan.questTitle(), 80),
                    Quest.Source.valueOf(plan.source().name()), plan.provider());
            for (var t : plan.tasks()) {
                quest.addTask(clip(t.title(), 100), clip(t.description(), 250), t.minutes(),
                        t.difficulty() == null ? Difficulty.MEDIUM : t.difficulty());
            }
            return QuestDto.of(quests.save(quest));
        });
    }

    @Transactional(readOnly = true)
    public List<QuestDto> list(UUID userId) {
        return quests.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(listLimit)).stream()
                .map(QuestDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestDto get(UUID userId, UUID questId) {
        return quests.findByIdAndUserId(questId, userId)
                .map(QuestDto::of)
                .orElseThrow(() -> ApiException.notFound("Quest not found."));
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String clean = s.strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
