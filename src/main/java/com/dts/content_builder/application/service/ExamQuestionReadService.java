package com.dts.content_builder.application.service;

import com.dts.content_builder.api.response.InternalQuestionDetailResponse;
import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.api.response.InternalQuestionOptionResponse;
import com.dts.content_builder.api.response.InternalQuestionBankResponse;
import com.dts.content_builder.application.enums.ChapterBlockStatus;
import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.application.enums.LearningProgramStatus;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.enums.QuestionType;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.domain.entity.ChapterBlockEntity;
import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import com.dts.content_builder.domain.repository.ChapterBlockRepository;
import com.dts.content_builder.domain.repository.ChapterRepository;
import com.dts.content_builder.domain.repository.LearningProgramRepository;
import com.dts.content_builder.domain.repository.QuestionBlockRepository;
import com.dts.content_builder.domain.repository.QuestionOptionRepository;
import com.dts.content_builder.domain.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ExamQuestionReadService {

    private static final int MAX_BATCH_SIZE = 1000;
    private static final Set<String> LICENSE_CLASSES = Set.of(
            "A1", "A", "B1", "B", "C1", "C", "D1", "D2", "D",
            "BE", "C1E", "CE", "D1E", "D2E", "DE");
    private static final Pattern MEDIA_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ChapterRepository chapterRepository;
    private final LearningProgramRepository learningProgramRepository;
    private final ChapterBlockRepository chapterBlockRepository;
    private final QuestionBlockRepository questionBlockRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final ObjectMapper objectMapper;

    /** A complete catalog and licensed metadata from one repeatable-read database snapshot. */
    public InternalQuestionBankResponse getBank(String bankVersion, String licenseClass) {
        String license = normalizeLicense(licenseClass);
        if (license == null) {
            throw new BusinessValidationException("licenseClass is required for a source bank read");
        }
        if (bankVersion == null || !bankVersion.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new BusinessValidationException("A valid bankVersion is required");
        }
        var chapters = chapterRepository.findPublishedByBankVersion(bankVersion);
        if (chapters.isEmpty()) {
            throw new ResourceNotFoundException("Published source bank not found");
        }
        if (chapters.size() != 6) {
            throw new BusinessValidationException("The published source bank must have exactly six chapters");
        }
        Set<Integer> numbers = new HashSet<>();
        Set<UUID> chapterIds = new HashSet<>();
        List<InternalQuestionBankResponse.ChapterReference> references = new ArrayList<>();
        for (var chapter : chapters) {
            Map<String, Object> metadata = chapter.getMetadata();
            int sourceId;
            try {
                Object value = metadata == null ? null : metadata.get("sourceChapterId");
                if (!(value instanceof Number) && !(value instanceof String text && text.matches("[1-6]"))) {
                    throw new IllegalArgumentException("Missing source chapter number");
                }
                sourceId = new BigDecimal(value.toString()).intValueExact();
            } catch (IllegalArgumentException | ArithmeticException error) {
                throw new BusinessValidationException("Source chapter has an invalid sourceChapterId");
            }
            if (chapter.getId() == null || chapter.getDeletedAt() != null
                    || chapter.getStatus() != ChapterStatus.PUBLISHED
                    || !bankVersion.equals(metadata.get("bankVersion"))
                    || sourceId < 1 || sourceId > 6 || !numbers.add(sourceId)
                    || !chapterIds.add(chapter.getId())) {
                throw new BusinessValidationException("Source bank chapter catalog is inconsistent");
            }
            references.add(new InternalQuestionBankResponse.ChapterReference(chapter.getId(), sourceId, bankVersion));
        }
        references.sort(Comparator.comparingInt(InternalQuestionBankResponse.ChapterReference::sourceChapterId));
        Map<UUID, InternalQuestionMetadataResponse> questions = new LinkedHashMap<>();
        for (var chapter : references) {
            for (var question : getMetadata(chapter.id(), "CHAPTER", license)) {
                InternalQuestionMetadataResponse previous = questions.get(question.getId());
                if (previous == null) {
                    questions.put(question.getId(), new InternalQuestionMetadataResponse(
                            question.getId(), question.getOptionIds(), question.getChapterIds(), question.getIsCritical(),
                            question.getStatus(), question.getQuestionType(), question.getMediaFileIds(), question.getContentVersion()));
                } else {
                    if (!java.util.Objects.equals(previous.getOptionIds(), question.getOptionIds())
                            || !java.util.Objects.equals(previous.getIsCritical(), question.getIsCritical())
                            || !java.util.Objects.equals(previous.getStatus(), question.getStatus())
                            || !java.util.Objects.equals(previous.getQuestionType(), question.getQuestionType())
                            || !java.util.Objects.equals(previous.getMediaFileIds(), question.getMediaFileIds())
                            || !java.util.Objects.equals(previous.getContentVersion(), question.getContentVersion())) {
                        throw new BusinessValidationException("Repeated source question metadata disagrees");
                    }
                    LinkedHashSet<UUID> linkedChapters = new LinkedHashSet<>(previous.getChapterIds());
                    linkedChapters.addAll(question.getChapterIds());
                    previous.setChapterIds(List.copyOf(linkedChapters));
                }
            }
        }
        return new InternalQuestionBankResponse(bankVersion, license, List.copyOf(references),
                List.copyOf(questions.values()));
    }

    public List<InternalQuestionMetadataResponse> getMetadata(
            UUID contentId, String contentType, String licenseClass) {
        String license = normalizeLicense(licenseClass);
        List<UUID> chapterIds = publishedChapterIds(contentId, contentType);
        if (chapterIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> chapterRank = new HashMap<>();
        for (int i = 0; i < chapterIds.size(); i++) {
            chapterRank.put(chapterIds.get(i), i);
        }
        List<QuestionBlockEntity> links = questionBlockRepository
                .findByChapterIdInAndDeletedAtIsNull(chapterIds);
        Map<UUID, QuestionBlockEntity> linksById = links.stream()
                .collect(Collectors.toMap(QuestionBlockEntity::getId, link -> link));

        Map<UUID, LinkedHashSet<UUID>> chaptersByQuestion = new LinkedHashMap<>();
        links.stream()
                .filter(link -> link.getQuestionId() != null)
                .filter(link -> chapterRank.containsKey(link.getChapterId()))
                .filter(link -> publishedQuestionPath(link, linksById))
                .sorted(Comparator
                        .comparing((QuestionBlockEntity link) -> chapterRank.get(link.getChapterId()))
                        .thenComparing(QuestionBlockEntity::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QuestionBlockEntity::getId))
                .forEach(link -> chaptersByQuestion
                        .computeIfAbsent(link.getQuestionId(), ignored -> new LinkedHashSet<>())
                        .add(link.getChapterId()));
        if (chaptersByQuestion.isEmpty()) {
            return List.of();
        }

        Map<UUID, QuestionEntity> questions = questionRepository
                .findByIdInAndDeletedAtIsNull(new ArrayList<>(chaptersByQuestion.keySet()))
                .stream().collect(Collectors.toMap(QuestionEntity::getId, question -> question));
        List<UUID> eligibleIds = chaptersByQuestion.keySet().stream()
                .filter(id -> questions.containsKey(id))
                .filter(id -> questions.get(id).getDeletedAt() == null)
                .filter(id -> questions.get(id).getStatus() == QuestionStatus.PUBLISHED)
                .filter(id -> matchesLicense(questions.get(id), license))
                .toList();
        if (eligibleIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<QuestionOptionEntity>> options = optionsByQuestion(eligibleIds);
        List<InternalQuestionMetadataResponse> result = new ArrayList<>();
        for (UUID id : eligibleIds) {
            QuestionData data = prepare(questions.get(id), options.getOrDefault(id, List.of()), license);
            result.add(InternalQuestionMetadataResponse.builder()
                    .id(id)
                    .optionIds(data.options().stream().map(InternalQuestionOptionResponse::getId).toList())
                    .chapterIds(List.copyOf(chaptersByQuestion.get(id)))
                    .isCritical(data.critical())
                    .status(data.question().getStatus().name())
                    .questionType(data.question().getType().name())
                    .mediaFileIds(data.mediaFileIds())
                    .contentVersion(data.revision())
                    .build());
        }
        return result;
    }

    public List<InternalQuestionDetailResponse> getBatch(
            List<UUID> questionIds, String licenseClass) {
        String license = normalizeLicense(licenseClass);
        if (questionIds == null || questionIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessValidationException("questionIds must not contain null values");
        }
        if (questionIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessValidationException("Maximum question batch size is " + MAX_BATCH_SIZE);
        }
        if (new HashSet<>(questionIds).size() != questionIds.size()) {
            throw new BusinessValidationException("questionIds must be unique");
        }
        if (questionIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, QuestionEntity> questions = questionRepository
                .findByIdInAndDeletedAtIsNull(questionIds).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, question -> question));
        Map<UUID, List<QuestionOptionEntity>> options = optionsByQuestion(questionIds);
        List<InternalQuestionDetailResponse> result = new ArrayList<>();
        for (UUID id : questionIds) {
            QuestionEntity question = questions.get(id);
            if (question == null || question.getDeletedAt() != null) {
                throw new ResourceNotFoundException("Question not found: " + id);
            }
            if (question.getStatus() != QuestionStatus.PUBLISHED) {
                throw invalid(id, "Question is not PUBLISHED");
            }
            if (!matchesLicense(question, license)) {
                throw invalid(id, "Question is not applicable to license " + license);
            }
            QuestionData data = prepare(question, options.getOrDefault(id, List.of()), license);
            result.add(InternalQuestionDetailResponse.builder()
                    .id(id)
                    .content(question.getContent())
                    .type(question.getType().name())
                    .isCritical(data.critical())
                    .mediaFileIds(data.mediaFileIds())
                    .options(data.options())
                    .status(question.getStatus().name())
                    .contentVersion(data.revision())
                    .explanations(question.getExplanations())
                    .build());
        }
        return result;
    }

    private List<UUID> publishedChapterIds(UUID contentId, String contentType) {
        if (contentId == null || contentType == null) {
            throw new BusinessValidationException("contentId and contentType are required");
        }
        String type = contentType.trim().toUpperCase(Locale.ROOT);
        if ("CHAPTER".equals(type)) {
            var chapter = chapterRepository.findByIdAndDeletedAtIsNull(contentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + contentId));
            if (chapter.getStatus() != ChapterStatus.PUBLISHED) {
                throw new BusinessValidationException("Source chapter is not PUBLISHED: " + contentId);
            }
            return List.of(contentId);
        }
        if (!"LEARNING_PROGRAM".equals(type)) {
            throw new BusinessValidationException("contentType must be CHAPTER or LEARNING_PROGRAM");
        }
        var program = learningProgramRepository.findByIdAndDeletedAtIsNull(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Learning program not found: " + contentId));
        if (program.getStatus() != LearningProgramStatus.PUBLISHED) {
            throw new BusinessValidationException("Source learning program is not PUBLISHED: " + contentId);
        }
        List<ChapterBlockEntity> links = chapterBlockRepository
                .findByLearningProgramIdAndDeletedAtIsNull(contentId);
        Map<UUID, ChapterBlockEntity> linksById = links.stream()
                .collect(Collectors.toMap(ChapterBlockEntity::getId, link -> link));
        List<UUID> linkedIds = links.stream()
                .filter(link -> link.getChapterId() != null)
                .filter(link -> publishedChapterPath(link, linksById))
                .sorted(Comparator.comparing(ChapterBlockEntity::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChapterBlockEntity::getId))
                .map(ChapterBlockEntity::getChapterId).distinct().toList();
        if (linkedIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> publishedIds = chapterRepository.findAllById(linkedIds).stream()
                .filter(chapter -> chapter.getDeletedAt() == null)
                .filter(chapter -> chapter.getStatus() == ChapterStatus.PUBLISHED)
                .map(chapter -> chapter.getId()).collect(Collectors.toSet());
        return linkedIds.stream().filter(publishedIds::contains).toList();
    }

    private boolean publishedChapterPath(
            ChapterBlockEntity link, Map<UUID, ChapterBlockEntity> links) {
        Set<UUID> seen = new HashSet<>();
        ChapterBlockEntity current = link;
        while (current != null && seen.add(current.getId())) {
            if (current.getDeletedAt() != null || current.getStatus() != ChapterBlockStatus.PUBLISHED) {
                return false;
            }
            if (current.getParentId() == null) {
                return true;
            }
            current = links.get(current.getParentId());
        }
        return false;
    }

    private boolean publishedQuestionPath(
            QuestionBlockEntity link, Map<UUID, QuestionBlockEntity> links) {
        Set<UUID> seen = new HashSet<>();
        QuestionBlockEntity current = link;
        while (current != null && seen.add(current.getId())) {
            if (current.getDeletedAt() != null || current.getStatus() != ChapterStatus.PUBLISHED
                    || !link.getChapterId().equals(current.getChapterId())) {
                return false;
            }
            if (current.getParentId() == null) {
                return true;
            }
            current = links.get(current.getParentId());
        }
        return false;
    }

    private Map<UUID, List<QuestionOptionEntity>> optionsByQuestion(List<UUID> ids) {
        return questionOptionRepository.findByQuestionIdInAndDeletedAtIsNull(ids).stream()
                .filter(option -> option.getDeletedAt() == null)
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
    }

    private QuestionData prepare(
            QuestionEntity question, List<QuestionOptionEntity> rawOptions, String license) {
        UUID id = question.getId();
        if (question.getType() == null || question.getContent() == null || question.getContent().isBlank()) {
            throw invalid(id, "Question type and content are required");
        }
        if (rawOptions.size() < 2) {
            throw invalid(id, "At least two published options are required");
        }
        Set<UUID> optionIds = new HashSet<>();
        for (QuestionOptionEntity option : rawOptions) {
            if (option.getId() == null || !optionIds.add(option.getId())
                    || !id.equals(option.getQuestionId()) || option.getStatus() != QuestionStatus.PUBLISHED
                    || option.getIsCorrect() == null || option.getContent() == null || option.getContent().isBlank()
                    || (option.getSortOrder() != null && option.getSortOrder() < 0)) {
                throw invalid(id, "Invalid or unpublished option: " + option.getId());
            }
        }
        long correctCount = rawOptions.stream().filter(option -> Boolean.TRUE.equals(option.getIsCorrect())).count();
        if (correctCount == 0
                || (question.getType() == QuestionType.SINGLE_CHOICE && correctCount != 1)
                || (question.getType() == QuestionType.TRUE_FALSE
                    && (rawOptions.size() != 2 || correctCount != 1))) {
            throw invalid(id, "Invalid correct-answer configuration");
        }
        List<InternalQuestionOptionResponse> options = rawOptions.stream()
                .sorted(Comparator.comparing(QuestionOptionEntity::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(QuestionOptionEntity::getId))
                .map(option -> InternalQuestionOptionResponse.builder()
                        .id(option.getId()).content(option.getContent())
                        .sortOrder(option.getSortOrder()).isCorrect(option.getIsCorrect()).build())
                .toList();
        List<String> media = question.getMediaFileIds() == null ? List.of() : question.getMediaFileIds();
        if (media.stream().anyMatch(value -> value == null || !MEDIA_ID.matcher(value).matches())) {
            throw invalid(id, "mediaFileIds must contain UUIDs, not URLs or filesystem paths");
        }
        Map<String, Object> metadata = question.getMetadata() == null ? Map.of() : question.getMetadata();
        if (metadata.get("sourceImagePath") instanceof String path && !path.isBlank() && media.isEmpty()) {
            throw invalid(id, "Imported image question has no media reference");
        }
        boolean critical;
        if (license != null) {
            critical = licenseRules(question).critical().contains(license);
        } else {
            Object value = metadata.get("isCritical");
            if (value != null && !(value instanceof Boolean)) {
                throw invalid(id, "isCritical must be a boolean");
            }
            critical = Boolean.TRUE.equals(value);
        }
        List<String> mediaIds = List.copyOf(media);
        return new QuestionData(question, options, critical, mediaIds,
                revision(question, options, critical, mediaIds, license));
    }

    private boolean matchesLicense(QuestionEntity question, String license) {
        return license == null || licenseRules(question).applicable().contains(license);
    }

    private LicenseRules licenseRules(QuestionEntity question) {
        Map<String, Object> metadata = question.getMetadata() == null ? Map.of() : question.getMetadata();
        Set<String> applicable = licenseSet(metadata.get("applicableLicenses"), question.getId(), "applicableLicenses");
        Set<String> critical = licenseSet(metadata.get("criticalLicenses"), question.getId(), "criticalLicenses");
        if (!applicable.containsAll(critical)) {
            throw invalid(question.getId(), "criticalLicenses must be a subset of applicableLicenses");
        }
        return new LicenseRules(applicable, critical);
    }

    private Set<String> licenseSet(Object value, UUID questionId, String field) {
        if (!(value instanceof List<?> values)) {
            throw invalid(questionId, "Missing license classification: " + field);
        }
        Set<String> result = new HashSet<>();
        for (Object item : values) {
            if (!(item instanceof String license) || !LICENSE_CLASSES.contains(license)) {
                throw invalid(questionId, "Invalid license classification: " + field);
            }
            result.add(license);
        }
        return result;
    }

    private String normalizeLicense(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String license = value.trim().toUpperCase(Locale.ROOT);
        if (!LICENSE_CLASSES.contains(license)) {
            throw new BusinessValidationException("Invalid license class: " + value);
        }
        return license;
    }

    private String revision(QuestionEntity question, List<InternalQuestionOptionResponse> options,
            boolean critical, List<String> mediaIds, String license) {
        Map<String, Object> snapshot = new TreeMap<>();
        snapshot.put("id", question.getId());
        snapshot.put("type", question.getType().name());
        snapshot.put("content", question.getContent());
        snapshot.put("status", question.getStatus().name());
        snapshot.put("mediaFileIds", mediaIds);
        snapshot.put("options", options);
        snapshot.put("explanations", question.getExplanations());
        snapshot.put("metadata", question.getMetadata());
        snapshot.put("licenseClass", license);
        snapshot.put("isCritical", critical);
        try {
            byte[] content = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(snapshot);
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate question revision", exception);
        }
    }

    private BusinessValidationException invalid(UUID questionId, String message) {
        return new BusinessValidationException(message + " [questionId=" + questionId + "]");
    }

    private record LicenseRules(Set<String> applicable, Set<String> critical) { }

    private record QuestionData(QuestionEntity question, List<InternalQuestionOptionResponse> options,
            boolean critical, List<String> mediaFileIds, String revision) { }
}
