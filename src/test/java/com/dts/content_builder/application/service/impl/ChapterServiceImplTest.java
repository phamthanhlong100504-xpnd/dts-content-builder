package com.dts.content_builder.application.service.impl;

import com.dts.content_builder.api.form.*;
import com.dts.content_builder.api.response.*;
import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.ChapterMapper;
import com.dts.content_builder.domain.entity.ChapterEntity;
import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.repository.ChapterRepository;
import com.dts.content_builder.domain.repository.QuestionBlockRepository;
import com.dts.content_builder.domain.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChapterServiceImplTest {

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private QuestionBlockRepository questionBlockRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ChapterMapper chapterMapper;

    @InjectMocks
    private ChapterServiceImpl chapterService;

    private UUID userId;
    private UUID chapterId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
    }

    // =========================================================================
    // Phase 1: createDraftChapter, createPublishedChapter, getChapterDetail, listChapters
    // =========================================================================

    @Test
    @DisplayName("createDraftChapter - Path 1: Happy Case")
    void testCreateDraftChapter_Success() {
        CreateDraftChapterRequest request = new CreateDraftChapterRequest();
        ChapterEntity entity = new ChapterEntity();
        
        when(chapterMapper.toEntity(request)).thenReturn(entity);
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(chapterMapper.toResponse(any(ChapterEntity.class))).thenReturn(new ChapterResponse());

        ChapterResponse response = chapterService.createDraftChapter(request, userId);

        assertNotNull(response);
        assertEquals(ChapterStatus.DRAFT, entity.getStatus());
        assertEquals(userId, entity.getCreatedBy());
        assertNotNull(entity.getId());
        verify(chapterRepository).save(entity);
    }

    @Test
    @DisplayName("createPublishedChapter - Path 1: Negative Case (Empty Blocks)")
    void testCreatePublishedChapter_EmptyBlocks() {
        CreatePublishedChapterRequest request = new CreatePublishedChapterRequest();
        request.setQuestionBlocks(Collections.emptyList());

        BusinessValidationException exception = assertThrows(BusinessValidationException.class, 
            () -> chapterService.createPublishedChapter(request, userId));
            
        assertEquals("Cannot publish chapter without valid question blocks.", exception.getMessage());
    }

    @Test
    @DisplayName("createPublishedChapter - Path 2: Happy Case")
    void testCreatePublishedChapter_Success() {
        CreatePublishedChapterRequest request = new CreatePublishedChapterRequest();
        QuestionBlockItemRequest blockReq = new QuestionBlockItemRequest();
        blockReq.setQuestionId(UUID.randomUUID());
        request.setQuestionBlocks(List.of(blockReq));

        ChapterEntity chapter = new ChapterEntity();
        QuestionBlockEntity block = new QuestionBlockEntity();
        ChapterDetailResponse detailResponse = new ChapterDetailResponse();
        
        when(chapterMapper.toEntity(request)).thenReturn(chapter);
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(i -> i.getArgument(0));
        
        QuestionEntity question = new QuestionEntity();
        when(questionRepository.findByIdAndDeletedAtIsNull(blockReq.getQuestionId())).thenReturn(Optional.of(question));
        
        when(chapterMapper.toEntity(blockReq)).thenReturn(block);
        when(questionBlockRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        
        when(chapterMapper.toDetailResponse(any(ChapterEntity.class))).thenReturn(detailResponse);
        when(chapterMapper.toResponseList(anyList())).thenReturn(List.of(new QuestionBlockResponse()));

        ChapterDetailResponse response = chapterService.createPublishedChapter(request, userId);

        assertNotNull(response);
        assertEquals(ChapterStatus.PUBLISHED, chapter.getStatus());
        verify(chapterRepository).save(any());
        verify(questionBlockRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("getChapterDetail - Path 1: Negative Case (Not Found)")
    void testGetChapterDetail_NotFound() {
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, 
            () -> chapterService.getChapterDetail(chapterId));
    }

    @Test
    @DisplayName("getChapterDetail - Path 2: Happy Case")
    void testGetChapterDetail_Success() {
        ChapterEntity chapter = new ChapterEntity();
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(chapter));
        when(questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(chapterId))
            .thenReturn(Collections.emptyList());
        when(chapterMapper.toDetailResponse(chapter)).thenReturn(new ChapterDetailResponse());
        
        ChapterDetailResponse response = chapterService.getChapterDetail(chapterId);
        assertNotNull(response);
    }

    @Test
    @DisplayName("listChapters - Path 1: Happy Case")
    void testListChapters_Success() {
        Page<ChapterEntity> page = new PageImpl<>(List.of(new ChapterEntity()));
        when(chapterRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        
        PageResponse<ChapterResponse> response = chapterService.listChapters("kw", "DRAFT", userId, 0, 10);
        assertNotNull(response);
    }
    // =========================================================================
    // Phase 2: updateChapter, deleteChapter
    // =========================================================================

    @Test
    @DisplayName("updateChapter - Path 1: Negative Case (Not Found)")
    void testUpdateChapter_NotFound() {
        UpdateChapterRequest request = new UpdateChapterRequest();
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, 
            () -> chapterService.updateChapter(chapterId, request, userId));
        assertEquals("Chapter not found with ID: " + chapterId, exception.getMessage());
    }

    @Test
    @DisplayName("updateChapter - Path 2: Negative Case (DRAFT to PUBLISHED with 0 blocks)")
    void testUpdateChapter_PublishWithoutBlocks() {
        UpdateChapterRequest request = new UpdateChapterRequest();
        request.setStatus(ChapterStatus.PUBLISHED);

        ChapterEntity chapter = new ChapterEntity();
        chapter.setStatus(ChapterStatus.DRAFT);

        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(chapter));
        when(questionBlockRepository.countByChapterIdAndDeletedAtIsNull(chapterId)).thenReturn(0L);

        BusinessValidationException exception = assertThrows(BusinessValidationException.class, 
            () -> chapterService.updateChapter(chapterId, request, userId));
        assertEquals("Cannot publish chapter without at least 1 question block.", exception.getMessage());
    }

    @Test
    @DisplayName("updateChapter - Path 3: Happy Case")
    void testUpdateChapter_Success() {
        UpdateChapterRequest request = new UpdateChapterRequest();
        request.setStatus(ChapterStatus.PUBLISHED);

        ChapterEntity chapter = new ChapterEntity();
        chapter.setStatus(ChapterStatus.DRAFT);

        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(chapter));
        when(questionBlockRepository.countByChapterIdAndDeletedAtIsNull(chapterId)).thenReturn(5L);
        when(chapterRepository.save(any(ChapterEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(chapterMapper.toResponse(any(ChapterEntity.class))).thenReturn(new ChapterResponse());

        ChapterResponse response = chapterService.updateChapter(chapterId, request, userId);

        assertNotNull(response);
        verify(chapterMapper).updateEntity(chapter, request);
        verify(chapterRepository).save(chapter);
        assertEquals(userId, chapter.getUpdatedBy());
        assertNotNull(chapter.getUpdatedAt());
    }

    @Test
    @DisplayName("deleteChapter - Path 1: Negative Case (Not Found)")
    void testDeleteChapter_NotFound() {
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, 
            () -> chapterService.deleteChapter(chapterId, userId));
        assertEquals("Chapter not found with ID: " + chapterId, exception.getMessage());
    }

    @Test
    @DisplayName("deleteChapter - Path 2: Negative Case (Not DRAFT)")
    void testDeleteChapter_NotDraft() {
        ChapterEntity chapter = new ChapterEntity();
        chapter.setStatus(ChapterStatus.PUBLISHED);
        
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(chapter));

        BusinessValidationException exception = assertThrows(BusinessValidationException.class, 
            () -> chapterService.deleteChapter(chapterId, userId));
        assertEquals("Only DRAFT chapters can be soft-deleted. Archive it instead.", exception.getMessage());
    }

    @Test
    @DisplayName("deleteChapter - Path 3: Happy Case")
    void testDeleteChapter_Success() {
        ChapterEntity chapter = new ChapterEntity();
        chapter.setStatus(ChapterStatus.DRAFT);
        
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(chapter));

        assertDoesNotThrow(() -> chapterService.deleteChapter(chapterId, userId));

        verify(chapterRepository).softDeleteChapter(chapterId, userId);
        verify(questionBlockRepository).softDeleteByChapterId(chapterId, userId);
    }
    // =========================================================================
    // Phase 3: Question Block Management
    // =========================================================================

    @Test
    @DisplayName("addQuestionBlock - Path 1: Negative Case (Chapter Not Found)")
    void testAddQuestionBlock_ChapterNotFound() {
        CreateQuestionBlockRequest request = new CreateQuestionBlockRequest();
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, 
            () -> chapterService.addQuestionBlock(chapterId, request, userId));
        assertEquals("Chapter not found with ID: " + chapterId, exception.getMessage());
    }

    @Test
    @DisplayName("addQuestionBlock - Path 2: Negative Case (Question Not Found)")
    void testAddQuestionBlock_QuestionNotFound() {
        CreateQuestionBlockRequest request = new CreateQuestionBlockRequest();
        request.setQuestionId(UUID.randomUUID());
        
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(new ChapterEntity()));
        when(questionRepository.findByIdAndDeletedAtIsNull(request.getQuestionId())).thenReturn(Optional.empty());

        BusinessValidationException exception = assertThrows(BusinessValidationException.class, 
            () -> chapterService.addQuestionBlock(chapterId, request, userId));
        assertEquals("Question not found with ID: " + request.getQuestionId(), exception.getMessage());
    }

    @Test
    @DisplayName("addQuestionBlock - Path 3: Happy Case (Auto Sort Order)")
    void testAddQuestionBlock_Success_AutoSortOrder() {
        CreateQuestionBlockRequest request = new CreateQuestionBlockRequest();
        request.setTitle("Title");
        
        when(chapterRepository.findByIdAndDeletedAtIsNull(chapterId)).thenReturn(Optional.of(new ChapterEntity()));
        when(questionBlockRepository.countByChapterIdAndDeletedAtIsNull(chapterId)).thenReturn(2L);
        when(questionBlockRepository.save(any(QuestionBlockEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(chapterMapper.toResponse(any(QuestionBlockEntity.class))).thenReturn(new QuestionBlockResponse());

        QuestionBlockResponse response = chapterService.addQuestionBlock(chapterId, request, userId);

        assertNotNull(response);
        verify(questionBlockRepository).save(argThat(b -> b.getSortOrder() == 2 && b.getTitle().equals("Title")));
    }

    @Test
    @DisplayName("updateQuestionBlock - Path 1: Negative Case (Block Not Found)")
    void testUpdateQuestionBlock_BlockNotFound() {
        UUID blockId = UUID.randomUUID();
        UpdateQuestionBlockRequest request = new UpdateQuestionBlockRequest();
        when(questionBlockRepository.findByIdAndChapterIdAndDeletedAtIsNull(blockId, chapterId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, 
            () -> chapterService.updateQuestionBlock(chapterId, blockId, request, userId));
        assertEquals("Question Block not found with ID: " + blockId, exception.getMessage());
    }

    @Test
    @DisplayName("updateQuestionBlock - Path 2: Happy Case")
    void testUpdateQuestionBlock_Success() {
        UUID blockId = UUID.randomUUID();
        UpdateQuestionBlockRequest request = new UpdateQuestionBlockRequest();
        request.setTitle("New Title");
        
        QuestionBlockEntity block = new QuestionBlockEntity();
        block.setId(blockId);
        
        when(questionBlockRepository.findByIdAndChapterIdAndDeletedAtIsNull(blockId, chapterId)).thenReturn(Optional.of(block));
        when(questionBlockRepository.save(any(QuestionBlockEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(chapterMapper.toResponse(any(QuestionBlockEntity.class))).thenReturn(new QuestionBlockResponse());

        QuestionBlockResponse response = chapterService.updateQuestionBlock(chapterId, blockId, request, userId);

        assertNotNull(response);
        assertEquals("New Title", block.getTitle());
        assertEquals(userId, block.getUpdatedBy());
        verify(questionBlockRepository).save(block);
    }

    @Test
    @DisplayName("deleteQuestionBlock - Path 1: Happy Case")
    void testDeleteQuestionBlock_Success() {
        UUID blockId = UUID.randomUUID();
        QuestionBlockEntity block = new QuestionBlockEntity();
        
        when(questionBlockRepository.findByIdAndChapterIdAndDeletedAtIsNull(blockId, chapterId)).thenReturn(Optional.of(block));

        assertDoesNotThrow(() -> chapterService.deleteQuestionBlock(chapterId, blockId, userId));

        assertNotNull(block.getDeletedAt());
        assertEquals(userId, block.getUpdatedBy());
        verify(questionBlockRepository).save(block);
    }

    @Test
    @DisplayName("reorderQuestionBlocks - Path 1: Happy Case")
    void testReorderQuestionBlocks_Success() {
        UUID block1Id = UUID.randomUUID();
        QuestionBlockEntity block1 = new QuestionBlockEntity();
        block1.setId(block1Id);
        
        ReorderItem item1 = new ReorderItem();
        item1.setId(block1Id);
        item1.setSortOrder(5);

        List<ReorderItem> request = List.of(item1);
        List<QuestionBlockEntity> dbBlocks = List.of(block1);

        when(questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(chapterId))
            .thenReturn(dbBlocks);
        
        when(chapterMapper.toResponseList(anyList())).thenReturn(List.of(new QuestionBlockResponse()));

        List<QuestionBlockResponse> response = chapterService.reorderQuestionBlocks(chapterId, request, userId);

        assertNotNull(response);
        assertEquals(5, block1.getSortOrder());
        assertEquals(userId, block1.getUpdatedBy());
        verify(questionBlockRepository).saveAll(anyList());
    }
}
