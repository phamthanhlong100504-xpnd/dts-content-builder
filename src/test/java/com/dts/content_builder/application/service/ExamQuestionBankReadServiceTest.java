package com.dts.content_builder.application.service;

import com.dts.content_builder.api.controller.InternalQuestionController;
import com.dts.content_builder.api.response.InternalQuestionBankResponse;
import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.config.ExamSourceAccess;
import com.dts.content_builder.domain.entity.ChapterEntity;
import com.dts.content_builder.domain.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamQuestionBankReadServiceTest {
    private static final String BANK = "dts-2026-600-v1";
    private final ObjectMapper mapper = new ObjectMapper();
    private ChapterRepository chapters;
    private ExamQuestionReadService service;
    private List<ChapterEntity> published;
    private Map<UUID, List<InternalQuestionMetadataResponse>> b;
    private Map<UUID, List<InternalQuestionMetadataResponse>> a1;

    @BeforeEach
    void setUp() throws Exception {
        chapters = mock(ChapterRepository.class);
        service = spy(new ExamQuestionReadService(chapters, mock(LearningProgramRepository.class),
                mock(ChapterBlockRepository.class), mock(QuestionBlockRepository.class),
                mock(QuestionRepository.class), mock(QuestionOptionRepository.class), mapper));
        JsonNode root = fixture("screen12-source-B-real.json",
                "719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3");
        published = new ArrayList<>();
        for (JsonNode chapter : root.get("chapters")) {
            published.add(ChapterEntity.builder().id(UUID.fromString(chapter.get("id").textValue()))
                    .status(ChapterStatus.PUBLISHED).metadata(new HashMap<>(Map.of("bankVersion", BANK,
                            "sourceChapterId", chapter.get("sourceChapterId").intValue()))).build());
        }
        b = metadata(root);
        a1 = metadata(fixture("screen12-source-real.json",
                "07ec32a6bea06b30f0493abd3bdd71e131d34428167b41b2107948176b5a9749"));
        when(chapters.findPublishedByBankVersion(BANK)).thenReturn(published);
        for (var chapter : published) {
            doReturn(b.get(chapter.getId())).when(service).getMetadata(chapter.getId(), "CHAPTER", "B");
            doReturn(a1.get(chapter.getId())).when(service).getMetadata(chapter.getId(), "CHAPTER", "A1");
        }
    }

    @Test
    void actualBBankHasSixChapters600UniqueQuestions60CriticalAnd318Media() {
        InternalQuestionBankResponse result = service.getBank(BANK, "B");
        assertEquals(BANK, result.bankVersion());
        assertEquals("B", result.licenseClass());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), result.chapters().stream()
                .map(InternalQuestionBankResponse.ChapterReference::sourceChapterId).toList());
        assertEquals(600, result.questions().size());
        assertEquals(600, result.questions().stream().map(InternalQuestionMetadataResponse::getId).distinct().count());
        assertEquals(60, result.questions().stream().filter(q -> q.getIsCritical()).count());
        assertEquals(318, result.questions().stream().filter(q -> !q.getMediaFileIds().isEmpty()).count());
        for (var chapter : published) verify(service).getMetadata(chapter.getId(), "CHAPTER", "B");
        verify(service, never()).getMetadata(any(), any(), isNull());
        assertEquals(b.values().stream().flatMap(List::stream).toList(), result.questions());
    }

    @Test
    void actualA1BankIsLicensedAndIncludesEmptyConstructionChapter() {
        var result = service.getBank(BANK, " a1 ");
        assertEquals("A1", result.licenseClass());
        assertEquals(6, result.chapters().size());
        assertEquals(250, result.questions().size());
        assertEquals(20, result.questions().stream().filter(q -> q.getIsCritical()).count());
        for (var chapter : published) verify(service).getMetadata(chapter.getId(), "CHAPTER", "A1");
    }

    @Test
    void repeatedBankReadIsStableAndDoesNotModifyActualMetadataLists() {
        var first = service.getBank(BANK, "B");
        assertEquals(first, service.getBank(BANK, "B"));
        assertEquals(600, b.values().stream().mapToInt(List::size).sum());
        // Existing JSON metadata can store the same chapter number as a string.
        published.get(0).getMetadata().put("sourceChapterId", "1");
        assertEquals(first, service.getBank(BANK, "B"));
    }

    @Test
    void repeatedIdenticalQuestionLinksAreDeduplicatedWithoutMutatingSourceMetadata() {
        var chapter = published.get(0);
        List<InternalQuestionMetadataResponse> repeated = new ArrayList<>(b.get(chapter.getId()));
        repeated.add(repeated.get(0));
        doReturn(repeated).when(service).getMetadata(chapter.getId(), "CHAPTER", "B");
        assertEquals(600, service.getBank(BANK, "B").questions().size());
        assertEquals(181, repeated.size());
    }

    @Test
    void missingLicenseCannotFallBackToAnUnlicensedBankRead() {
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, null));
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, " "));
        verifyNoInteractions(chapters);
    }

    @Test
    void unknownLicenseAndInvalidBankAreRejectedBeforeQuery() {
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B2"));
        for (String bank : new String[]{null, "", "../bank"}) {
            assertThrows(BusinessValidationException.class, () -> service.getBank(bank, "B"));
        }
        verifyNoInteractions(chapters);
    }

    @Test
    void nonexistentPublishedBankIsNotFound() {
        when(chapters.findPublishedByBankVersion(BANK)).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.getBank(BANK, "B"));
    }

    @Test
    void incompleteOrExtraChapterCatalogCannotBeAcceptedAsCompleteBank() {
        when(chapters.findPublishedByBankVersion(BANK)).thenReturn(published.subList(0, 5));
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
        var extra = new ArrayList<>(published);
        extra.add(published.get(0));
        when(chapters.findPublishedByBankVersion(BANK)).thenReturn(extra);
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
    }

    @Test
    void duplicateSourceChapterNumbersCannotMixImports() {
        published.get(1).getMetadata().put("sourceChapterId", 1);
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
    }

    @Test
    void wrongBankDraftAndSoftDeletedChaptersAreRejectedEvenIfRepositoryReturnsThem() {
        var chapter = published.get(0);
        chapter.getMetadata().put("bankVersion", "other-bank");
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
        chapter.getMetadata().put("bankVersion", BANK);
        chapter.setStatus(ChapterStatus.DRAFT);
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setDeletedAt(LocalDateTime.now());
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
    }

    @Test
    void missingFractionalAndOutOfRangeSourceChapterNumbersAreRejected() {
        var chapter = published.get(0);
        for (Object invalid : new Object[]{"x", "1.25", 1.25, 0, 7}) {
            chapter.getMetadata().put("sourceChapterId", invalid);
            assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
        }
        chapter.getMetadata().remove("sourceChapterId");
        assertThrows(BusinessValidationException.class, () -> service.getBank(BANK, "B"));
    }

    @Test
    void controllerChecksAccessBeforeReadingAndAddsNoStore() {
        ExamSourceAccess access = mock(ExamSourceAccess.class);
        var controller = new InternalQuestionController(service, access);
        var response = controller.getQuestionBank(BANK, "B", null);
        verify(access).requireAccess(null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
    }

    @Test
    void controllerDenialNeverCallsTheSourceReadService() {
        var reader = mock(ExamQuestionReadService.class);
        var access = mock(ExamSourceAccess.class);
        doThrow(new AccessDeniedException("denied")).when(access).requireAccess(null);
        var controller = new InternalQuestionController(reader, access);
        assertThrows(AccessDeniedException.class, () -> controller.getQuestionBank(BANK, "B", null));
        verifyNoInteractions(reader);
    }

    private JsonNode fixture(String resource, String hash) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            byte[] bytes = stream.readAllBytes();
            assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            return mapper.readTree(bytes);
        }
    }

    private Map<UUID, List<InternalQuestionMetadataResponse>> metadata(JsonNode root) {
        Map<UUID, List<InternalQuestionMetadataResponse>> result = new java.util.LinkedHashMap<>();
        for (JsonNode chapter : root.get("chapters")) {
            List<InternalQuestionMetadataResponse> items = new ArrayList<>();
            chapter.get("items").forEach(item -> items.add(mapper.convertValue(item, InternalQuestionMetadataResponse.class)));
            result.put(UUID.fromString(chapter.get("id").textValue()), List.copyOf(items));
        }
        return result;
    }
}
