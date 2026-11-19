package com.laimengchao.sso.forum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.forum.module.admin.dto.ThreadStatusUpdateRequest;
import com.laimengchao.sso.forum.module.badge.dto.BadgeRequest;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.member.dto.MemberStatsDTO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import com.laimengchao.sso.forum.module.poll.service.PollService;
import com.laimengchao.sso.forum.module.poll.vo.PollOptionVO;
import com.laimengchao.sso.forum.module.poll.vo.PollVO;
import com.laimengchao.sso.forum.module.site.dto.SiteConfigRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumModelCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesLongIdsAsStringsAndMetricsAsNumbers() throws Exception {
        long id = 9_007_199_254_740_993L;
        ThreadVO thread = new ThreadVO();
        thread.setId(id);
        thread.setAuthorAccountId(id + 1);
        thread.setCoverAttachmentId(id + 2);
        thread.setReward(20);
        thread.setReplies(3);
        thread.setViews(100);
        thread.setLikes(8);

        JsonNode threadJson = objectMapper.readTree(objectMapper.writeValueAsString(thread));
        assertTextValue(threadJson, "id", id);
        assertTextValue(threadJson, "authorAccountId", id + 1);
        assertTextValue(threadJson, "coverAttachmentId", id + 2);
        assertNumericValue(threadJson, "reward", 20);
        assertNumericValue(threadJson, "replies", 3);
        assertNumericValue(threadJson, "views", 100);
        assertNumericValue(threadJson, "likes", 8);

        PollOptionVO option = new PollOptionVO();
        option.setId(id + 3);
        option.setVotes(6);
        PollVO poll = new PollVO();
        poll.setId(id + 4);
        poll.setMaxChoices(2);
        poll.setTotalVotes(12);
        poll.setSelectedOptionIds(List.of(id, id + 3));
        poll.setOptions(List.of(option));

        JsonNode pollJson = objectMapper.readTree(objectMapper.writeValueAsString(poll));
        assertTextValue(pollJson, "id", id + 4);
        assertNumericValue(pollJson, "maxChoices", 2);
        assertNumericValue(pollJson, "totalVotes", 12);
        assertTextValue(pollJson.at("/selectedOptionIds/0"), id);
        assertTextValue(pollJson.at("/selectedOptionIds/1"), id + 3);
        assertTextValue(pollJson.at("/options/0/id"), id + 3);
        assertNumericValue(pollJson.at("/options/0/votes"), 6);

        MemberStatsDTO stats = new MemberStatsDTO();
        stats.setThreadsCount(7L);
        stats.setCommentsCount(11L);
        stats.setLikesReceived(13L);
        stats.setTotalPoints(320L);
        JsonNode statsJson = objectMapper.readTree(objectMapper.writeValueAsString(stats));
        assertNumericValue(statsJson, "threadsCount", 7);
        assertNumericValue(statsJson, "commentsCount", 11);
        assertNumericValue(statsJson, "likesReceived", 13);
        assertNumericValue(statsJson, "totalPoints", 320);
    }

    @Test
    void distinguishesOmittedAndExplicitNullSiteConfigFields() throws Exception {
        SiteConfigRequest omitted = objectMapper.readValue("{}", SiteConfigRequest.class);
        assertFalse(omitted.isAboutContentPresent());
        assertFalse(omitted.isFooterTextPresent());

        SiteConfigRequest explicitNull = objectMapper.readValue(
                "{\"aboutContent\":null,\"footerText\":\"footer\"}", SiteConfigRequest.class);
        assertTrue(explicitNull.isAboutContentPresent());
        assertNull(explicitNull.getAboutContent());
        assertTrue(explicitNull.isFooterTextPresent());
        assertEquals("footer", explicitNull.getFooterText());
    }

    @Test
    void tracksBadgeFieldsWithoutChangingTheirInputRepresentation() throws Exception {
        BadgeRequest request = objectMapper.readValue(
                "{\"description\":null,\"threshold\":\"5\"}", BadgeRequest.class);

        assertFalse(request.isNamePresent());
        assertTrue(request.isDescriptionPresent());
        assertNull(request.getDescription());
        assertTrue(request.isThresholdPresent());
        assertEquals("5", request.getThreshold());
        assertFalse(request.isRuleTypePresent());
    }

    @Test
    void tracksAdminPartialUpdateFieldsAndUnknownFields() throws Exception {
        ThreadStatusUpdateRequest request = objectMapper.readValue(
                "{\"deleted\":null,\"pinned\":false,\"legacyFlag\":1}", ThreadStatusUpdateRequest.class);

        assertTrue(request.isDeletedPresent());
        assertNull(request.getDeleted());
        assertTrue(request.isPinnedPresent());
        assertEquals(Boolean.FALSE, request.getPinned());
        assertFalse(request.isSolvedPresent());
        assertFalse(request.isRecommendedPresent());
        assertTrue(request.getUnsupportedFields().contains("legacyFlag"));
        assertEquals(3, request.fieldCount());
    }

    @Test
    void acceptsLegacyPollOptionJsonAndBooleanEnabled() throws Exception {
        PollService service = new PollService(null, null, null);
        PollConfigRequest disabled = objectMapper.readValue("{\"enabled\":false}", PollConfigRequest.class);
        assertNull(service.normalizeDraftConfig(disabled));

        PollConfigRequest legacy = objectMapper.readValue("{"
                + "\"enabled\":true,\"mode\":\"multiple\",\"maxChoices\":\"2\","
                + "\"options\":[\"纯文本\",{\"text\":\"对象选项\"},null,{\"text\":123}]}"
                , PollConfigRequest.class);
        PollConfigDTO normalized = service.normalizeDraftConfig(legacy);

        assertEquals("MULTIPLE", normalized.getMode());
        assertEquals(2, normalized.getMaxChoices());
        assertEquals(List.of("纯文本", "对象选项", "123"), normalized.getOptions());
    }

    private static void assertTextValue(JsonNode parent, String field, long expected) {
        assertTextValue(parent.get(field), expected);
    }

    private static void assertTextValue(JsonNode value, long expected) {
        assertTrue(value.isTextual(), value.toString());
        assertEquals(Long.toString(expected), value.textValue());
    }

    private static void assertNumericValue(JsonNode parent, String field, long expected) {
        assertNumericValue(parent.get(field), expected);
    }

    private static void assertNumericValue(JsonNode value, long expected) {
        assertTrue(value.isIntegralNumber(), value.toString());
        assertEquals(expected, value.longValue());
    }
}
